package org.justnoone.jme.mixin;

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
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardList;
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
import java.util.List;

/**
 * An "Alternative platforms" button under the stop list while a route is being edited. It opens the alternatives of
 * the selected stop, or of the route's first stop that has other platforms when no stop is selected.
 */
@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenAlternativePlatformMixin implements IGui {

    // MTR's left panel is 144 wide; its custom destination field spans x=2..142 on the row above the bottom buttons.
    @Unique
    private static final int JME_PANEL_WIDTH = 144;
    @Unique
    private static final int JME_ROW_HEIGHT = SQUARE_SIZE + 2;

    @Shadow
    private Route editingRoute;

    @Shadow
    private int editingRoutePlatformIndex;

    @Shadow
    @Final
    private DashboardList dashboardList;

    @Shadow
    @Final
    private ButtonWidgetExtension buttonDoneEditingRoute;

    @Unique
    private ButtonWidgetExtension jme$alternativesButton;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createAlternativesButton(org.mtr.core.data.TransportMode transportMode, CallbackInfo ci) {
        jme$alternativesButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Alternative platforms"), button -> jme$openAlternativesForSelection());
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initAlternativesButton(CallbackInfo ci) {
        if (jme$alternativesButton == null) {
            return;
        }
        // init2 runs on first open and again after every resize, when Screen#clearAndInit has removed all children,
        // so the button has to be added every time.
        jme$addChild(new ClickableWidget(jme$alternativesButton));
        jme$updateAlternativesButton(true);
    }

    @Inject(method = "toggleButtons", at = @At("TAIL"), remap = false)
    private void jme$toggleAlternativesButton(CallbackInfo ci) {
        // MTR has just sized the list for the current mode: make room for the button below it.
        jme$updateAlternativesButton(true);
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

        jme$openAlternatives(resolvedIndex);
        ci.cancel();
    }

    @Inject(method = "stopEditing", at = @At("TAIL"), remap = false)
    private void jme$hideAlternativesButton(CallbackInfo ci) {
        jme$updateAlternativesButton(false);
        DashboardRouteRenderState.clear();
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false)
    private void jme$syncRouteOverlayState(CallbackInfo ci) {
        DashboardRouteRenderState.update(editingRoute, editingRoutePlatformIndex);
        // The count follows changes made in the alternatives screen.
        jme$updateAlternativesButton(false);
    }

    @Inject(method = "onClose2", at = @At("TAIL"), remap = false)
    private void jme$clearRouteOverlayState(CallbackInfo ci) {
        DashboardRouteRenderState.clear();
    }

    @Unique
    private void jme$openAlternativesForSelection() {
        if (!jme$isEditingRoute()) {
            return;
        }
        if (((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            jme$openAlternatives(editingRoutePlatformIndex);
            return;
        }
        // No stop selected: start at the first stop where there is another platform to choose.
        final List<RoutePlatformData> stops = editingRoute.getRoutePlatforms();
        for (int i = 0; i < stops.size(); i++) {
            if (!AlternativePlatformRegistry.getCompatiblePlatforms(stops.get(i).platform).isEmpty()) {
                jme$openAlternatives(i);
                return;
            }
        }
        jme$openAlternatives(0);
    }

    @Unique
    private void jme$openAlternatives(int stopIndex) {
        if (editingRoute == null || stopIndex < 0 || stopIndex >= editingRoute.getRoutePlatforms().size()) {
            return;
        }
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new AlternativePlatformMenuScreen((ScreenExtension) (Object) this, editingRoute, stopIndex)));
    }

    /**
     * Shows the button on its own row above the row MTR keeps above "Done" while a route is edited (the route's name
     * and colour, or the selected stop's custom destination), with the number of alternatives of the selected stop.
     *
     * @param resizeList whether MTR has just sized the list, which then gets one row shorter
     */
    @Unique
    private void jme$updateAlternativesButton(boolean resizeList) {
        if (jme$alternativesButton == null || buttonDoneEditingRoute == null) {
            return;
        }

        final boolean show = jme$isEditingRoute();
        jme$alternativesButton.visible = show;
        jme$alternativesButton.active = show;
        if (!show) {
            return;
        }

        if (resizeList && dashboardList != null) {
            dashboardList.height = Math.max(SQUARE_SIZE * 2, dashboardList.height - JME_ROW_HEIGHT);
        }
        final boolean stopSelected = ((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex();
        final int rowY = buttonDoneEditingRoute.getY2() - 2 * JME_ROW_HEIGHT;
        IDrawing.setPositionAndWidth(jme$alternativesButton, 2, rowY, JME_PANEL_WIDTH - 4);

        String label = "Alternative platforms";
        if (stopSelected) {
            final Platform platform = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex).platform;
            final int count = platform == null ? 0 : AlternativePlatformRegistry.getAlternatives(editingRoute.getId(), platform.getId()).size();
            label += count > 0 ? " (" + count + ")" : "...";
        } else {
            label += "...";
        }
        jme$alternativesButton.setMessage2(Text.cast(TextHelper.literal(label)));
    }

    @Unique
    private boolean jme$isEditingRoute() {
        return AlternativePlatformRegistry.isEnabled() && editingRoute != null && !editingRoute.getRoutePlatforms().isEmpty();
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
