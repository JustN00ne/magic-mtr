package org.justnoone.jme.client.screen;

import org.justnoone.jme.client.MagicRailTiltClient;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class JmeSettingsScreen extends ScreenExtension {

    private enum Tab {
        GENERAL("General"),
        DASHBOARD("Dashboard"),
        SYSTEM_MAP("System Map"),
        TRACK_COLORS("Track Colors");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int RAIL_CULL_MAX_MIN = 1;
    private static final int RAIL_CULL_MAX_MAX = 64;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 6;
    private static final int SCROLL_STEP = 18;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 6;

    private final Screen parent;

    private Tab currentTab = Tab.GENERAL;
    private final EnumMap<Tab, TabScrollState> tabScrollStates = new EnumMap<>(Tab.class);
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();

    private boolean useMph;
    private boolean inWorldSpeedTextEnabled;
    private boolean cameraTiltEnabled;
    private double cameraTiltStrength;
    private JmeConfig.DashboardRouteListMode routeListMode;
    private boolean dashboardMapAutoSaveEnabled;
    private boolean alternativePlatformsEnabled;
    private JmeConfig.DashboardRailOverlayMode dashboardRailOverlayMode;
    private int dashboardRailCullMaxPerCell;
    private boolean systemMapOverlayCacheEnabled;
    private boolean systemMapOverlayCachePersistEnabled;
    private JmeConfig.SystemMapLanguageDisplay systemMapLanguageDisplay;
    private JmeConfig.TrackColorMode trackColorMode;
    private JmeConfig.TrackColorStop[] trackColorCustomGradientStops;

    private ButtonWidgetExtension speedUnitButton;
    private ButtonWidgetExtension inWorldSpeedTextButton;
    private ButtonWidgetExtension cameraTiltButton;
    private SliderWidgetExtension cameraTiltStrengthSlider;

    private ButtonWidgetExtension routeListModeButton;
    private ButtonWidgetExtension mapAutoSaveButton;
    private ButtonWidgetExtension alternativePlatformsButton;
    private ButtonWidgetExtension dashboardRailOverlayButton;
    private SliderWidgetExtension dashboardRailCullMaxSlider;

    private ButtonWidgetExtension systemMapOverlayCacheButton;
    private ButtonWidgetExtension systemMapOverlayCachePersistButton;
    private ButtonWidgetExtension systemMapLanguageDisplayButton;

    private ButtonWidgetExtension trackColorModeButton;
    private ButtonWidgetExtension editCustomGradientButton;
    private ButtonWidgetExtension resetCustomGradientButton;

    private ButtonWidgetExtension doneButton;
    private ButtonWidgetExtension cancelButton;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int tabBarY;
    private int contentX;
    private int contentTop;
    private int contentBottom;
    private int contentWidth;

    public JmeSettingsScreen(ScreenExtension parent) {
        this(new Screen(parent));
    }

    public JmeSettingsScreen(Screen parent) {
        this.parent = parent;
        this.useMph = JmeConfig.useMph();
        this.inWorldSpeedTextEnabled = JmeConfig.inWorldSpeedTextEnabled();
        this.cameraTiltEnabled = JmeConfig.cameraTiltEnabled();
        this.cameraTiltStrength = JmeConfig.cameraTiltStrength();
        this.routeListMode = JmeConfig.dashboardRouteListMode();
        this.dashboardMapAutoSaveEnabled = JmeConfig.dashboardMapAutoSaveEnabled();
        this.alternativePlatformsEnabled = JmeConfig.alternativePlatformsEnabled();
        this.dashboardRailOverlayMode = JmeConfig.dashboardRailOverlayMode();
        this.dashboardRailCullMaxPerCell = JmeConfig.dashboardRailOverlayCullMaxPerCell();
        this.systemMapOverlayCacheEnabled = JmeConfig.systemMapOverlayCacheEnabled();
        this.systemMapOverlayCachePersistEnabled = JmeConfig.systemMapOverlayCachePersistEnabled();
        this.systemMapLanguageDisplay = JmeConfig.systemMapLanguageDisplay();
        this.trackColorMode = JmeConfig.trackColorMode();
        this.trackColorCustomGradientStops = jme$copyStops(JmeConfig.trackColorCustomGradientStops());
    }

    @Override
    protected void init2() {
        final int padding = 14;
        panelX = padding;
        panelY = padding;
        panelWidth = Math.max(1, width - padding * 2);
        panelHeight = Math.max(1, height - padding * 2);

        headerHeight = 62;
        tabBarY = panelY + 32;
        contentX = panelX + 16;
        contentWidth = Math.max(1, panelWidth - 32);
        contentTop = panelY + headerHeight + 12;
        contentBottom = panelY + panelHeight - 46;

        tabScrollStates.clear();
        for (final Tab tab : Tab.values()) {
            tabScrollStates.put(tab, new TabScrollState());
        }
        tabHitboxes.clear();
        buildTabHitboxes();

        buildGeneralTab();
        buildDashboardTab();
        buildSystemMapTab();
        buildTrackColorsTab();

        doneButton = new ButtonWidgetExtension(panelX + panelWidth - 88, panelY + panelHeight - 30, 74, 20, TextHelper.literal("Done"), button -> onDone());
        addChild(new ClickableWidget(doneButton));

        cancelButton = new ButtonWidgetExtension(panelX + panelWidth - 168, panelY + panelHeight - 30, 74, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));

        for (final Tab tab : Tab.values()) {
            final TabScrollState state = tabScrollStates.get(tab);
            if (state != null) {
                state.scrollOffset = clampScrollOffset(state, state.scrollOffset);
            }
        }
        updateDynamicStates();
        updateScrollLayout();
    }

    private void buildTabHitboxes() {
        final int tabStartX = panelX + 16;
        int x = tabStartX;

        for (final Tab tab : Tab.values()) {
            final int textW = GraphicsHolder.getTextWidth(tab.label);
            final int tabW = Math.max(86, Math.min(160, textW + 26));
            tabHitboxes.add(new TabHitbox(tab, x, tabBarY, tabW, TAB_HEIGHT));
            x += tabW + TAB_GAP;
        }
    }

    private void buildGeneralTab() {
        final TabScrollState state = tabScrollStates.get(Tab.GENERAL);
        if (state == null) {
            return;
        }
        int rowY = 0;

        speedUnitButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getSpeedUnitLabel(), button -> {
            useMph = !useMph;
            button.setMessage(Text.cast(getSpeedUnitLabel()));
        });
        addChild(new ClickableWidget(speedUnitButton));
        state.addEntry(speedUnitButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        inWorldSpeedTextButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getInWorldSpeedTextLabel(), button -> {
            inWorldSpeedTextEnabled = !inWorldSpeedTextEnabled;
            button.setMessage(Text.cast(getInWorldSpeedTextLabel()));
        });
        addChild(new ClickableWidget(inWorldSpeedTextButton));
        state.addEntry(inWorldSpeedTextButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        cameraTiltButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getCameraTiltLabel(), button -> {
            cameraTiltEnabled = !cameraTiltEnabled;
            button.setMessage(Text.cast(getCameraTiltLabel()));
            updateDynamicStates();
        });
        addChild(new ClickableWidget(cameraTiltButton));
        state.addEntry(cameraTiltButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        cameraTiltStrengthSlider = new SliderWidgetExtension(0, 0, 0, ROW_HEIGHT, "") {
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
        addChild(new ClickableWidget(cameraTiltStrengthSlider));
        state.addEntry(cameraTiltStrengthSlider, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        state.contentHeight = Math.max(0, rowY - ROW_GAP);
    }

    private void buildDashboardTab() {
        final TabScrollState state = tabScrollStates.get(Tab.DASHBOARD);
        if (state == null) {
            return;
        }
        int rowY = 0;

        routeListModeButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getRouteListModeLabel(), button -> {
            routeListMode = routeListMode == JmeConfig.DashboardRouteListMode.FOLDERS ? JmeConfig.DashboardRouteListMode.FLAT : JmeConfig.DashboardRouteListMode.FOLDERS;
            button.setMessage(Text.cast(getRouteListModeLabel()));
        });
        addChild(new ClickableWidget(routeListModeButton));
        state.addEntry(routeListModeButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        mapAutoSaveButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getMapAutoSaveLabel(), button -> {
            dashboardMapAutoSaveEnabled = !dashboardMapAutoSaveEnabled;
            button.setMessage(Text.cast(getMapAutoSaveLabel()));
        });
        addChild(new ClickableWidget(mapAutoSaveButton));
        state.addEntry(mapAutoSaveButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        alternativePlatformsButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getAlternativePlatformsLabel(), button -> {
            alternativePlatformsEnabled = !alternativePlatformsEnabled;
            button.setMessage(Text.cast(getAlternativePlatformsLabel()));
        });
        addChild(new ClickableWidget(alternativePlatformsButton));
        state.addEntry(alternativePlatformsButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        dashboardRailOverlayButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getDashboardRailOverlayLabel(), button -> {
            dashboardRailOverlayMode = nextOverlayMode(dashboardRailOverlayMode);
            button.setMessage(Text.cast(getDashboardRailOverlayLabel()));
            updateDynamicStates();
        });
        addChild(new ClickableWidget(dashboardRailOverlayButton));
        state.addEntry(dashboardRailOverlayButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        dashboardRailCullMaxSlider = new SliderWidgetExtension(0, 0, 0, ROW_HEIGHT, "") {
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
        addChild(new ClickableWidget(dashboardRailCullMaxSlider));
        state.addEntry(dashboardRailCullMaxSlider, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        state.contentHeight = Math.max(0, rowY - ROW_GAP);
    }

    private void buildSystemMapTab() {
        final TabScrollState state = tabScrollStates.get(Tab.SYSTEM_MAP);
        if (state == null) {
            return;
        }
        int rowY = 0;

        systemMapOverlayCacheButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getSystemMapOverlayCacheLabel(), button -> {
            systemMapOverlayCacheEnabled = !systemMapOverlayCacheEnabled;
            button.setMessage(Text.cast(getSystemMapOverlayCacheLabel()));
            updateDynamicStates();
        });
        addChild(new ClickableWidget(systemMapOverlayCacheButton));
        state.addEntry(systemMapOverlayCacheButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        systemMapOverlayCachePersistButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getSystemMapOverlayCachePersistLabel(), button -> {
            systemMapOverlayCachePersistEnabled = !systemMapOverlayCachePersistEnabled;
            button.setMessage(Text.cast(getSystemMapOverlayCachePersistLabel()));
        });
        addChild(new ClickableWidget(systemMapOverlayCachePersistButton));
        state.addEntry(systemMapOverlayCachePersistButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        systemMapLanguageDisplayButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getSystemMapLanguageDisplayLabel(), button -> {
            systemMapLanguageDisplay = nextLanguageDisplay(systemMapLanguageDisplay);
            button.setMessage(Text.cast(getSystemMapLanguageDisplayLabel()));
        });
        addChild(new ClickableWidget(systemMapLanguageDisplayButton));
        state.addEntry(systemMapLanguageDisplayButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        state.contentHeight = Math.max(0, rowY - ROW_GAP);
    }

    private void buildTrackColorsTab() {
        final TabScrollState state = tabScrollStates.get(Tab.TRACK_COLORS);
        if (state == null) {
            return;
        }
        int rowY = 0;

        trackColorModeButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getTrackColorModeLabel(), button -> {
            trackColorMode = nextTrackColorMode(trackColorMode);
            button.setMessage(Text.cast(getTrackColorModeLabel()));
            updateDynamicStates();
        });
        addChild(new ClickableWidget(trackColorModeButton));
        state.addEntry(trackColorModeButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        editCustomGradientButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, getEditCustomGradientLabel(), button -> openCustomGradientEditor());
        addChild(new ClickableWidget(editCustomGradientButton));
        state.addEntry(editCustomGradientButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        resetCustomGradientButton = new ButtonWidgetExtension(0, 0, 0, ROW_HEIGHT, TextHelper.literal("Reset Custom Gradient"), button -> {
            trackColorCustomGradientStops = jme$defaultCustomGradientStops();
            if (editCustomGradientButton != null) {
                editCustomGradientButton.setMessage2(Text.cast(getEditCustomGradientLabel()));
            }
        });
        addChild(new ClickableWidget(resetCustomGradientButton));
        state.addEntry(resetCustomGradientButton, rowY, ROW_HEIGHT);
        rowY += ROW_HEIGHT + ROW_GAP;

        state.contentHeight = Math.max(0, rowY - ROW_GAP);
    }

    private void openCustomGradientEditor() {
        if (client == null) {
            return;
        }
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new TrackColorGradientEditorScreen(this, trackColorCustomGradientStops, stops -> {
            trackColorCustomGradientStops = jme$copyStops(stops);
            updateDynamicStates();
            if (editCustomGradientButton != null) {
                editCustomGradientButton.setMessage2(Text.cast(getEditCustomGradientLabel()));
            }
        })));
    }

    private void updateDynamicStates() {
        if (cameraTiltStrengthSlider != null) {
            cameraTiltStrengthSlider.setActiveMapped(cameraTiltEnabled);
        }
        if (dashboardRailCullMaxSlider != null) {
            dashboardRailCullMaxSlider.setActiveMapped(dashboardRailOverlayMode == JmeConfig.DashboardRailOverlayMode.CULL);
        }
        if (systemMapOverlayCachePersistButton != null) {
            systemMapOverlayCachePersistButton.setActiveMapped(systemMapOverlayCacheEnabled);
        }
        final boolean custom = trackColorMode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT;
        if (editCustomGradientButton != null) {
            editCustomGradientButton.setActiveMapped(custom);
            editCustomGradientButton.setMessage2(Text.cast(getEditCustomGradientLabel()));
        }
        if (resetCustomGradientButton != null) {
            resetCustomGradientButton.setActiveMapped(custom);
        }
    }

    private void onDone() {
        final boolean tiltChanged = cameraTiltEnabled != JmeConfig.cameraTiltEnabled()
                || Math.abs(cameraTiltStrength - JmeConfig.cameraTiltStrength()) > 1.0e-6;

        JmeConfig.setUseMph(useMph);
        JmeConfig.setInWorldSpeedTextEnabled(inWorldSpeedTextEnabled);
        JmeConfig.setCameraTiltEnabled(cameraTiltEnabled);
        JmeConfig.setCameraTiltStrength(cameraTiltStrength);
        JmeConfig.setDashboardRouteListMode(routeListMode);
        JmeConfig.setDashboardMapAutoSaveEnabled(dashboardMapAutoSaveEnabled);
        JmeConfig.setAlternativePlatformsEnabled(alternativePlatformsEnabled);
        JmeConfig.setDashboardRailOverlayMode(dashboardRailOverlayMode);
        JmeConfig.setDashboardRailOverlayCullMaxPerCell(dashboardRailCullMaxPerCell);
        JmeConfig.setSystemMapOverlayCacheEnabled(systemMapOverlayCacheEnabled);
        JmeConfig.setSystemMapOverlayCachePersistEnabled(systemMapOverlayCachePersistEnabled);
        JmeConfig.setSystemMapLanguageDisplay(systemMapLanguageDisplay);
        JmeConfig.setTrackColorMode(trackColorMode);
        JmeConfig.setTrackColorCustomGradientStops(trackColorCustomGradientStops);
        JmeConfig.save();

        if (tiltChanged) {
            MagicRailTiltClient.clearSmoothingCache();
        }

        onClose2();
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        drawPanel(graphicsHolder);
        drawTabs(graphicsHolder, mouseX, mouseY);
        drawScrollBar(graphicsHolder, mouseX, mouseY);

        graphicsHolder.drawText(TextHelper.literal("MAGIC Settings"), panelX + 16, panelY + 12, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());

        super.render(graphicsHolder, mouseX, mouseY, delta);
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

    private void drawTabs(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        for (final TabHitbox hitbox : tabHitboxes) {
            final boolean active = hitbox.tab == currentTab;
            final boolean hovered = mouseX >= hitbox.x && mouseX <= hitbox.x + hitbox.width && mouseY >= hitbox.y && mouseY <= hitbox.y + hitbox.height;

            final int bg = active ? 0xCC1C1C20 : 0x88202024;
            final int bgHover = active ? 0xDD24242A : 0xAA2A2A30;
            final int border = 0xFF2B2B2B;
            final int fill = hovered ? bgHover : bg;

            guiDrawing.drawRectangle(hitbox.x, hitbox.y, hitbox.x + hitbox.width, hitbox.y + hitbox.height, fill);
            guiDrawing.drawRectangle(hitbox.x, hitbox.y, hitbox.x + hitbox.width, hitbox.y + 1, border);
            guiDrawing.drawRectangle(hitbox.x, hitbox.y, hitbox.x + 1, hitbox.y + hitbox.height, border);
            guiDrawing.drawRectangle(hitbox.x + hitbox.width - 1, hitbox.y, hitbox.x + hitbox.width, hitbox.y + hitbox.height, border);

            // Active tab looks "raised": omit bottom border so it blends into the header.
            if (!active) {
                guiDrawing.drawRectangle(hitbox.x, hitbox.y + hitbox.height - 1, hitbox.x + hitbox.width, hitbox.y + hitbox.height, border);
            }
        }

        guiDrawing.finishDrawingRectangle();

        // Draw labels after finishing the rectangle batch; mixing text rendering into GuiDrawing's
        // rectangle batch can invalidate the underlying BufferBuilder and spam "BufferBuilder not started".
        for (final TabHitbox hitbox : tabHitboxes) {
            final int textX = hitbox.x + (hitbox.width - GraphicsHolder.getTextWidth(hitbox.tab.label)) / 2;
            final int textY = hitbox.y + (hitbox.height - 8) / 2;
            graphicsHolder.drawText(TextHelper.literal(hitbox.tab.label), textX, textY, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
        }
    }

    @Override
    public boolean mouseClicked2(double mouseX, double mouseY, int button) {
        for (final TabHitbox hitbox : tabHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                if (hitbox.tab != currentTab) {
                    currentTab = hitbox.tab;
                    final TabScrollState state = tabScrollStates.get(currentTab);
                    if (state != null) {
                        state.scrollOffset = clampScrollOffset(state, state.scrollOffset);
                    }
                    updateDynamicStates();
                    updateScrollLayout();
                }
                return true;
            }
        }
        return super.mouseClicked2(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
        final TabScrollState state = tabScrollStates.get(currentTab);
        if (state != null && state.contentHeight > getViewportHeight() && isInContentArea(mouseX, mouseY)) {
            final double next = state.scrollOffset - amount * SCROLL_STEP;
            final double clamped = clampScrollOffset(state, next);
            if (Math.abs(clamped - state.scrollOffset) > 0.01D) {
                state.scrollOffset = clamped;
                updateScrollLayout();
                return true;
            }
        }
        return super.mouseScrolled2(mouseX, mouseY, amount);
    }

    private void updateScrollLayout() {
        final int viewportTop = contentTop;
        final int viewportBottom = contentBottom;

        for (final Tab tab : Tab.values()) {
            final TabScrollState state = tabScrollStates.get(tab);
            if (state == null) {
                continue;
            }

            final boolean activeTab = tab == currentTab;
            final int scroll = (int) Math.round(activeTab ? state.scrollOffset : 0D);

            for (final ScrollEntry entry : state.entries) {
                final int y = viewportTop + entry.baseY - scroll;
                entry.setPosition(contentX, y, contentWidth);
                final boolean visible = activeTab && y + entry.height >= viewportTop && y <= viewportBottom;
                entry.setVisible(visible);
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

    private double clampScrollOffset(TabScrollState state, double next) {
        final int viewportHeight = getViewportHeight();
        final int maxScroll = Math.max(0, state == null ? 0 : state.contentHeight - viewportHeight);
        if (!Double.isFinite(next)) {
            return 0D;
        }
        return Math.max(0D, Math.min(maxScroll, next));
    }

    private void drawScrollBar(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final TabScrollState state = tabScrollStates.get(currentTab);
        if (state == null) {
            return;
        }

        final int viewportHeight = getViewportHeight();
        if (state.contentHeight <= viewportHeight) {
            return;
        }

        final int trackX1 = panelX + panelWidth - 10;
        final int trackX2 = panelX + panelWidth - 6;
        final int trackY1 = contentTop;
        final int trackY2 = contentBottom;

        final int maxScroll = Math.max(1, state.contentHeight - viewportHeight);
        final double ratio = Math.max(0D, Math.min(1D, state.scrollOffset / maxScroll));
        final int thumbHeight = Math.max(18, (int) Math.round(viewportHeight * (viewportHeight / (double) state.contentHeight)));
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

    private org.mtr.mapping.holder.MutableText getSpeedUnitLabel() {
        return TextHelper.literal("Speed Unit: " + (useMph ? "MPH" : "KM/H"));
    }

    private org.mtr.mapping.holder.MutableText getInWorldSpeedTextLabel() {
        return TextHelper.literal("In-world Speed Text: " + (inWorldSpeedTextEnabled ? "ON" : "OFF"));
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

    private org.mtr.mapping.holder.MutableText getAlternativePlatformsLabel() {
        return TextHelper.literal("Alternative Platforms: " + (alternativePlatformsEnabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText getDashboardRailOverlayLabel() {
        return TextHelper.literal("Dashboard Rail Overlay: " + dashboardRailOverlayMode.name());
    }

    private org.mtr.mapping.holder.MutableText getSystemMapOverlayCacheLabel() {
        return TextHelper.literal("System Map Overlay Cache: " + (systemMapOverlayCacheEnabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText getSystemMapOverlayCachePersistLabel() {
        return TextHelper.literal("Persist Cache To Disk: " + (systemMapOverlayCachePersistEnabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText getSystemMapLanguageDisplayLabel() {
        return TextHelper.literal("System Map Language: " + (systemMapLanguageDisplay == null ? JmeConfig.SystemMapLanguageDisplay.NORMAL.name() : systemMapLanguageDisplay.name()));
    }

    private org.mtr.mapping.holder.MutableText getTrackColorModeLabel() {
        return TextHelper.literal("Track Colors: " + getTrackColorModeDisplayName(trackColorMode));
    }

    private org.mtr.mapping.holder.MutableText getEditCustomGradientLabel() {
        final int count = trackColorCustomGradientStops == null ? 0 : trackColorCustomGradientStops.length;
        return TextHelper.literal("Edit Custom Gradient (" + count + " stops)");
    }

    private String getDashboardRailCullLabel() {
        return "Cull Max Rails/Cell: " + dashboardRailCullMaxPerCell;
    }

    private String getCameraTiltStrengthLabel() {
        return "Tilt Strength: " + (int) Math.round(cameraTiltStrength * 100D) + "%";
    }

    private static String getTrackColorModeDisplayName(JmeConfig.TrackColorMode mode) {
        if (mode == null) {
            return "OpenRailwayMap (Default)";
        }
        if (mode == JmeConfig.TrackColorMode.MTR_DEFAULT) {
            return "MTR Default";
        }
        if (mode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT) {
            return "Custom Gradient";
        }
        return "OpenRailwayMap (Default)";
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

    private static JmeConfig.SystemMapLanguageDisplay nextLanguageDisplay(JmeConfig.SystemMapLanguageDisplay current) {
        if (current == null) {
            return JmeConfig.SystemMapLanguageDisplay.NORMAL;
        }
        if (current == JmeConfig.SystemMapLanguageDisplay.NORMAL) {
            return JmeConfig.SystemMapLanguageDisplay.CJK_ONLY;
        }
        if (current == JmeConfig.SystemMapLanguageDisplay.CJK_ONLY) {
            return JmeConfig.SystemMapLanguageDisplay.NON_CJK_ONLY;
        }
        return JmeConfig.SystemMapLanguageDisplay.NORMAL;
    }

    private static JmeConfig.TrackColorMode nextTrackColorMode(JmeConfig.TrackColorMode current) {
        if (current == null || current == JmeConfig.TrackColorMode.OPEN_RAILWAY_MAP) {
            return JmeConfig.TrackColorMode.MTR_DEFAULT;
        }
        if (current == JmeConfig.TrackColorMode.MTR_DEFAULT) {
            return JmeConfig.TrackColorMode.CUSTOM_GRADIENT;
        }
        return JmeConfig.TrackColorMode.OPEN_RAILWAY_MAP;
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

    private static JmeConfig.TrackColorStop[] jme$copyStops(JmeConfig.TrackColorStop[] stops) {
        if (stops == null || stops.length == 0) {
            return new JmeConfig.TrackColorStop[0];
        }
        final JmeConfig.TrackColorStop[] copied = new JmeConfig.TrackColorStop[stops.length];
        System.arraycopy(stops, 0, copied, 0, stops.length);
        return copied;
    }

    private static JmeConfig.TrackColorStop[] jme$defaultCustomGradientStops() {
        return new JmeConfig.TrackColorStop[]{
                new JmeConfig.TrackColorStop(5, 0xFF102A8A),
                new JmeConfig.TrackColorStop(100, 0xFF25C977),
                new JmeConfig.TrackColorStop(180, 0xFFD9E344),
                new JmeConfig.TrackColorStop(220, 0xFFFFE028),
                new JmeConfig.TrackColorStop(300, 0xFFEF3A26),
                new JmeConfig.TrackColorStop(400, 0xFFB42AE6)
        };
    }

    @Override
    public void onClose2() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
        }
    }

    private static final class TabHitbox {
        private final Tab tab;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private TabHitbox(Tab tab, int x, int y, int width, int height) {
            this.tab = tab;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static final class TabScrollState {
        private final List<ScrollEntry> entries = new ArrayList<>();
        private double scrollOffset;
        private int contentHeight;

        private void addEntry(Object widget, int baseY, int height) {
            entries.add(new ScrollEntry(widget, baseY, height));
        }
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
}
