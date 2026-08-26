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
    private int getMainHeight() {
        throw new AssertionError();
    }

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
        // MTR tweaks this layout across versions; only shift widgets when they would overflow
        // the bottom of the screen (otherwise we end up with severely offset input boxes).
        final int screenBottom = Math.max(0, getMainHeight() + SQUARE_SIZE);
        final int resetBottom = buttonReset.getY2() + buttonReset.getHeight2();
        final int overflow = resetBottom - screenBottom;
        if (overflow <= 0) {
            return;
        }

        textFieldColor.setY2(textFieldColor.getY2() - overflow);
        textFieldRed.setY2(textFieldRed.getY2() - overflow);
        textFieldGreen.setY2(textFieldGreen.getY2() - overflow);
        textFieldBlue.setY2(textFieldBlue.getY2() - overflow);
        buttonReset.setY2(buttonReset.getY2() - overflow);
    }
}
