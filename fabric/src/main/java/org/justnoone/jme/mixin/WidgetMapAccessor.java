package org.justnoone.jme.mixin;

import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetMap.class, remap = false)
public interface WidgetMapAccessor {

    @Accessor("scale")
    double jme$getScale();

    @Accessor("scale")
    void jme$setScale(double scale);

    @Accessor("centerX")
    double jme$getCenterX();

    @Accessor("centerX")
    void jme$setCenterX(double centerX);

    @Accessor("centerY")
    double jme$getCenterY();

    @Accessor("centerY")
    void jme$setCenterY(double centerY);
}
