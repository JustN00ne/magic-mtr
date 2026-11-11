package org.justnoone.jme.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.generated.data.NameColorDataBaseSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NameColorDataBaseSchema.class, remap = false)
public interface NameColorDataAccessor {

    @Accessor("data")
    Data jme$getData();
}
