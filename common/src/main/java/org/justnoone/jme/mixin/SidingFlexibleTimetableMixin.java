package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.RouteTimetableRegistry;
import org.justnoone.jme.util.FlexibleTimetables;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Trip;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies flexible route timetables ({@link RouteTimetableRegistry}) by changing the dwell times of the siding's path.
 * <p>
 * {@code generatePathDistancesAndTimeSegments} simulates the trip and writes the timetable (stop times, time segments,
 * route durations). After it ran, the arrival time at every stop is known, so the dwell times that make the train
 * leave each timed stop at its departure time can be computed; the method then runs a second time with those dwell
 * times. Everything MTR derives from the path (arrival boards, the vehicles' own dwell times, delay handling) then
 * follows the flexible timetable.
 * <p>
 * The path data changed are the siding's own copies ({@code SidingPathFinder.generatePathDataDistances} copies them on
 * every run), never the depot's shared path. They are restored before the next run, so it starts from the platforms'
 * dwell times again.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class SidingFlexibleTimetableMixin {

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathSidingToMainRoute;

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathMainRoute;

    @Shadow
    @Final
    private ObjectArrayList<PathData> pathMainRouteToSiding;

    @Shadow
    @Final
    private ObjectArrayList<Trip> trips;

    @Shadow
    private void generatePathDistancesAndTimeSegments() {
    }

    @Unique
    private final Map<PathData, Long> jme$originalDwellTimes = new IdentityHashMap<>();
    /**
     * Set during the second run: the dwell time of each stop of the path, in order.
     */
    @Unique
    private long[] jme$pendingDwellTimes;
    /**
     * The run adds to and removes from these lists (for depots repeating indefinitely), so the second run starts from
     * copies taken before the first.
     */
    @Unique
    private List<PathData> jme$sidingToMainRouteBefore;
    @Unique
    private List<PathData> jme$mainRouteToSidingBefore;

    @Inject(method = "generatePathDistancesAndTimeSegments", at = @At("HEAD"), remap = false)
    private void jme$restorePlatformDwellTimes(CallbackInfo ci) {
        if (jme$pendingDwellTimes != null) {
            return;
        }
        jme$originalDwellTimes.forEach((pathData, dwellTime) -> ((PathDataDwellTimeAccessor) pathData).jme$setDwellTime(dwellTime));
        jme$originalDwellTimes.clear();
        if (RouteTimetableRegistry.isEmpty()) {
            jme$sidingToMainRouteBefore = null;
            jme$mainRouteToSidingBefore = null;
        } else {
            jme$sidingToMainRouteBefore = new ArrayList<>(pathSidingToMainRoute);
            jme$mainRouteToSidingBefore = new ArrayList<>(pathMainRouteToSiding);
        }
    }

    /**
     * Right before the trip is simulated, when the three path lists are complete and hold the siding's own copies.
     */
    @Inject(
            method = "generatePathDistancesAndTimeSegments",
            at = @At(value = "NEW", target = "org/mtr/libraries/it/unimi/dsi/fastutil/doubles/DoubleArrayList"),
            remap = false
    )
    private void jme$applyDwellTimes(CallbackInfo ci) {
        final long[] dwellTimes = jme$pendingDwellTimes;
        if (dwellTimes == null) {
            return;
        }
        final List<PathData> stops = jme$getStops();
        for (int i = 0; i < stops.size() && i < dwellTimes.length; i++) {
            final PathData stop = stops.get(i);
            if (stop.getDwellTime() != dwellTimes[i]) {
                jme$originalDwellTimes.putIfAbsent(stop, stop.getDwellTime());
                ((PathDataDwellTimeAccessor) stop).jme$setDwellTime(dwellTimes[i]);
            }
        }
    }

    @Inject(method = "generatePathDistancesAndTimeSegments", at = @At("TAIL"), remap = false)
    private void jme$applyFlexibleTimetable(CallbackInfo ci) {
        if (jme$pendingDwellTimes != null || jme$sidingToMainRouteBefore == null) {
            return;
        }
        final long[] dwellTimes = jme$computeDwellTimes();
        if (dwellTimes == null) {
            return;
        }

        pathSidingToMainRoute.clear();
        pathSidingToMainRoute.addAll(jme$sidingToMainRouteBefore);
        pathMainRouteToSiding.clear();
        pathMainRouteToSiding.addAll(jme$mainRouteToSidingBefore);
        jme$pendingDwellTimes = dwellTimes;
        try {
            generatePathDistancesAndTimeSegments();
        } finally {
            jme$pendingDwellTimes = null;
        }
    }

    /**
     * @return the dwell time of each stop of the path, or {@code null} if no route of this siding has a flexible
     * timetable (or nothing changes)
     */
    @Unique
    private long[] jme$computeDwellTimes() {
        final List<Trip.StopTime> stopTimes = new ArrayList<>();
        boolean anyTimetable = false;
        for (final Trip trip : trips) {
            stopTimes.addAll(((TripStopTimesAccessor) trip).jme$getStopTimes());
            anyTimetable |= trip.route != null && RouteTimetableRegistry.get(trip.route.getId()) != null;
        }
        if (!anyTimetable) {
            return null;
        }

        final List<PathData> stops = jme$getStops();
        final int count = stops.size();
        final long[] arrivals = new long[count];
        final long[] dwellTimes = new long[count];
        final long[] departures = new long[count];
        final long[] minimumDwellTimes = new long[count];
        int stopTimeIndex = 0;
        for (int i = 0; i < count; i++) {
            final PathData stop = stops.get(i);
            dwellTimes[i] = stop.getDwellTime();
            departures[i] = FlexibleTimetables.NO_OFFSET;
            // MTR writes the stop times by walking the path in the same order: a stop gets the stop times of every
            // route stop at its platform that comes next (two when one route ends where the next one starts).
            Trip.StopTime departingStopTime = null;
            while (stopTimeIndex < stopTimes.size() && stopTimes.get(stopTimeIndex).platformId == stop.getSavedRailBaseId()) {
                final Trip.StopTime stopTime = stopTimes.get(stopTimeIndex++);
                if (departingStopTime == null) {
                    arrivals[i] = stopTime.startTime;
                }
                departingStopTime = stopTime;
                final RouteTimetableRegistry.Entry entry = stopTime.trip.route == null ? null : RouteTimetableRegistry.get(stopTime.trip.route.getId());
                if (entry != null) {
                    minimumDwellTimes[i] = Math.max(minimumDwellTimes[i], entry.minimumDwellMillis);
                }
            }
            // The departure belongs to the route the train leaves on.
            if (departingStopTime != null && departingStopTime.trip.route != null) {
                final RouteTimetableRegistry.Entry entry = RouteTimetableRegistry.get(departingStopTime.trip.route.getId());
                if (entry != null) {
                    departures[i] = entry.getOffset(departingStopTime.tripStopIndex, departingStopTime.platformId);
                }
            }
        }

        final long[] result = FlexibleTimetables.computeDwellTimes(arrivals, dwellTimes, departures, minimumDwellTimes);
        for (int i = 0; i < count; i++) {
            if (result[i] != dwellTimes[i]) {
                return result;
            }
        }
        return null;
    }

    /**
     * @return the stops of the path, in the order MTR simulates them
     */
    @Unique
    private List<PathData> jme$getStops() {
        final List<PathData> stops = new ArrayList<>();
        jme$addStops(pathSidingToMainRoute, stops);
        jme$addStops(pathMainRoute, stops);
        jme$addStops(pathMainRouteToSiding, stops);
        return stops;
    }

    @Unique
    private static void jme$addStops(List<PathData> path, List<PathData> stops) {
        for (final PathData pathData : path) {
            if (pathData.getSavedRailBaseId() != 0) {
                stops.add(pathData);
            }
        }
    }
}
