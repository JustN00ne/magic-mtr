package org.justnoone.jme.rail;

import org.justnoone.jme.mixin.SidingVehiclesAccessor;
import org.justnoone.jme.mixin.VehicleDelayAccessor;
import org.justnoone.jme.mixin.VehicleScheduleAccessor;
import org.justnoone.jme.mixin.VehicleSidingAccessor;
import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.justnoone.jme.util.HeadcodePriority;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overtaking at stations with alternative platforms.
 * <p>
 * Each train has a priority from its route's headcode class and its delay ({@link HeadcodePriority}). When a train
 * approaches a station where its route has alternative platforms, and a train of a higher priority behind it would
 * catch up with it on the stretch
 * after the station (both following their timetables), the slower train is sent to a free alternative platform that the
 * faster one does not use, and waits there (doors open) until the faster train has passed the station's scheduled
 * platform. Which platform is free, and building the new path, is left to the alternative platform code.
 */
public final class Overtaking {

    /**
     * A faster train is only looked for this far ahead (time to reach the station, by its timetable).
     */
    private static final long LOOKAHEAD_MILLIS = 10 * 60_000;
    /**
     * The faster train counts as caught up when it would reach the end of the shared stretch less than this after the
     * slower one.
     */
    private static final long HEADWAY_MILLIS = 60_000;
    /**
     * The slower train never waits longer than this for the faster one.
     */
    public static final long MAX_HOLD_MILLIS = 5 * 60_000;
    /**
     * How far along a path (in path segments) rails are compared.
     */
    private static final int PATH_SCAN_NODES = 400;

    private static final Map<Long, Hold> HOLDS = new ConcurrentHashMap<>();

    private Overtaking() {
    }

    /**
     * Looks for a train of a higher priority that would catch up with {@code self} after its next stop.
     *
     * @param path              the path of {@code self}
     * @param stopPathIndex     the index of its next stop in {@code path}
     * @param nextStopPathIndex the index of the stop after it, the end of the stretch checked
     * @return the faster train, or {@code null} if no train needs to overtake here
     */
    @Nullable
    public static Overtaker findOvertaker(Data data, Vehicle self, List<PathData> path, double railProgress, int stopPathIndex, int nextStopPathIndex, int priority) {
        if (priority <= 0) {
            return null;
        }
        final Siding ownSiding = ((VehicleSidingAccessor) (Object) self).jme$getSiding();
        final PathData stop = path.get(stopPathIndex);
        if (ownSiding == null || stop.getRail() == null) {
            return null;
        }
        final double ownNow = scheduleTime(self, ownSiding, railProgress);
        final double ownArrival = scheduleTime(self, ownSiding, stop.getStartDistance()) - ownNow;

        Overtaker best = null;
        for (final Siding siding : data.sidings) {
            for (final Vehicle other : ((SidingVehiclesAccessor) (Object) siding).jme$getVehicles()) {
                if (other == self || !other.getIsOnRoute()) {
                    continue;
                }
                final VehicleExtraData otherExtraData = other.vehicleExtraData;
                if (otherExtraData == null || otherExtraData.immutablePath == null || trainPriority(data, other) <= priority) {
                    continue;
                }
                final List<PathData> otherPath = otherExtraData.immutablePath;
                final double otherProgress = ((VehicleScheduleAccessor) (Object) other).jme$getRailProgress();
                final int otherIndex = Utilities.getIndexFromConditionalList(otherPath, otherProgress);
                // The rails the other train runs on from here, and where it reaches the station's platform track.
                final Map<Rail, Integer> otherRails = new IdentityHashMap<>();
                for (int i = Math.max(0, otherIndex); i < otherPath.size() && i < otherIndex + PATH_SCAN_NODES; i++) {
                    final Rail rail = otherPath.get(i).getRail();
                    if (rail != null) {
                        otherRails.putIfAbsent(rail, i);
                    }
                }
                final Integer otherStopIndex = otherRails.get(stop.getRail());
                if (otherStopIndex == null) {
                    continue;
                }
                // The end of the stretch both trains share after the station.
                int sharedEnd = -1;
                for (int i = Math.min(nextStopPathIndex, path.size() - 1); i > stopPathIndex; i--) {
                    final Integer otherSharedIndex = path.get(i).getRail() == null ? null : otherRails.get(path.get(i).getRail());
                    if (otherSharedIndex != null && otherSharedIndex > otherStopIndex) {
                        sharedEnd = i;
                        break;
                    }
                }
                if (sharedEnd < 0) {
                    continue;
                }
                final Siding otherSiding = ((VehicleSidingAccessor) (Object) other).jme$getSiding();
                if (otherSiding == null) {
                    continue;
                }
                final double otherNow = scheduleTime(other, otherSiding, otherProgress);
                final double otherArrival = scheduleTime(other, otherSiding, otherPath.get(otherStopIndex).getStartDistance()) - otherNow;
                if (otherArrival <= ownArrival || otherArrival > LOOKAHEAD_MILLIS) {
                    // Already ahead, or still far away.
                    continue;
                }
                final double ownSharedEnd = scheduleTime(self, ownSiding, path.get(sharedEnd).getEndDistance()) - ownNow;
                final double otherSharedEnd = scheduleTime(other, otherSiding, otherPath.get(otherRails.get(path.get(sharedEnd).getRail())).getEndDistance()) - otherNow;
                if (otherSharedEnd >= ownSharedEnd + HEADWAY_MILLIS) {
                    // It would not catch up before the end of the shared stretch.
                    continue;
                }
                if (best == null || otherArrival < best.arrivalMillis) {
                    best = new Overtaker(other, otherRails.keySet(), stop.getRail(), (long) otherArrival);
                }
            }
        }
        return best;
    }

