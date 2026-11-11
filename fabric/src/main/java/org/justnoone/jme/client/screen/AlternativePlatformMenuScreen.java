package org.justnoone.jme.client.screen;

import org.justnoone.jme.client.AlternativePlatformClient;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The alternative platforms of a whole route: its stops on the left, and on the right the platforms of the selected
 * stop's station, which can be added as alternatives (or removed). Opened from the dashboard while editing a route.
 */
public class AlternativePlatformMenuScreen extends ScreenExtension implements IGui {

    private static final int MAX_PANEL_WIDTH = 460;
    private static final int MAX_PANEL_HEIGHT = 320;
    private static final int STOP_COLUMN_WIDTH = 150;
    private static final int ROW_HEIGHT = SQUARE_SIZE;
    private static final int ACTION_WIDTH = 48;
    private static final int COLUMNS_TOP = 44;
    private static final int PLATFORM_LIST_TOP = COLUMNS_TOP + 48;
    private static final int FOOTER_HEIGHT = 32;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xA0A0A0;
    private static final int COLOR_PRIMARY = 0xFFD966;
    private static final int COLOR_WARNING = 0xFF7F7F;

    private final Screen parent;
    private final Route route;
    private int stopIndex;
    private final List<Entry> entries = new ArrayList<>();
    private final List<ButtonWidgetExtension> stopButtons = new ArrayList<>();
    private final List<ButtonWidgetExtension> slotButtons = new ArrayList<>();

    private ButtonWidgetExtension wildcardButton;
    private int stopScrollOffset;
    private int scrollOffset;
    private int visibleStopRows;
    private int visibleRows;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public AlternativePlatformMenuScreen(ScreenExtension parent, Route route, int stopIndex) {
        this.parent = new Screen(parent);
        this.route = route;
        this.stopIndex = stopIndex;
    }

    @Override
    protected void init2() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        final int rightX = getRightColumnX();
        final int rightWidth = panelX + panelWidth - 10 - rightX;

