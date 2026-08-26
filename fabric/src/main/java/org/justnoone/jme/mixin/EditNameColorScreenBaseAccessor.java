package org.justnoone.jme.mixin;

import org.mtr.core.data.NameColorDataBase;
import org.mtr.mod.screen.EditNameColorScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EditNameColorScreenBase.class, remap = false)
public interface EditNameColorScreenBaseAccessor {

    @Accessor("data")
    NameColorDataBase jme$getData();
}
