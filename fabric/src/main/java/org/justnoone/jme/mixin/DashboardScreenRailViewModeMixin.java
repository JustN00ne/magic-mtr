package org.justnoone.jme.mixin;

import org.justnoone.jme.client.DashboardRailViewMode;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.mapper.TexturedButtonWidgetExtension;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenRailViewModeMixin implements IGui {

    @Shadow
    @Final
    private TexturedButtonWidgetExtension buttonMapTopView;

    @Shadow
    @Final
    private TexturedButtonWidgetExtension buttonMapCurrentY;

    @Unique
    private ButtonWidgetExtension jme$buttonRailViewMode;
    @Unique
    private boolean jme$railViewModeButtonAdded;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$initRailViewModeButton(TransportMode transportMode, CallbackInfo ci) {
        jme$buttonRailViewMode = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 3, SQUARE_SIZE, TextHelper.literal("BLU"), button -> {
            DashboardRailViewMode.cycle();
            jme$updateRailViewModeLabel();
        });
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$layoutRailViewModeButton(CallbackInfo ci) {
        if (jme$buttonRailViewMode == null) {
            return;
        }

        final int width = SQUARE_SIZE * 3;
        final int x = buttonMapTopView.getX2() - width - 4;
        final int y = buttonMapTopView.getY2() - SQUARE_SIZE - 2;
        IDrawing.setPositionAndWidth(jme$buttonRailViewMode, x, y, width);

        if (!jme$railViewModeButtonAdded) {
            jme$addChild(new ClickableWidget(jme$buttonRailViewMode));
            jme$railViewModeButtonAdded = true;
        }

        jme$updateRailViewModeLabel();
    }

    @Inject(method = "toggleButtons", at = @At("TAIL"), remap = false)
    private void jme$toggleRailViewModeButton(CallbackInfo ci) {
        if (jme$buttonRailViewMode == null) {
            return;
        }

        final boolean visible = buttonMapTopView.visible || buttonMapCurrentY.visible;
        jme$buttonRailViewMode.visible = visible;
        jme$buttonRailViewMode.active = visible;
        jme$updateRailViewModeLabel();
    }

    @Unique
    private void jme$updateRailViewModeLabel() {
        if (jme$buttonRailViewMode == null) {
            return;
        }
        final String label = DashboardRailViewMode.isSpeedMode() ? "SPD" : "BLU";
        jme$buttonRailViewMode.setMessage2(Text.cast(TextHelper.literal(label)));
    }

    @Unique
    private void jme$addChild(ClickableWidget clickableWidget) {
        try {
            Method addChildMethod = null;
            Class<?> targetClass = this.getClass();
            while (targetClass != null && addChildMethod == null) {
                for (final Method method : targetClass.getDeclaredMethods()) {
                    if ("addChild".equals(method.getName()) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(clickableWidget.getClass())) {
                        addChildMethod = method;
                        break;
                    }
                }
                targetClass = targetClass.getSuperclass();
            }

            if (addChildMethod != null) {
                addChildMethod.setAccessible(true);
                addChildMethod.invoke(this, clickableWidget);
            }
        } catch (Exception ignored) {
        }
    }
}
