package org.justnoone.jme.mixin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.client.ui.OverlayClickHandler;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.core.data.Route;
import org.mtr.core.data.RouteType;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.ButtonWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.EditRouteScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditRouteScreen.class, remap = false)
public abstract class EditRouteScreenRouteTypesMixin implements IGui, OverlayClickHandler {

    private static final String[] JME_TRAIN_ROUTE_TYPE_ORDER = new String[]{
            RouteTypeOverrideConfig.TRAIN_NORMAL,
            RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL,
            RouteTypeOverrideConfig.TRAIN_HIGH_SPEED,
            RouteTypeOverrideConfig.TRAIN_METRO,
            RouteTypeOverrideConfig.TRAIN_BUS,
            RouteTypeOverrideConfig.TRAIN_TRAM,
            RouteTypeOverrideConfig.TRAIN_SBAHN
    };

    private static final String[] JME_TRAIN_ROUTE_TYPE_PREFIXES = new String[]{
            "[R] ",
            "[L] ",
            "[H] ",
            "[M] ",
            "[B] ",
            "[T] ",
            "[S] "
    };

    @Shadow
    @Final
    private ButtonWidgetExtension buttonRouteType;

    @Shadow
    private void setRouteType(Route route, RouteType newRouteType) {
    }

    @Unique
    private boolean jme$routeTypeDropdownOpen;
    @Unique
    private Route jme$routeTypeDropdownRoute;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$resetRouteTypeDropdown(CallbackInfo ci) {
        jme$routeTypeDropdownRoute = null;
        jme$routeTypeDropdownOpen = false;
    }

    @Inject(method = "lambda$new$0", at = @At("HEAD"), remap = false, cancellable = true)
    private void jme$cycleExtendedRouteTypes(Route route, ButtonWidget buttonWidget, CallbackInfo ci) {
        if (route == null || route.getTransportMode() != TransportMode.TRAIN) {
            return;
        }

        // Replace the cycle button with a dropdown.
        jme$routeTypeDropdownRoute = route;
        jme$routeTypeDropdownOpen = !jme$routeTypeDropdownOpen;
        ci.cancel();
        return;
    }

