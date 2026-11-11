package org.justnoone.jme.mixin;

import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Vehicle.class, remap = false)
public interface VehicleSidingAccessor {

    @Accessor("siding")
    Siding jme$getSiding();
}
