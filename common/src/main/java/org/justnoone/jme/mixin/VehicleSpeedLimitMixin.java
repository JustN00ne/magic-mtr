package org.justnoone.jme.mixin;

import org.justnoone.jme.util.VehicleSpeedCap;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Vehicle.class)
public abstract class VehicleSpeedLimitMixin {

    @Shadow(remap = false)
    public VehicleExtraData vehicleExtraData;

    @Redirect(
            method = "simulateMoving",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D"
            ),
            remap = false
    )
    private double jme$capPathSpeedLimit(PathData pathData) {
        return jme$capSpeed(pathData.getSpeedLimitMetersPerMillisecond());
    }

    @Redirect(
            method = "simulateMoving",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"
            ),
            remap = false
    )
    private double jme$capUpcomingSlowerSpeed(
            org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList<PathData> path,
            int startIndex,
            double railProgress,
            double speed,
            double deceleration
    ) {
        return jme$capSpeed(Siding.getUpcomingSlowerSpeed(path, startIndex, railProgress, speed, deceleration));
    }

    private double jme$capSpeed(double speed) {
        return VehicleSpeedCap.cap(speed, vehicleExtraData.getMaxManualSpeed());
    }
}
