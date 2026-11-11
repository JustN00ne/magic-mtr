package org.justnoone.jme.mixin;

import org.mtr.core.data.Trip;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Trip.class, remap = false)
public interface TripStopTimesAccessor {

    @Accessor("stopTimes")
    ObjectArrayList<Trip.StopTime> jme$getStopTimes();
}
