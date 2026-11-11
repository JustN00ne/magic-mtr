package org.justnoone.jme.mixin;

import org.justnoone.jme.util.VehicleSpeedCap;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the timetable respect the siding's maximum vehicle speed.
 * <p>
 * {@link VehicleSpeedLimitMixin} caps how fast vehicles actually drive, but MTR builds the schedule (arrival times,
 * route durations and the delay baseline) in {@code generatePathDistancesAndTimeSegments} from the rail speed limits
 * only. These redirects apply the same cap there, so a 100 km/h train on a 300 km/h line is scheduled at 100 km/h
 * instead of running permanently late. Acceleration, braking, rail speed limits and dwell times are still simulated
 * by MTR's own time-segment model.
 * <p>
 * (These redirects previously targeted {@code writePathCache}, which never calls either method, so they silently
 * matched nothing.)
 */
@Mixin(Siding.class)
public abstract class SidingSpeedLimitMixin {

    @Shadow(remap = false)
    public abstract double getMaxManualSpeed();

    @Shadow(remap = false)
    public abstract double getAcceleration();

    @Shadow(remap = false)
    public abstract double getDeceleration();

    @Shadow(remap = false)
    private PathData defaultPathData;

    @Shadow(remap = false)
    @Final
    private ObjectArrayList<PathData> pathMainRoute;

    @Shadow(remap = false)
    private void generatePathDistancesAndTimeSegments() {
    }

    @Unique
    private double jme$maxSpeedBeforeUpdate;
    @Unique
    private double jme$accelerationBeforeUpdate;
    @Unique
    private double jme$decelerationBeforeUpdate;

    @Redirect(
            method = "generatePathDistancesAndTimeSegments",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"
            ),
            remap = false
    )
    private double jme$capUpcomingSlowerSpeed(
            ObjectList<PathData> path,
            int startIndex,
            double railProgress,
            double speed,
            double deceleration
    ) {
        return VehicleSpeedCap.cap(Siding.getUpcomingSlowerSpeed(path, startIndex, railProgress, speed, deceleration), getMaxManualSpeed());
    }

    @Redirect(
            method = "generatePathDistancesAndTimeSegments",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D"
            ),
            remap = false
    )
    private double jme$capPathSpeedLimit(PathData pathData) {
        return VehicleSpeedCap.cap(pathData.getSpeedLimitMetersPerMillisecond(), getMaxManualSpeed());
    }

    @Inject(method = "updateData", at = @At("HEAD"), remap = false)
    private void jme$rememberSpeedConstraints(ReaderBase readerBase, CallbackInfo ci) {
        jme$maxSpeedBeforeUpdate = getMaxManualSpeed();
        jme$accelerationBeforeUpdate = getAcceleration();
        jme$decelerationBeforeUpdate = getDeceleration();
    }

    /**
     * MTR only rebuilds a siding's time segments when it loads or its depot regenerates paths, so after the train's
     * maximum speed (or acceleration / braking) was changed in the siding screen the timetable kept the old travel
     * times until a manual depot refresh. Rebuild them right away; this reuses the existing paths (no pathfinding).
     */
    @Inject(method = "updateData", at = @At("TAIL"), remap = false)
    private void jme$regenerateTimetableOnSpeedChange(ReaderBase readerBase, CallbackInfo ci) {
        // Also runs while the siding is being constructed from saved data, before its paths exist.
        if (defaultPathData == null || pathMainRoute == null || pathMainRoute.isEmpty()) {
            return;
        }
        if (VehicleSpeedCap.hasChanged(jme$maxSpeedBeforeUpdate, getMaxManualSpeed())
                || VehicleSpeedCap.hasChanged(jme$accelerationBeforeUpdate, getAcceleration())
                || VehicleSpeedCap.hasChanged(jme$decelerationBeforeUpdate, getDeceleration())) {
            // The rebuild also clears the siding's vehicles. MTR's own updateData only drops the parked ones (already
            // done above), so keep the trains that are running: their paths are unchanged.
            final ObjectArraySet<Vehicle> vehicles = ((SidingVehiclesAccessor) this).jme$getVehicles();
            final ObjectArrayList<Vehicle> runningVehicles = new ObjectArrayList<>(vehicles);
            generatePathDistancesAndTimeSegments();
            vehicles.addAll(runningVehicles);
        }
    }
}
