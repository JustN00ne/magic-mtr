package org.justnoone.jme.mixin;

import org.justnoone.jme.client.screen.RouteTimetableScreen;
import org.justnoone.jme.rail.RouteTimetableRegistry;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Route;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.EditRouteScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Timetable" button to MTR's route screen, which opens the route's settings (flexible timetable and headcode
 * class).
 */
@Mixin(value = EditRouteScreen.class, remap = false)
public abstract class EditRouteScreenTimetableMixin implements IGui {

    /**
     * Below MTR's left column (route type, checkboxes, light rail number and the circular route checkboxes, which end
     * at y = 204).
     */
    @Unique
    private static final int JME_TIMETABLE_BUTTON_Y = 208;

    @Unique
    private ButtonWidgetExtension jme$timetableButton;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initTimetableButton(CallbackInfo ci) {
        final Route route = jme$getRoute();
        if (route == null || route.getTransportMode().continuousMovement) {
            return;
        }
        if (jme$timetableButton == null) {
            jme$timetableButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Timetable"), button -> jme$openTimetable());
        }
        jme$timetableButton.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal(RouteTimetableRegistry.get(route.getId()) == null ? "Timetable" : "Timetable: On")));
        IDrawing.setPositionAndWidth(jme$timetableButton, 20, JME_TIMETABLE_BUTTON_Y, 160);
        // init2 runs again after every resize, which clears the children.
        ((ScreenExtension) (Object) this).addChild(new ClickableWidget(jme$timetableButton));
    }

    @Unique
    private Route jme$getRoute() {
        final NameColorDataBase data = ((EditNameColorScreenBaseAccessor) this).jme$getData();
        return data instanceof Route ? (Route) data : null;
    }

    @Unique
    private void jme$openTimetable() {
        final Route route = jme$getRoute();
        if (route != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new RouteTimetableScreen((ScreenExtension) (Object) this, route)));
        }
    }
}
