package org.justnoone.jme.client.screen;

import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;

import java.util.ArrayList;
import java.util.List;

public class JmeSettingsScreen extends ScreenExtension {

    private final Screen parent;
    private boolean useMph;
    private boolean cameraTiltEnabled;
    private double cameraTiltStrength;
    private JmeConfig.DashboardRouteListMode routeListMode;
    private boolean dashboardMapAutoSaveEnabled;
    private JmeConfig.DashboardRailOverlayMode dashboardRailOverlayMode;
    private int dashboardRailCullMaxPerCell;

    private ButtonWidgetExtension speedUnitButton;
    private ButtonWidgetExtension cameraTiltButton;
    private SliderWidgetExtension cameraTiltStrengthSlider;
    private ButtonWidgetExtension routeListModeButton;
    private ButtonWidgetExtension mapAutoSaveButton;
    private ButtonWidgetExtension dashboardRailOverlayButton;
    private SliderWidgetExtension dashboardRailCullMaxSlider;

    private final List<ScrollEntry> scrollEntries = new ArrayList<>();
    private double scrollOffset;
    private int contentHeight;

    private int panelX;
    private int panelY;
    private int contentX;
    private int contentTop;
    private int contentBottom;
    private int contentWidth;

    private static final int RAIL_CULL_MAX_MIN = 1;
    private static final int RAIL_CULL_MAX_MAX = 64;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 6;
    private static final int SCROLL_STEP = 18;

    public JmeSettingsScreen(ScreenExtension parent) {
        this(new Screen(parent));
    }

    public JmeSettingsScreen(Screen parent) {
        this.parent = parent;
        this.useMph = JmeConfig.useMph();
        this.cameraTiltEnabled = JmeConfig.cameraTiltEnabled();
        this.cameraTiltStrength = JmeConfig.cameraTiltStrength();
        this.routeListMode = JmeConfig.dashboardRouteListMode();
        this.dashboardMapAutoSaveEnabled = JmeConfig.dashboardMapAutoSaveEnabled();
        this.dashboardRailOverlayMode = JmeConfig.dashboardRailOverlayMode();
        this.dashboardRailCullMaxPerCell = JmeConfig.dashboardRailOverlayCullMaxPerCell();
    }

