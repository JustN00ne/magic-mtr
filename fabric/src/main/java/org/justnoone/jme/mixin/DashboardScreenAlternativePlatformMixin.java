package org.justnoone.jme.mixin;

import org.justnoone.jme.client.AlternativePlatformClient;
import org.justnoone.jme.client.DashboardRouteFolderStore;
import org.justnoone.jme.client.DashboardRouteRenderState;
import org.justnoone.jme.client.screen.AlternativePlatformMenuScreen;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardListItem;
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
public abstract class DashboardScreenAlternativePlatformMixin implements IGui {

    // MTR's left panel is 144 wide; its custom destination field spans x=2..142 on the row above the bottom buttons.
    @Unique
    private static final int JME_PANEL_WIDTH = 144;

    @Shadow
    private Route editingRoute;

    @Shadow
    private int editingRoutePlatformIndex;

    @Shadow
    @Final
    private ButtonWidgetExtension buttonDoneEditingRouteDestination;

    @Shadow
    @Final
    private TextFieldWidgetExtension textFieldCustomDestination;

    @Unique
    private ButtonWidgetExtension jme$buttonAlternativePlatform;
    @Unique
    private ButtonWidgetExtension jme$buttonAlternativeSelector;
    @Unique
    private boolean jme$alternativeSelectionMode;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createAlternativeButton(org.mtr.core.data.TransportMode transportMode, CallbackInfo ci) {
        jme$buttonAlternativePlatform = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("⎇"), button -> {
            jme$alternativeSelectionMode = !jme$alternativeSelectionMode && jme$canEditAlternatives();
            jme$updateAlternativeButtons();
        });
        jme$buttonAlternativeSelector = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("≣"), button -> jme$openAlternativeSelector());
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initAlternativeButton(CallbackInfo ci) {
        if (jme$buttonAlternativePlatform == null || jme$buttonAlternativeSelector == null) {
            return;
        }

        // init2 runs on first open and again after every resize, when Screen#clearAndInit has removed all children,
        // so the buttons have to be added every time.
        jme$addChild(new ClickableWidget(jme$buttonAlternativeSelector));
        jme$addChild(new ClickableWidget(jme$buttonAlternativePlatform));
        jme$updateAlternativeButtons();
    }

    @Inject(method = "toggleButtons", at = @At("TAIL"), remap = false)
    private void jme$toggleAlternativeButton(CallbackInfo ci) {
        if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            jme$alternativeSelectionMode = false;
        }
        jme$updateAlternativeButtons();
        DashboardRouteRenderState.update(editingRoute, editingRoutePlatformIndex);
    }

    @Inject(method = "onDrawArea", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$openAlternativeSelectorFromList(DashboardListItem dashboardListItem, int index, CallbackInfo ci) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        if (editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return;
        }

        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, index);
        if (rowMetadata == null || rowMetadata.folder) {
            ci.cancel();
            return;
        }

        final int resolvedIndex = jme$resolveRoutePlatformIndex(rowMetadata);
        if (resolvedIndex < 0 || resolvedIndex >= editingRoute.getRoutePlatforms().size()) {
            ci.cancel();
            return;
        }

        editingRoutePlatformIndex = resolvedIndex;
        jme$openAlternativeSelector();
        ci.cancel();
    }

    @Inject(method = "stopEditing", at = @At("TAIL"), remap = false)
    private void jme$clearAlternativeSelectionMode(CallbackInfo ci) {
        jme$alternativeSelectionMode = false;
        jme$updateAlternativeButtons();
        DashboardRouteRenderState.clear();
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false)
    private void jme$syncRouteOverlayState(CallbackInfo ci) {
        DashboardRouteRenderState.update(editingRoute, editingRoutePlatformIndex);
    }

    @Inject(method = "onClose2", at = @At("TAIL"), remap = false)
    private void jme$clearRouteOverlayState(CallbackInfo ci) {
        DashboardRouteRenderState.clear();
    }

    @Inject(method = "onClickAddPlatformToRoute", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$toggleAlternativePlatformOnMapClick(long platformId, CallbackInfo ci) {
        if (!jme$alternativeSelectionMode) {
            return;
        }

        // While picking alternatives, map clicks never add stops to the route.
        ci.cancel();
        if (!jme$canEditAlternatives()) {
            jme$alternativeSelectionMode = false;
            jme$updateAlternativeButtons();
            return;
        }

        final Platform primaryPlatform = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex).platform;
        final Platform clickedPlatform = MinecraftClientData.getDashboardInstance().platformIdMap.get(platformId);
        if (!AlternativePlatformRegistry.isCompatibleAlternative(primaryPlatform, clickedPlatform)) {
            // Only platforms of the same station and transport mode can stand in for this stop.
            return;
        }

        final boolean enabled = !AlternativePlatformRegistry.getAlternatives(editingRoute.getId(), primaryPlatform.getId()).contains(platformId);
        AlternativePlatformClient.setAlternative(editingRoute.getId(), primaryPlatform.getId(), platformId, enabled);
    }

    @Unique
    private void jme$openAlternativeSelector() {
        if (!jme$canEditAlternatives()) {
            return;
        }

        jme$alternativeSelectionMode = false;
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new AlternativePlatformMenuScreen((ScreenExtension) (Object) this, editingRoute, editingRoutePlatformIndex)));
    }

    /**
     * Shows the two compact buttons next to MTR's custom destination field (only while a route stop is being edited)
     * and narrows that field to make room, instead of drawing the buttons on top of it.
     */
    @Unique
    private void jme$updateAlternativeButtons() {
        if (jme$buttonAlternativePlatform == null || jme$buttonAlternativeSelector == null || buttonDoneEditingRouteDestination == null) {
            return;
        }

        final boolean show = jme$canEditAlternatives() && jme$hasStationAlternatives();
        final int rowY = buttonDoneEditingRouteDestination.getY2() - SQUARE_SIZE - 2;
        final int buttonsX = JME_PANEL_WIDTH - SQUARE_SIZE * 2;
        IDrawing.setPositionAndWidth(jme$buttonAlternativeSelector, buttonsX, rowY, SQUARE_SIZE);
        IDrawing.setPositionAndWidth(jme$buttonAlternativePlatform, buttonsX + SQUARE_SIZE, rowY, SQUARE_SIZE);
        if (textFieldCustomDestination != null) {
            IDrawing.setPositionAndWidth(textFieldCustomDestination, 2, rowY, (show ? buttonsX - 2 : JME_PANEL_WIDTH - 2) - 2);
        }

        jme$buttonAlternativeSelector.visible = show;
        jme$buttonAlternativeSelector.active = show;
        jme$buttonAlternativePlatform.visible = show;
        jme$buttonAlternativePlatform.active = show;
        if (!show) {
            jme$alternativeSelectionMode = false;
        }
        jme$buttonAlternativePlatform.setMessage2(Text.cast(TextHelper.literal(jme$alternativeSelectionMode ? "⎇*" : "⎇")));
    }

    @Unique
    private boolean jme$canEditAlternatives() {
        if (!AlternativePlatformRegistry.isEnabled() || editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return false;
        }
        if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            return false;
        }
        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
        return routePlatformData != null && routePlatformData.platform != null && routePlatformData.platform.area != null;
    }

    @Unique
    private boolean jme$hasStationAlternatives() {
        final Platform primaryPlatform = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex).platform;
        return !AlternativePlatformRegistry.getCompatiblePlatforms(primaryPlatform).isEmpty()
                || !AlternativePlatformRegistry.getAlternatives(editingRoute.getId(), primaryPlatform.getId()).isEmpty();
    }

    @Unique
    private int jme$resolveRoutePlatformIndex(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null) {
            return -1;
        }

        if (rowMetadata.platformIndex >= 0 && rowMetadata.platformIndex < editingRoute.getRoutePlatforms().size()) {
            final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(rowMetadata.platformIndex);
            if (routePlatformData != null && routePlatformData.platform != null && (rowMetadata.platformId == 0 || routePlatformData.platform.getId() == rowMetadata.platformId)) {
                return rowMetadata.platformIndex;
            }
        }

        if (rowMetadata.platformId != 0) {
            for (int i = 0; i < editingRoute.getRoutePlatforms().size(); i++) {
                final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(i);
                if (routePlatformData != null && routePlatformData.platform != null && routePlatformData.platform.getId() == rowMetadata.platformId) {
                    return i;
                }
            }
        }

        return -1;
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
