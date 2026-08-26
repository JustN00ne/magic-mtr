package org.justnoone.jme.mixin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.client.DashboardRouteFolderStore;
import org.justnoone.jme.client.DashboardRouteRenderState;
import org.justnoone.jme.client.screen.AlternativePlatformSelectorScreen;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.MagicRailConstants;
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
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenAlternativePlatformMixin implements IGui {

    @Shadow
    private Route editingRoute;

    @Shadow
    private int editingRoutePlatformIndex;

    @Shadow
    @Final
    private ButtonWidgetExtension buttonDoneEditingRouteDestination;

    @Unique
    private ButtonWidgetExtension jme$buttonAlternativePlatform;
    @Unique
    private ButtonWidgetExtension jme$buttonAlternativeSelector;
    @Unique
    private boolean jme$alternativeSelectionMode;
    @Unique
    private boolean jme$alternativeButtonAdded;
    @Unique
    private boolean jme$alternativeSelectorButtonAdded;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createAlternativeButton(org.mtr.core.data.TransportMode transportMode, CallbackInfo ci) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        jme$buttonAlternativePlatform = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("⎇"), button -> {
            if (editingRoute != null && !editingRoute.getRoutePlatforms().isEmpty()) {
                if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
                    jme$alternativeSelectionMode = false;
                    jme$updateAlternativeButtonLabel();
                    return;
                }
                jme$alternativeSelectionMode = !jme$alternativeSelectionMode;
                jme$updateAlternativeButtonLabel();
            }
        });

        jme$buttonAlternativeSelector = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("≣"), button -> jme$openAlternativeSelector());
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initAlternativeButton(CallbackInfo ci) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        if (jme$buttonAlternativePlatform == null || jme$buttonAlternativeSelector == null) {
            return;
        }

        final int anchorX = buttonDoneEditingRouteDestination.getX2() + buttonDoneEditingRouteDestination.getWidth2() - SQUARE_SIZE * 2;
        final int anchorY = buttonDoneEditingRouteDestination.getY2() - SQUARE_SIZE - 2;
        IDrawing.setPositionAndWidth(jme$buttonAlternativeSelector, anchorX, anchorY, SQUARE_SIZE);
        IDrawing.setPositionAndWidth(jme$buttonAlternativePlatform, anchorX + SQUARE_SIZE, anchorY, SQUARE_SIZE);
        if (!jme$alternativeButtonAdded) {
            jme$addChild(new ClickableWidget(jme$buttonAlternativePlatform));
            jme$alternativeButtonAdded = true;
        }
        if (!jme$alternativeSelectorButtonAdded) {
            jme$addChild(new ClickableWidget(jme$buttonAlternativeSelector));
            jme$alternativeSelectorButtonAdded = true;
        }
        jme$updateAlternativeButtonLabel();
    }

    @Inject(method = "toggleButtons", at = @At("TAIL"), remap = false)
    private void jme$toggleAlternativeButton(CallbackInfo ci) {
        if (jme$buttonAlternativePlatform == null || jme$buttonAlternativeSelector == null) {
            return;
        }

        jme$alternativeSelectionMode = false;
        jme$buttonAlternativePlatform.visible = false;
        jme$buttonAlternativePlatform.active = false;
        jme$buttonAlternativeSelector.visible = false;
        jme$buttonAlternativeSelector.active = false;
        jme$updateAlternativeButtonLabel();
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
        jme$updateAlternativeButtonLabel();
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
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        if (!jme$alternativeSelectionMode || editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return;
        }
        if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            jme$alternativeSelectionMode = false;
            jme$updateAlternativeButtonLabel();
            return;
        }

        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
        if (routePlatformData.platform == null || routePlatformData.platform.getId() == platformId) {
            ci.cancel();
            return;
        }

        final long routeId = editingRoute.getId();
        final long primaryPlatformId = routePlatformData.platform.getId();
        final boolean changed = AlternativePlatformRegistry.toggleAlternative(routeId, primaryPlatformId, platformId);
        if (changed) {
            final List<Long> alternatives = AlternativePlatformRegistry.getAlternatives(routeId, primaryPlatformId);
            final boolean enabled = alternatives.contains(platformId);
            final PacketByteBuf packet = PacketByteBufs.create();
            packet.writeLong(routeId);
            packet.writeLong(primaryPlatformId);
            packet.writeLong(platformId);
            packet.writeBoolean(enabled);
            MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, packet);
        }

        ci.cancel();
    }

    @Unique
    private void jme$openAlternativeSelector() {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        if (editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return;
        }
        if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            return;
        }

        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
        final Platform primaryPlatform = routePlatformData.platform;
        if (primaryPlatform == null || primaryPlatform.area == null) {
            return;
        }

        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(AlternativePlatformSelectorScreen.create((ScreenExtension) (Object) this, editingRoute, primaryPlatform)));
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
    private boolean jme$hasStationAlternatives() {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return false;
        }

        if (editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return false;
        }
        if (!((DashboardScreenAccessor) this).jme$isValidRoutePlatformIndex()) {
            return false;
        }

        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
        final Platform primaryPlatform = routePlatformData.platform;
        if (primaryPlatform == null || primaryPlatform.area == null) {
            return false;
        }

        final long primaryPlatformId = primaryPlatform.getId();
        return primaryPlatform.area.savedRails.stream().anyMatch(savedRail -> savedRail instanceof Platform && ((Platform) savedRail).getId() != primaryPlatformId);
    }

    @Unique
    private void jme$updateAlternativeButtonLabel() {
        if (jme$buttonAlternativePlatform == null) {
            return;
        }
        final Text text = Text.cast(TextHelper.literal(jme$alternativeSelectionMode ? "⎇*" : "⎇"));
        jme$buttonAlternativePlatform.setMessage2(text);
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