    @Override
    protected void init2() {
        final int panelWidth = 300;
        final int panelHeight = 282;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentX = panelX + 12;
        contentWidth = panelWidth - 24;
        contentTop = panelY + 34;
        contentBottom = panelY + panelHeight - 34;

        scrollEntries.clear();

        int rowY = 0;

        speedUnitButton = new ButtonWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, getSpeedUnitLabel(), button -> {
            useMph = !useMph;
            button.setMessage(Text.cast(getSpeedUnitLabel()));
        });
        addChild(new ClickableWidget(speedUnitButton));
        scrollEntries.add(new ScrollEntry(speedUnitButton, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        cameraTiltButton = new ButtonWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, getCameraTiltLabel(), button -> {
            cameraTiltEnabled = !cameraTiltEnabled;
            button.setMessage(Text.cast(getCameraTiltLabel()));
            if (cameraTiltStrengthSlider != null) {
                cameraTiltStrengthSlider.setActiveMapped(cameraTiltEnabled);
            }
        });
        addChild(new ClickableWidget(cameraTiltButton));
        scrollEntries.add(new ScrollEntry(cameraTiltButton, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        cameraTiltStrengthSlider = new SliderWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, "") {
            @Override
            protected void updateMessage2() {
                setMessage2(Text.cast(TextHelper.literal(getCameraTiltStrengthLabel())));
            }

            @Override
            protected void applyValue2() {
                cameraTiltStrength = sliderToTiltStrength(getValueMapped());
                updateMessage2();
            }
        };
        cameraTiltStrengthSlider.setValueMapped(tiltStrengthToSlider(cameraTiltStrength));
        cameraTiltStrengthSlider.setMessage2(Text.cast(TextHelper.literal(getCameraTiltStrengthLabel())));
        cameraTiltStrengthSlider.setActiveMapped(cameraTiltEnabled);
        addChild(new ClickableWidget(cameraTiltStrengthSlider));
        scrollEntries.add(new ScrollEntry(cameraTiltStrengthSlider, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        routeListModeButton = new ButtonWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, getRouteListModeLabel(), button -> {
            routeListMode = routeListMode == JmeConfig.DashboardRouteListMode.FOLDERS ? JmeConfig.DashboardRouteListMode.FLAT : JmeConfig.DashboardRouteListMode.FOLDERS;
            button.setMessage(Text.cast(getRouteListModeLabel()));
        });
        addChild(new ClickableWidget(routeListModeButton));
        scrollEntries.add(new ScrollEntry(routeListModeButton, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        mapAutoSaveButton = new ButtonWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, getMapAutoSaveLabel(), button -> {
            dashboardMapAutoSaveEnabled = !dashboardMapAutoSaveEnabled;
            button.setMessage(Text.cast(getMapAutoSaveLabel()));
        });
        addChild(new ClickableWidget(mapAutoSaveButton));
        scrollEntries.add(new ScrollEntry(mapAutoSaveButton, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        dashboardRailOverlayButton = new ButtonWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, getDashboardRailOverlayLabel(), button -> {
            dashboardRailOverlayMode = nextOverlayMode(dashboardRailOverlayMode);
            button.setMessage(Text.cast(getDashboardRailOverlayLabel()));
            if (dashboardRailCullMaxSlider != null) {
                dashboardRailCullMaxSlider.setActiveMapped(dashboardRailOverlayMode == JmeConfig.DashboardRailOverlayMode.CULL);
            }
        });
        addChild(new ClickableWidget(dashboardRailOverlayButton));
        scrollEntries.add(new ScrollEntry(dashboardRailOverlayButton, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        dashboardRailCullMaxSlider = new SliderWidgetExtension(0, 0, contentWidth, ROW_HEIGHT, "") {
            @Override
            protected void updateMessage2() {
                setMessage2(Text.cast(TextHelper.literal(getDashboardRailCullLabel())));
            }

            @Override
            protected void applyValue2() {
                dashboardRailCullMaxPerCell = sliderToRailCullMax(getValueMapped());
                updateMessage2();
            }
        };
        dashboardRailCullMaxSlider.setValueMapped(railCullMaxToSlider(dashboardRailCullMaxPerCell));
        dashboardRailCullMaxSlider.setMessage2(Text.cast(TextHelper.literal(getDashboardRailCullLabel())));
        dashboardRailCullMaxSlider.setActiveMapped(dashboardRailOverlayMode == JmeConfig.DashboardRailOverlayMode.CULL);
        addChild(new ClickableWidget(dashboardRailCullMaxSlider));
        scrollEntries.add(new ScrollEntry(dashboardRailCullMaxSlider, rowY, ROW_HEIGHT));
        rowY += ROW_HEIGHT + ROW_GAP;

        contentHeight = Math.max(0, rowY - ROW_GAP);
        scrollOffset = clampScrollOffset(scrollOffset);
        updateScrollLayout();

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(panelX + panelWidth - 80, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Done"), button -> onDone());
        addChild(new ClickableWidget(doneButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + panelWidth - 156, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        final int panelWidth = 300;
        final int panelHeight = 282;
        final int panelX = this.panelX;
        final int panelY = this.panelY;

        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, panelWidth, panelHeight);

        graphicsHolder.drawText(TextHelper.literal("MAGIC Settings"), panelX + 12, panelY + 12, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        drawScrollBar(graphicsHolder, mouseX, mouseY);

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

    private org.mtr.mapping.holder.MutableText getSpeedUnitLabel() {
        return TextHelper.literal("Speed Unit: " + (useMph ? "MPH" : "KM/H"));
    }

    private org.mtr.mapping.holder.MutableText getCameraTiltLabel() {
        return TextHelper.literal("Camera Tilt: " + (cameraTiltEnabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText getRouteListModeLabel() {
        return TextHelper.literal("Route List Mode: " + (routeListMode == JmeConfig.DashboardRouteListMode.FOLDERS ? "FOLDERS" : "FLAT"));
    }

    private org.mtr.mapping.holder.MutableText getMapAutoSaveLabel() {
        return TextHelper.literal("Map Auto Save Areas: " + (dashboardMapAutoSaveEnabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText getDashboardRailOverlayLabel() {
        return TextHelper.literal("Dashboard Rail Overlay: " + dashboardRailOverlayMode.name());
    }

    private String getDashboardRailCullLabel() {
        return "Cull Max Rails/Cell: " + dashboardRailCullMaxPerCell;
    }

    private String getCameraTiltStrengthLabel() {
        return "Tilt Strength: " + (int) Math.round(cameraTiltStrength * 100D) + "%";
    }

    private static double sliderToTiltStrength(double sliderValue) {
        return Math.max(0D, Math.min(2D, sliderValue * 2D));
    }

    private static double tiltStrengthToSlider(double tiltStrength) {
        return Math.max(0D, Math.min(1D, tiltStrength / 2D));
    }

    private static JmeConfig.DashboardRailOverlayMode nextOverlayMode(JmeConfig.DashboardRailOverlayMode current) {
        if (current == null) {
            return JmeConfig.DashboardRailOverlayMode.ALL;
        }
        if (current == JmeConfig.DashboardRailOverlayMode.ALL) {
            return JmeConfig.DashboardRailOverlayMode.CULL;
        }
        if (current == JmeConfig.DashboardRailOverlayMode.CULL) {
            return JmeConfig.DashboardRailOverlayMode.OFF;
        }
        return JmeConfig.DashboardRailOverlayMode.ALL;
    }

    private static int sliderToRailCullMax(double sliderValue) {
        final double clamped = Math.max(0D, Math.min(1D, sliderValue));
        final int range = RAIL_CULL_MAX_MAX - RAIL_CULL_MAX_MIN;
        return RAIL_CULL_MAX_MIN + (int) Math.round(clamped * range);
    }

    private static double railCullMaxToSlider(int maxPerCell) {
        final int clamped = Math.max(RAIL_CULL_MAX_MIN, Math.min(RAIL_CULL_MAX_MAX, maxPerCell));
        final int range = RAIL_CULL_MAX_MAX - RAIL_CULL_MAX_MIN;
        if (range <= 0) {
            return 0D;
        }
        return (clamped - RAIL_CULL_MAX_MIN) / (double) range;
    }

    private void onDone() {
        final boolean tiltChanged = cameraTiltEnabled != JmeConfig.cameraTiltEnabled()
                || Math.abs(cameraTiltStrength - JmeConfig.cameraTiltStrength()) > 1.0e-6;

        JmeConfig.setUseMph(useMph);
        JmeConfig.setCameraTiltEnabled(cameraTiltEnabled);
        JmeConfig.setCameraTiltStrength(cameraTiltStrength);
        JmeConfig.setDashboardRouteListMode(routeListMode);
        JmeConfig.setDashboardMapAutoSaveEnabled(dashboardMapAutoSaveEnabled);
        JmeConfig.setDashboardRailOverlayMode(dashboardRailOverlayMode);
        JmeConfig.setDashboardRailOverlayCullMaxPerCell(dashboardRailCullMaxPerCell);
        JmeConfig.save();

        if (tiltChanged) {
            MagicRailTiltClient.clearSmoothingCache();
        }

        onClose2();
    }

    private void updateScrollLayout() {
        final int scroll = (int) Math.round(scrollOffset);
        final int viewportTop = contentTop;
        final int viewportBottom = contentBottom;

        for (final ScrollEntry entry : scrollEntries) {
            final int y = viewportTop + entry.baseY - scroll;
            entry.setPosition(contentX, y, contentWidth);
            final boolean visible = y + entry.height >= viewportTop && y <= viewportBottom;
            entry.setVisible(visible);
        }
    }

    private int getViewportHeight() {
        return Math.max(1, contentBottom - contentTop);
    }

    private boolean isInContentArea(double mouseX, double mouseY) {
        final int viewportTop = contentTop;
        final int viewportBottom = contentBottom;
        final int viewportLeft = panelX + 8;
        final int viewportRight = panelX + 300 - 8;
        return mouseX >= viewportLeft && mouseX <= viewportRight && mouseY >= viewportTop && mouseY <= viewportBottom;
    }

    private double clampScrollOffset(double next) {
        final int viewportHeight = getViewportHeight();
        final int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (!Double.isFinite(next)) {
            return 0D;
        }
        return Math.max(0D, Math.min(maxScroll, next));
    }

    private void drawScrollBar(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final int viewportHeight = getViewportHeight();
        if (contentHeight <= viewportHeight) {
            return;
        }

        final int trackX1 = panelX + 300 - 10;
        final int trackX2 = panelX + 300 - 6;
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

        final org.mtr.mapping.mapper.GuiDrawing guiDrawing = new org.mtr.mapping.mapper.GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(trackX1, trackY1, trackX2, trackY2, trackColor);
        guiDrawing.drawRectangle(trackX1, thumbY1, trackX2, thumbY2, thumbColor);
        guiDrawing.finishDrawingRectangle();
    }

    private static final class ScrollEntry {
        private final Object widget;
        private final int baseY;
        private final int height;

        private ScrollEntry(Object widget, int baseY, int height) {
            this.widget = widget;
            this.baseY = baseY;
            this.height = height;
        }

        private void setPosition(int x, int y, int width) {
            if (widget instanceof ButtonWidgetExtension) {
                final ButtonWidgetExtension button = (ButtonWidgetExtension) widget;
                button.setX2(x);
                button.setY2(y);
                button.setWidth2(width);
            } else if (widget instanceof SliderWidgetExtension) {
                final SliderWidgetExtension slider = (SliderWidgetExtension) widget;
                slider.setX2(x);
                slider.setY2(y);
                slider.setWidth2(width);
            }
        }

        private void setVisible(boolean visible) {
            if (widget instanceof ButtonWidgetExtension) {
                ((ButtonWidgetExtension) widget).setVisibleMapped(visible);
            } else if (widget instanceof SliderWidgetExtension) {
                ((SliderWidgetExtension) widget).setVisibleMapped(visible);
            }
        }
    }

    @Override
    public void onClose2() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
        }
    }
}
