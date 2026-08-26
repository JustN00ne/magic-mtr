package org.justnoone.jme.mixin;

import org.justnoone.jme.client.DashboardRailExporter;
import org.justnoone.jme.client.DashboardRouteFolderStore;
import org.justnoone.jme.client.screen.AlternativePlatformSelectorScreen;
import org.justnoone.jme.client.screen.RouteFolderEditScreen;
import org.justnoone.jme.client.ui.OverlayClickHandler;
import org.justnoone.jme.client.ui.OverlayMenuState;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.Main;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenMagicMenuMixin extends ScreenExtension implements IGui, OverlayClickHandler, OverlayMenuState {

    @Unique
    private static final Identifier JME_MAGIC_ICON_TEXTURE = new Identifier("jme", "textures/item/magic_icon.png");

    // Hidden for the main release UI.
    @Unique
    private static final boolean JME_ENABLE_MAGIC_DASHBOARD_BUTTON = true;

    // When enabled, replaces the stock per-row dock buttons with a 3-dot overflow menu.
    // The user requested restoring the dock buttons.
    @Unique
    private static final boolean JME_ENABLE_DASHBOARD_ROW_OVERFLOW_MENU = false;

    @Unique
    private ButtonWidgetExtension jme$magicMenuButton;
    @Unique
    private boolean jme$magicMenuButtonAdded;
    @Unique
    private boolean jme$magicMenuOpen;

    @Shadow
    private Route editingRoute;

    @Shadow
    private AreaBase<?, ?> editingArea;

    @Shadow
    private int editingRoutePlatformIndex;

    @Shadow
    @Final
    private DashboardList dashboardList;

    @Unique
    private boolean jme$routeOverflowOpen;
    @Unique
    private int jme$routeOverflowVisibleIndex = -1;
    @Unique
    private int jme$routeOverflowButtonX;
    @Unique
    private int jme$routeOverflowButtonY;

    @Unique
    private boolean jme$listOverflowOpen;
    @Unique
    private int jme$listOverflowIndex = -1;
    @Unique
    private int jme$listOverflowButtonX;
    @Unique
    private int jme$listOverflowButtonY;

    protected DashboardScreenMagicMenuMixin() {
        super();
    }

    @Unique
    @Override
    public boolean jme$isOverlayMenuOpen() {
        final boolean overflowOpen = JME_ENABLE_DASHBOARD_ROW_OVERFLOW_MENU && (jme$routeOverflowOpen || jme$listOverflowOpen);
        return overflowOpen || (JME_ENABLE_MAGIC_DASHBOARD_BUTTON && jme$magicMenuOpen);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createMagicMenuButton(org.mtr.core.data.TransportMode transportMode, CallbackInfo ci) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            jme$magicMenuButton = null;
            jme$magicMenuButtonAdded = false;
            jme$magicMenuOpen = false;
            return;
        }

        jme$magicMenuButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), button -> jme$magicMenuOpen = !jme$magicMenuOpen);
        jme$magicMenuButtonAdded = false;
        jme$magicMenuOpen = false;
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initMagicMenuButton(CallbackInfo ci) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return;
        }

        if (jme$magicMenuButton == null) {
            return;
        }

        final WidgetMap widgetMap = ((DashboardScreenAccessor) this).jme$getWidgetMap();
        if (widgetMap == null) {
            return;
        }

        final int margin = 6;
        // Top-left of the map.
        final int x = widgetMap.getX2() + margin;
        final int y = widgetMap.getY2() + margin;
        IDrawing.setPositionAndWidth(jme$magicMenuButton, x, y, SQUARE_SIZE);

        if (!jme$magicMenuButtonAdded) {
            jme$addChild(new ClickableWidget(jme$magicMenuButton));
            jme$magicMenuButtonAdded = true;
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderMagicMenu(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (JME_ENABLE_DASHBOARD_ROW_OVERFLOW_MENU) {
            jme$renderOverflow(graphicsHolder, mouseX, mouseY);
        } else {
            jme$routeOverflowOpen = false;
            jme$listOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            jme$listOverflowIndex = -1;
        }

        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON || jme$magicMenuButton == null) {
            return;
        }

        jme$drawMagicLogo(graphicsHolder);

        if (!jme$magicMenuOpen) {
            return;
        }

        final int panelWidth = 210;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int rows = 4;
        final int panelHeight = rows * (rowHeight + gap) - gap;
        final int[] magicLayout = jme$getMagicMenuLayout(panelWidth, panelHeight, padding);
        final int panelX = magicLayout[0];
        final int panelY = magicLayout[1];

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 700);

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        // Shadow + panel.
        guiDrawing.drawRectangle(panelX + 1, panelY + 1, panelX + panelWidth + 1, panelY + panelHeight + padding * 2 + 1, 0x66000000);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xCC101014);

        // Border.
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight + padding * 2 - 1, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight + padding * 2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xFF2B2B2B);

        for (int i = 0; i < rows; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final boolean hovered = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                guiDrawing.drawRectangle(panelX + 2, rowY, panelX + panelWidth - 2, rowY + rowHeight, 0x33FFFFFF);
            }
        }

        guiDrawing.finishDrawingRectangle();

        final String[] labels = new String[]{
                "Export PNG (viewport)",
                "Export PNG (all rails)",
                "Export SVG (viewport)",
                "Export SVG (all rails)",
        };

        for (int i = 0; i < labels.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final int textX = panelX + 10;
            final int textY = rowY + (rowHeight - TEXT_HEIGHT) / 2;
            graphicsHolder.drawText(TextHelper.literal(labels[i]), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        }

        graphicsHolder.pop();
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false, order = 1100)
    private void jme$overrideDashboardListRowButtons(CallbackInfo ci) {
        if (!JME_ENABLE_DASHBOARD_ROW_OVERFLOW_MENU) {
            return;
        }
        if (dashboardList == null) {
            return;
        }

        if (!((Object) dashboardList instanceof DashboardListAccessor)) {
            return;
        }

        // Replace the per-row hover buttons with our 3-dot overflow menu (all dashboard lists).
        final DashboardListAccessor accessor = (DashboardListAccessor) (Object) dashboardList;
        accessor.jme$setHasFind(false);
        accessor.jme$setHasDrawArea(false);
        accessor.jme$setHasEdit(false);
        accessor.jme$setHasSort(false);
        accessor.jme$setHasAdd(false);
        accessor.jme$setHasDelete(false);
    }

    @Unique
    private void jme$drawMagicLogo(GraphicsHolder graphicsHolder) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return;
        }

        if (jme$magicMenuButton == null) {
            return;
        }

        final int iconSize = 14;
        final int iconX1 = jme$magicMenuButton.getX2() + (SQUARE_SIZE - iconSize) / 2;
        final int iconY1 = jme$magicMenuButton.getY2() + (SQUARE_SIZE - iconSize) / 2;
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
    @Override
    public boolean jme$handleOverlayClick(double mouseX, double mouseY, int button) {
        if (JME_ENABLE_DASHBOARD_ROW_OVERFLOW_MENU) {
            if (jme$handleTopLevelOverflowClick(mouseX, mouseY, button)) {
                return true;
            }
            if (jme$handleRouteOverflowClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON || jme$magicMenuButton == null) {
            return false;
        }

        // Toggle the menu button ourselves so the underlying WidgetMap can't steal the click.
        if (jme$isInMagicMenuButtonBounds(mouseX, mouseY)) {
            jme$magicMenuOpen = !jme$magicMenuOpen;
            return true;
        }

        if (!jme$magicMenuOpen) {
            return false;
        }

        final int panelWidth = 210;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int rows = 4;
        final int panelHeight = rows * (rowHeight + gap) - gap;
        final int[] magicLayout = jme$getMagicMenuLayout(panelWidth, panelHeight, padding);
        final int panelX = magicLayout[0];
        final int panelY = magicLayout[1];
        final int panelX1 = panelX;
        final int panelY1 = panelY;
        final int panelX2 = panelX + panelWidth;
        final int panelY2 = panelY + panelHeight + padding * 2;

        // Clicking outside closes and consumes to avoid accidental map interactions.
        if (mouseX < panelX1 || mouseX > panelX2 || mouseY < panelY1 || mouseY > panelY2) {
            jme$magicMenuOpen = false;
            return true;
        }

        final double relativeY = mouseY - (panelY + padding);
        final int stride = rowHeight + gap;
        final int index = (int) Math.floor(relativeY / stride);
        final int inside = (int) Math.floor(relativeY - index * (double) stride);
        if (index < 0 || index >= rows || inside < 0 || inside >= rowHeight) {
            // Clicked the panel padding or a gap; consume.
            return true;
        }

        final WidgetMap widgetMap = ((DashboardScreenAccessor) this).jme$getWidgetMap();
        if (widgetMap != null) {
            try {
                final Path exported;
                if (index == 0) {
                    exported = DashboardRailExporter.exportRailsPngViewport(widgetMap);
                } else if (index == 1) {
                    exported = DashboardRailExporter.exportRailsPngFull();
                } else if (index == 2) {
                    exported = DashboardRailExporter.exportRailsSvgViewport(widgetMap);
                } else {
                    exported = DashboardRailExporter.exportRailsSvgFull();
                }
                if (exported != null) {
                    Main.LOGGER.info("[MAGIC] Exported rails to {}", exported);
                    jme$sendClientMessage("MAGIC: Exported rails to " + exported);
                } else {
                    jme$sendClientMessage("MAGIC: Export failed (map/rails not ready)");
                }
            } catch (Throwable throwable) {
                Main.LOGGER.warn("[MAGIC] Failed exporting rails", throwable);
                jme$sendClientMessage("MAGIC: Export failed (" + throwable.getClass().getSimpleName() + ")");
            }
        }

        jme$magicMenuOpen = false;
        return true;
    }

    @Unique
    private int[] jme$getMagicMenuLayout(int panelWidth, int panelHeight, int padding) {
        // Prefer opening upwards (we place the button near the bottom), but clamp to the screen.
        int x = jme$magicMenuButton.getX2();
        int y = jme$magicMenuButton.getY2() - (panelHeight + padding * 2) - 4;
        if (y < 4) {
            y = jme$magicMenuButton.getY2() + SQUARE_SIZE + 4;
        }

        if (y + panelHeight + padding * 2 > height - 4) {
            y = Math.max(4, height - 4 - (panelHeight + padding * 2));
        }

        if (x + panelWidth > width - 4) {
            x = Math.max(4, width - 4 - panelWidth);
        }
        x = Math.max(4, x);

        return new int[]{x, y};
    }

    @Unique
    private void jme$renderOverflow(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (editingRoute == null) {
            jme$renderTopLevelOverflow(graphicsHolder, mouseX, mouseY);
            return;
        }
        jme$renderRouteOverflow(graphicsHolder, mouseX, mouseY);
    }

    @Unique
    private void jme$renderTopLevelOverflow(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (dashboardList == null) {
            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return;
        }

        // While a menu is open, keep the anchor button drawn even if the mouse moved away.
        if (!jme$listOverflowOpen) {
            final int[] hoverInfo = jme$getDashboardListHoverInfo(mouseX, mouseY);
            if (hoverInfo == null) {
                return;
            }

            final int hoverRow = hoverInfo[0];
            dashboardList.mouseMoved(mouseX, mouseY);
            final int visibleIndex = dashboardList.getHoverItemIndex();
            if (visibleIndex < 0) {
                return;
            }

            final DashboardListItem listItem = jme$getDashboardListItem(visibleIndex);
            if (listItem == null || listItem.data == null) {
                return;
            }

            jme$drawOverflowButton(graphicsHolder, dashboardList.x + dashboardList.width - SQUARE_SIZE, dashboardList.y + hoverRow * SQUARE_SIZE + 24, mouseX, mouseY, false);
            return;
        }

        jme$drawOverflowButton(graphicsHolder, jme$listOverflowButtonX, jme$listOverflowButtonY, mouseX, mouseY, true);
        jme$renderTopLevelOverflowMenu(graphicsHolder, mouseX, mouseY);
    }

    @Unique
    private void jme$renderRouteOverflow(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (editingRoute == null || dashboardList == null) {
            jme$routeOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            return;
        }

        // While a menu is open, keep the anchor button drawn even if the mouse moved away.
        if (!jme$routeOverflowOpen) {
            final int[] hoverInfo = jme$getDashboardListHoverInfo(mouseX, mouseY);
            if (hoverInfo == null) {
                return;
            }

            final int hoverRow = hoverInfo[0];
            dashboardList.mouseMoved(mouseX, mouseY);
            final int visibleIndex = dashboardList.getHoverItemIndex();
            if (visibleIndex < 0) {
                return;
            }

            jme$drawOverflowButton(graphicsHolder, dashboardList.x + dashboardList.width - SQUARE_SIZE, dashboardList.y + hoverRow * SQUARE_SIZE + 24, mouseX, mouseY, false);
            return;
        }

        jme$drawOverflowButton(graphicsHolder, jme$routeOverflowButtonX, jme$routeOverflowButtonY, mouseX, mouseY, true);
        jme$renderRouteOverflowMenu(graphicsHolder, mouseX, mouseY);
    }

    @Unique
    private void jme$drawOverflowButton(GraphicsHolder graphicsHolder, int x, int y, int mouseX, int mouseY, boolean active) {
        final boolean hovered = mouseX >= x && mouseX < x + SQUARE_SIZE && mouseY >= y && mouseY < y + SQUARE_SIZE;
        // Opaque background so list row separators don't "bleed through" as outlines.
        final int bg = active ? 0xFF1B1B1F : (hovered ? 0xFF232328 : 0xFF1A1A1E);

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 650);
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        // Flat fill: avoid the top/bottom outline strips that look like a border artifact.
        guiDrawing.drawRectangle(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE, bg);
        guiDrawing.finishDrawingRectangle();

        final String label = "...";
        final int textWidth = GraphicsHolder.getTextWidth(label);
        final int textX = x + (SQUARE_SIZE - textWidth) / 2;
        // Put the dots near the top edge so it reads as a menu affordance, not inline with row text.
        final int textY = y + 2;
        graphicsHolder.drawText(TextHelper.literal(label), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        graphicsHolder.pop();
    }

    @Unique
    private void jme$renderRouteOverflowMenu(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (!jme$routeOverflowOpen || editingRoute == null) {
            return;
        }

        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, jme$routeOverflowVisibleIndex);
        if (rowMetadata == null) {
            jme$routeOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            return;
        }

        final int[] actions = jme$getRouteOverflowActions(rowMetadata);
        if (actions.length == 0) {
            jme$routeOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            return;
        }

        final int[] layout = jme$getRouteOverflowMenuLayout(actions.length);
        final int panelX = layout[0];
        final int panelY = layout[1];
        final int panelWidth = layout[2];
        final int panelHeight = layout[3];
        final int padding = layout[4];
        final int rowHeight = layout[5];
        final int gap = layout[6];

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 700);

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        // Shadow + panel.
        guiDrawing.drawRectangle(panelX + 1, panelY + 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0x66000000);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC101014);

        // Border.
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);

        for (int i = 0; i < actions.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final boolean hovered = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                guiDrawing.drawRectangle(panelX + 2, rowY, panelX + panelWidth - 2, rowY + rowHeight, 0x33FFFFFF);
            }
        }

        guiDrawing.finishDrawingRectangle();

        for (int i = 0; i < actions.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final int textX = panelX + 10;
            final int textY = rowY + (rowHeight - TEXT_HEIGHT) / 2;
            graphicsHolder.drawText(TextHelper.literal(jme$getRouteOverflowActionLabel(actions[i], rowMetadata)), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        }

        graphicsHolder.pop();
    }

    @Unique
    private void jme$renderTopLevelOverflowMenu(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (!jme$listOverflowOpen || dashboardList == null) {
            return;
        }

        final DashboardListItem listItem = jme$getDashboardListItem(jme$listOverflowIndex);
        if (listItem == null || listItem.data == null) {
            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return;
        }

        final int[] actions = jme$getTopLevelOverflowActions(listItem);
        if (actions.length == 0) {
            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return;
        }

        final int[] layout = jme$getTopLevelOverflowMenuLayout(actions.length);
        final int panelX = layout[0];
        final int panelY = layout[1];
        final int panelWidth = layout[2];
        final int panelHeight = layout[3];
        final int padding = layout[4];
        final int rowHeight = layout[5];
        final int gap = layout[6];

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 700);

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        // Shadow + panel.
        guiDrawing.drawRectangle(panelX + 1, panelY + 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0x66000000);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC101014);

        // Border.
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);

        for (int i = 0; i < actions.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final boolean hovered = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                guiDrawing.drawRectangle(panelX + 2, rowY, panelX + panelWidth - 2, rowY + rowHeight, 0x33FFFFFF);
            }
        }

        guiDrawing.finishDrawingRectangle();

        for (int i = 0; i < actions.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final int textX = panelX + 10;
            final int textY = rowY + (rowHeight - TEXT_HEIGHT) / 2;
            graphicsHolder.drawText(TextHelper.literal(jme$getTopLevelOverflowActionLabel(actions[i], listItem)), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        }

        graphicsHolder.pop();
    }

    @Unique
    private boolean jme$handleTopLevelOverflowClick(double mouseX, double mouseY, int button) {
        if (dashboardList == null) {
            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return false;
        }

        // Route platform list is handled separately.
        if (editingRoute != null) {
            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return false;
        }

        // When open: consume all clicks (either action selection or close).
        if (jme$listOverflowOpen) {
            final DashboardListItem listItem = jme$getDashboardListItem(jme$listOverflowIndex);
            if (listItem == null || listItem.data == null) {
                jme$listOverflowOpen = false;
                jme$listOverflowIndex = -1;
                return true;
            }

            final int[] actions = jme$getTopLevelOverflowActions(listItem);
            if (actions.length == 0) {
                jme$listOverflowOpen = false;
                jme$listOverflowIndex = -1;
                return true;
            }

            final int[] layout = jme$getTopLevelOverflowMenuLayout(actions.length);
            final int panelX = layout[0];
            final int panelY = layout[1];
            final int panelWidth = layout[2];
            final int panelHeight = layout[3];
            final int padding = layout[4];
            final int rowHeight = layout[5];
            final int gap = layout[6];

            // Clicking the anchor toggles closed.
            if (mouseX >= jme$listOverflowButtonX && mouseX < jme$listOverflowButtonX + SQUARE_SIZE && mouseY >= jme$listOverflowButtonY && mouseY < jme$listOverflowButtonY + SQUARE_SIZE) {
                jme$listOverflowOpen = false;
                jme$listOverflowIndex = -1;
                return true;
            }

            // Clicking outside closes and consumes to avoid accidental interactions.
            if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
                jme$listOverflowOpen = false;
                jme$listOverflowIndex = -1;
                return true;
            }

            final double relativeY = mouseY - (panelY + padding);
            final int stride = rowHeight + gap;
            final int index = (int) Math.floor(relativeY / stride);
            final int inside = (int) Math.floor(relativeY - index * (double) stride);
            if (button == 0 && index >= 0 && index < actions.length && inside >= 0 && inside < rowHeight) {
                jme$performTopLevelOverflowAction(actions[index], listItem, jme$listOverflowIndex);
            }

            jme$listOverflowOpen = false;
            jme$listOverflowIndex = -1;
            return true;
        }

        // Only open on left click.
        if (button != 0) {
            return false;
        }

        // When closed: clicking the 3-dot button on the hovered row opens.
        final int[] hoverInfo = jme$getDashboardListHoverInfo((int) mouseX, (int) mouseY);
        if (hoverInfo == null) {
            return false;
        }

        final int hoverRow = hoverInfo[0];
        final int buttonX = dashboardList.x + dashboardList.width - SQUARE_SIZE;
        final int buttonY = dashboardList.y + hoverRow * SQUARE_SIZE + 24;
        if (mouseX < buttonX || mouseX >= buttonX + SQUARE_SIZE || mouseY < buttonY || mouseY >= buttonY + SQUARE_SIZE) {
            return false;
        }

        dashboardList.mouseMoved(mouseX, mouseY);
        final int visibleIndex = dashboardList.getHoverItemIndex();
        if (visibleIndex < 0) {
            return false;
        }

        final DashboardListItem listItem = jme$getDashboardListItem(visibleIndex);
        if (listItem == null || listItem.data == null) {
            return false;
        }

        jme$listOverflowOpen = true;
        jme$listOverflowIndex = visibleIndex;
        jme$listOverflowButtonX = buttonX;
        jme$listOverflowButtonY = buttonY;
        return true;
    }

    @Unique
    private boolean jme$handleRouteOverflowClick(double mouseX, double mouseY, int button) {
        if (editingRoute == null || dashboardList == null) {
            jme$routeOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            return false;
        }

        // When open: consume all clicks (either action selection or close).
        if (jme$routeOverflowOpen) {
            final int[] actions;
            final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, jme$routeOverflowVisibleIndex);
            if (rowMetadata == null) {
                jme$routeOverflowOpen = false;
                jme$routeOverflowVisibleIndex = -1;
                return true;
            }

            actions = jme$getRouteOverflowActions(rowMetadata);
            final int[] layout = jme$getRouteOverflowMenuLayout(actions.length);
            final int panelX = layout[0];
            final int panelY = layout[1];
            final int panelWidth = layout[2];
            final int panelHeight = layout[3];
            final int padding = layout[4];
            final int rowHeight = layout[5];
            final int gap = layout[6];

            // Clicking the anchor toggles closed.
            if (mouseX >= jme$routeOverflowButtonX && mouseX < jme$routeOverflowButtonX + SQUARE_SIZE && mouseY >= jme$routeOverflowButtonY && mouseY < jme$routeOverflowButtonY + SQUARE_SIZE) {
                jme$routeOverflowOpen = false;
                jme$routeOverflowVisibleIndex = -1;
                return true;
            }

            // Clicking outside closes and consumes to avoid accidental list/map interactions.
            if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
                jme$routeOverflowOpen = false;
                jme$routeOverflowVisibleIndex = -1;
                return true;
            }

            final double relativeY = mouseY - (panelY + padding);
            final int stride = rowHeight + gap;
            final int index = (int) Math.floor(relativeY / stride);
            final int inside = (int) Math.floor(relativeY - index * (double) stride);
            if (button == 0 && index >= 0 && index < actions.length && inside >= 0 && inside < rowHeight) {
                jme$performRouteOverflowAction(actions[index], rowMetadata);
            }

            jme$routeOverflowOpen = false;
            jme$routeOverflowVisibleIndex = -1;
            return true;
        }

        // Only open the menu on left click; right click should still be available for the existing context menu.
        if (button != 0) {
            return false;
        }

        // When closed: clicking the 3-dot button on the hovered row opens.
        final int[] hoverInfo = jme$getDashboardListHoverInfo((int) mouseX, (int) mouseY);
        if (hoverInfo == null) {
            return false;
        }

        final int hoverRow = hoverInfo[0];
        final int buttonX = dashboardList.x + dashboardList.width - SQUARE_SIZE;
        final int buttonY = dashboardList.y + hoverRow * SQUARE_SIZE + 24;
        if (mouseX < buttonX || mouseX >= buttonX + SQUARE_SIZE || mouseY < buttonY || mouseY >= buttonY + SQUARE_SIZE) {
            return false;
        }

        dashboardList.mouseMoved(mouseX, mouseY);
        final int visibleIndex = dashboardList.getHoverItemIndex();
        if (visibleIndex < 0) {
            return false;
        }

        jme$routeOverflowOpen = true;
        jme$routeOverflowVisibleIndex = visibleIndex;
        jme$routeOverflowButtonX = buttonX;
        jme$routeOverflowButtonY = buttonY;
        return true;
    }

    @Unique
    private int[] jme$getDashboardListHoverInfo(int mouseX, int mouseY) {
        if (dashboardList == null) {
            return null;
        }

        final int listTop = dashboardList.y + 24;
        final int itemsToShow = Math.max(0, (dashboardList.height - 24) / SQUARE_SIZE);
        final int listBottom = listTop + itemsToShow * SQUARE_SIZE;
        if (mouseX < dashboardList.x || mouseX >= dashboardList.x + dashboardList.width || mouseY < listTop || mouseY >= listBottom) {
            return null;
        }

        final int hoverRow = (mouseY - listTop) / SQUARE_SIZE;
        if (hoverRow < 0 || hoverRow >= itemsToShow) {
            return null;
        }

        return new int[]{hoverRow};
    }

    @Unique
    private static final int JME_TOP_ACTION_FIND = 101;
    @Unique
    private static final int JME_TOP_ACTION_DRAW_AREA = 102;
    @Unique
    private static final int JME_TOP_ACTION_EDIT = 103;
    @Unique
    private static final int JME_TOP_ACTION_DUPLICATE_ROUTE = 104;
    @Unique
    private static final int JME_TOP_ACTION_DELETE = 105;

    @Unique
    private int[] jme$getTopLevelOverflowActions(DashboardListItem listItem) {
        if (listItem == null || listItem.data == null) {
            return new int[0];
        }

        // Platform/siding lists while editing a station/depot area.
        if (editingArea != null) {
            if (listItem.data instanceof Platform) {
                return new int[]{JME_TOP_ACTION_FIND, JME_TOP_ACTION_EDIT};
            }
            return new int[]{JME_TOP_ACTION_EDIT};
        }

        if (listItem.data instanceof Route) {
            // Preserve MTR's "draw area" button behavior (it opens the route platforms list).
            return new int[]{JME_TOP_ACTION_DRAW_AREA, JME_TOP_ACTION_EDIT, JME_TOP_ACTION_DUPLICATE_ROUTE, JME_TOP_ACTION_DELETE};
        }

        if (listItem.data instanceof Station || listItem.data instanceof org.mtr.core.data.Depot) {
            return new int[]{JME_TOP_ACTION_FIND, JME_TOP_ACTION_DRAW_AREA, JME_TOP_ACTION_EDIT, JME_TOP_ACTION_DELETE};
        }

        return new int[]{JME_TOP_ACTION_EDIT, JME_TOP_ACTION_DELETE};
    }

    @Unique
    private static String jme$getTopLevelOverflowActionLabel(int action, DashboardListItem listItem) {
        switch (action) {
            case JME_TOP_ACTION_FIND:
                return "Find";
            case JME_TOP_ACTION_DRAW_AREA:
                if (listItem != null && listItem.data instanceof Route) {
                    return "Edit platforms";
                }
                return "Draw area";
            case JME_TOP_ACTION_EDIT:
                return "Edit";
            case JME_TOP_ACTION_DUPLICATE_ROUTE:
                return "Duplicate route";
            case JME_TOP_ACTION_DELETE:
                return "Delete";
            default:
                return "Action";
        }
    }

    @Unique
    private int[] jme$getTopLevelOverflowMenuLayout(int rows) {
        final int panelWidth = 180;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int panelHeight = rows * (rowHeight + gap) - gap + padding * 2;

        int x = jme$listOverflowButtonX + SQUARE_SIZE + 4;
        int y = jme$listOverflowButtonY;
        if (y + panelHeight > height - 4) {
            y = height - 4 - panelHeight;
        }
        y = Math.max(4, y);

        if (x + panelWidth > width - 4) {
            x = Math.max(4, width - 4 - panelWidth);
        }

        return new int[]{x, y, panelWidth, panelHeight, padding, rowHeight, gap};
    }

    @Unique
    private void jme$performTopLevelOverflowAction(int action, DashboardListItem listItem, int index) {
        if (listItem == null || listItem.data == null) {
            return;
        }

        final DashboardScreenAccessor accessor = (DashboardScreenAccessor) (Object) this;
        if (action == JME_TOP_ACTION_FIND) {
            accessor.jme$onFind(listItem, index);
            return;
        }

        if (action == JME_TOP_ACTION_DRAW_AREA) {
            accessor.jme$onDrawArea(listItem, index);
            return;
        }

        if (action == JME_TOP_ACTION_EDIT) {
            accessor.jme$onEdit(listItem, index);
            return;
        }

        if (action == JME_TOP_ACTION_DELETE) {
            accessor.jme$onDelete(listItem, index);
            return;
        }

        if (action == JME_TOP_ACTION_DUPLICATE_ROUTE && listItem.data instanceof Route) {
            jme$duplicateRoute((Route) listItem.data);
        }
    }

    @Unique
    private static final int JME_ROUTE_ACTION_SELECT_PLATFORM = 1;
    @Unique
    private static final int JME_ROUTE_ACTION_DUPLICATE = 2;
    @Unique
    private static final int JME_ROUTE_ACTION_REMOVE = 3;
    @Unique
    private static final int JME_ROUTE_ACTION_EDIT_FOLDER = 4;
    @Unique
    private static final int JME_ROUTE_ACTION_DELETE_FOLDER = 5;
    @Unique
    private static final int JME_ROUTE_ACTION_EDIT_DESTINATION = 6;
    @Unique
    private static final int JME_ROUTE_ACTION_MOVE_UP = 7;
    @Unique
    private static final int JME_ROUTE_ACTION_MOVE_DOWN = 8;

    @Unique
    private int[] jme$getRouteOverflowActions(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (rowMetadata == null || editingRoute == null) {
            return new int[0];
        }

        if (rowMetadata.folder) {
            return new int[]{JME_ROUTE_ACTION_EDIT_FOLDER, JME_ROUTE_ACTION_DELETE_FOLDER};
        }

        final boolean isPlatform = rowMetadata.platformIndex >= 0;
        if (!isPlatform) {
            return new int[0];
        }

        final int resolvedIndex = jme$resolveRoutePlatformIndex(editingRoute, rowMetadata);
        if (resolvedIndex < 0 || resolvedIndex >= editingRoute.getRoutePlatforms().size()) {
            return new int[0];
        }

        final List<Integer> actions = new ArrayList<>();
        actions.add(JME_ROUTE_ACTION_EDIT_DESTINATION);
        if (resolvedIndex > 0) {
            actions.add(JME_ROUTE_ACTION_MOVE_UP);
        }
        if (resolvedIndex < editingRoute.getRoutePlatforms().size() - 1) {
            actions.add(JME_ROUTE_ACTION_MOVE_DOWN);
        }

        final boolean canSelectPlatform = jme$canSelectAlternativePlatform(rowMetadata);
        if (canSelectPlatform) {
            actions.add(JME_ROUTE_ACTION_SELECT_PLATFORM);
        }
        actions.add(JME_ROUTE_ACTION_DUPLICATE);
        actions.add(JME_ROUTE_ACTION_REMOVE);

        final int[] result = new int[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            result[i] = actions.get(i);
        }
        return result;
    }

    @Unique
    private static String jme$getRouteOverflowActionLabel(int action, DashboardRouteFolderStore.RowMetadata rowMetadata) {
        switch (action) {
            case JME_ROUTE_ACTION_SELECT_PLATFORM:
                return "Select platform";
            case JME_ROUTE_ACTION_DUPLICATE:
                return "Duplicate";
            case JME_ROUTE_ACTION_REMOVE:
                return "Remove";
            case JME_ROUTE_ACTION_EDIT_FOLDER:
                return "Edit folder";
            case JME_ROUTE_ACTION_DELETE_FOLDER:
                return "Delete folder";
            case JME_ROUTE_ACTION_EDIT_DESTINATION:
                return "Edit";
            case JME_ROUTE_ACTION_MOVE_UP:
                return "Move up";
            case JME_ROUTE_ACTION_MOVE_DOWN:
                return "Move down";
            default:
                return rowMetadata != null && rowMetadata.folder ? "Folder action" : "Action";
        }
    }

    @Unique
    private int[] jme$getRouteOverflowMenuLayout(int rows) {
        final int panelWidth = 170;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int panelHeight = rows * (rowHeight + gap) - gap + padding * 2;

        int x = jme$routeOverflowButtonX + SQUARE_SIZE + 4;
        int y = jme$routeOverflowButtonY;
        if (y + panelHeight > height - 4) {
            y = height - 4 - panelHeight;
        }
        y = Math.max(4, y);

        if (x + panelWidth > width - 4) {
            x = Math.max(4, width - 4 - panelWidth);
        }

        return new int[]{x, y, panelWidth, panelHeight, padding, rowHeight, gap};
    }

    @Unique
    private void jme$performRouteOverflowAction(int action, DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null) {
            return;
        }

        if (action == JME_ROUTE_ACTION_EDIT_FOLDER) {
            jme$openFolderEditScreen(editingRoute, rowMetadata);
            return;
        }

        if (action == JME_ROUTE_ACTION_DELETE_FOLDER) {
            DashboardRouteFolderStore.removeFolder(editingRoute, rowMetadata);
            jme$syncRoute(editingRoute);
            return;
        }

        if (rowMetadata.platformIndex < 0 || rowMetadata.platformIndex >= editingRoute.getRoutePlatforms().size()) {
            return;
        }

        final int resolvedIndex = jme$resolveRoutePlatformIndex(editingRoute, rowMetadata);
        if (resolvedIndex < 0 || resolvedIndex >= editingRoute.getRoutePlatforms().size()) {
            return;
        }

        if (action == JME_ROUTE_ACTION_EDIT_DESTINATION) {
            ((DashboardScreenAccessor) (Object) this).jme$startEditingRouteDestination(resolvedIndex);
            return;
        }

        if (action == JME_ROUTE_ACTION_MOVE_UP) {
            if (resolvedIndex <= 0) {
                return;
            }

            final RoutePlatformData moved = editingRoute.getRoutePlatforms().remove(resolvedIndex);
            editingRoute.getRoutePlatforms().add(resolvedIndex - 1, moved);
            jme$syncRoute(editingRoute);
            return;
        }

        if (action == JME_ROUTE_ACTION_MOVE_DOWN) {
            if (resolvedIndex >= editingRoute.getRoutePlatforms().size() - 1) {
                return;
            }

            final RoutePlatformData moved = editingRoute.getRoutePlatforms().remove(resolvedIndex);
            editingRoute.getRoutePlatforms().add(resolvedIndex + 1, moved);
            jme$syncRoute(editingRoute);
            return;
        }

        if (action == JME_ROUTE_ACTION_SELECT_PLATFORM) {
            editingRoutePlatformIndex = resolvedIndex;
            jme$openAlternativeSelector();
            return;
        }

        if (action == JME_ROUTE_ACTION_DUPLICATE) {
            final RoutePlatformData source = editingRoute.getRoutePlatforms().get(resolvedIndex);
            if (source == null || source.platform == null) {
                return;
            }

            final RoutePlatformData duplicate = new RoutePlatformData(source.platform.getId());
            duplicate.setCustomDestination(source.getCustomDestination());
            duplicate.writePlatformCache(editingRoute, MinecraftClientData.getDashboardInstance().platformIdMap);
            editingRoute.getRoutePlatforms().add(resolvedIndex + 1, duplicate);
            jme$syncRoute(editingRoute);
            return;
        }

        if (action == JME_ROUTE_ACTION_REMOVE) {
            if (rowMetadata.platformId != 0) {
                DashboardRouteFolderStore.removePlatformFromFolders(editingRoute, rowMetadata.platformId);
            }
            editingRoute.getRoutePlatforms().remove(resolvedIndex);
            jme$syncRoute(editingRoute);
        }
    }

    @Unique
    private boolean jme$canSelectAlternativePlatform(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return false;
        }

        if (editingRoute == null || rowMetadata == null) {
            return false;
        }

        final int index = rowMetadata.platformIndex;
        if (index < 0 || index >= editingRoute.getRoutePlatforms().size()) {
            return false;
        }

        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(index);
        if (routePlatformData == null) {
            return false;
        }

        final Platform primaryPlatform = routePlatformData.platform;
        if (primaryPlatform == null || primaryPlatform.area == null) {
            return false;
        }

        final long primaryPlatformId = primaryPlatform.getId();
        return primaryPlatform.area.savedRails.stream().anyMatch(savedRail -> savedRail instanceof Platform && ((Platform) savedRail).getId() != primaryPlatformId);
    }

    @Unique
    private void jme$openAlternativeSelector() {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }

        if (editingRoute == null || editingRoute.getRoutePlatforms().isEmpty()) {
            return;
        }
        if (editingRoutePlatformIndex < 0 || editingRoutePlatformIndex >= editingRoute.getRoutePlatforms().size()) {
            return;
        }

        final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
        final Platform primaryPlatform = routePlatformData.platform;
        if (primaryPlatform == null || primaryPlatform.area == null) {
            return;
        }

        MinecraftClient.getInstance().openScreen(new Screen(AlternativePlatformSelectorScreen.create((ScreenExtension) (Object) this, editingRoute, primaryPlatform)));
    }

    @Unique
    private static int jme$resolveRoutePlatformIndex(Route route, DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (route == null || rowMetadata == null) {
            return -1;
        }

        if (rowMetadata.platformIndex >= 0 && rowMetadata.platformIndex < route.getRoutePlatforms().size()) {
            final RoutePlatformData routePlatformData = route.getRoutePlatforms().get(rowMetadata.platformIndex);
            if (routePlatformData != null && routePlatformData.platform != null && (rowMetadata.platformId == 0 || routePlatformData.platform.getId() == rowMetadata.platformId)) {
                return rowMetadata.platformIndex;
            }
        }

        if (rowMetadata.platformId != 0) {
            for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
                final RoutePlatformData routePlatformData = route.getRoutePlatforms().get(i);
                if (routePlatformData != null && routePlatformData.platform != null && routePlatformData.platform.getId() == rowMetadata.platformId) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Unique
    private void jme$openFolderEditScreen(Route route, DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (route == null || rowMetadata == null || !rowMetadata.folder) {
            return;
        }

        final String folderName = DashboardRouteFolderStore.getFolderName(route, rowMetadata);
        final int folderColor = DashboardRouteFolderStore.getFolderColor(route, rowMetadata);
        MinecraftClient.getInstance().openScreen(new Screen(new RouteFolderEditScreen((ScreenExtension) (Object) this, "Edit Folder", folderName, folderColor, (name, iconColor) -> {
            if (DashboardRouteFolderStore.setFolderAppearance(route, rowMetadata, name, iconColor)) {
                jme$syncRoute(route);
            }
        })));
    }

    @Unique
    private DashboardListItem jme$getDashboardListItem(int index) {
        if (dashboardList == null) {
            return null;
        }

        if (!((Object) dashboardList instanceof DashboardListAccessor)) {
            return null;
        }

        final org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<DashboardListItem> dataSorted = ((DashboardListAccessor) (Object) dashboardList).jme$getDataSorted();
        if (dataSorted == null || index < 0 || index >= dataSorted.size()) {
            return null;
        }
        return dataSorted.get(index);
    }

    @Unique
    private void jme$duplicateRoute(Route sourceRoute) {
        if (sourceRoute == null) {
            return;
        }

        try {
            final Route duplicate = new Route(sourceRoute.getTransportMode(), MinecraftClientData.getDashboardInstance());
            duplicate.setName(jme$getDuplicateRouteName(sourceRoute.getName()));
            duplicate.setColor(sourceRoute.getColor());
            duplicate.setRouteNumber(sourceRoute.getRouteNumber());
            duplicate.setHidden(sourceRoute.getHidden());
            duplicate.setCircularState(sourceRoute.getCircularState());
            duplicate.setRouteType(sourceRoute.getRouteType());

            // Copy platforms.
            for (final RoutePlatformData routePlatformData : sourceRoute.getRoutePlatforms()) {
                if (routePlatformData == null) {
                    continue;
                }
                final Platform platform = routePlatformData.getPlatform();
                if (platform == null) {
                    continue;
                }

                final RoutePlatformData platformDuplicate = new RoutePlatformData(platform.getId());
                platformDuplicate.setCustomDestination(routePlatformData.getCustomDestination());
                platformDuplicate.writePlatformCache(duplicate, MinecraftClientData.getDashboardInstance().platformIdMap);
                duplicate.getRoutePlatforms().add(platformDuplicate);
            }

            // Copy depots + durations (best-effort; safe if empty).
            if (sourceRoute.depots != null) {
                for (final Depot depot : sourceRoute.depots) {
                    if (depot != null) {
                        duplicate.depots.add(depot);
                    }
                }
            }
            if (sourceRoute.durations != null) {
                for (int i = 0; i < sourceRoute.durations.size(); i++) {
                    duplicate.durations.add(sourceRoute.durations.getLong(i));
                }
            }

            jme$syncRoute(duplicate);
            jme$sendClientMessage("MAGIC: Duplicated route \"" + sourceRoute.getName() + "\"");
        } catch (Throwable throwable) {
            Main.LOGGER.warn("[MAGIC] Failed duplicating route", throwable);
            jme$sendClientMessage("MAGIC: Duplicate route failed (" + throwable.getClass().getSimpleName() + ")");
        }
    }

    @Unique
    private static String jme$getDuplicateRouteName(String sourceName) {
        final String rawName = sourceName == null ? "" : sourceName.trim();
        if (rawName.isEmpty()) {
            return "Copy";
        }

        final int variationIndex = rawName.indexOf("||");
        if (variationIndex < 0) {
            return rawName + "||Copy";
        }

        final String baseName = rawName.substring(0, variationIndex).trim();
        final String variationName = rawName.substring(variationIndex + 2).trim();
        final String duplicateVariationName = variationName.isEmpty() ? "Copy" : variationName + " Copy";
        return (baseName.isEmpty() ? rawName : baseName) + "||" + duplicateVariationName;
    }

    @Unique
    private static void jme$syncRoute(Route route) {
        if (route == null) {
            return;
        }
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addRoute(route)));
    }

    @Unique
    private static boolean jme$isInButtonBounds(ButtonWidgetExtension button, double mouseX, double mouseY) {
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
    private boolean jme$isInMagicMenuButtonBounds(double mouseX, double mouseY) {
        final int x1 = jme$magicMenuButton.getX2();
        final int y1 = jme$magicMenuButton.getY2();
        final int x2 = x1 + SQUARE_SIZE;
        final int y2 = y1 + SQUARE_SIZE;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Unique
    private static void jme$sendClientMessage(String message) {
        try {
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            final ClientPlayerEntity player = client.getPlayerMapped();
            if (player == null) {
                return;
            }
            player.sendMessage(Text.cast(TextHelper.literal(message)), false);
        } catch (Exception ignored) {
        }
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
