package org.justnoone.jme.mixin;

import org.justnoone.jme.client.MagicRailRenderContext;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.mtr.core.data.Rail;
import org.mtr.mod.data.RailType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RailType.class)
public class RailTypeMixin {

    @Inject(method = "getRailColor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jme$getMagicRailColor(Rail rail, CallbackInfoReturnable<Integer> cir) {
        if (rail.isPlatform() || rail.isSiding() || rail.canTurnBack()) {
            return;
        }

        final Integer overrideSpeed = MagicRailRenderContext.getOverrideSpeed();
        if (overrideSpeed != null) {
            cir.setReturnValue(MagicRailSpeedColor.colorForSpeed(overrideSpeed));
            return;
        }

        final int speed = (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true));
        if (speed > 0) {
            cir.setReturnValue(MagicRailSpeedColor.colorForSpeed(speed));
        }
    }
}
