package org.justnoone.jme.mixin;

import org.justnoone.jme.client.screen.DepotCancellationSettingsScreen;
import org.mtr.core.data.Depot;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.EditDepotScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = EditDepotScreen.class, remap = false)
public abstract class EditDepotScreenCancellationMixin implements IGui {

    @Shadow
    @Final
    private ButtonWidgetExtension buttonClearTrains;

    @Unique
    private ButtonWidgetExtension jme$cancellationButton;
    @Unique
    private boolean jme$cancellationButtonAdded;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initCancellationButton(CallbackInfo ci) {
        if (jme$cancellationButton == null) {
            jme$cancellationButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Cancellations"), button -> jme$openCancellationSettings());
        }

        final int x = buttonClearTrains.getX2();
        final int width = buttonClearTrains.getWidth2();
        final int y = Math.max(0, buttonClearTrains.getY2() + SQUARE_SIZE * 2 + 4);
        IDrawing.setPositionAndWidth(jme$cancellationButton, x, y, width);

        // EditDepotScreen can be re-initialized after returning from child screens.
        // Re-add every init pass so the tab never disappears.
        jme$cancellationButtonAdded = false;
        if (!jme$cancellationButtonAdded) {
            jme$addChild(new ClickableWidget(jme$cancellationButton));
            jme$cancellationButtonAdded = true;
        }
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false)
    private void jme$updateCancellationButtonState(CallbackInfo ci) {
        if (jme$cancellationButton != null) {
            jme$cancellationButton.visible = true;
            jme$cancellationButton.active = true;
        }
    }

    @Unique
    private void jme$openCancellationSettings() {
        final NameColorDataBase data = ((EditNameColorScreenBaseAccessor) this).jme$getData();
        if (!(data instanceof Depot)) {
            return;
        }

        final Depot depot = (Depot) data;
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new DepotCancellationSettingsScreen((ScreenExtension) (Object) this, depot.getId())));
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
