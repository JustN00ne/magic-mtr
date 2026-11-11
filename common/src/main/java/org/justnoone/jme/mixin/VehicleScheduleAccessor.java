package org.justnoone.jme.mixin;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleSchema.class, remap = false)
public interface VehicleScheduleAccessor {

    @Accessor("speed")
    double jme$getSpeed();

    @Accessor("railProgress")
    double jme$getRailProgress();

    @Accessor("elapsedDwellTime")
    long jme$getElapsedDwellTime();

    @Accessor("elapsedDwellTime")
    void jme$setElapsedDwellTime(long elapsedDwellTime);

    @Accessor("sidingDepartureTime")
    long jme$getSidingDepartureTime();
}
