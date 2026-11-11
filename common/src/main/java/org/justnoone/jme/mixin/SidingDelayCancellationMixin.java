package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.CanceledTrainRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.util.TrainDelay;
import org.mtr.core.data.Data;
import org.mtr.core.data.Route;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingDelayCancellationMixin {

    // A train is cancelled once its delay stays above the threshold this long. MTR's schedule lookup briefly reports a
    // delay as large as the dwell time in the tick a train departs a stop; instant cancellation removed trains on time.
    @Unique
    private static final long JME_OVER_THRESHOLD_CONFIRM_MILLIS = 5_000;
    // Simulation time at which each train's delay went above the threshold.
    @Unique
    private static final java.util.Map<Long, Long> JME_OVER_THRESHOLD_SINCE = new java.util.concurrent.ConcurrentHashMap<>();

    @Shadow
    abstract long getRepeatInterval(long defaultAmount);

    @Inject(method = "simulateTrain", at = @At("TAIL"), remap = false)
    private void jme$cancelDelayedTrains(long millisElapsed, ObjectArrayList<?> vehiclePositions, CallbackInfo ci) {
        final Siding siding = (Siding) (Object) this;
        final long sidingId = siding.getId();
        final Data data = ((NameColorDataAccessor) this).jme$getData();
        if (data != null) {
            CanceledTrainRegistry.pruneTrips(data.getCurrentMillis());
        }

        final DepotCancellationRegistry.Settings settings = DepotCancellationRegistry.getForSiding(siding);
        if (!settings.enabled) {
            DepotCancellationRegistry.clearPendingForSiding(sidingId);
            return;
        }

        final ObjectArraySet<Vehicle> vehicles = ((SidingVehiclesAccessor) this).jme$getVehicles();
        if (vehicles == null || vehicles.isEmpty() || data == null) {
            return;
        }

        final long thresholdMillis = DepotCancellationRegistry.getThresholdMillis(settings);
        final long currentMillis = data.getCurrentMillis();
        final long repeatInterval = getRepeatInterval(86400000L);
        final ObjectArrayList<Vehicle> vehiclesToRemove = new ObjectArrayList<>();
        for (final Vehicle vehicle : vehicles) {
            if (vehicle != null && jme$shouldRemove(siding, vehicle, thresholdMillis, currentMillis, repeatInterval)) {
                vehiclesToRemove.add(vehicle);
            }
        }

        if (vehiclesToRemove.isEmpty()) {
            return;
        }

        // Removing the vehicle is how MTR itself retires vehicles (see Siding#simulateTrain). The trip is recorded as
        // cancelled (persisted) so its remaining scheduled stops disappear from every departure board, and a
        // "Service Cancelled" notice is shown at its current platform.
        final List<Long> routeIds = new ArrayList<>();
        if (siding.area != null) {
            for (final Route route : siding.area.routes) {
                routeIds.add(route.getId());
            }
        }
        vehiclesToRemove.forEach(vehicle -> {
            final long sidingDepartureTime = ((VehicleScheduleAccessor) (Object) vehicle).jme$getSidingDepartureTime();
            final Long delayMillis = jme$getLiveDelayMillis(siding, vehicle, currentMillis, repeatInterval);
            org.mtr.core.Main.LOGGER.info("[MAGIC] Cancelled train {} of siding {} (delay {} s, threshold {} s)", vehicle.getId(), sidingId, delayMillis == null ? "?" : delayMillis / 1000, thresholdMillis / 1000);
            CanceledTrainRegistry.markTripCanceled(routeIds, vehicle.getDepartureIndex(), sidingDepartureTime, repeatInterval - 1);
            CanceledTrainRegistry.markCanceled(vehicle);
            vehicles.remove(vehicle);
            JME_OVER_THRESHOLD_SINCE.remove(vehicle.getId());
            DepotCancellationRegistry.clearReturnPending(sidingId, vehicle.getId());
        });
    }

    @Unique
    private static boolean jme$shouldRemove(
            Siding siding,
            Vehicle vehicle,
            long thresholdMillis,
            long currentMillis,
            long repeatInterval
    ) {
        if (!vehicle.getIsOnRoute()) {
            JME_OVER_THRESHOLD_SINCE.remove(vehicle.getId());
            return false;
        }

        final Long delayMillis = jme$getLiveDelayMillis(siding, vehicle, currentMillis, repeatInterval);
        // Delay > threshold cancels the train (a legacy "return_to_depot" action in old configs is treated the same way),
        // once it has lasted JME_OVER_THRESHOLD_CONFIRM_MILLIS.
        if (delayMillis == null || delayMillis <= thresholdMillis) {
            JME_OVER_THRESHOLD_SINCE.remove(vehicle.getId());
            return false;
        }
        if (JME_OVER_THRESHOLD_SINCE.size() > 10_000) {
            // Trains removed by MTR while late leave entries behind; never let them pile up.
            JME_OVER_THRESHOLD_SINCE.clear();
        }
        final long overSince = JME_OVER_THRESHOLD_SINCE.computeIfAbsent(vehicle.getId(), id -> currentMillis);
        return currentMillis - overSince >= JME_OVER_THRESHOLD_CONFIRM_MILLIS;
    }

    /**
     * MTR only refreshes a vehicle's stored deviation when it starts or stops, so a train held at a red signal keeps
     * its old (small) delay forever. This computes the current delay the same way as Vehicle#updateDeviation.
     *
     * @return the current delay, or {@code null} if the vehicle has no schedule to compare against (eg manual runs)
     */
    @Unique
    private static Long jme$getLiveDelayMillis(Siding siding, Vehicle vehicle, long currentMillis, long repeatInterval) {
        final VehicleScheduleAccessor accessor = (VehicleScheduleAccessor) (Object) vehicle;
        final long sidingDepartureTime = accessor.jme$getSidingDepartureTime();
        if (sidingDepartureTime < 0) {
            return null;
        }

        // Same position adjustment as Vehicle#getTimeAlongRoute.
        // A path rebuilt for an alternative platform is read on the siding's timetable (see TimetableProgressMap).
        final double railProgress = ((org.justnoone.jme.rail.TimetableAligned) (Object) vehicle).jme$toTimetableProgress(accessor.jme$getRailProgress()) - (accessor.jme$getSpeed() == 0 ? 1 : 0);
        final double timeAlongRoute = siding.getTimeAlongRoute(railProgress);
        if (timeAlongRoute < 0) {
            return null;
        }

        final long scheduledTimeAlongRoute = (long) Math.floor(timeAlongRoute + accessor.jme$getElapsedDwellTime());
        return TrainDelay.liveDelayMillis(currentMillis, sidingDepartureTime, scheduledTimeAlongRoute, repeatInterval);
    }
}
