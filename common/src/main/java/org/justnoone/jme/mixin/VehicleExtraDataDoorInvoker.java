package org.justnoone.jme.mixin;

import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = VehicleExtraData.class, remap = false)
public interface VehicleExtraDataDoorInvoker {

    @Invoker("openDoors")
    void jme$openDoors();

    @Invoker("closeDoors")
    void jme$closeDoors();

    @Invoker("toggleDoors")
    void jme$toggleDoors();

    @Invoker("setStoppingPoint")
    void jme$setStoppingPoint(double stoppingPoint);
}

