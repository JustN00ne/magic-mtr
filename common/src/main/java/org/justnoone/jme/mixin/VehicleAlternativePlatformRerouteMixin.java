package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.Overtaking;
import org.justnoone.jme.rail.PlatformClaims;
import org.justnoone.jme.rail.RouteTimetableRegistry;
import org.justnoone.jme.rail.TimetableAligned;
import org.justnoone.jme.rail.TimetableProgressMap;
import org.justnoone.jme.util.PathCache;
import org.justnoone.jme.util.PositionPathFinder;
import org.justnoone.jme.util.SavedRailPathFinder;
import org.mtr.core.Main;
import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.data.VehiclePosition;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Mid-route alternative platform rerouting.
 *
 * <p>When a train is stopped at a signal right before a station throat (often "green" but the
 * train can't clear the block), rebuild the remaining path to a usable alternative platform.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class VehicleAlternativePlatformRerouteMixin implements TimetableAligned {

    @Shadow
    @Final
    private Siding siding;

    // Keeps a rebuilt path on the siding's timetable; only valid for the path it was made for.
    @Unique
    private TimetableProgressMap jme$timetableMap;
    @Unique
    private Object jme$timetableMapPath;

    @Override
    public void jme$alignTimetable(List<PathData> oldPath, List<PathData> newPath, int fromIndex) {
        final TimetableProgressMap previous = jme$timetableMapPath == oldPath ? jme$timetableMap : null;
        jme$timetableMap = TimetableProgressMap.create(previous, oldPath, newPath, fromIndex);
        jme$timetableMapPath = newPath;
    }

    @Override
    public double jme$toTimetableProgress(double railProgress) {
        final VehicleExtraData vehicleExtraData = ((Vehicle) (Object) this).vehicleExtraData;
        return vehicleExtraData != null && jme$timetableMapPath == vehicleExtraData.immutablePath ? TimetableProgressMap.map(jme$timetableMap, railProgress) : railProgress;
    }

    /**
     * MTR reads the timetable (delay, predicted arrivals, catch-up speed) by distance along the path.
     */
    @ModifyVariable(method = "getTimeAlongRoute", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private double jme$readTimetableAtScheduledProgress(double checkRailProgress) {
        return jme$toTimetableProgress(checkRailProgress);
    }

    @Unique
    private long jme$lastRerouteAttemptMillis;

    @Unique
    private static final long REROUTE_COOLDOWN_MILLIS = 10_000;

    @Unique
    private static final long PATHFIND_TIME_BUDGET_MILLIS = 120;

    @Unique
    private static final int MAX_PATHFIND_TICK_CALLS = 48;

    @Unique
    private static volatile Field jme$dataField;

    @Unique
    private static volatile Field jme$railProgressField;

    // Early assignment: a train picks the platform of its next stop while it is this many path nodes (MTR path
    // segments) away, instead of only when it is already held at a signal in front of the station.
    @Unique
    private static final int EARLY_LOOKAHEAD_NODES = 35;

    // A route through an alternative may be at most this much longer than the scheduled one (factor, plus metres).
    @Unique
    private static final double MAX_DETOUR_FACTOR = 1.5;
    @Unique
    private static final double MAX_DETOUR_EXTRA_METERS = 100;

    @Unique
    private static final long EARLY_RETRY_MILLIS = 2_000;

    // Extra distance kept between the train's braking point and the first replaced path segment.
    @Unique
    private static final double EARLY_PATCH_MARGIN = 2;

    // The stop (path and index) the early check last ran for, and whether its platform is settled.
    @Unique
    private Object jme$earlyPath;
    @Unique
    private int jme$earlyStopPathIndex = -1;
    @Unique
    private boolean jme$earlySettled;
    @Unique
    private long jme$earlyRetryAtMillis;
    // The platform this train currently claims (its next stop, or the stop it stands at).
    @Unique
    private long jme$claimedPlatformId;
    @Unique
    private long jme$lastStateLogMillis;

    /**
     * Claims the platform of the train's next stop and, if another train already claimed it, sends this train to a
     * free configured alternative while it can still change track (beyond its braking distance). Once the platform of
     * a stop is settled it is kept; the reroute at a signal ({@link #jme$rerouteIfStuckBeforePlatform}) stays as the
     * fallback for trains that could not be helped early.
     * <p>
     * A train that has its platform is also sent to an alternative when a train of a higher priority behind it would
     * catch up with it after the station ({@link Overtaking}); it then waits there until that train has passed.
     */
    @Inject(method = "simulate", at = @At("TAIL"), remap = false, require = 0)
    private void jme$assignPlatformEarly(
            long millisElapsed,
            @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
            @Nullable Long2LongAVLTreeMap vehicleTimesAlongRoute,
            CallbackInfo ci
    ) {
        final Vehicle self = (Vehicle) (Object) this;
        final long vehicleId = self.getId();
        if (!AlternativePlatformRegistry.isEnabled() || !self.getIsOnRoute()) {
            jme$releaseClaim(vehicleId, "not on route");
            return;
        }
        final Data data = jme$getData(self);
        if (!(data instanceof Simulator)) {
            return;
        }
        final VehicleExtraData vehicleExtraData = self.vehicleExtraData;
        final List<PathData> path = vehicleExtraData == null ? null : vehicleExtraData.immutablePath;
        if (path == null || path.isEmpty()) {
            return;
        }

        final VehicleScheduleAccessor schedule = (VehicleScheduleAccessor) (Object) this;
        final double railProgress = schedule.jme$getRailProgress();
        final int currentIndex = Utilities.getIndexFromConditionalList(path, railProgress);
        final int stopPathIndex = jme$findCurrentOrNextStop(path, currentIndex, railProgress);
        if (stopPathIndex < 0) {
            jme$releaseClaim(vehicleId, "no stop within reach");
            return;
        }
        final long now = System.currentTimeMillis();
        final long targetPlatformId = path.get(stopPathIndex).getSavedRailBaseId();
        if (stopPathIndex - currentIndex > EARLY_LOOKAHEAD_NODES) {
            jme$releaseClaim(vehicleId, "next stop beyond look-ahead");
            return;
        }

        // Keep (or try to take) the claim on the platform the path stops at.
        if (jme$claimedPlatformId != targetPlatformId) {
            jme$releaseClaim(vehicleId, "next stop changed");
        }
        if (PlatformClaims.claim(targetPlatformId, vehicleId, now)) {
            jme$claimedPlatformId = targetPlatformId;
        }

        if (jme$earlyPath != path || jme$earlyStopPathIndex != stopPathIndex) {
            jme$earlyPath = path;
            jme$earlyStopPathIndex = stopPathIndex;
            jme$earlySettled = false;
            jme$earlyRetryAtMillis = 0;
        }
        if (JME_DEBUG && now - jme$lastStateLogMillis >= 5_000) {
            jme$lastStateLogMillis = now;
            final PathData currentPathData = path.get(Math.min(currentIndex, path.size() - 1));
            jme$debug("state vehicle {}: target {} index {}/{} stop at {} progress {} (segment {}..{}, speed limit {} km/h, start {}, end {}) speed {} km/h",
                    vehicleId, targetPlatformId, currentIndex, path.size(), stopPathIndex, Math.round(railProgress * 10) / 10D,
                    Math.round(currentPathData.getStartDistance() * 10) / 10D, Math.round(currentPathData.getEndDistance() * 10) / 10D,
                    Math.round(currentPathData.getSpeedLimitKilometersPerHour()), currentPathData.getOrderedPosition1(), currentPathData.getOrderedPosition2(),
                    Math.round(schedule.jme$getSpeed() * 3600));
            if (siding != null) {
                final double scheduled = siding.getTimeAlongRoute(jme$toTimetableProgress(railProgress) - (schedule.jme$getSpeed() == 0 ? 1 : 0)) + schedule.jme$getElapsedDwellTime();
                jme$debug("state vehicle {}: MTR deviation {} s, live delay {} s (elapsed dwell {} s)", vehicleId, ((VehicleDelayAccessor) (Object) this).jme$getDeviation() / 1000, org.justnoone.jme.util.TrainDelay.liveDelayMillis(data.getCurrentMillis(), schedule.jme$getSidingDepartureTime(), (long) scheduled, 86_400_000L) / 1000, schedule.jme$getElapsedDwellTime() / 1000);
            }
        }
        if (jme$earlySettled || now < jme$earlyRetryAtMillis) {
            return;
        }
        jme$earlyRetryAtMillis = now + EARLY_RETRY_MILLIS;

        final boolean ownsTarget = jme$claimedPlatformId == targetPlatformId;
        final int ownPriority = ownsTarget && RouteTimetableRegistry.hasHeadcodeClasses() ? Overtaking.trainPriority(data, self) : 0;
        if (ownsTarget && ownPriority == 0) {
            // The platform in the path is ours, and no train could overtake this one: keep it.
            jme$debug("vehicle {} keeps platform {} (claimed, stop in {} nodes)", vehicleId, targetPlatformId, stopPathIndex - currentIndex);
            jme$earlySettled = true;
            return;
        }
        if (currentIndex >= stopPathIndex) {
            // Already on the platform track: nothing can change any more.
            jme$debug("vehicle {} already on platform {} track, cannot change", vehicleId, targetPlatformId);
            jme$earlySettled = true;
            return;
        }

        final Depot depot = data.depotIdMap.get(vehicleExtraData.getDepotId());
        final PathData stopPathData = path.get(stopPathIndex);
        final VehiclePlatformRouteInfoAccessor routeInfo = depot == null ? null : (VehiclePlatformRouteInfoAccessor) (Object) depot.getVehiclePlatformRouteInfo(stopPathData.getStopIndex());
        final Platform scheduledPrimaryPlatform = routeInfo == null ? null : routeInfo.jme$getThisPlatform();
        Route route = routeInfo == null ? null : routeInfo.jme$getThisRoute();
        if (route == null && routeInfo != null) {
            route = routeInfo.jme$getNextRoute();
        }
        if (scheduledPrimaryPlatform == null || route == null) {
            jme$debug("vehicle {} stop {}: no route info", vehicleId, targetPlatformId);
            jme$earlySettled = true;
            return;
        }
        final List<Long> candidateIds = AlternativePlatformRegistry.getCandidatePlatformIds(route, scheduledPrimaryPlatform);
        if (candidateIds.size() <= 1) {
            jme$debug("vehicle {} stop {}: no alternatives configured (scheduled {}, route {})", vehicleId, targetPlatformId, scheduledPrimaryPlatform.getId(), route.getId());
            jme$earlySettled = true;
            return;
        }

        final long[] stop2 = new long[3];
        if (!jme$findNextSavedRailStop(path, stopPathIndex + 1, stop2)) {
            jme$debug("vehicle {} stop {}: no following stop to route on to", vehicleId, targetPlatformId);
            jme$earlySettled = true;
            return;
        }
        final SavedRailBase<?, ?> secondStopSavedRail = jme$getSavedRailBase(data, stop2[0]);
        final int stop2PathIndex = (int) stop2[1];
        final VehicleExtraDataAccessor extraDataAccessor = (VehicleExtraDataAccessor) vehicleExtraData;
        final int repeatIndex1 = extraDataAccessor.jme$getRepeatIndex1();
        final int repeatIndex2 = extraDataAccessor.jme$getRepeatIndex2();

        // Only path segments the train can still stop before may change.
        final double speed = schedule.jme$getSpeed();
        final double brakingDistance = vehicleExtraData.getDeceleration() > 0 ? 0.5 * speed * speed / vehicleExtraData.getDeceleration() : 0;
        int patchStartIndex = -1;
        for (int i = Math.max(1, currentIndex + 1); i <= stopPathIndex; i++) {
            if (path.get(i).getStartDistance() >= railProgress + brakingDistance + EARLY_PATCH_MARGIN) {
                patchStartIndex = i;
                break;
            }
        }
        if (secondStopSavedRail == null || patchStartIndex < 0
                || repeatIndex2 > 0 && (stop2PathIndex >= repeatIndex2 || patchStartIndex < repeatIndex1 && stop2PathIndex >= repeatIndex1)) {
            // No following stop to route on to, too close to change, or the change would cross the loop point.
            jme$debug("vehicle {} stop {}: cannot change now (stop2 {}, patchStart {}, current {}, stop {}, repeat {}/{}, braking {})", vehicleId, targetPlatformId, secondStopSavedRail != null, patchStartIndex, currentIndex, stopPathIndex, repeatIndex1, repeatIndex2, brakingDistance);
            return;
        }

        // A train that has its platform only moves to let a faster one overtake. Until it is too close to change, this
        // is checked again every few seconds, as the faster train may still be catching up.
        Overtaking.Overtaker overtaker = null;
        if (ownsTarget) {
            overtaker = Overtaking.findOvertaker(data, self, path, railProgress, stopPathIndex, stop2PathIndex, ownPriority);
            if (overtaker == null) {
                jme$debug("vehicle {} keeps platform {} (claimed, no faster train catching up)", vehicleId, targetPlatformId);
                return;
            }
            jme$debug("vehicle {} stop {}: vehicle {} of a higher priority would catch up, looking for a platform to wait at", vehicleId, targetPlatformId, overtaker.vehicle.getId());
        }

        // Primary platform first, then the alternatives in an order fixed per train and stop.
        final long selectionSeed = jme$mix64(vehicleId ^ depot.getId() ^ route.getId() ^ ((long) stopPathData.getStopIndex() << 32));
        final List<Long> orderedCandidates = new ArrayList<>(new LinkedHashSet<>(candidateIds));
        orderedCandidates.sort(Comparator
                .comparingInt((Long candidateId) -> candidateId == scheduledPrimaryPlatform.getId() ? 0 : 1)
                .thenComparingLong(candidateId -> jme$mix64(selectionSeed ^ candidateId)));

        for (final long candidateId : orderedCandidates) {
            // MTR ids are random longs and may be negative: only 0 and the "every platform" wildcard are not platforms.
            if (candidateId == targetPlatformId || candidateId == 0 || candidateId == AlternativePlatformRegistry.ALL_STATION_PLATFORMS || PlatformClaims.isClaimedByOther(candidateId, vehicleId, now)) {
                jme$debug("vehicle {} stop {}: skip candidate {} (current target or claimed by {})", vehicleId, targetPlatformId, candidateId, PlatformClaims.getHolder(candidateId, now));
                continue;
            }
            final Platform candidatePlatform = data.platformIdMap.get(candidateId);
            if (candidatePlatform == null) {
                continue;
            }
            final PathData patchStart = path.get(patchStartIndex);
            final VehicleExtraData candidateExtraData = jme$tryBuildPatchedVehicleExtraData(
                    data,
                    self.getTransportMode(),
                    patchStart.getStartDistance(),
                    vehicleExtraData,
                    depot,
                    patchStartIndex,
                    patchStart,
                    stopPathData.getStopIndex(),
                    (int) stop2[2],
                    stop2PathIndex,
                    candidatePlatform,
                    secondStopSavedRail
            );
            final int candidateStopPathIndex = candidateExtraData == null ? -1 : jme$findStopPathIndex(candidateExtraData.immutablePath, patchStartIndex, candidateId);
            if (overtaker != null && candidateExtraData != null && (candidateStopPathIndex < 0 || overtaker.rails.contains(candidateExtraData.immutablePath.get(candidateStopPathIndex).getRail()))) {
                // The faster train runs on this platform's track too: no use waiting there.
                jme$debug("vehicle {} stop {}: candidate {} is on the overtaking train's path", vehicleId, targetPlatformId, candidateId);
                continue;
            }
            if (candidateExtraData == null || !PlatformClaims.claim(candidateId, vehicleId, now)) {
                jme$debug("vehicle {} stop {}: candidate {} failed (path built: {})", vehicleId, targetPlatformId, candidateId, candidateExtraData != null);
                continue;
            }

            jme$releaseClaim(vehicleId, "reassigned");
            jme$claimedPlatformId = candidateId;
            ((VehicleMutableExtraDataAccessor) (Object) this).jme$setVehicleExtraData(candidateExtraData);
            jme$alignTimetable(path, candidateExtraData.immutablePath, patchStartIndex);
            ((VehicleSignalInvoker) (Object) this).jme$setNextStoppingIndex();
            jme$earlyPath = candidateExtraData.immutablePath;
            jme$earlySettled = true;
            if (overtaker != null) {
                Overtaking.hold(vehicleId, overtaker, candidateExtraData.immutablePath, candidateStopPathIndex, now);
            }
            jme$debug("vehicle {} assigned platform {} instead of {} ahead of the station{}", vehicleId, candidateId, targetPlatformId, overtaker == null ? "" : " to be overtaken");
            if (JME_DEBUG) {
                for (int i = Math.max(0, patchStartIndex - 2); i < Math.min(candidateExtraData.immutablePath.size(), stop2PathIndex + 4); i++) {
                    final PathData pathData = candidateExtraData.immutablePath.get(i);
                    jme$debug("  new path [{}] {} -> {} dist {}..{} limit {} km/h dwell {} stop {} rail {}", i, pathData.getOrderedPosition1(), pathData.getOrderedPosition2(),
                            Math.round(pathData.getStartDistance()), Math.round(pathData.getEndDistance()), Math.round(pathData.getSpeedLimitKilometersPerHour()), pathData.getDwellTime(), pathData.getSavedRailBaseId(), pathData.getRail() != null);
                }
            }
            return;
        }
        // No free alternative: keep the path and try again shortly (the claim on the target is retried every tick).
        jme$debug("vehicle {} stop {}: no free alternative among {}", vehicleId, targetPlatformId, orderedCandidates);
    }

    @Unique
    private static int jme$findStopPathIndex(List<PathData> path, int fromIndex, long platformId) {
        for (int i = Math.max(0, fromIndex); i < path.size(); i++) {
            if (path.get(i).getSavedRailBaseId() == platformId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Keeps a train that waits to be overtaken at its platform: its dwell time does not run out (MTR closes the doors
     * and departs once it does) until the faster train has passed.
     */
    @Inject(method = "simulateStopped", at = @At("HEAD"), remap = false, require = 0)
    private void jme$waitToBeOvertaken(
            long millisElapsed,
            @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
            int currentIndex,
            CallbackInfo ci
    ) {
        final Vehicle self = (Vehicle) (Object) this;
        // Client copies of vehicles have their own paths, which would end the server's hold.
        if (!(jme$getData(self) instanceof Simulator)) {
            return;
        }
        final VehicleExtraData vehicleExtraData = self.vehicleExtraData;
        if (vehicleExtraData == null || vehicleExtraData.immutablePath == null || !Overtaking.mustWait(self.getId(), vehicleExtraData.immutablePath, currentIndex, System.currentTimeMillis())) {
            return;
        }
        final PathData stop = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex);
        if (stop == null) {
            return;
        }
        // MTR closes the doors and leaves once the elapsed dwell time reaches this.
        final long doorsCloseAt = Math.max(stop.getDwellTime() / 2, stop.getDwellTime() - 4200);
        final VehicleScheduleAccessor schedule = (VehicleScheduleAccessor) (Object) this;
        if (doorsCloseAt > 1 && schedule.jme$getElapsedDwellTime() >= doorsCloseAt - 1) {
            schedule.jme$setElapsedDwellTime(doorsCloseAt - 1);
        }
    }

    // Decision log for diagnosing alternative platforms in a real world: set the environment variable
    // JME_DEBUG_ALTERNATIVES=1 before starting the game or server.
    @Unique
    private static final boolean JME_DEBUG = System.getenv("JME_DEBUG_ALTERNATIVES") != null;

    @Unique
    private static void jme$debug(String message, Object... arguments) {
        if (JME_DEBUG) {
            Main.LOGGER.info("[MAGIC alternatives] " + message, arguments);
        }
    }

    @Unique
    private void jme$releaseClaim(long vehicleId, String reason) {
        if (jme$claimedPlatformId != 0) {
            jme$debug("vehicle {} releases platform {} ({})", vehicleId, jme$claimedPlatformId, reason);
            PlatformClaims.release(jme$claimedPlatformId, vehicleId);
            jme$claimedPlatformId = 0;
        }
    }

    /**
     * @return the path index of the stop the train stands at (it stops at the end of the platform segment, so that
     * segment is behind it) or of its next stop within the early-assignment distance, or -1
     */
    @Unique
    private static int jme$findCurrentOrNextStop(List<PathData> path, int currentIndex, double railProgress) {
        // Runs every tick for every train: only look as far ahead as the early-assignment distance.
        final int end = Math.min(path.size() - 1, currentIndex + EARLY_LOOKAHEAD_NODES);
        for (int i = Math.max(0, currentIndex - 1); i <= end; i++) {
            final PathData pathData = path.get(i);
            if (pathData.getDwellTime() > 0 && pathData.getSavedRailBaseId() != 0 && (i >= currentIndex || railProgress <= pathData.getEndDistance() + 1e-3)) {
                return i;
            }
        }
        return -1;
    }

    @Inject(method = "simulateStopped", at = @At("HEAD"), remap = false, require = 0)
    private void jme$rerouteIfStuckBeforePlatform(
            long millisElapsed,
            @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
            int currentIndex,
            CallbackInfo ci
    ) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        final Vehicle self = (Vehicle) (Object) this;
        final VehicleSignalInvoker invoker = (VehicleSignalInvoker) (Object) this;
        final Data data = jme$getData(self);
        if (!(data instanceof Simulator)) {
            return;
        }
        final double railProgress = jme$getRailProgress(self);
        final TransportMode transportMode = self.getTransportMode();

        final VehicleExtraData vehicleExtraData = self.vehicleExtraData;
        if (vehicleExtraData == null || vehicleExtraData.immutablePath == null || vehicleExtraData.immutablePath.isEmpty()) {
            return;
        }
        if (vehiclePositions == null || vehiclePositions.size() < 2) {
            return;
        }

        final Depot depot = data.depotIdMap.get(vehicleExtraData.getDepotId());
        if (depot == null) {
            return;
        }

        // Only attempt reroute when stopped exactly behind a node (typical for signals).
        if (currentIndex < 0 || currentIndex >= vehicleExtraData.immutablePath.size()) {
            return;
        }
        final PathData pathData = vehicleExtraData.immutablePath.get(currentIndex);
        if (pathData == null) {
            return;
        }
        if (Math.abs(railProgress - pathData.getStartDistance()) > 1e-6) {
            return;
        }

        final PathData previousPathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex - 1);
        if (previousPathData != null && previousPathData.getDwellTime() > 0) {
            // Platform stop; don't reroute.
            return;
        }

        // This runs every simulation tick for every stopped vehicle. Do the cheap checks (cooldown, are alternatives
        // even configured for the next stop?) before the signal-block query, which walks the path and pre-reserves
        // blocks; it used to run first, for every stopped train on every route.
        final long vehicleId = self.getId();
        final long now = System.currentTimeMillis();
        if (now - jme$lastRerouteAttemptMillis < REROUTE_COOLDOWN_MILLIS) {
            return;
        }

        final long[] stop1 = new long[3];
        if (!jme$findNextSavedRailStop(vehicleExtraData.immutablePath, currentIndex, stop1)) {
            return;
        }
        final long stop1SavedRailBaseId = stop1[0];
        final int stop1PathIndex = (int) stop1[1];
        final int stop1StopIndex = (int) stop1[2];

        final Platform primaryTargetPlatform = data.platformIdMap.get(stop1SavedRailBaseId);
        if (primaryTargetPlatform == null) {
            return;
        }

        final long[] stop2 = new long[3];
        if (!jme$findNextSavedRailStop(vehicleExtraData.immutablePath, stop1PathIndex + 1, stop2)) {
            return;
        }
        final long stop2SavedRailBaseId = stop2[0];
        final int stop2PathIndex = (int) stop2[1];
        final int stop2StopIndex = (int) stop2[2];

        final VehicleExtraData.VehiclePlatformRouteInfo routeInfo = depot.getVehiclePlatformRouteInfo(stop1StopIndex);
        final VehiclePlatformRouteInfoAccessor routeInfoAccessor = (VehiclePlatformRouteInfoAccessor) (Object) routeInfo;
        final Platform scheduledPrimaryPlatform = routeInfoAccessor.jme$getThisPlatform();
        if (scheduledPrimaryPlatform == null) {
            return;
        }

        Route route = routeInfoAccessor.jme$getThisRoute();
        if (route == null) {
            route = routeInfoAccessor.jme$getNextRoute();
        }
        if (route == null) {
            route = routeInfoAccessor.jme$getPreviousRoute();
        }
        if (route == null) {
            return;
        }

        final List<Long> candidateIds = AlternativePlatformRegistry.getCandidatePlatformIds(route, scheduledPrimaryPlatform);
        if (candidateIds.size() <= 1) {
            return;
        }

        // Only now check whether the vehicle is actually held. Match vanilla simulateStopped(), which pre-reserves signals.
        final boolean isOpposite = previousPathData != null && previousPathData.isOppositeRail(pathData);
        final double checkRailProgress = railProgress + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0);
        final double blockedDistance = invoker.jme$railBlockedDistance(currentIndex, checkRailProgress, 0, vehiclePositions, true, false);
        if (blockedDistance < 0) {
            return;
        }
        jme$lastRerouteAttemptMillis = now;

        // Determine which platform we are currently heading towards (could already be an alternative).
        final long currentTargetPlatformId = stop1SavedRailBaseId;

        // Build a deterministic-but-varied candidate order to avoid always trying the same platform first.
        // Keep the ordering stable for this vehicle + stop; using wall-clock time here makes reroute attempts
        // non-deterministic and can cause flip-flopping across repeated attempts.
        final long selectionSeed = jme$mix64(vehicleId ^ depot.getId() ^ route.getId() ^ ((long) stop1StopIndex << 32));
        final LinkedHashSet<Long> uniqueCandidateIds = new LinkedHashSet<>(candidateIds);
        final List<Long> orderedCandidates = new ArrayList<>(uniqueCandidateIds);
        orderedCandidates.sort(Comparator
                .comparingLong((Long candidateId) -> jme$mix64(selectionSeed ^ candidateId))
        );

        final SavedRailBase<?, ?> secondStopSavedRail = jme$getSavedRailBase(data, stop2SavedRailBaseId);
        if (secondStopSavedRail == null) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason stop2-not-savedrail, stop2Id {}, idx {}, blockedDistance {})",
                        vehicleId,
                        stop2SavedRailBaseId,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }

        final VehicleExtraData originalExtraData = vehicleExtraData;

        int pathBuildFailed = 0;
        int remainedBlocked = 0;

        for (int i = 0; i < orderedCandidates.size(); i++) {
            final long candidateId = orderedCandidates.get(i);
            if (candidateId == 0 || candidateId == currentTargetPlatformId) {
                continue;
            }

            final Platform candidatePlatform = data.platformIdMap.get(candidateId);
            if (candidatePlatform == null) {
                continue;
            }

            final VehicleExtraData candidateExtraData = jme$tryBuildPatchedVehicleExtraData(
                    data,
                    transportMode,
                    railProgress,
                    originalExtraData,
                    depot,
                    currentIndex,
                    pathData,
                    stop1StopIndex,
                    stop2StopIndex,
                    stop2PathIndex,
                    candidatePlatform,
                    secondStopSavedRail
            );
            if (candidateExtraData == null) {
                pathBuildFailed++;
                continue;
            }

            ((VehicleMutableExtraDataAccessor) (Object) this).jme$setVehicleExtraData(candidateExtraData);
            try {
                invoker.jme$setNextStoppingIndex();
            } catch (Throwable ignored) {
            }

            final PathData patchedPrevious = Utilities.getElement(candidateExtraData.immutablePath, currentIndex - 1);
            final PathData patchedCurrent = Utilities.getElement(candidateExtraData.immutablePath, currentIndex);
            final boolean patchedOpposite = patchedPrevious != null && patchedCurrent != null && patchedPrevious.isOppositeRail(patchedCurrent);
            final double patchedCheckRailProgress = railProgress + (patchedOpposite ? candidateExtraData.getTotalVehicleLength() : 0);
            final double blockedAfterPatch = invoker.jme$railBlockedDistance(currentIndex, patchedCheckRailProgress, 0, vehiclePositions, true, false);
            if (blockedAfterPatch < 0) {
                jme$alignTimetable(originalExtraData.immutablePath, candidateExtraData.immutablePath, currentIndex);
                if (Main.LOGGER.isDebugEnabled()) {
                    Main.LOGGER.debug("[MAGIC] Vehicle {} rerouted at signal from platform {} to {}", vehicleId, currentTargetPlatformId, candidateId);
                }
                return;
            }
            remainedBlocked++;

            // Not usable; revert and keep searching.
            ((VehicleMutableExtraDataAccessor) (Object) this).jme$setVehicleExtraData(originalExtraData);
            try {
                invoker.jme$setNextStoppingIndex();
            } catch (Throwable ignored) {
            }
        }

        if (pathBuildFailed > 0 || remainedBlocked > 0) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} could not reroute at signal (target {}, candidates {}, stats {pathFail {}, stillBlocked {}})",
                        vehicleId,
                        currentTargetPlatformId,
                        orderedCandidates,
                        pathBuildFailed,
                        remainedBlocked
                );
            }
        }
    }

    @Unique
    private static VehicleExtraData jme$tryBuildPatchedVehicleExtraData(
            Data data,
            TransportMode transportMode,
            double railProgress,
            VehicleExtraData baseExtraData,
            Depot depot,
            int replaceStartIndex,
            PathData currentPathData,
            int stop1StopIndex,
            int stop2StopIndex,
            int stop2PathIndex,
            Platform candidatePlatform,
            SavedRailBase<?, ?> secondStopSavedRail
    ) {
        if (data == null || transportMode == null || baseExtraData == null || depot == null || currentPathData == null || candidatePlatform == null || secondStopSavedRail == null) {
            jme$debug("path build to {} failed: missing input", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        final ObjectArrayList<PathData> basePath = new ObjectArrayList<>(baseExtraData.immutablePath);
        if (replaceStartIndex < 0 || replaceStartIndex >= basePath.size()) {
            jme$debug("path build to {} failed: bad replace index", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }
        if (stop2PathIndex < 0 || stop2PathIndex >= basePath.size()) {
            jme$debug("path build to {} failed: bad stop2 index", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }
        if (stop2PathIndex <= replaceStartIndex) {
            jme$debug("path build to {} failed: stop2 before replace start", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        final Position startPosition = jme$getTravelStartPosition(currentPathData);
        if (startPosition == null) {
            jme$debug("path build to {} failed: no start position", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        // Leg: signal node -> candidate platform
        final int nodeToPlatformStopIndex = stop1StopIndex - 1;
        final ObjectArrayList<PathData> pathToCandidate = PositionPathFinder.findPath(
                data,
                transportMode,
                startPosition,
                // Leave the node in the train's direction of travel (no reversing on open track).
                currentPathData.getFacingStart(),
                candidatePlatform,
                nodeToPlatformStopIndex,
                depot.getCruisingAltitude(),
                PATHFIND_TIME_BUDGET_MILLIS,
                MAX_PATHFIND_TICK_CALLS
        );
        if (pathToCandidate.size() < 2) {
            jme$debug("path build to {} failed: no path from node to candidate", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        // Ensure the patched path actually starts from our current node. Allow changing the first segment
        // (station throat divergence), but don't allow immediately reversing back.
        if (!startPosition.equals(jme$getTravelStartPosition(pathToCandidate.get(0)))) {
            jme$debug("path build to {} failed: path to candidate starts elsewhere (wanted {}, got {} -> {}, {} segments)", candidatePlatform.getId(), startPosition, pathToCandidate.get(0).getOrderedPosition1(), pathToCandidate.get(0).getOrderedPosition2(), pathToCandidate.size());
            return null;
        }
        if (replaceStartIndex > 0) {
            final PathData previousPathData = basePath.get(replaceStartIndex - 1);
            if (previousPathData != null && jme$isInvalidOppositeTransition(previousPathData, pathToCandidate.get(0))) {
                jme$debug("path build to {} failed: reversal right after patch start", candidatePlatform == null ? 0 : candidatePlatform.getId());
                return null;
            }
        }

        // Leg: candidate platform -> second stop platform
        final int platformToSecondStopIndex = stop2StopIndex - 1;
        final ObjectArrayList<PathData> pathCandidateToSecond = jme$findSavedRailPathCached(data, candidatePlatform, secondStopSavedRail, platformToSecondStopIndex, depot.getCruisingAltitude());
        if (pathCandidateToSecond.size() < 2) {
            jme$debug("path build to {} failed: no path from candidate to following stop", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        if (jme$hasInvalidImmediateOppositeRailTransition(pathToCandidate) || jme$hasInvalidImmediateOppositeRailTransition(pathCandidateToSecond)) {
            jme$debug("path build to {} failed: reversal on open track", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        final PathData lastToCandidate = pathToCandidate.get(pathToCandidate.size() - 1);
        final PathData firstFromCandidate = pathCandidateToSecond.get(0);
        if (jme$isInvalidOppositeTransition(lastToCandidate, firstFromCandidate)) {
            jme$debug("path build to {} failed: reversal at candidate", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        // Merge legs, removing overlap at candidate platform.
        final ObjectArrayList<PathData> newSegments = new ObjectArrayList<>(pathToCandidate);
        if (lastToCandidate.isSameRail(firstFromCandidate)) {
            pathCandidateToSecond.remove(0);
        }
        newSegments.addAll(pathCandidateToSecond);

        // An alternative platform is in the same station, so the way through it is about as long as the scheduled
        // one. A much longer way is a loop around the network (eg passing the scheduled platform without stopping):
        // that would skip the stop, so it is not an alternative.
        double newLength = 0;
        for (final PathData pathData : newSegments) {
            newLength += pathData.getRailLength();
        }
        final double oldLength = basePath.get(stop2PathIndex).getEndDistance() - basePath.get(replaceStartIndex).getStartDistance();
        if (newLength > oldLength * MAX_DETOUR_FACTOR + MAX_DETOUR_EXTRA_METERS) {
            jme$debug("path build to {} failed: detour too long ({} m instead of {} m)", candidatePlatform.getId(), Math.round(newLength), Math.round(oldLength));
            return null;
        }

        // Tail: keep everything after the second stop platform segment.
        final ObjectArrayList<PathData> tail = new ObjectArrayList<>();
        for (int i = stop2PathIndex + 1; i < basePath.size(); i++) {
            final PathData pd = basePath.get(i);
            if (pd != null) {
                tail.add(pd);
            }
        }

        // Drop duplicate rail at the merge point.
        if (!tail.isEmpty() && !newSegments.isEmpty() && newSegments.get(newSegments.size() - 1).isSameRail(tail.get(0))) {
            tail.remove(0);
        }

        // Preserve the repeating marker (endDistance = Double.MAX_VALUE) without running it through distance regeneration.
        final PathData repeatingMarkerTemplate = jme$popRepeatingMarkerIfPresent(tail);

        // Build suffix: new segments + tail (without marker), then regenerate distances from current railProgress.
        final ObjectArrayList<PathData> suffix = new ObjectArrayList<>(newSegments);
        suffix.addAll(tail);
        if (suffix.isEmpty()) {
            jme$debug("path build to {} failed: empty suffix", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        final double initialDistance = railProgress;
        SidingPathFinder.generatePathDataDistances(suffix, initialDistance);

        final ObjectArrayList<PathData> patchedPath = new ObjectArrayList<>();
        for (int i = 0; i < replaceStartIndex; i++) {
            patchedPath.add(basePath.get(i));
        }
        patchedPath.addAll(suffix);

        if (repeatingMarkerTemplate != null) {
            final double markerStartDistance = patchedPath.isEmpty() ? initialDistance : patchedPath.get(patchedPath.size() - 1).getEndDistance();
            patchedPath.add(new PathData(repeatingMarkerTemplate, markerStartDistance, Double.MAX_VALUE));
        }

        if (patchedPath.size() < 2) {
            jme$debug("path build to {} failed: patched path too short", candidatePlatform == null ? 0 : candidatePlatform.getId());
            return null;
        }

        // Fill speed limits; otherwise MTR clamps to 1 km/h for rails with speedLimit=0.
        try {
            PathData.writePathCache(patchedPath, data, transportMode);
        } catch (Throwable ignored) {
        }

        return jme$copyVehicleExtraDataWithNewPath(baseExtraData, patchedPath, replaceStartIndex);
    }

    @Unique
    private static ObjectArrayList<PathData> jme$findSavedRailPathCached(
            Data data,
            SavedRailBase<?, ?> start,
            SavedRailBase<?, ?> end,
            int stopIndex,
            long cruisingAltitude
    ) {
        if (data == null) {
            return new ObjectArrayList<>();
        }
        final long now = System.currentTimeMillis();
        final long startId = start == null ? 0 : start.getId();
        final long endId = end == null ? 0 : end.getId();
        if (startId == 0 || endId == 0 || startId == endId) {
            return new ObjectArrayList<>();
        }

        final ObjectArrayList<PathData> cached = PathCache.getCopy(startId, endId, stopIndex, cruisingAltitude, now);
        if (cached != null) {
            return cached;
        }
        if (PathCache.isFailureCached(startId, endId, stopIndex, cruisingAltitude, now)) {
            return new ObjectArrayList<>();
        }

        final ObjectArrayList<PathData> path = SavedRailPathFinder.findPath(
                data,
                start,
                end,
                stopIndex,
                cruisingAltitude,
                PATHFIND_TIME_BUDGET_MILLIS,
                MAX_PATHFIND_TICK_CALLS
        );

        if (path.size() >= 2) {
            PathCache.putCopy(startId, endId, stopIndex, cruisingAltitude, path, now);
        } else {
            PathCache.putFailure(startId, endId, stopIndex, cruisingAltitude, now);
        }

        return path;
    }

    @Unique
    private static PathData jme$popRepeatingMarkerIfPresent(ObjectArrayList<PathData> tail) {
        if (tail == null || tail.isEmpty()) {
            return null;
        }

        final PathData last = tail.get(tail.size() - 1);
        if (last == null) {
            return null;
        }

        // The repeating marker added by VehicleExtraData#create has endDistance = Double.MAX_VALUE.
        if (last.getEndDistance() == Double.MAX_VALUE) {
            tail.remove(tail.size() - 1);
            return last;
        }

        return null;
    }

    @Unique
    private static VehicleExtraData jme$copyVehicleExtraDataWithNewPath(
            VehicleExtraData baseExtraData,
            ObjectArrayList<PathData> newPath,
            int replaceStartIndex
    ) {
        if (baseExtraData == null || newPath == null || newPath.isEmpty()) {
            return null;
        }

        final JsonObject json = Utilities.getJsonObjectFromData(baseExtraData);

        final int oldSize = baseExtraData.immutablePath == null ? 0 : baseExtraData.immutablePath.size();
        final int newSize = newPath.size();
        final long delta = newSize - oldSize;

        long repeatIndex1 = json.has("repeatIndex1") ? json.get("repeatIndex1").getAsLong() : 0;
        long repeatIndex2 = json.has("repeatIndex2") ? json.get("repeatIndex2").getAsLong() : 0;

        if (delta != 0) {
            if (replaceStartIndex >= 0 && replaceStartIndex < repeatIndex1) {
                repeatIndex1 = Math.max(0, repeatIndex1 + delta);
            }
            if (repeatIndex2 > 0 && replaceStartIndex >= 0 && replaceStartIndex < repeatIndex2) {
                repeatIndex2 = Math.max(0, repeatIndex2 + delta);
            }
        }

        // Keep repeatIndex2 consistent for repeating routes: it should point at the repeating marker (last element).
        if (repeatIndex2 > 0) {
            repeatIndex2 = Math.max(0, newSize - 1L);
        }

        json.addProperty("repeatIndex1", repeatIndex1);
        json.addProperty("repeatIndex2", repeatIndex2);

        final double totalDistance;
        if (repeatIndex2 > 0 && repeatIndex2 < newSize) {
            totalDistance = newPath.get((int) repeatIndex2).getStartDistance();
        } else {
            totalDistance = newPath.get(newSize - 1).getEndDistance();
        }
        json.addProperty("totalDistance", totalDistance);

        final JsonArray pathArray = new JsonArray();
        for (int i = 0; i < newPath.size(); i++) {
            final PathData pathData = newPath.get(i);
            if (pathData != null) {
                pathArray.add(Utilities.getJsonObjectFromData(pathData));
            }
        }
        json.add("path", pathArray);

        try {
            return new VehicleExtraData(new JsonReader(json));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static boolean jme$findNextSavedRailStop(List<PathData> path, int startIndex, long[] out) {
        if (out == null || out.length < 3 || path == null || path.isEmpty()) {
            return false;
        }

        for (int i = Math.max(0, startIndex); i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData == null) {
                continue;
            }
            if (pathData.getDwellTime() > 0) {
                final long savedRailBaseId = pathData.getSavedRailBaseId();
                if (savedRailBaseId != 0) {
                    out[0] = savedRailBaseId;
                    out[1] = i;
                    out[2] = pathData.getStopIndex();
                    return true;
                }
            }
        }

        return false;
    }

    @Unique
    private static SavedRailBase<?, ?> jme$getSavedRailBase(Data data, long savedRailBaseId) {
        if (data == null || savedRailBaseId == 0) {
            return null;
        }

        final Platform platform = data.platformIdMap.get(savedRailBaseId);
        if (platform != null) {
            return platform;
        }

        final Siding siding = data.sidingIdMap.get(savedRailBaseId);
        if (siding != null) {
            return siding;
        }

        return null;
    }

    @Unique
    private static Position jme$getTravelStartPosition(PathData pathData) {
        if (pathData == null) {
            return null;
        }
        // PathData stores positions in travel order as startPosition -> endPosition, but only exposes ordered positions.
        return pathData.reversePositions ? pathData.getOrderedPosition2() : pathData.getOrderedPosition1();
    }

    @Unique
    private static boolean jme$hasInvalidImmediateOppositeRailTransition(ObjectArrayList<PathData> path) {
        if (path == null || path.size() < 2) {
            return false;
        }

        for (int i = 1; i < path.size(); i++) {
            final PathData previous = path.get(i - 1);
            final PathData current = path.get(i);
            if (jme$isInvalidOppositeTransition(previous, current)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static boolean jme$isInvalidOppositeTransition(PathData previous, PathData current) {
        return previous != null
                && current != null
                && previous.isOppositeRail(current)
                && previous.getDwellTime() <= 0
                && current.getDwellTime() <= 0;
    }

    @Unique
    private static long jme$mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    @Unique
    private static double jme$getRailProgress(Vehicle vehicle) {
        if (vehicle == null) {
            return 0;
        }

        try {
            Field field = jme$railProgressField;
            if (field == null) {
                field = jme$findField(vehicle.getClass(), "railProgress");
                if (field == null) {
                    return 0;
                }
                field.setAccessible(true);
                jme$railProgressField = field;
            }

            final Object value = field.get(vehicle);
            return value instanceof Number ? ((Number) value).doubleValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Unique
    private static Data jme$getData(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }

        try {
            Field field = jme$dataField;
            if (field == null) {
                field = jme$findField(vehicle.getClass(), "data");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                jme$dataField = field;
            }

            final Object value = field.get(vehicle);
            return value instanceof Data ? (Data) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static Field jme$findField(Class<?> clazz, String fieldName) {
        Class<?> check = clazz;
        while (check != null) {
            try {
                return check.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                check = check.getSuperclass();
            }
        }
        return null;
    }

    // Intentionally no inner helper classes here: loading any non-mixin class from the mixin
    // package can trigger IllegalClassLoadError in production. Use primitive arrays instead.
}
