package org.justnoone.jme.mixin;

import org.justnoone.jme.client.screen.CronTimetableScreen;
import org.justnoone.jme.client.screen.DepotCancellationSettingsScreen;
import org.justnoone.jme.rail.CronTimetableRegistry;
import org.mtr.core.data.Depot;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
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
    private int rightPanelsX;

    @Shadow
    @Final
    private boolean showScheduleControls;

    @Shadow
    @Final
    private ButtonWidgetExtension buttonInstantDeploy;

    @Unique
    private ButtonWidgetExtension jme$cancellationButton;

    @Unique
    private ButtonWidgetExtension jme$cronButton;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initCancellationButton(CallbackInfo ci) {
        // Delay cancellations only apply to scheduled (non continuous-movement) depots.
        if (!showScheduleControls) {
            return;
        }

        if (jme$cancellationButton == null) {
            jme$cancellationButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Cancellations"), button -> jme$openCancellationSettings());
        }
        if (jme$cronButton == null) {
            jme$cronButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Cron"), button -> jme$openCronTimetable());
        }

        // Share MTR's full-width "Instant Deploy" row instead of stacking below "Clear Trains", where the button
        // covered the "Repeat Indefinitely" checkbox and the sidings/route-segment text.
        final int screenWidth = ((ScreenExtension) (Object) this).getWidthMapped();
        final int rowWidth = screenWidth - rightPanelsX;
        final int thirdWidth = rowWidth / 3;
        final int rowY = buttonInstantDeploy.getY2();
        IDrawing.setPositionAndWidth(buttonInstantDeploy, rightPanelsX, rowY, thirdWidth);
        IDrawing.setPositionAndWidth(jme$cancellationButton, rightPanelsX + thirdWidth, rowY, thirdWidth);
        IDrawing.setPositionAndWidth(jme$cronButton, rightPanelsX + thirdWidth * 2, rowY, rowWidth - thirdWidth * 2);
        // Older Minecraft versions do not clip button text, so use short labels when a third of the row is narrow.
        final boolean narrow = GraphicsHolder.getTextWidth("Cancellations") + 8 > thirdWidth;
        jme$cancellationButton.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal(narrow ? "Cancel" : "Cancellations")));
        if (narrow && GraphicsHolder.getTextWidth(buttonInstantDeploy.getMessage2().getString()) + 8 > thirdWidth) {
            buttonInstantDeploy.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal("Deploy")));
        }
        jme$cronButton.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal(jme$isCronActive() ? (narrow ? "Cron*" : "Cron: On") : "Cron")));

        // init2 runs on first open and after every resize (Screen#clearAndInit clears all children), so add them every time.
        jme$addChild(new ClickableWidget(jme$cancellationButton));
        jme$addChild(new ClickableWidget(jme$cronButton));
    }

    @Unique
    private boolean jme$isCronActive() {
        final NameColorDataBase data = ((EditNameColorScreenBaseAccessor) this).jme$getData();
        return data instanceof Depot && CronTimetableRegistry.getActive(((Depot) data).getId()) != null;
    }

    @Unique
    private void jme$openCronTimetable() {
        final NameColorDataBase data = ((EditNameColorScreenBaseAccessor) this).jme$getData();
        if (data instanceof Depot) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new CronTimetableScreen((ScreenExtension) (Object) this, (Depot) data)));
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
