package org.justnoone.jme.mixin;

import org.mtr.core.generated.data.PathDataSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PathDataSchema.class, remap = false)
public interface PathDataDwellTimeAccessor {

    @Mutable
    @Accessor("dwellTime")
    void jme$setDwellTime(long dwellTime);
}
