package org.justnoone.jme.mixin;

import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Vehicle.class, remap = false)
public interface VehicleMutableExtraDataAccessor {

    @Mutable
    @Accessor("vehicleExtraData")
    void jme$setVehicleExtraData(VehicleExtraData vehicleExtraData);
}
