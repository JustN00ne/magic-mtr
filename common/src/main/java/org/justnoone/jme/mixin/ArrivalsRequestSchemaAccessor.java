package org.justnoone.jme.mixin;

import org.mtr.core.generated.operation.ArrivalsRequestSchema;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ArrivalsRequestSchema.class, remap = false)
public interface ArrivalsRequestSchemaAccessor {

    @Accessor("platformIds")
    LongArrayList jme$getPlatformIds();

    @Accessor("platformIdsHex")
    ObjectArrayList<String> jme$getPlatformIdsHex();

    @Accessor("stationIds")
    LongArrayList jme$getStationIds();

    @Accessor("stationIdsHex")
    ObjectArrayList<String> jme$getStationIdsHex();

    @Accessor("maxCountTotal")
    long jme$getMaxCountTotal();
}