        stopButtons.clear();
        visibleStopRows = Math.max(1, (panelHeight - COLUMNS_TOP - FOOTER_HEIGHT) / ROW_HEIGHT);
        for (int i = 0; i < visibleStopRows; i++) {
            final int slot = i;
            final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), pressed -> selectStop(stopScrollOffset + slot));
            IDrawing.setPositionAndWidth(button, panelX + 10, panelY + COLUMNS_TOP + i * ROW_HEIGHT, STOP_COLUMN_WIDTH);
            stopButtons.add(button);
            addChild(new ClickableWidget(button));
        }

        wildcardButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), button -> toggleWildcard());
        IDrawing.setPositionAndWidth(wildcardButton, rightX, panelY + COLUMNS_TOP + 24, rightWidth);
        addChild(new ClickableWidget(wildcardButton));

        slotButtons.clear();
        visibleRows = Math.max(1, (panelHeight - PLATFORM_LIST_TOP - FOOTER_HEIGHT) / ROW_HEIGHT);
        for (int i = 0; i < visibleRows; i++) {
            final int slot = i;
            final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), pressed -> onSlotPressed(slot));
            IDrawing.setPositionAndWidth(button, panelX + panelWidth - 10 - ACTION_WIDTH, panelY + PLATFORM_LIST_TOP + i * ROW_HEIGHT, ACTION_WIDTH);
            slotButtons.add(button);
            addChild(new ClickableWidget(button));
        }

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.done"), button -> onClose2());
        IDrawing.setPositionAndWidth(doneButton, panelX + panelWidth - 10 - 80, panelY + panelHeight - 26, 80);
        addChild(new ClickableWidget(doneButton));

        // Keep the selected stop in view.
        if (stopIndex < stopScrollOffset || stopIndex >= stopScrollOffset + visibleStopRows) {
            stopScrollOffset = Math.max(0, stopIndex - visibleStopRows / 2);
        }
        refresh();
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, panelWidth, panelHeight);

        final int light = GraphicsHolder.getDefaultLight();
        final int textWidth = panelWidth - 20;
        graphicsHolder.drawText(fit("Alternative platforms - " + IGui.formatStationName(route.getName()), textWidth), panelX + 10, panelY + 8, COLOR_TEXT, true, light);
        graphicsHolder.drawText(fit("Pick a stop, then add the platforms its trains may use when the main one is taken.", textWidth), panelX + 10, panelY + 22, COLOR_MUTED, false, light);

        final int rightX = getRightColumnX();
        final int rightWidth = panelX + panelWidth - 10 - rightX;
        graphicsHolder.drawText("Stops", panelX + 10, panelY + COLUMNS_TOP - 10, COLOR_MUTED, false, light);
        if (route.getRoutePlatforms().size() > visibleStopRows) {
            graphicsHolder.drawText((stopScrollOffset + 1) + "-" + Math.min(route.getRoutePlatforms().size(), stopScrollOffset + visibleStopRows) + " / " + route.getRoutePlatforms().size() + " (scroll)",
                    panelX + 10, panelY + panelHeight - 20, COLOR_MUTED, false, light);
        }

        final Platform primaryPlatform = getPrimaryPlatform();
        if (primaryPlatform == null) {
            graphicsHolder.drawText(fit("This stop no longer exists.", rightWidth), rightX, panelY + COLUMNS_TOP, COLOR_WARNING, false, light);
            super.render(graphicsHolder, mouseX, mouseY, delta);
            return;
        }

        graphicsHolder.drawText(fit("Stop " + (stopIndex + 1) + ": " + getStationName(primaryPlatform), rightWidth), rightX, panelY + COLUMNS_TOP, COLOR_TEXT, false, light);
        graphicsHolder.drawText(fit("★ Main platform: " + describePlatform(primaryPlatform), rightWidth), rightX, panelY + COLUMNS_TOP + 11, COLOR_PRIMARY, false, light);

        final int labelWidth = rightWidth - ACTION_WIDTH - 6;
        for (int i = 0; i < visibleRows; i++) {
            final int entryIndex = scrollOffset + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            final Entry entry = entries.get(entryIndex);
            final int rowY = panelY + PLATFORM_LIST_TOP + i * ROW_HEIGHT + (ROW_HEIGHT - TEXT_HEIGHT) / 2;
            final boolean hasAction = entry.getActionLabel() != null;
            final int x = entry.type == EntryType.HEADER ? rightX : rightX + 6;
            graphicsHolder.drawText(fit(entry.label, hasAction ? labelWidth : rightWidth - 6), x, rowY, entry.color, false, light);
        }

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
        final int step = -(int) Math.signum(amount);
        if (mouseX < getRightColumnX()) {
            final int maxOffset = Math.max(0, route.getRoutePlatforms().size() - visibleStopRows);
            stopScrollOffset = Math.max(0, Math.min(maxOffset, stopScrollOffset + step));
            syncStopButtons();
        } else {
            final int maxOffset = Math.max(0, entries.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + step));
            syncSlots();
        }
        return true;
    }

    @Override
    public void onClose2() {
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
    }

    private int getRightColumnX() {
        return panelX + 10 + STOP_COLUMN_WIDTH + 10;
    }

    private void selectStop(int newStopIndex) {
        if (newStopIndex >= 0 && newStopIndex < route.getRoutePlatforms().size()) {
            stopIndex = newStopIndex;
            scrollOffset = 0;
            refresh();
        }
    }

    private void onSlotPressed(int slot) {
        final int entryIndex = scrollOffset + slot;
        final Platform primaryPlatform = getPrimaryPlatform();
        if (primaryPlatform == null || entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }

        final Entry entry = entries.get(entryIndex);
        if (entry.type == EntryType.ALTERNATIVE || entry.type == EntryType.MISSING) {
            AlternativePlatformClient.setAlternative(route.getId(), primaryPlatform.getId(), entry.platformId, false);
        } else if (entry.type == EntryType.ADDABLE) {
            AlternativePlatformClient.setAlternative(route.getId(), primaryPlatform.getId(), entry.platformId, true);
        }
        refresh();
    }

    private void toggleWildcard() {
        final Platform primaryPlatform = getPrimaryPlatform();
        if (primaryPlatform == null) {
            return;
        }
        final boolean wildcard = isWildcardEnabled(primaryPlatform);
        AlternativePlatformClient.setAlternative(route.getId(), primaryPlatform.getId(), AlternativePlatformRegistry.ALL_STATION_PLATFORMS, !wildcard);
        refresh();
    }

    private void refresh() {
        entries.clear();
        final Platform primaryPlatform = getPrimaryPlatform();
        if (primaryPlatform != null) {
            final boolean wildcard = isWildcardEnabled(primaryPlatform);
            final List<Platform> compatiblePlatforms = AlternativePlatformRegistry.getCompatiblePlatforms(primaryPlatform);
            final LinkedHashSet<Long> configuredIds = new LinkedHashSet<>(AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatform.getId()));
            configuredIds.remove(AlternativePlatformRegistry.ALL_STATION_PLATFORMS);

            final List<Entry> assigned = new ArrayList<>();
            final List<Entry> addable = new ArrayList<>();
            for (final Platform platform : compatiblePlatforms) {
                final long platformId = platform.getId();
                if (wildcard) {
                    assigned.add(new Entry(EntryType.AUTO, platformId, describePlatform(platform), COLOR_TEXT));
                } else if (configuredIds.contains(platformId)) {
                    assigned.add(new Entry(EntryType.ALTERNATIVE, platformId, describePlatform(platform), COLOR_TEXT));
                } else {
                    addable.add(new Entry(EntryType.ADDABLE, platformId, describePlatform(platform), COLOR_MUTED));
                }
                configuredIds.remove(platformId);
            }
            // Whatever is left was configured but no longer exists in this station (deleted, moved, other mode).
            for (final long platformId : configuredIds) {
                assigned.add(new Entry(EntryType.MISSING, platformId, "Unavailable platform (removed or moved)", COLOR_WARNING));
            }

            // At a terminus shared with another route of the depot, the alternatives configured on either route apply.
            for (final Route linkedRoute : AlternativePlatformRegistry.getLinkedTerminusRoutes(route, primaryPlatform.getId())) {
                final int linkedCount = AlternativePlatformRegistry.getAlternatives(linkedRoute.getId(), primaryPlatform.getId()).size();
                final boolean linkedStartsHere = stopIndex == route.getRoutePlatforms().size() - 1;
                entries.add(new Entry(EntryType.INFO, 0, (linkedStartsHere ? "Next route " : "Previous route ") + IGui.formatStationName(linkedRoute.getName())
                        + (linkedStartsHere ? " starts here" : " ends here") + (linkedCount == 0 ? "" : " (+" + linkedCount + " of its alternatives)"), COLOR_PRIMARY));
            }
            entries.add(new Entry(EntryType.HEADER, 0, "Alternatives (" + assigned.size() + ")", COLOR_MUTED));
            if (assigned.isEmpty()) {
                entries.add(new Entry(EntryType.INFO, 0, "None: trains always use the main platform", COLOR_MUTED));
            }
            entries.addAll(assigned);
            if (!wildcard) {
                entries.add(new Entry(EntryType.HEADER, 0, "Other platforms of the station", COLOR_MUTED));
                if (addable.isEmpty()) {
                    entries.add(new Entry(EntryType.INFO, 0, compatiblePlatforms.isEmpty() ? "None: the station has no other platform" : "All of them are already added", COLOR_MUTED));
                }
                entries.addAll(addable);
            }

            wildcardButton.setMessage2(Text.cast(TextHelper.literal("Any free platform of the station: " + (wildcard ? "ON" : "OFF"))));
            wildcardButton.visible = true;
            wildcardButton.active = !compatiblePlatforms.isEmpty() || wildcard;
        } else if (wildcardButton != null) {
            wildcardButton.visible = false;
        }

        scrollOffset = Math.max(0, Math.min(scrollOffset, entries.size() - visibleRows));
        syncStopButtons();
        syncSlots();
    }

    private void syncStopButtons() {
        final List<RoutePlatformData> stops = route.getRoutePlatforms();
        for (int i = 0; i < stopButtons.size(); i++) {
            final ButtonWidgetExtension button = stopButtons.get(i);
            final int index = stopScrollOffset + i;
            final boolean exists = index < stops.size();
            button.visible = exists;
            button.active = exists && index != stopIndex;
            if (exists) {
                button.setMessage2(Text.cast(TextHelper.literal(fit((index == stopIndex ? "▶ " : "") + (index + 1) + ". " + getStopLabel(stops.get(index)), STOP_COLUMN_WIDTH - 8))));
            }
        }
    }

    private void syncSlots() {
        for (int i = 0; i < slotButtons.size(); i++) {
            final ButtonWidgetExtension button = slotButtons.get(i);
            final int entryIndex = scrollOffset + i;
            final Entry entry = entryIndex < entries.size() ? entries.get(entryIndex) : null;
            final String action = entry == null ? null : entry.getActionLabel();
            button.visible = action != null;
            button.active = action != null;
            if (action != null) {
                button.setMessage2(Text.cast(TextHelper.literal(action)));
            }
        }
    }

    /**
     * The stop's station, with the number of its alternatives (eg "Lisbon ⎇1").
     */
    private String getStopLabel(RoutePlatformData routePlatformData) {
        final Platform platform = routePlatformData == null ? null : routePlatformData.platform;
        if (platform == null) {
            return "(removed stop)";
        }
        final List<Long> alternatives = AlternativePlatformRegistry.getAlternatives(route.getId(), platform.getId());
        final String mark = alternatives.isEmpty() ? "" : alternatives.contains(AlternativePlatformRegistry.ALL_STATION_PLATFORMS) ? " ⎇all" : " ⎇" + alternatives.size();
        return getStationName(platform) + mark;
    }

    private Platform getPrimaryPlatform() {
        if (route == null || stopIndex < 0 || stopIndex >= route.getRoutePlatforms().size()) {
            return null;
        }
        final RoutePlatformData routePlatformData = route.getRoutePlatforms().get(stopIndex);
        return routePlatformData == null ? null : routePlatformData.platform;
    }

    private boolean isWildcardEnabled(Platform primaryPlatform) {
        return AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatform.getId()).contains(AlternativePlatformRegistry.ALL_STATION_PLATFORMS);
    }

    private static String describePlatform(Platform platform) {
        final int routeCount = platform.routes == null ? 0 : platform.routes.size();
        return "Platform " + IGui.formatStationName(platform.getName()) + " · " + routeCount + (routeCount == 1 ? " route" : " routes");
    }

    private static String getStationName(Platform platform) {
        return platform.area == null ? "(no station)" : IGui.formatStationName(platform.area.getName());
    }

    private static String fit(String text, int maxWidth) {
        if (GraphicsHolder.getTextWidth(text) <= maxWidth) {
            return text;
        }
        final String ellipsis = "...";
        int end = text.length();
        while (end > 0 && GraphicsHolder.getTextWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + ellipsis;
    }

    private enum EntryType {
        HEADER, INFO, ALTERNATIVE, AUTO, MISSING, ADDABLE
    }

    private static final class Entry {
        private final EntryType type;
        private final long platformId;
        private final String label;
        private final int color;

        private Entry(EntryType type, long platformId, String label, int color) {
            this.type = type;
            this.platformId = platformId;
            this.label = label;
            this.color = color;
        }

        private String getActionLabel() {
            switch (type) {
                case ALTERNATIVE:
                case MISSING:
                    return "Remove";
                case ADDABLE:
                    return "Add";
                default:
                    return null;
            }
        }
    }
}
