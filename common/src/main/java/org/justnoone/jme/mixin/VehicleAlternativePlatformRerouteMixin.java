package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
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
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
public abstract class VehicleAlternativePlatformRerouteMixin {

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
        if (invoker.jme$isCurrentlyManual()) {
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

        // If not blocked, don't touch anything.
        final boolean isOpposite = previousPathData != null && previousPathData.isOppositeRail(pathData);
        final double checkRailProgress = railProgress + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0);
        // Match vanilla simulateStopped() behavior: it pre-reserves signals.
        final double blockedDistance = invoker.jme$railBlockedDistance(currentIndex, checkRailProgress, 0, vehiclePositions, true, false);
        if (blockedDistance < 0) {
            return;
        }

        final long vehicleId = self.getId();
        final long now = System.currentTimeMillis();
        final long lastAttempt = jme$lastRerouteAttemptMillis;
        if (now - lastAttempt < REROUTE_COOLDOWN_MILLIS) {
            return;
        }
        jme$lastRerouteAttemptMillis = now;

        String abortReason = null;

        final long[] stop1 = new long[3];
        if (!jme$findNextSavedRailStop(vehicleExtraData.immutablePath, currentIndex, stop1)) {
            abortReason = "no-stop1";
        }
        if (abortReason != null) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason {}, idx {}, blockedDistance {})",
                        vehicleId,
                        abortReason,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }
        final long stop1SavedRailBaseId = stop1[0];
        final int stop1PathIndex = (int) stop1[1];
        final int stop1StopIndex = (int) stop1[2];

        final Platform primaryTargetPlatform = data.platformIdMap.get(stop1SavedRailBaseId);
        if (primaryTargetPlatform == null) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason stop1-not-platform, stop1Id {}, idx {}, blockedDistance {})",
                        vehicleId,
                        stop1SavedRailBaseId,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }

        final long[] stop2 = new long[3];
        if (!jme$findNextSavedRailStop(vehicleExtraData.immutablePath, stop1PathIndex + 1, stop2)) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason no-stop2, stop1Id {}, idx {}, blockedDistance {})",
                        vehicleId,
                        stop1SavedRailBaseId,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }
        final long stop2SavedRailBaseId = stop2[0];
        final int stop2PathIndex = (int) stop2[1];
        final int stop2StopIndex = (int) stop2[2];

        final VehicleExtraData.VehiclePlatformRouteInfo routeInfo = depot.getVehiclePlatformRouteInfo(stop1StopIndex);
        final VehiclePlatformRouteInfoAccessor routeInfoAccessor = (VehiclePlatformRouteInfoAccessor) (Object) routeInfo;
        Platform scheduledPrimaryPlatform = routeInfoAccessor.jme$getThisPlatform();
        if (scheduledPrimaryPlatform == null) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason missing-scheduled-platform, stop1Id {}, stopIdx {}, idx {}, blockedDistance {})",
                        vehicleId,
                        stop1SavedRailBaseId,
                        stop1StopIndex,
                        currentIndex,
                        blockedDistance
                );
            }
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
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason missing-route, stop1Id {}, stopIdx {}, idx {}, blockedDistance {})",
                        vehicleId,
                        stop1SavedRailBaseId,
                        stop1StopIndex,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }

        final List<Long> candidateIds = AlternativePlatformRegistry.getCandidatePlatformIds(route, scheduledPrimaryPlatform);
        if (candidateIds.size() <= 1) {
            if (Main.LOGGER.isDebugEnabled()) {
                Main.LOGGER.debug(
                        "[MAGIC] Vehicle {} stuck at signal but cannot reroute (reason no-candidates, route {}, scheduledPrimary {}, stop1Id {}, idx {}, blockedDistance {})",
                        vehicleId,
                        route.getId(),
                        scheduledPrimaryPlatform.getId(),
                        stop1SavedRailBaseId,
                        currentIndex,
                        blockedDistance
                );
            }
            return;
        }

        // Determine which platform we are currently heading towards (could already be an alternative).
        final long currentTargetPlatformId = stop1SavedRailBaseId;

        // Build a deterministic-but-varied candidate order to avoid always trying the same platform first.
        final long selectionSeed = jme$mix64(vehicleId ^ depot.getId() ^ now ^ currentTargetPlatformId ^ ((long) stop1StopIndex << 32));
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
            return null;
        }

        final ObjectArrayList<PathData> basePath = new ObjectArrayList<>(baseExtraData.immutablePath);
        if (replaceStartIndex < 0 || replaceStartIndex >= basePath.size()) {
            return null;
        }
        if (stop2PathIndex < 0 || stop2PathIndex >= basePath.size()) {
            return null;
        }
        if (stop2PathIndex <= replaceStartIndex) {
            return null;
        }

        final Position startPosition = jme$getTravelStartPosition(currentPathData);
        if (startPosition == null) {
            return null;
        }

        // Leg: signal node -> candidate platform
        final int nodeToPlatformStopIndex = stop1StopIndex - 1;
        final ObjectArrayList<PathData> pathToCandidate = PositionPathFinder.findPath(
                data,
                transportMode,
                startPosition,
                candidatePlatform,
                nodeToPlatformStopIndex,
                depot.getCruisingAltitude(),
                PATHFIND_TIME_BUDGET_MILLIS,
                MAX_PATHFIND_TICK_CALLS
        );
        if (pathToCandidate.size() < 2) {
            return null;
        }

        // Ensure the patched path actually starts from our current node. Allow changing the first segment
        // (station throat divergence), but don't allow immediately reversing back.
        if (!startPosition.equals(jme$getTravelStartPosition(pathToCandidate.get(0)))) {
            return null;
        }
        if (replaceStartIndex > 0) {
            final PathData previousPathData = basePath.get(replaceStartIndex - 1);
            if (previousPathData != null && jme$isInvalidOppositeTransition(previousPathData, pathToCandidate.get(0))) {
                return null;
            }
        }

        // Leg: candidate platform -> second stop platform
        final int platformToSecondStopIndex = stop2StopIndex - 1;
        final ObjectArrayList<PathData> pathCandidateToSecond = jme$findSavedRailPathCached(data, candidatePlatform, secondStopSavedRail, platformToSecondStopIndex, depot.getCruisingAltitude());
        if (pathCandidateToSecond.size() < 2) {
            return null;
        }

        if (jme$hasInvalidImmediateOppositeRailTransition(pathToCandidate) || jme$hasInvalidImmediateOppositeRailTransition(pathCandidateToSecond)) {
            return null;
        }

        final PathData lastToCandidate = pathToCandidate.get(pathToCandidate.size() - 1);
        final PathData firstFromCandidate = pathCandidateToSecond.get(0);
        if (jme$isInvalidOppositeTransition(lastToCandidate, firstFromCandidate)) {
            return null;
        }

        // Merge legs, removing overlap at candidate platform.
        final ObjectArrayList<PathData> newSegments = new ObjectArrayList<>(pathToCandidate);
        if (lastToCandidate.isSameRail(firstFromCandidate)) {
            pathCandidateToSecond.remove(0);
        }
        newSegments.addAll(pathCandidateToSecond);

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
