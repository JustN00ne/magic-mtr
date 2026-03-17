package org.justnoone.jme.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Siding.class)
public abstract class SidingSpeedLimitMixin {

    @Shadow(remap = false)
    public abstract double getMaxManualSpeed();

    @Redirect(
            method = "writePathCache",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"
            ),
            remap = false,
            require = 0
    )
    private double jme$capUpcomingSlowerSpeed(
            ObjectList<PathData> path,
            int startIndex,
            double railProgress,
            double speed,
            double deceleration
    ) {
        return jme$capSpeed(Siding.getUpcomingSlowerSpeed(path, startIndex, railProgress, speed, deceleration));
    }

    @Redirect(
            method = "writePathCache",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D"
            ),
            remap = false,
            require = 0
    )
    private double jme$capPathSpeedLimit(PathData pathData) {
        return jme$capSpeed(pathData.getSpeedLimitMetersPerMillisecond());
    }

    private double jme$capSpeed(double speed) {
        final double maxManualSpeed = getMaxManualSpeed();
        if (Double.isFinite(maxManualSpeed) && maxManualSpeed > 0 && speed > maxManualSpeed) {
            return maxManualSpeed;
        }
        return speed;
    }
}
