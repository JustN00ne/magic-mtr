package org.justnoone.jme.mixin;

import org.justnoone.jme.client.screen.JmeSettingsScreen;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.ScreenAbstractMapping;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.ConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMagicSettingsButtonMixin implements IGui {

    @Unique
    private static final Identifier JME_MAGIC_ICON_TEXTURE = new Identifier("jme", "textures/item/magic_icon.png");

    @Unique
    private ButtonWidgetExtension jme$magicSettingsButton;
    @Unique
    private ClickableWidget jme$magicSettingsButtonWidget;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createMagicSettingsButton(org.mtr.mapping.holder.Screen previousScreen, CallbackInfo ci) {
        jme$magicSettingsButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), button -> jme$openMagicSettings());
        jme$magicSettingsButtonWidget = new ClickableWidget(jme$magicSettingsButton);
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initMagicSettingsButton(CallbackInfo ci) {
        if (jme$magicSettingsButton == null || jme$magicSettingsButtonWidget == null) {
            return;
        }

        final int margin = 6;
        final int x = ((ScreenAbstractMapping) (Object) this).getWidthMapped() - SQUARE_SIZE - margin;
        final int y = margin;
        IDrawing.setPositionAndWidth(jme$magicSettingsButton, x, y, SQUARE_SIZE);

        // The screen clears children on each init; always re-add so it doesn't disappear after resizes.
        ((ScreenExtension) (Object) this).addChild(jme$magicSettingsButtonWidget);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderMagicSettingsButtonIcon(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (jme$magicSettingsButton == null) {
            return;
        }

        final int iconSize = 14;
        final int iconX1 = jme$magicSettingsButton.getX2() + (SQUARE_SIZE - iconSize) / 2;
        final int iconY1 = jme$magicSettingsButton.getY2() + (SQUARE_SIZE - iconSize) / 2;
        final int iconX2 = iconX1 + iconSize;
        final int iconY2 = iconY1 + iconSize;

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 650);
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingTexture(JME_MAGIC_ICON_TEXTURE);
        IGui.drawTexture(guiDrawing, iconX1, iconY1, iconX2, iconY2, 0F, 0F, 1F, 1F);
        guiDrawing.finishDrawingTexture();
        graphicsHolder.pop();
    }

    @Unique
    private void jme$openMagicSettings() {
        try {
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }

            client.openScreen(new Screen(new JmeSettingsScreen((ScreenExtension) (Object) this)));
        } catch (Exception ignored) {
        }
    }
}