    @Unique
    private boolean jme$isInButtonBounds(ButtonWidgetExtension button, double mouseX, double mouseY) {
        if (button == null) {
            return false;
        }
        final int x1 = button.getX2();
        final int y1 = button.getY2();
        final int x2 = x1 + button.getWidth2();
        final int y2 = y1 + button.getHeight2();
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Unique
    private int[] jme$getDropdownBounds() {
        if (buttonRouteType == null) {
            return null;
        }

        final int x = buttonRouteType.getX2();
        final int width = buttonRouteType.getWidth2();
        final int startY = buttonRouteType.getY2() + SQUARE_SIZE + 2;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int count = JME_TRAIN_ROUTE_TYPE_ORDER.length;
        final int height = count * (rowHeight + gap) - gap;
        final int padding = 4;
        return new int[]{
                x, startY, width, height, padding
        };
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderRouteTypeDropdown(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!jme$routeTypeDropdownOpen || jme$routeTypeDropdownRoute == null) {
            return;
        }

        final int[] bounds = jme$getDropdownBounds();
        if (bounds == null) {
            return;
        }

        final int x = bounds[0];
        final int startY = bounds[1];
        final int width = bounds[2];
        final int height = bounds[3];
        final int padding = bounds[4];
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;

        // Current selection (for highlighting).
        final String routeId = Utilities.numberToPaddedHexString(jme$routeTypeDropdownRoute.getId());
        final String selectedKey = jme$getTrainRouteTypeKeyForUi(routeId, jme$routeTypeDropdownRoute.getRouteType());

        final int panelX1 = x - padding;
        final int panelY1 = startY - padding;
        final int panelX2 = x + width + padding;
        final int panelY2 = startY + height + padding;

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 600);

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        // Shadow + panel.
        guiDrawing.drawRectangle(panelX1 + 1, panelY1 + 1, panelX2 + 1, panelY2 + 1, 0x66000000);
        guiDrawing.drawRectangle(panelX1, panelY1, panelX2, panelY2, 0xCC101014);

        // Border.
        guiDrawing.drawRectangle(panelX1, panelY1, panelX2, panelY1 + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX1, panelY2 - 1, panelX2, panelY2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX1, panelY1, panelX1 + 1, panelY2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX2 - 1, panelY1, panelX2, panelY2, 0xFF2B2B2B);

        // Rows.
        for (int i = 0; i < JME_TRAIN_ROUTE_TYPE_ORDER.length; i++) {
            final int rowY = startY + i * (rowHeight + gap);
            final boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + rowHeight;
            final boolean selected = selectedKey != null && selectedKey.equals(JME_TRAIN_ROUTE_TYPE_ORDER[i]);

            if (selected) {
                guiDrawing.drawRectangle(x, rowY, x + width, rowY + rowHeight, hovered ? 0x55FF5A57 : 0x3BFF5A57);
            } else if (hovered) {
                guiDrawing.drawRectangle(x, rowY, x + width, rowY + rowHeight, 0x33FFFFFF);
            }
        }

        guiDrawing.finishDrawingRectangle();

        for (int i = 0; i < JME_TRAIN_ROUTE_TYPE_ORDER.length; i++) {
            final String key = JME_TRAIN_ROUTE_TYPE_ORDER[i];
            final String prefix = i < JME_TRAIN_ROUTE_TYPE_PREFIXES.length ? JME_TRAIN_ROUTE_TYPE_PREFIXES[i] : "";
            final String translated = new Text(jme$getTrainRouteTypeText(key).data).getString();
            final String label = prefix + translated;
            final int rowY = startY + i * (rowHeight + gap);
            final int textX = x + 6;
            final int textY = rowY + (rowHeight - TEXT_HEIGHT) / 2;
            graphicsHolder.drawText(TextHelper.literal(label), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        }

        graphicsHolder.pop();
    }

    @Unique
    @Override
    public boolean jme$handleOverlayClick(double mouseX, double mouseY, int button) {
        if (!jme$routeTypeDropdownOpen || jme$routeTypeDropdownRoute == null) {
            return false;
        }

        // Let the original route type button keep its behavior (toggle open/close) while the dropdown is open.
        if (jme$isInButtonBounds(buttonRouteType, mouseX, mouseY)) {
            return false;
        }

        final int[] bounds = jme$getDropdownBounds();
        if (bounds == null) {
            jme$routeTypeDropdownOpen = false;
            return true;
        }

        final int x = bounds[0];
        final int startY = bounds[1];
        final int width = bounds[2];
        final int height = bounds[3];
        final int padding = bounds[4];
        final int panelX1 = x - padding;
        final int panelY1 = startY - padding;
        final int panelX2 = x + width + padding;
        final int panelY2 = startY + height + padding;

        // Clicking outside closes the dropdown and consumes the click to prevent accidental toggles on underlying widgets.
        if (mouseX < panelX1 || mouseX > panelX2 || mouseY < panelY1 || mouseY > panelY2) {
            jme$routeTypeDropdownOpen = false;
            return true;
        }

        // Clicking inside selects an item (if a row was clicked).
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final double relativeY = mouseY - startY;
        final int stride = rowHeight + gap;
        final int index = (int) Math.floor(relativeY / stride);
        final int inside = (int) Math.floor(relativeY - index * (double) stride);
        if (index >= 0 && index < JME_TRAIN_ROUTE_TYPE_ORDER.length && inside >= 0 && inside < rowHeight) {
            jme$applyRouteTypeKey(jme$routeTypeDropdownRoute, JME_TRAIN_ROUTE_TYPE_ORDER[index]);
        }

        jme$routeTypeDropdownOpen = false;
        return true;
    }

    @Unique
    private void jme$applyRouteTypeKey(Route route, String routeTypeKey) {
        if (route == null) {
            return;
        }

        final String routeId = Utilities.numberToPaddedHexString(route.getId());
        final String nextRouteType = routeTypeKey == null ? RouteTypeOverrideConfig.TRAIN_NORMAL : routeTypeKey;

        final RouteType nextEnumRouteType = jme$tryGetEnumRouteType(nextRouteType);
        if (nextEnumRouteType != null) {
            if (jme$isBaseTrainKey(nextRouteType)) {
                // Clear any legacy override so the UI does not get "stuck" on an old type (eg S-Bahn).
                RouteTypeOverrideConfig.setRouteType(routeId, "");
                final PacketByteBuf packet = PacketByteBufs.create();
                packet.writeString(routeId);
                packet.writeString("");
                MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ROUTE_TYPE_OVERRIDE_PACKET_ID, packet);
            }
            setRouteType(route, nextEnumRouteType);
            return;
        }

        // Legacy fallback: store the extended route type in a sidecar config and keep the base enum for compatibility.
        RouteTypeOverrideConfig.setRouteType(routeId, nextRouteType);
        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeString(routeId);
        packet.writeString(nextRouteType);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ROUTE_TYPE_OVERRIDE_PACKET_ID, packet);
        setRouteType(route, jme$getBaseRouteType(nextRouteType));
    }

    @Inject(method = "setRouteType", at = @At("TAIL"), remap = false)
    private void jme$overrideExtendedRouteTypeLabel(Route route, RouteType newRouteType, CallbackInfo ci) {
        if (route == null || route.getTransportMode() == null) {
            return;
        }

        switch (route.getTransportMode()) {
            case TRAIN:
                final String routeId = Utilities.numberToPaddedHexString(route.getId());
                buttonRouteType.setMessage2(jme$getTrainRouteTypeText(jme$getTrainRouteTypeKeyForUi(routeId, route.getRouteType())));
                break;
            case BOAT:
                buttonRouteType.setMessage2(jme$getBoatRouteTypeText(route.getRouteType()));
                break;
            default:
                break;
        }
    }

    private static String jme$getTrainRouteTypeKeyForUi(String routeId, RouteType routeType) {
        final String baseKey = jme$routeTypeEnumToKey(routeType);
        if (!jme$isBaseTrainKey(baseKey)) {
            return baseKey;
        }

        // Only respect sidecar overrides when the route is still using a base enum type.
        final String override = RouteTypeOverrideConfig.getRouteType(routeId);
        return override.isEmpty() ? baseKey : override;
    }

    private static String jme$routeTypeEnumToKey(RouteType routeType) {
        if (routeType == null) {
            return RouteTypeOverrideConfig.TRAIN_NORMAL;
        }

        // Use name() to keep this compatible with older RouteType enums (some versions don't have METRO/BUS/TRAM/SBAHN).
        switch (routeType.name()) {
            case "LIGHT_RAIL":
                return RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL;
            case "HIGH_SPEED":
                return RouteTypeOverrideConfig.TRAIN_HIGH_SPEED;
            case "METRO":
                return RouteTypeOverrideConfig.TRAIN_METRO;
            case "BUS":
                return RouteTypeOverrideConfig.TRAIN_BUS;
            case "TRAM":
                return RouteTypeOverrideConfig.TRAIN_TRAM;
            case "SBAHN":
                return RouteTypeOverrideConfig.TRAIN_SBAHN;
            default:
                return RouteTypeOverrideConfig.TRAIN_NORMAL;
        }
    }

    private static RouteType jme$getBaseRouteType(String trainRouteTypeKey) {
        if (RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL.equals(trainRouteTypeKey)) {
            return RouteType.LIGHT_RAIL;
        } else if (RouteTypeOverrideConfig.TRAIN_HIGH_SPEED.equals(trainRouteTypeKey)) {
            return RouteType.HIGH_SPEED;
        } else {
            return RouteType.NORMAL;
        }
    }

    private static boolean jme$isBaseTrainKey(String trainRouteTypeKey) {
        return trainRouteTypeKey.isEmpty()
                || RouteTypeOverrideConfig.TRAIN_NORMAL.equals(trainRouteTypeKey)
                || RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL.equals(trainRouteTypeKey)
                || RouteTypeOverrideConfig.TRAIN_HIGH_SPEED.equals(trainRouteTypeKey);
    }

    private static String jme$getNextTrainRouteType(String currentRouteTypeKey) {
        for (int i = 0; i < JME_TRAIN_ROUTE_TYPE_ORDER.length; i++) {
            if (JME_TRAIN_ROUTE_TYPE_ORDER[i].equals(currentRouteTypeKey)) {
                return JME_TRAIN_ROUTE_TYPE_ORDER[(i + 1) % JME_TRAIN_ROUTE_TYPE_ORDER.length];
            }
        }
        return RouteTypeOverrideConfig.TRAIN_NORMAL;
    }

    private static RouteType jme$tryGetEnumRouteType(String trainRouteTypeKey) {
        final String enumName;
        if (RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL.equals(trainRouteTypeKey)) {
            enumName = "LIGHT_RAIL";
        } else if (RouteTypeOverrideConfig.TRAIN_HIGH_SPEED.equals(trainRouteTypeKey)) {
            enumName = "HIGH_SPEED";
        } else if (RouteTypeOverrideConfig.TRAIN_METRO.equals(trainRouteTypeKey)) {
            enumName = "METRO";
        } else if (RouteTypeOverrideConfig.TRAIN_BUS.equals(trainRouteTypeKey)) {
            enumName = "BUS";
        } else if (RouteTypeOverrideConfig.TRAIN_TRAM.equals(trainRouteTypeKey)) {
            enumName = "TRAM";
        } else if (RouteTypeOverrideConfig.TRAIN_SBAHN.equals(trainRouteTypeKey)) {
            enumName = "SBAHN";
        } else if (RouteTypeOverrideConfig.TRAIN_NORMAL.equals(trainRouteTypeKey)) {
            enumName = "NORMAL";
        } else {
            return null;
        }

        try {
            return Enum.valueOf(RouteType.class, enumName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Text jme$getTrainRouteTypeText(String routeTypeKey) {
        if (RouteTypeOverrideConfig.TRAIN_METRO.equals(routeTypeKey)) {
            return Text.cast(TextHelper.translatable("gui.mtr.route_type_train_metro"));
        } else if (RouteTypeOverrideConfig.TRAIN_BUS.equals(routeTypeKey)) {
            return Text.cast(TextHelper.translatable("gui.mtr.route_type_train_bus"));
        } else if (RouteTypeOverrideConfig.TRAIN_TRAM.equals(routeTypeKey)) {
            return Text.cast(TextHelper.translatable("gui.mtr.route_type_train_tram"));
        } else if (RouteTypeOverrideConfig.TRAIN_SBAHN.equals(routeTypeKey)) {
            return Text.cast(TextHelper.translatable("gui.mtr.route_type_train_sbahn"));
        } else if (RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL.equals(routeTypeKey)) {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_TRAIN_LIGHT_RAIL.getText();
        } else if (RouteTypeOverrideConfig.TRAIN_HIGH_SPEED.equals(routeTypeKey)) {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_TRAIN_HIGH_SPEED.getText();
        } else {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_TRAIN_NORMAL.getText();
        }
    }

    private static Text jme$getBoatRouteTypeText(RouteType routeType) {
        if (routeType == RouteType.LIGHT_RAIL) {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_BOAT_LIGHT_RAIL.getText();
        } else if (routeType == RouteType.HIGH_SPEED) {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_BOAT_HIGH_SPEED.getText();
        } else {
            return TranslationProvider.GUI_MTR_ROUTE_TYPE_BOAT_NORMAL.getText();
        }
    }
}
