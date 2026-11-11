package org.justnoone.jme.rail;

import org.justnoone.jme.mixin.SidingVehiclesAccessor;
import org.justnoone.jme.mixin.VehiclePlatformRouteInfoAccessor;
import org.justnoone.jme.mixin.VehicleScheduleAccessor;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Departure boards for trains sent to an alternative platform.
 * <p>
 * MTR lists a trip at the platforms of its timetable. Whether a train really stops elsewhere is read from the train's
 * own path (the platform of its next stop there), so the board always agrees with where the train goes: its next
 * arrival is dropped from the scheduled platform and listed at the platform in its path.
 */
public final class DivertedArrivals {

    // Enough to always include the train's own upcoming arrival when looking it up at its scheduled platform.
    private static final long LOOKUP_COUNT = 8;
    private static final ThreadLocal<int[]> LOOKUP_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private DivertedArrivals() {
    }

    /**
     * The next stop of a train whose path serves it at another platform than the timetable.
     */
    public static final class Diversion {
        public final long departureIndex;
        public final Route route;
        public final int routeStopIndex;
        public final Platform scheduledPlatform;
        public final long actualPlatformId;

        private Diversion(long departureIndex, Route route, int routeStopIndex, Platform scheduledPlatform, long actualPlatformId) {
            this.departureIndex = departureIndex;
            this.route = route;
            this.routeStopIndex = routeStopIndex;
            this.scheduledPlatform = scheduledPlatform;
            this.actualPlatformId = actualPlatformId;
        }
    }

    /**
     * Whether the current thread is looking up a trip at its scheduled platform (the scheduled board must then not
     * hide it).
     */
    public static boolean isLookingUp() {
        return LOOKUP_DEPTH.get()[0] > 0;
    }

    /**
     * @return the diversion of the train's next stop (or of the stop it is standing at), or {@code null}
     */
    @Nullable
    public static Diversion getDiversion(Depot depot, Vehicle vehicle) {
        if (depot == null || vehicle == null || !vehicle.getIsOnRoute() || vehicle.getDepartureIndex() < 0) {
            return null;
        }
        final VehicleExtraData vehicleExtraData = vehicle.vehicleExtraData;
        final List<PathData> path = vehicleExtraData == null ? null : vehicleExtraData.immutablePath;
        if (path == null || path.isEmpty()) {
            return null;
        }

        final double railProgress = ((VehicleScheduleAccessor) (Object) vehicle).jme$getRailProgress();
        final int currentIndex = Utilities.getIndexFromConditionalList(path, railProgress);
        // A train standing at a platform has already passed its platform segment (it stops at the segment's end).
        int stopPathIndex = -1;
        for (int i = Math.max(0, currentIndex - 1); i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData.getDwellTime() > 0 && pathData.getSavedRailBaseId() != 0 && (i >= currentIndex || railProgress <= pathData.getEndDistance() + 1e-3)) {
                stopPathIndex = i;
                break;
            }
        }
        if (stopPathIndex < 0) {
            return null;
        }

        final PathData stop = path.get(stopPathIndex);
        final VehiclePlatformRouteInfoAccessor routeInfo = (VehiclePlatformRouteInfoAccessor) (Object) depot.getVehiclePlatformRouteInfo(stop.getStopIndex());
        final Platform scheduledPlatform = routeInfo.jme$getThisPlatform();
        if (scheduledPlatform == null || scheduledPlatform.getId() == stop.getSavedRailBaseId()) {
            return null;
        }
        Route route = routeInfo.jme$getThisRoute();
        if (route == null) {
            route = routeInfo.jme$getNextRoute();
        }
        if (route == null) {
            return null;
        }
        return new Diversion(vehicle.getDepartureIndex(), route, getRouteStopIndex(route, scheduledPlatform.getId()), scheduledPlatform, stop.getSavedRailBaseId());
    }

    /**
     * Adds an arrival at {@code platform} for every train of the station's routes whose next stop is served there
     * instead of at its scheduled platform.
     */
    public static void addArrivalsDivertedTo(Simulator simulator, Platform platform, ObjectArrayList<ArrivalResponse> arrivals) {
        if (platform == null || platform.area == null) {
            return;
        }

        // Alternatives are platforms of the same station, so the trains to look at are those of the station's routes.
        final Set<Siding> sidings = new HashSet<>();
        platform.area.savedRails.forEach(stationPlatform -> stationPlatform.routes.forEach(route -> route.depots.forEach(depot -> sidings.addAll(depot.savedRails))));

        for (final Siding siding : sidings) {
            for (final Vehicle vehicle : ((SidingVehiclesAccessor) (Object) siding).jme$getVehicles()) {
                final Diversion diversion = getDiversion(siding.area, vehicle);
                if (diversion == null || diversion.actualPlatformId != platform.getId()) {
                    continue;
                }

                final ObjectArrayList<ArrivalResponse> scheduledArrivals = new ObjectArrayList<>();
                LOOKUP_DEPTH.get()[0]++;
                try {
                    siding.getArrivals(simulator.getCurrentMillis(), diversion.scheduledPlatform, LOOKUP_COUNT, scheduledArrivals);
                } finally {
                    LOOKUP_DEPTH.get()[0]--;
                }

                // The trip's earliest listed arrival there is this train's next visit (earlier visits are gone).
                final ArrivalResponse next = findNext(scheduledArrivals, diversion);
                if (next != null) {
                    final ArrivalResponse moved = new ArrivalResponse(next.getDestination(), next.getArrival(), next.getDeparture(), next.getDeviation(), next.getRealtime(), next.getDepartureIndex(), diversion.routeStopIndex, diversion.route, platform);
                    moved.setCarDetails(siding.getVehicleCars());
                    arrivals.add(moved);
                }
            }
        }
    }

    /**
     * @return the arrival in {@code arrivals} that is the diverted train's next visit
     */
    @Nullable
    public static ArrivalResponse findNext(List<ArrivalResponse> arrivals, Diversion diversion) {
        ArrivalResponse next = null;
        for (final ArrivalResponse arrivalResponse : arrivals) {
            if (arrivalResponse.getDepartureIndex() == diversion.departureIndex && arrivalResponse.getRouteId() == diversion.route.getId() && (next == null || arrivalResponse.getArrival() < next.getArrival())) {
                next = arrivalResponse;
            }
        }
        return next;
    }

    private static int getRouteStopIndex(Route route, long platformId) {
        for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
            final Platform platform = route.getRoutePlatforms().get(i).platform;
            if (platform != null && platform.getId() == platformId) {
                return i;
            }
        }
        return 0;
    }
}
