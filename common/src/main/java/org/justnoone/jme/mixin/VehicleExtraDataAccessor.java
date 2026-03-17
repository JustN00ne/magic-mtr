package org.justnoone.jme.mixin;

import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VehicleExtraData.class)
public interface VehicleExtraDataAccessor {

    @Invoker(value = "setSpeedTarget", remap = false)
    void jme$setSpeedTarget(double speedTarget);
}
