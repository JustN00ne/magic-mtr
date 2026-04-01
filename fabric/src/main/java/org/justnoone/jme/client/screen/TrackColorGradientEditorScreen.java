package org.justnoone.jme.client.screen;

import org.justnoone.jme.config.JmeConfig;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TrackColorGradientEditorScreen extends ScreenExtension {

    public interface SaveHandler {
        void onSave(JmeConfig.TrackColorStop[] stops);
    }

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 6;
    private static final int SCROLL_STEP = 18;

    private final Screen parent;
    private final SaveHandler saveHandler;
    private final List<StopDraft> drafts;

    private final List<RowWidgets> rows = new ArrayList<>();
    private double scrollOffset;
    private int contentHeight;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int contentX;
    private int contentTop;
    private int contentBottom;
    private int contentWidth;

    public TrackColorGradientEditorScreen(ScreenExtension parent, JmeConfig.TrackColorStop[] initialStops, SaveHandler saveHandler) {
        this(new Screen(parent), toDrafts(initialStops), saveHandler);
    }

    private static boolean jme$useMph() {
        return JmeConfig.useMph();
    }

    private static int jme$displaySpeedFromKmh(int speedKmh) {
        return jme$useMph() ? JmeConfig.toMph(speedKmh) : speedKmh;
    }

    private static int jme$speedKmhFromDisplay(int speedDisplay) {
        if (!jme$useMph()) {
            return speedDisplay;
        }
        return JmeConfig.toKmh(speedDisplay);
    }

    private static int jme$maxDisplaySpeed() {
        return jme$displaySpeedFromKmh(400);
    }

    private static String jme$getSpeedUnitLabel() {
        return jme$useMph() ? "mph" : "km/h";
    }

    private TrackColorGradientEditorScreen(Screen parent, List<StopDraft> drafts, SaveHandler saveHandler) {
        this.parent = parent;
        this.drafts = drafts == null ? new ArrayList<>() : new ArrayList<>(drafts);
        this.saveHandler = saveHandler;
    }

    @Override
    protected void init2() {
        final int padding = 14;
        panelX = padding;
        panelY = padding;
        panelWidth = Math.max(1, width - padding * 2);
        panelHeight = Math.max(1, height - padding * 2);

        headerHeight = 62;
        contentX = panelX + 16;
        contentWidth = Math.max(1, panelWidth - 32);
        contentTop = panelY + headerHeight + 12;
        contentBottom = panelY + panelHeight - 46;

        rows.clear();

        // Create text fields with non-zero dimensions from the start.
        // Some versions of TextFieldWidgetExtension don't render the initial text correctly if the field starts at 0 width,
        // and only begin showing it after focusing the widget.
        final int speedW = 72;
        final int colorW = 100;
        final int gap = 10;
        final int removeW = 26;
        final int speedX = contentX;
        final int colorX = speedX + speedW + gap;
        final int removeX = contentX + contentWidth - removeW;

        int rowY = 0;
        final List<StopDraft> resolvedDrafts = drafts.isEmpty() ? toDrafts(defaultStops()) : drafts;
        for (int i = 0; i < resolvedDrafts.size(); i++) {
            final StopDraft draft = resolvedDrafts.get(i);
            if (draft == null) {
                continue;
            }

            final int y = contentTop + rowY;

            final TextFieldWidgetExtension speedField = new TextFieldWidgetExtension(speedX, y, speedW, ROW_HEIGHT, 4, TextCase.DEFAULT, "[^\\d]", "");
            addChild(new ClickableWidget(speedField));
            speedField.setText2(draft.speedText);

            final TextFieldWidgetExtension colorField = new TextFieldWidgetExtension(colorX, y, colorW, ROW_HEIGHT, 9, TextCase.DEFAULT, "[^0-9a-fA-F#]", "");
            addChild(new ClickableWidget(colorField));
            colorField.setText2(draft.colorText);

            final int removeIndex = i;
            final ButtonWidgetExtension removeButton = new ButtonWidgetExtension(removeX, y, removeW, ROW_HEIGHT, TextHelper.literal("X"), button -> removeRow(removeIndex));
            addChild(new ClickableWidget(removeButton));

            rows.add(new RowWidgets(speedField, colorField, removeButton, rowY, ROW_HEIGHT));
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        contentHeight = Math.max(0, rowY - ROW_GAP);
        scrollOffset = clampScrollOffset(scrollOffset);
        updateScrollLayout();

        final ButtonWidgetExtension addStopButton = new ButtonWidgetExtension(panelX + panelWidth - 170, panelY + 34, 78, 20, TextHelper.literal("Add Stop"), button -> addRow());
        addChild(new ClickableWidget(addStopButton));

        final ButtonWidgetExtension resetButton = new ButtonWidgetExtension(panelX + panelWidth - 86, panelY + 34, 72, 20, TextHelper.literal("Reset"), button -> resetStops());
        addChild(new ClickableWidget(resetButton));

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(panelX + panelWidth - 88, panelY + panelHeight - 30, 74, 20, TextHelper.literal("Done"), button -> onDone());
        addChild(new ClickableWidget(doneButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + panelWidth - 168, panelY + panelHeight - 30, 74, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        drawPanel(graphicsHolder);
        drawScrollBar(graphicsHolder, mouseX, mouseY);

        graphicsHolder.drawText(TextHelper.literal("Custom Track Gradient"), panelX + 16, panelY + 12, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Speed (" + jme$getSpeedUnitLabel() + ")"), panelX + 16, panelY + headerHeight + 2, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Color (#RRGGBB)"), panelX + 110, panelY + headerHeight + 2, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());

        // Color previews.
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        for (final RowWidgets row : rows) {
            if (row == null || row.colorField == null || !row.isVisible()) {
                continue;
            }
            final int previewRgb = parseColorRgb(row.colorField.getText2(), 0x666666);
            final int px = row.previewX;
            final int py = row.previewY;
            guiDrawing.drawRectangle(px, py, px + 18, py + 18, 0xFF000000 | (previewRgb & 0xFFFFFF));
            guiDrawing.drawRectangle(px, py, px + 18, py + 1, 0xFF2B2B2B);
            guiDrawing.drawRectangle(px, py + 17, px + 18, py + 18, 0xFF2B2B2B);
            guiDrawing.drawRectangle(px, py, px + 1, py + 18, 0xFF2B2B2B);
            guiDrawing.drawRectangle(px + 17, py, px + 18, py + 18, 0xFF2B2B2B);
        }
        guiDrawing.finishDrawingRectangle();

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
        if (contentHeight > getViewportHeight() && isInContentArea(mouseX, mouseY)) {
            final double next = scrollOffset - amount * SCROLL_STEP;
            final double clamped = clampScrollOffset(next);
            if (Math.abs(clamped - scrollOffset) > 0.01D) {
                scrollOffset = clamped;
                updateScrollLayout();
                return true;
            }
        }
        return super.mouseScrolled2(mouseX, mouseY, amount);
    }

    private void updateScrollLayout() {
        final int scroll = (int) Math.round(scrollOffset);
        final int viewportTop = contentTop;
        final int viewportBottom = contentBottom;

        final int speedW = 72;
        final int colorW = 100;
        final int gap = 10;
        final int removeW = 26;
        final int previewW = 18;

        final int speedX = contentX;
        final int colorX = speedX + speedW + gap;
        final int previewX = colorX + colorW + gap;
        final int removeX = contentX + contentWidth - removeW;

        for (final RowWidgets row : rows) {
            final int y = viewportTop + row.baseY - scroll;
            final boolean visible = y + row.height >= viewportTop && y <= viewportBottom;

            row.setVisible(visible);
            row.previewX = previewX;
            row.previewY = y + 1;

            if (!visible) {
                continue;
            }

            row.speedField.setX2(speedX);
            row.speedField.setY2(y);
            row.speedField.setWidth2(speedW);

            row.colorField.setX2(colorX);
            row.colorField.setY2(y);
            row.colorField.setWidth2(colorW);

            row.removeButton.setX2(removeX);
            row.removeButton.setY2(y);
            row.removeButton.setWidth2(removeW);

            // Keep preview rect within bounds even if the screen is narrow.
            if (previewX + previewW > removeX - gap) {
                row.previewX = Math.max(colorX + colorW + 2, removeX - gap - previewW);
            }
        }
    }

    private int getViewportHeight() {
        return Math.max(1, contentBottom - contentTop);
    }

    private boolean isInContentArea(double mouseX, double mouseY) {
        final int viewportLeft = contentX - 4;
        final int viewportRight = contentX + contentWidth + 4;
        return mouseX >= viewportLeft && mouseX <= viewportRight && mouseY >= contentTop && mouseY <= contentBottom;
    }

    private double clampScrollOffset(double next) {
        final int viewportHeight = getViewportHeight();
        final int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (!Double.isFinite(next)) {
            return 0D;
        }
        return Math.max(0D, Math.min(maxScroll, next));
    }

    private void drawPanel(GraphicsHolder graphicsHolder) {
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xB0101010);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + headerHeight, 0x60000000);

        // Border.
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);

        // Content divider.
        guiDrawing.drawRectangle(panelX, contentTop - 6, panelX + panelWidth, contentTop - 5, 0x332B2B2B);

        guiDrawing.finishDrawingRectangle();
    }

    private void drawScrollBar(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final int viewportHeight = getViewportHeight();
        if (contentHeight <= viewportHeight) {
            return;
        }

        final int trackX1 = panelX + panelWidth - 10;
        final int trackX2 = panelX + panelWidth - 6;
        final int trackY1 = contentTop;
        final int trackY2 = contentBottom;

        final int maxScroll = Math.max(1, contentHeight - viewportHeight);
        final double ratio = Math.max(0D, Math.min(1D, scrollOffset / maxScroll));
        final int thumbHeight = Math.max(18, (int) Math.round(viewportHeight * (viewportHeight / (double) contentHeight)));
        final int thumbY1 = trackY1 + (int) Math.round(ratio * Math.max(1, viewportHeight - thumbHeight));
        final int thumbY2 = thumbY1 + thumbHeight;

        final boolean hovered = mouseX >= trackX1 && mouseX <= trackX2 && mouseY >= thumbY1 && mouseY <= thumbY2;
        final int trackColor = 0x33000000;
        final int thumbColor = hovered ? 0xAAFFFFFF : 0x77FFFFFF;

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(trackX1, trackY1, trackX2, trackY2, trackColor);
        guiDrawing.drawRectangle(trackX1, thumbY1, trackX2, thumbY2, thumbColor);
        guiDrawing.finishDrawingRectangle();
    }

    private void addRow() {
        final List<StopDraft> collected = collectDraftsFromWidgets();
        final int nextSpeed = guessNextSpeed(collected);
        collected.add(new StopDraft(String.valueOf(nextSpeed), "#FFFFFF"));
        reopen(collected);
    }

    private void removeRow(int index) {
        final List<StopDraft> collected = collectDraftsFromWidgets();
        if (index >= 0 && index < collected.size()) {
            collected.remove(index);
        }
        reopen(collected);
    }

    private void resetStops() {
        reopen(toDrafts(defaultStops()));
    }

    private void reopen(List<StopDraft> nextDrafts) {
        if (client == null) {
            return;
        }
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new TrackColorGradientEditorScreen(parent, nextDrafts, saveHandler)));
    }

    private void onDone() {
        final JmeConfig.TrackColorStop[] stops = sanitize(collectStopsFromWidgets());
        if (saveHandler != null) {
            saveHandler.onSave(stops);
        }
        onClose2();
    }

    private List<JmeConfig.TrackColorStop> collectStopsFromWidgets() {
        final ArrayList<JmeConfig.TrackColorStop> stops = new ArrayList<>();
        for (final RowWidgets row : rows) {
            if (row == null || row.speedField == null || row.colorField == null) {
                continue;
            }
            final int speedDisplay = parseIntSafe(row.speedField.getText2(), -1);
            final int speed = jme$speedKmhFromDisplay(speedDisplay);
            final int rgb = parseColorRgb(row.colorField.getText2(), -1);
            if (speed <= 0 || rgb < 0) {
                continue;
            }
            stops.add(new JmeConfig.TrackColorStop(speed, 0xFF000000 | (rgb & 0xFFFFFF)));
        }
        return stops;
    }

    private List<StopDraft> collectDraftsFromWidgets() {
        final ArrayList<StopDraft> collected = new ArrayList<>();
        for (final RowWidgets row : rows) {
            if (row == null || row.speedField == null || row.colorField == null) {
                continue;
            }
            collected.add(new StopDraft(row.speedField.getText2(), row.colorField.getText2()));
        }
        return collected;
    }

    private static int guessNextSpeed(List<StopDraft> collected) {
        int max = 0;
        if (collected != null) {
            for (final StopDraft draft : collected) {
                final int speed = parseIntSafe(draft == null ? "" : draft.speedText, 0);
                max = Math.max(max, speed);
            }
        }
        final int maxAllowed = jme$maxDisplaySpeed();
        final int increment = jme$useMph() ? 10 : 20;
        if (max <= 0) {
            return Math.min(maxAllowed, jme$useMph() ? 60 : 100);
        }
        if (max >= maxAllowed) {
            return maxAllowed;
        }
        return Math.min(maxAllowed, max + increment);
    }

    private static JmeConfig.TrackColorStop[] sanitize(List<JmeConfig.TrackColorStop> stops) {
        final TreeMap<Integer, Integer> bySpeed = new TreeMap<>();
        if (stops != null) {
            for (final JmeConfig.TrackColorStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                final int speed = Math.max(1, Math.min(400, stop.speedKmh));
                final int argb = 0xFF000000 | (stop.colorArgb & 0xFFFFFF);
                bySpeed.put(speed, argb);
            }
        }

        if (bySpeed.size() < 2) {
            return defaultStops();
        }

        final JmeConfig.TrackColorStop[] sanitized = new JmeConfig.TrackColorStop[bySpeed.size()];
        int idx = 0;
        for (final Map.Entry<Integer, Integer> entry : bySpeed.entrySet()) {
            sanitized[idx++] = new JmeConfig.TrackColorStop(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }

    private static JmeConfig.TrackColorStop[] defaultStops() {
        return new JmeConfig.TrackColorStop[]{
                new JmeConfig.TrackColorStop(5, 0xFF102A8A),
                new JmeConfig.TrackColorStop(100, 0xFF25C977),
                new JmeConfig.TrackColorStop(180, 0xFFD9E344),
                new JmeConfig.TrackColorStop(220, 0xFFFFE028),
                new JmeConfig.TrackColorStop(300, 0xFFEF3A26),
                new JmeConfig.TrackColorStop(400, 0xFFB42AE6)
        };
    }

    private static List<StopDraft> toDrafts(JmeConfig.TrackColorStop[] stops) {
        final ArrayList<StopDraft> drafts = new ArrayList<>();
        if (stops == null) {
            return drafts;
        }
        for (final JmeConfig.TrackColorStop stop : stops) {
            if (stop == null) {
                continue;
            }
            drafts.add(new StopDraft(String.valueOf(jme$displaySpeedFromKmh(stop.speedKmh)), formatColorRgb(stop.colorArgb)));
        }
        return drafts;
    }

    private static String formatColorRgb(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    private static int parseColorRgb(String raw, int fallbackRgb) {
        if (raw == null) {
            return fallbackRgb;
        }

        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return fallbackRgb;
        }

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        if (normalized.length() == 8) {
            normalized = normalized.substring(2);
        }

        if (normalized.length() != 6) {
            return fallbackRgb;
        }

        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return fallbackRgb;
        }
    }

    private static int parseIntSafe(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public void onClose2() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
        }
    }

    private static final class StopDraft {
        private final String speedText;
        private final String colorText;

        private StopDraft(String speedText, String colorText) {
            this.speedText = speedText == null ? "" : speedText;
            this.colorText = colorText == null ? "" : colorText;
        }
    }

    private static final class RowWidgets {
        private final TextFieldWidgetExtension speedField;
        private final TextFieldWidgetExtension colorField;
        private final ButtonWidgetExtension removeButton;
        private final int baseY;
        private final int height;

        private int previewX;
        private int previewY;

        private RowWidgets(TextFieldWidgetExtension speedField, TextFieldWidgetExtension colorField, ButtonWidgetExtension removeButton, int baseY, int height) {
            this.speedField = speedField;
            this.colorField = colorField;
            this.removeButton = removeButton;
            this.baseY = baseY;
            this.height = height;
        }

        private void setVisible(boolean visible) {
            speedField.setVisibleMapped(visible);
            speedField.setActiveMapped(visible);
            colorField.setVisibleMapped(visible);
            colorField.setActiveMapped(visible);
            removeButton.setVisibleMapped(visible);
            removeButton.setActiveMapped(visible);
        }

        private boolean isVisible() {
            return speedField.getVisibleMapped();
        }
    }
}
