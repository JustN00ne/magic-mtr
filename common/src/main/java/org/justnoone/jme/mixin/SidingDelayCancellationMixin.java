package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.CanceledTrainRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingDelayCancellationMixin {

    @Inject(method = "simulateTrain", at = @At("TAIL"), remap = false)
    private void jme$cancelDelayedTrains(long millisElapsed, ObjectArrayList<?> vehiclePositions, CallbackInfo ci) {
        final Siding siding = (Siding) (Object) this;
        final long sidingId = siding.getId();
        final DepotCancellationRegistry.Settings settings = DepotCancellationRegistry.getForSiding(siding);
        if (!settings.enabled) {
            DepotCancellationRegistry.clearPendingForSiding(sidingId);
            return;
        }

        final long thresholdMillis = DepotCancellationRegistry.getThresholdMillis(settings);
        final ObjectArraySet<Vehicle> vehicles = ((SidingVehiclesAccessor) this).jme$getVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            return;
        }

        final ObjectArrayList<Vehicle> vehiclesToRemove = new ObjectArrayList<>();
        for (final Vehicle vehicle : vehicles) {
            if (vehicle == null) {
                continue;
            }

            final long vehicleId = vehicle.getId();
            if (jme$shouldDespawn(vehicle, sidingId, vehicleId, thresholdMillis)) {
                vehiclesToRemove.add(vehicle);
            }
        }

        if (vehiclesToRemove.isEmpty()) {
            return;
        }

        // Instant despawn: remove the simulated vehicle entity and keep a canceled PIDS/map entry.
        vehiclesToRemove.forEach(vehicle -> {
            CanceledTrainRegistry.markCanceled(vehicle);
            vehicles.remove(vehicle);
            DepotCancellationRegistry.clearReturnPending(sidingId, vehicle.getId());
        });
    }

    @Unique
    private static boolean jme$shouldDespawn(Vehicle vehicle, long sidingId, long vehicleId, long thresholdMillis) {
        if (DepotCancellationRegistry.isReturnPending(sidingId, vehicleId)) {
            return vehicle.closeToDepot();
        }

        if (!vehicle.getIsOnRoute()) {
            return false;
        }

        // Spec: Delay > T_cancel (even by 1 second) triggers cancellation.
        final long delayMillis = ((VehicleDelayAccessor) vehicle).jme$getDeviation();
        return delayMillis > thresholdMillis;
    }
}
