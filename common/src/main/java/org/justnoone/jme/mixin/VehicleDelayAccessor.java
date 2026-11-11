package org.justnoone.jme.mixin;

import org.mtr.core.data.Vehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Vehicle.class, remap = false)
public interface VehicleDelayAccessor {

    @Accessor("deviation")
    long jme$getDeviation();
}
