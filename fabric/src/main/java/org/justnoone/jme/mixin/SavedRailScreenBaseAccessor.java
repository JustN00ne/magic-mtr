package org.justnoone.jme.mixin;

import org.mtr.core.data.SavedRailBase;
import org.mtr.mod.screen.SavedRailScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SavedRailScreenBase.class, remap = false)
public interface SavedRailScreenBaseAccessor {

    @Accessor("savedRailBase")
    SavedRailBase<?, ?> jme$getSavedRailBase();
}
