package org.justnoone.jme.mixin;

import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mod.data.IGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.mtr.mod.screen.WidgetColorSelector$ColorSelectorScreen", remap = false)
public abstract class ColorSelectorScreenLayoutMixin implements IGui {

    @Shadow
    @Final
    private TextFieldWidgetExtension textFieldColor;

    @Shadow
    @Final
    private TextFieldWidgetExtension textFieldRed;

    @Shadow
    @Final
    private TextFieldWidgetExtension textFieldGreen;

    @Shadow
    @Final
    private TextFieldWidgetExtension textFieldBlue;

    @Shadow
    @Final
    private ButtonWidgetExtension buttonReset;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$shiftColorControlsUp(CallbackInfo ci) {
        final int offset = SQUARE_SIZE * 2;
        textFieldColor.setY2(textFieldColor.getY2() - offset);
        textFieldRed.setY2(textFieldRed.getY2() - offset);
        textFieldGreen.setY2(textFieldGreen.getY2() - offset);
        textFieldBlue.setY2(textFieldBlue.getY2() - offset);
        buttonReset.setY2(buttonReset.getY2() - offset);
    }
}