    /**
     * @return the train's overtaking priority ({@link HeadcodePriority#priority}), {@code 0} if its route has no
     * headcode class
     */
    public static int trainPriority(Data data, Vehicle vehicle) {
        final VehicleExtraData extraData = vehicle.vehicleExtraData;
        if (extraData == null || extraData.immutablePath == null) {
            return 0;
        }
        final int headcodeClass = RouteTimetableRegistry.getHeadcodeClass(extraData.getThisRouteId());
        if (headcodeClass == HeadcodePriority.NONE) {
            return 0;
        }
        return HeadcodePriority.priority(headcodeClass, isFromDepot(data, vehicle), ((VehicleDelayAccessor) (Object) vehicle).jme$getDeviation());
    }

    /**
     * @return whether the train has not called at any platform yet since leaving its depot
     */
    private static boolean isFromDepot(Data data, Vehicle vehicle) {
        final List<PathData> path = vehicle.vehicleExtraData.immutablePath;
        final int currentIndex = Utilities.getIndexFromConditionalList(path, ((VehicleScheduleAccessor) (Object) vehicle).jme$getRailProgress());
        for (int i = 0; i < currentIndex && i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData.getDwellTime() > 0 && data.platformIdMap.containsKey(pathData.getSavedRailBaseId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes {@code vehicleId} wait at the platform at {@code platformPathIndex} of {@code path} until the overtaking
     * train has passed.
     */
    public static void hold(long vehicleId, Overtaker overtaker, List<PathData> path, int platformPathIndex, long nowMillis) {
        // Holds of trains that were removed before reaching their platform are never looked up again.
        HOLDS.values().removeIf(hold -> nowMillis > hold.deadlineMillis);
        HOLDS.put(vehicleId, new Hold(overtaker.vehicle, overtaker.passedRail, path, platformPathIndex, nowMillis + MAX_HOLD_MILLIS));
    }

    /**
     * @return whether the train stopped at {@code currentIndex} of {@code path} must keep waiting for a faster train
     */
    public static boolean mustWait(long vehicleId, List<PathData> path, int currentIndex, long nowMillis) {
        final Hold hold = HOLDS.get(vehicleId);
        if (hold == null) {
            return false;
        }
        if (hold.path != path || currentIndex > hold.platformPathIndex || nowMillis > hold.deadlineMillis || hasPassed(hold)) {
            // Rerouted, already left, waited long enough, or the faster train passed.
            HOLDS.remove(vehicleId);
            return false;
        }
        return currentIndex == hold.platformPathIndex;
    }

    public static void release(long vehicleId) {
        HOLDS.remove(vehicleId);
    }

    public static void clear() {
        HOLDS.clear();
    }

    private static boolean hasPassed(Hold hold) {
        final Vehicle other = hold.vehicle;
        final VehicleExtraData extraData = other.vehicleExtraData;
        if (!other.getIsOnRoute() || extraData == null || extraData.immutablePath == null) {
            return true;
        }
        final List<PathData> otherPath = extraData.immutablePath;
        final int otherIndex = Utilities.getIndexFromConditionalList(otherPath, ((VehicleScheduleAccessor) (Object) other).jme$getRailProgress());
        for (int i = Math.max(0, otherIndex); i < otherPath.size() && i < otherIndex + PATH_SCAN_NODES; i++) {
            if (otherPath.get(i).getRail() == hold.passedRail) {
                return false;
            }
        }
        return true;
    }

    /**
     * Time along the train's timetable at a position of its current path (which may have been rebuilt for an
     * alternative platform, see {@link TimetableAligned}).
     */
    private static double scheduleTime(Vehicle vehicle, Siding siding, double railProgress) {
        return siding.getTimeAlongRoute(((TimetableAligned) (Object) vehicle).jme$toTimetableProgress(railProgress));
    }

    public static final class Overtaker {

        public final Vehicle vehicle;
        /**
         * The rails the faster train runs on: the slower train must wait on another track.
         */
        public final Set<Rail> rails;
        private final Rail passedRail;
        private final long arrivalMillis;

        private Overtaker(Vehicle vehicle, Set<Rail> rails, Rail passedRail, long arrivalMillis) {
            this.vehicle = vehicle;
            this.rails = Collections.unmodifiableSet(rails);
            this.passedRail = passedRail;
            this.arrivalMillis = arrivalMillis;
        }
    }

    private static final class Hold {

        private final Vehicle vehicle;
        private final Rail passedRail;
        private final List<PathData> path;
        private final int platformPathIndex;
        private final long deadlineMillis;

        private Hold(Vehicle vehicle, Rail passedRail, List<PathData> path, int platformPathIndex, long deadlineMillis) {
            this.vehicle = vehicle;
            this.passedRail = passedRail;
            this.path = path;
            this.platformPathIndex = platformPathIndex;
            this.deadlineMillis = deadlineMillis;
        }
    }
}
