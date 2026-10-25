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
 * Compact menu for one stop of a route: shows the primary platform and lets the user add or remove alternative
 * platforms from the same station.
 */
public class AlternativePlatformMenuScreen extends ScreenExtension implements IGui {

    private static final int MAX_PANEL_WIDTH = 280;
    private static final int ROW_HEIGHT = SQUARE_SIZE;
    private static final int ACTION_WIDTH = 48;
    private static final int LIST_TOP = 74;
    private static final int FOOTER_HEIGHT = 32;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xA0A0A0;
    private static final int COLOR_PRIMARY = 0xFFD966;
    private static final int COLOR_WARNING = 0xFF7F7F;

    private final Screen parent;
    private final Route route;
    private final int stopIndex;
    private final List<Entry> entries = new ArrayList<>();
    private final List<ButtonWidgetExtension> slotButtons = new ArrayList<>();

    private ButtonWidgetExtension wildcardButton;
    private int scrollOffset;
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
        // Size the panel for the largest list this stop can produce, so it does not jump while adding/removing.
        final Platform primaryPlatform = getPrimaryPlatform();
        final int maxRows = primaryPlatform == null ? 1 : AlternativePlatformRegistry.getCompatiblePlatforms(primaryPlatform).size()
                + AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatform.getId()).size() + 4;
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        panelHeight = Math.max(LIST_TOP + FOOTER_HEIGHT + ROW_HEIGHT, Math.min(height - 16, LIST_TOP + maxRows * ROW_HEIGHT + FOOTER_HEIGHT));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        wildcardButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), button -> toggleWildcard());
        IDrawing.setPositionAndWidth(wildcardButton, panelX + 10, panelY + 46, panelWidth - 20);
        addChild(new ClickableWidget(wildcardButton));

        slotButtons.clear();
        visibleRows = Math.max(1, (panelHeight - LIST_TOP - FOOTER_HEIGHT) / ROW_HEIGHT);
        for (int i = 0; i < visibleRows; i++) {
            final int slot = i;
            final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), pressed -> onSlotPressed(slot));
            IDrawing.setPositionAndWidth(button, panelX + panelWidth - 10 - ACTION_WIDTH, panelY + LIST_TOP + i * ROW_HEIGHT, ACTION_WIDTH);
            slotButtons.add(button);
            addChild(new ClickableWidget(button));
        }

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.done"), button -> onClose2());
        IDrawing.setPositionAndWidth(doneButton, panelX + panelWidth - 10 - 80, panelY + panelHeight - 26, 80);
        addChild(new ClickableWidget(doneButton));

        refresh();
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, panelWidth, panelHeight);

        final Platform primaryPlatform = getPrimaryPlatform();
        final int textWidth = panelWidth - 20;
        graphicsHolder.drawText(fit("Alternative platforms", textWidth), panelX + 10, panelY + 8, COLOR_TEXT, true, GraphicsHolder.getDefaultLight());
        if (primaryPlatform == null) {
            graphicsHolder.drawText(fit("This stop no longer exists.", textWidth), panelX + 10, panelY + 30, COLOR_WARNING, false, GraphicsHolder.getDefaultLight());
            super.render(graphicsHolder, mouseX, mouseY, delta);
            return;
        }

        graphicsHolder.drawText(fit("Stop " + (stopIndex + 1) + ": " + getStationName(primaryPlatform), textWidth), panelX + 10, panelY + 24, COLOR_MUTED, false, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(fit("★ Primary: " + describePlatform(primaryPlatform), textWidth), panelX + 10, panelY + 35, COLOR_PRIMARY, false, GraphicsHolder.getDefaultLight());

        final int labelWidth = panelWidth - 30 - ACTION_WIDTH;
        for (int i = 0; i < visibleRows; i++) {
            final int entryIndex = scrollOffset + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            final Entry entry = entries.get(entryIndex);
            final int rowY = panelY + LIST_TOP + i * ROW_HEIGHT + (ROW_HEIGHT - TEXT_HEIGHT) / 2;
            final int x = entry.type == EntryType.HEADER ? panelX + 10 : panelX + 16;
            graphicsHolder.drawText(fit(entry.label, entry.type == EntryType.HEADER ? textWidth : labelWidth), x, rowY, entry.color, false, GraphicsHolder.getDefaultLight());
        }

        if (entries.size() > visibleRows) {
            final String scrollHint = (scrollOffset + 1) + "-" + Math.min(entries.size(), scrollOffset + visibleRows) + " / " + entries.size() + " (scroll)";
            graphicsHolder.drawText(scrollHint, panelX + 10, panelY + panelHeight - 20, COLOR_MUTED, false, GraphicsHolder.getDefaultLight());
        }

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
        final int maxOffset = Math.max(0, entries.size() - visibleRows);
        final int nextOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(amount)));
        if (nextOffset != scrollOffset) {
            scrollOffset = nextOffset;
            syncSlots();
        }
        return true;
    }

    @Override
    public void onClose2() {
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
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

            entries.add(new Entry(EntryType.HEADER, 0, "Alternatives (" + assigned.size() + ")", COLOR_MUTED));
            if (assigned.isEmpty()) {
                entries.add(new Entry(EntryType.INFO, 0, "None - trains always use the primary platform", COLOR_MUTED));
            }
            entries.addAll(assigned);
            if (!wildcard) {
                entries.add(new Entry(EntryType.HEADER, 0, "Add a platform", COLOR_MUTED));
                if (addable.isEmpty()) {
                    entries.add(new Entry(EntryType.INFO, 0, compatiblePlatforms.isEmpty() ? "No other platforms in this station" : "All platforms already added", COLOR_MUTED));
                }
                entries.addAll(addable);
            }

            wildcardButton.setMessage2(Text.cast(TextHelper.literal("Use every platform in station: " + (wildcard ? "ON" : "OFF"))));
            wildcardButton.visible = true;
            wildcardButton.active = !compatiblePlatforms.isEmpty() || wildcard;
        } else if (wildcardButton != null) {
            wildcardButton.visible = false;
        }

        scrollOffset = Math.max(0, Math.min(scrollOffset, entries.size() - visibleRows));
        syncSlots();
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
