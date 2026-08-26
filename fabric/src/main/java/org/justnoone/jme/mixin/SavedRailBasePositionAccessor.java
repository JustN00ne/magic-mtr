package org.justnoone.jme.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.generated.data.SavedRailBaseSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SavedRailBaseSchema.class, remap = false)
public interface SavedRailBasePositionAccessor {

    @Accessor("position1")
    Position jme$getPosition1();

    @Accessor("position2")
    Position jme$getPosition2();
}
