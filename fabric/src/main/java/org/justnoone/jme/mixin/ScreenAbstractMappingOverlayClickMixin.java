package org.justnoone.jme.mixin;

import org.justnoone.jme.client.ui.OverlayClickHandler;
import org.mtr.mapping.holder.ScreenAbstractMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ScreenAbstractMapping.class, remap = false)
public abstract class ScreenAbstractMappingOverlayClickMixin {

    @Inject(method = "mouseClicked2", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$dispatchOverlayMouseClicks(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof OverlayClickHandler) {
            if (((OverlayClickHandler) (Object) this).jme$handleOverlayClick(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
            }
        }
    }
}
