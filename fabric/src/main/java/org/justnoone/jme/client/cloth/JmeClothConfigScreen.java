package org.justnoone.jme.client.cloth;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.mapping.mapper.TextHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cloth Config implementation (works across all supported Minecraft versions).
 *
 * <p>Note: Cloth Config is still an optional dependency; this class is loaded reflectively.
 */
public final class JmeClothConfigScreen {

    private JmeClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        final State state = new State();

        final ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(literal("MAGIC"))
                .setSavingRunnable(() -> {
                    final boolean tiltChanged = state.cameraTiltEnabled != JmeConfig.cameraTiltEnabled()
                            || Math.abs(state.cameraTiltStrength - JmeConfig.cameraTiltStrength()) > 1.0e-6;
                    state.apply();
                    if (tiltChanged) {
                        MagicRailTiltClient.clearSmoothingCache();
                    }
                });

        final ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        buildGeneralCategory(builder, entryBuilder, state);
        buildDashboardCategory(builder, entryBuilder, state);
        buildSystemMapCategory(builder, entryBuilder, state);
        buildTrackColorsCategory(builder, entryBuilder, state);
        buildBlueMapCategory(builder, entryBuilder, state);

        return builder.build();
    }

    private static void buildGeneralCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("General"));

        category.addEntry(entryBuilder.startBooleanToggle(literal("Use mph"), state.useMph)
                .setDefaultValue(false)
                .setTooltip(literal("Display speeds in miles per hour instead of km/h."))
                .setSaveConsumer(value -> state.useMph = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Camera Tilt"), state.cameraTiltEnabled)
                .setDefaultValue(true)
                .setTooltip(literal("Enable MAGIC's vehicle camera tilt smoothing."))
                .setSaveConsumer(value -> state.cameraTiltEnabled = value)
                .build());

        category.addEntry(entryBuilder.startDoubleField(literal("Camera Tilt Strength"), state.cameraTiltStrength)
                .setDefaultValue(1D)
                .setMin(0D)
                .setMax(2D)
                .setTooltip(literal("How strongly the camera tilt effect is applied (0.0 to 2.0)."))
                .setSaveConsumer(value -> state.cameraTiltStrength = value)
                .build());
    }

    private static void buildDashboardCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("Dashboard"));

        category.addEntry(entryBuilder.startEnumSelector(literal("Route List Layout"), JmeConfig.DashboardRouteListMode.class, state.dashboardRouteListMode)
                .setDefaultValue(JmeConfig.DashboardRouteListMode.FOLDERS)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setSaveConsumer(value -> state.dashboardRouteListMode = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Auto-save Dashboard Map"), state.dashboardMapAutoSaveEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.dashboardMapAutoSaveEnabled = value)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(literal("Rail Overlay Mode"), JmeConfig.DashboardRailOverlayMode.class, state.dashboardRailOverlayMode)
                .setDefaultValue(JmeConfig.DashboardRailOverlayMode.ALL)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setSaveConsumer(value -> state.dashboardRailOverlayMode = value)
                .build());

        category.addEntry(entryBuilder.startIntSlider(literal("Overlay Cull Max (Per Cell)"), state.dashboardRailCullMaxPerCell, 1, 64)
                .setDefaultValue(8)
                .setTooltip(literal("Only used when Rail Overlay Mode is CULL."))
                .setSaveConsumer(value -> state.dashboardRailCullMaxPerCell = value)
                .build());
    }

    private static void buildSystemMapCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("System Map"));

        category.addEntry(entryBuilder.startBooleanToggle(literal("Hide Player"), state.systemMapHidePlayer)
                .setDefaultValue(false)
                .setTooltip(literal("Hide the player/clients layer on the System Map (port 8888)."))
                .setSaveConsumer(value -> state.systemMapHidePlayer = value)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(literal("Language Display"), JmeConfig.SystemMapLanguageDisplay.class, state.systemMapLanguageDisplay)
                .setDefaultValue(JmeConfig.SystemMapLanguageDisplay.NORMAL)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setTooltip(literal("Filters route/station names on the System Map."))
                .setSaveConsumer(value -> state.systemMapLanguageDisplay = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Base Rails"), state.systemMapOverlayShowBaseRails)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowBaseRails = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Details"), state.systemMapOverlayShowDetails)
                .setDefaultValue(false)
                .setTooltip(literal("Show speed coloring, arrows, labels and other details on the overlay."))
                .setSaveConsumer(value -> state.systemMapOverlayShowDetails = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Signals"), state.systemMapOverlayShowSignals)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowSignals = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Vehicles"), state.systemMapOverlayShowVehicles)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowVehicles = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Respect Route Filters"), state.systemMapOverlayRespectRouteFilters)
                .setDefaultValue(false)
                .setTooltip(literal("Only render rails that belong to visible routes."))
                .setSaveConsumer(value -> state.systemMapOverlayRespectRouteFilters = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Overlay Cache"), state.systemMapOverlayCacheEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("Keep rails/vehicles visible on the System Map even after chunks unload."))
                .setSaveConsumer(value -> state.systemMapOverlayCacheEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Persist Overlay Cache"), state.systemMapOverlayCachePersistEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("Persist the System Map overlay cache to disk under config/MAGIC/map."))
                .setSaveConsumer(value -> state.systemMapOverlayCachePersistEnabled = value)
                .build());
    }

    private static void buildTrackColorsCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("Track Colors"));

        category.addEntry(entryBuilder.startEnumSelector(literal("Track Color Mode"), JmeConfig.TrackColorMode.class, state.trackColorMode)
                .setDefaultValue(JmeConfig.TrackColorMode.OPEN_RAILWAY_MAP)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setTooltip(
                        literal("Controls how MAGIC colors rails by speed (System Map and BlueMap speed layer)."),
                        literal("CUSTOM_GRADIENT can be edited using the list below.")
                )
                .setSaveConsumer(value -> state.trackColorMode = value)
                .build());

        category.addEntry(entryBuilder.startTextDescription(literal("Custom Gradient"))
                .setColor(0xA0A0A0)
                .build());

        category.addEntry(entryBuilder.startTextDescription(literal("Format: \"speed=#RRGGBB\" (example: \"200=#ff0000\"). Clear the list to reset to default."))
                .setColor(0xA0A0A0)
                .build());

        category.addEntry(entryBuilder.startStrList(literal("Custom Gradient Stops"), encodeGradientStops(state.trackColorCustomGradientStops))
                .setDefaultValue(encodeGradientStops(defaultCustomStops()))
                .setExpanded(false)
                .setTooltip(literal("Only used when Track Color Mode is CUSTOM_GRADIENT."))
                .setSaveConsumer(lines -> state.trackColorCustomGradientStops = decodeGradientStops(lines))
                .build());
    }

    private static void buildBlueMapCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("BlueMap"));

        category.addEntry(entryBuilder.startBooleanToggle(literal("Enable BlueMap Integration"), state.blueMapEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.blueMapEnabled = value)
                .build());

        category.addEntry(entryBuilder.startIntField(literal("Refresh Interval (Seconds)"), state.blueMapRefreshIntervalSeconds)
                .setDefaultValue(120)
                .setMin(1)
                .setMax(86400)
                .setSaveConsumer(value -> state.blueMapRefreshIntervalSeconds = value)
                .build());

        category.addEntry(entryBuilder.startIntField(literal("Initial Delay (Seconds)"), state.blueMapRefreshInitialDelaySeconds)
                .setDefaultValue(15)
                .setMin(0)
                .setMax(86400)
                .setSaveConsumer(value -> state.blueMapRefreshInitialDelaySeconds = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Base Layer Enabled"), state.blueMapBaseLayerEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.blueMapBaseLayerEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Speed Layer Enabled"), state.blueMapSpeedLayerEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.blueMapSpeedLayerEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Marker Sets Toggleable"), state.blueMapMarkerSetsToggleable)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.blueMapMarkerSetsToggleable = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Base Layer Default Hidden"), state.blueMapBaseLayerDefaultHidden)
                .setDefaultValue(false)
                .setSaveConsumer(value -> state.blueMapBaseLayerDefaultHidden = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Speed Layer Default Hidden"), state.blueMapSpeedLayerDefaultHidden)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.blueMapSpeedLayerDefaultHidden = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Markers Listed"), state.blueMapMarkersListed)
                .setDefaultValue(false)
                .setTooltip(literal("Whether BlueMap lists these marker sets in the UI by default."))
                .setSaveConsumer(value -> state.blueMapMarkersListed = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Depth Test"), state.blueMapDepthTestEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("If enabled, rails can be occluded by terrain/buildings (often undesirable)."))
                .setSaveConsumer(value -> state.blueMapDepthTestEnabled = value)
                .build());

        category.addEntry(entryBuilder.startIntSlider(literal("Base Line Width"), state.blueMapBaseLineWidth, 1, 12)
                .setDefaultValue(3)
                .setSaveConsumer(value -> state.blueMapBaseLineWidth = value)
                .build());

        category.addEntry(entryBuilder.startIntSlider(literal("Speed Line Width"), state.blueMapSpeedLineWidth, 1, 12)
                .setDefaultValue(2)
                .setSaveConsumer(value -> state.blueMapSpeedLineWidth = value)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("Base Color (Rails)"), state.blueMapBaseColorRgb)
                .setDefaultValue(0xFF0000)
                .setSaveConsumer(value -> state.blueMapBaseColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("Base Color (Platform Rails)"), state.blueMapBasePlatformColorRgb)
                .setDefaultValue(0x8B0000)
                .setSaveConsumer(value -> state.blueMapBasePlatformColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("Base Color (Siding Rails)"), state.blueMapBaseSidingColorRgb)
                .setDefaultValue(0xFFD500)
                .setSaveConsumer(value -> state.blueMapBaseSidingColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("Base Color (Turnback Rails)"), state.blueMapBaseTurnBackColorRgb)
                .setDefaultValue(0x00008B)
                .setSaveConsumer(value -> state.blueMapBaseTurnBackColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Platform Override (Speed Layer)"), state.blueMapPlatformRailsForceRedEnabled)
                .setDefaultValue(true)
                .setTooltip(literal("If enabled, platform rails are rendered using the platform color on the speed layer."))
                .setSaveConsumer(value -> state.blueMapPlatformRailsForceRedEnabled = value)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("Platform Color (Speed Layer)"), state.blueMapPlatformColorRgb)
                .setDefaultValue(0xFF0000)
                .setSaveConsumer(value -> state.blueMapPlatformColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("High Speed Highlight (Base Layer)"), state.blueMapHighSpeedRailsForceRedEnabled)
                .setDefaultValue(true)
                .setTooltip(literal("If enabled, rails above the threshold are highlighted on the base layer using the high speed color."))
                .setSaveConsumer(value -> state.blueMapHighSpeedRailsForceRedEnabled = value)
                .build());

        category.addEntry(entryBuilder.startIntField(literal("High Speed Threshold (km/h)"), state.blueMapHighSpeedThresholdKmh)
                .setDefaultValue(200)
                .setMin(1)
                .setMax(20000)
                .setSaveConsumer(value -> state.blueMapHighSpeedThresholdKmh = value)
                .build());

        category.addEntry(entryBuilder.startColorField(literal("High Speed Color"), state.blueMapHighSpeedColorRgb)
                .setDefaultValue(0xFF0000)
                .setSaveConsumer(value -> state.blueMapHighSpeedColorRgb = value & 0xFFFFFF)
                .build());

        category.addEntry(entryBuilder.startStrField(literal("Base Marker Set ID"), state.blueMapBaseMarkerSetId)
                .setDefaultValue("jme_rails")
                .setSaveConsumer(value -> state.blueMapBaseMarkerSetId = value)
                .build());

        category.addEntry(entryBuilder.startStrField(literal("Speed Marker Set ID"), state.blueMapSpeedMarkerSetId)
                .setDefaultValue("jme_rails_speeds")
                .setSaveConsumer(value -> state.blueMapSpeedMarkerSetId = value)
                .build());

        category.addEntry(entryBuilder.startStrField(literal("Base Marker Set Label"), state.blueMapBaseMarkerSetLabel)
                .setDefaultValue("MAGIC Rails")
                .setSaveConsumer(value -> state.blueMapBaseMarkerSetLabel = value)
                .build());

        category.addEntry(entryBuilder.startStrField(literal("Speed Marker Set Label"), state.blueMapSpeedMarkerSetLabel)
                .setDefaultValue("MAGIC Rails (Speed)")
                .setSaveConsumer(value -> state.blueMapSpeedMarkerSetLabel = value)
                .build());

        category.addEntry(entryBuilder.startIntField(literal("Base Marker Set Sorting"), state.blueMapBaseMarkerSetSorting)
                .setDefaultValue(110)
                .setSaveConsumer(value -> state.blueMapBaseMarkerSetSorting = value)
                .build());

        category.addEntry(entryBuilder.startIntField(literal("Speed Marker Set Sorting"), state.blueMapSpeedMarkerSetSorting)
                .setDefaultValue(111)
                .setSaveConsumer(value -> state.blueMapSpeedMarkerSetSorting = value)
                .build());

        category.addEntry(entryBuilder.startDoubleField(literal("Line Y Bias"), state.blueMapLineYBias)
                .setDefaultValue(0.05D)
                .setMin(-4D)
                .setMax(16D)
                .setTooltip(literal("Vertical offset applied to lines to avoid z-fighting."))
                .setSaveConsumer(value -> state.blueMapLineYBias = value)
                .build());

        category.addEntry(entryBuilder.startIntSlider(literal("Curve Target Points"), state.blueMapCurveSampleTargetPoints, 4, 256)
                .setDefaultValue(24)
                .setTooltip(literal("Higher values draw smoother curves but cost more CPU."))
                .setSaveConsumer(value -> state.blueMapCurveSampleTargetPoints = value)
                .build());

        category.addEntry(entryBuilder.startDoubleField(literal("Curve Interval Min"), state.blueMapCurveSampleIntervalMin)
                .setDefaultValue(0.4D)
                .setMin(0.01D)
                .setMax(50D)
                .setSaveConsumer(value -> state.blueMapCurveSampleIntervalMin = value)
                .build());

        category.addEntry(entryBuilder.startDoubleField(literal("Curve Interval Max"), state.blueMapCurveSampleIntervalMax)
                .setDefaultValue(1.25D)
                .setMin(0.01D)
                .setMax(50D)
                .setSaveConsumer(value -> state.blueMapCurveSampleIntervalMax = value)
                .build());
    }

    private static Text literal(String value) {
        return TextHelper.literal(value).data;
    }

    private static Text prettyEnumValue(Enum<?> value) {
        if (value == null) {
            return literal("Unknown");
        }
        final String raw = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        final String pretty = raw.isEmpty()
                ? value.name()
                : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        return literal(pretty);
    }

    private static JmeConfig.TrackColorStop[] defaultCustomStops() {
        // Must match JmeConfig's defaultTrackColorCustomGradientStops().
        return new JmeConfig.TrackColorStop[]{
                new JmeConfig.TrackColorStop(5, 0xFF102A8A),
                new JmeConfig.TrackColorStop(100, 0xFF25C977),
                new JmeConfig.TrackColorStop(180, 0xFFD9E344),
                new JmeConfig.TrackColorStop(220, 0xFFFFE028),
                new JmeConfig.TrackColorStop(300, 0xFFEF3A26),
                new JmeConfig.TrackColorStop(400, 0xFFB42AE6)
        };
    }

    private static List<String> encodeGradientStops(JmeConfig.TrackColorStop[] stops) {
        final List<String> out = new ArrayList<>();
        if (stops == null) {
            return out;
        }
        for (final JmeConfig.TrackColorStop stop : stops) {
            if (stop == null) {
                continue;
            }
            out.add(stop.speedKmh + "=#" + String.format(Locale.ROOT, "%06X", stop.colorArgb & 0xFFFFFF));
        }
        return out;
    }

    private static JmeConfig.TrackColorStop[] decodeGradientStops(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new JmeConfig.TrackColorStop[0];
        }

        final List<JmeConfig.TrackColorStop> out = new ArrayList<>();
        for (final String raw : lines) {
            if (raw == null) {
                continue;
            }

            final String normalized = raw.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            final String[] parts = normalized.split("[=,:]", 2);
            if (parts.length != 2) {
                continue;
            }

            final int speed;
            try {
                speed = Integer.parseInt(parts[0].trim());
            } catch (Exception ignored) {
                continue;
            }

            final int rgb = parseRgb(parts[1].trim());
            if (rgb < 0) {
                continue;
            }

            out.add(new JmeConfig.TrackColorStop(speed, 0xFF000000 | (rgb & 0xFFFFFF)));
        }

        if (out.isEmpty()) {
            return new JmeConfig.TrackColorStop[0];
        }

        return out.toArray(new JmeConfig.TrackColorStop[0]);
    }

    private static int parseRgb(String raw) {
        if (raw == null) {
            return -1;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return -1;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        // Allow AARRGGBB but ignore alpha.
        if (s.length() == 8) {
            s = s.substring(2);
        }
        if (s.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static final class State {
        private boolean useMph = JmeConfig.useMph();
        private boolean cameraTiltEnabled = JmeConfig.cameraTiltEnabled();
        private double cameraTiltStrength = JmeConfig.cameraTiltStrength();

        private JmeConfig.DashboardRouteListMode dashboardRouteListMode = JmeConfig.dashboardRouteListMode();
        private boolean dashboardMapAutoSaveEnabled = JmeConfig.dashboardMapAutoSaveEnabled();
        private JmeConfig.DashboardRailOverlayMode dashboardRailOverlayMode = JmeConfig.dashboardRailOverlayMode();
        private int dashboardRailCullMaxPerCell = JmeConfig.dashboardRailOverlayCullMaxPerCell();

        private boolean systemMapOverlayCacheEnabled = JmeConfig.systemMapOverlayCacheEnabled();
        private boolean systemMapOverlayCachePersistEnabled = JmeConfig.systemMapOverlayCachePersistEnabled();
        private JmeConfig.SystemMapLanguageDisplay systemMapLanguageDisplay = JmeConfig.systemMapLanguageDisplay();
        private boolean systemMapHidePlayer = JmeConfig.systemMapHidePlayer();
        private boolean systemMapOverlayShowBaseRails = JmeConfig.systemMapOverlayShowBaseRails();
        private boolean systemMapOverlayShowDetails = JmeConfig.systemMapOverlayShowDetails();
        private boolean systemMapOverlayShowSignals = JmeConfig.systemMapOverlayShowSignals();
        private boolean systemMapOverlayShowVehicles = JmeConfig.systemMapOverlayShowVehicles();
        private boolean systemMapOverlayRespectRouteFilters = JmeConfig.systemMapOverlayRespectRouteFilters();

        private JmeConfig.TrackColorMode trackColorMode = JmeConfig.trackColorMode();
        private JmeConfig.TrackColorStop[] trackColorCustomGradientStops = JmeConfig.trackColorCustomGradientStops();

        private boolean blueMapEnabled = JmeConfig.blueMapEnabled();
        private int blueMapRefreshIntervalSeconds = JmeConfig.blueMapRefreshIntervalSeconds();
        private int blueMapRefreshInitialDelaySeconds = JmeConfig.blueMapRefreshInitialDelaySeconds();
        private boolean blueMapBaseLayerEnabled = JmeConfig.blueMapBaseLayerEnabled();
        private boolean blueMapSpeedLayerEnabled = JmeConfig.blueMapSpeedLayerEnabled();
        private boolean blueMapMarkerSetsToggleable = JmeConfig.blueMapMarkerSetsToggleable();
        private boolean blueMapBaseLayerDefaultHidden = JmeConfig.blueMapBaseLayerDefaultHidden();
        private boolean blueMapSpeedLayerDefaultHidden = JmeConfig.blueMapSpeedLayerDefaultHidden();
        private boolean blueMapMarkersListed = JmeConfig.blueMapMarkersListed();
        private boolean blueMapDepthTestEnabled = JmeConfig.blueMapDepthTestEnabled();
        private int blueMapBaseLineWidth = JmeConfig.blueMapBaseLineWidth();
        private int blueMapSpeedLineWidth = JmeConfig.blueMapSpeedLineWidth();
        private int blueMapBaseColorRgb = JmeConfig.blueMapBaseColorRgb();
        private int blueMapBasePlatformColorRgb = JmeConfig.blueMapBasePlatformColorRgb();
        private int blueMapBaseSidingColorRgb = JmeConfig.blueMapBaseSidingColorRgb();
        private int blueMapBaseTurnBackColorRgb = JmeConfig.blueMapBaseTurnBackColorRgb();
        private int blueMapPlatformColorRgb = JmeConfig.blueMapPlatformColorRgb();
        private boolean blueMapPlatformRailsForceRedEnabled = JmeConfig.blueMapPlatformRailsForceRedEnabled();
        private int blueMapHighSpeedThresholdKmh = JmeConfig.blueMapHighSpeedThresholdKmh();
        private int blueMapHighSpeedColorRgb = JmeConfig.blueMapHighSpeedColorRgb();
        private boolean blueMapHighSpeedRailsForceRedEnabled = JmeConfig.blueMapHighSpeedRailsForceRedEnabled();
        private String blueMapBaseMarkerSetId = JmeConfig.blueMapBaseMarkerSetId();
        private String blueMapSpeedMarkerSetId = JmeConfig.blueMapSpeedMarkerSetId();
        private String blueMapBaseMarkerSetLabel = JmeConfig.blueMapBaseMarkerSetLabel();
        private String blueMapSpeedMarkerSetLabel = JmeConfig.blueMapSpeedMarkerSetLabel();
        private int blueMapBaseMarkerSetSorting = JmeConfig.blueMapBaseMarkerSetSorting();
        private int blueMapSpeedMarkerSetSorting = JmeConfig.blueMapSpeedMarkerSetSorting();
        private double blueMapLineYBias = JmeConfig.blueMapLineYBias();
        private int blueMapCurveSampleTargetPoints = JmeConfig.blueMapCurveSampleTargetPoints();
        private double blueMapCurveSampleIntervalMin = JmeConfig.blueMapCurveSampleIntervalMin();
        private double blueMapCurveSampleIntervalMax = JmeConfig.blueMapCurveSampleIntervalMax();

        private void apply() {
            JmeConfig.setUseMph(useMph);
            JmeConfig.setCameraTiltEnabled(cameraTiltEnabled);
            JmeConfig.setCameraTiltStrength(cameraTiltStrength);

            JmeConfig.setDashboardRouteListMode(dashboardRouteListMode);
            JmeConfig.setDashboardMapAutoSaveEnabled(dashboardMapAutoSaveEnabled);
            JmeConfig.setDashboardRailOverlayMode(dashboardRailOverlayMode);
            JmeConfig.setDashboardRailOverlayCullMaxPerCell(dashboardRailCullMaxPerCell);

            JmeConfig.setSystemMapOverlayCacheEnabled(systemMapOverlayCacheEnabled);
            JmeConfig.setSystemMapOverlayCachePersistEnabled(systemMapOverlayCachePersistEnabled);
            JmeConfig.setSystemMapLanguageDisplay(systemMapLanguageDisplay);
            JmeConfig.setSystemMapHidePlayer(systemMapHidePlayer);
            JmeConfig.setSystemMapOverlayShowBaseRails(systemMapOverlayShowBaseRails);
            JmeConfig.setSystemMapOverlayShowDetails(systemMapOverlayShowDetails);
            JmeConfig.setSystemMapOverlayShowSignals(systemMapOverlayShowSignals);
            JmeConfig.setSystemMapOverlayShowVehicles(systemMapOverlayShowVehicles);
            JmeConfig.setSystemMapOverlayRespectRouteFilters(systemMapOverlayRespectRouteFilters);

            JmeConfig.setTrackColorMode(trackColorMode);
            JmeConfig.setTrackColorCustomGradientStops(trackColorCustomGradientStops);

            JmeConfig.setBlueMapEnabled(blueMapEnabled);
            JmeConfig.setBlueMapRefreshIntervalSeconds(blueMapRefreshIntervalSeconds);
            JmeConfig.setBlueMapRefreshInitialDelaySeconds(blueMapRefreshInitialDelaySeconds);
            JmeConfig.setBlueMapBaseLayerEnabled(blueMapBaseLayerEnabled);
            JmeConfig.setBlueMapSpeedLayerEnabled(blueMapSpeedLayerEnabled);
            JmeConfig.setBlueMapMarkerSetsToggleable(blueMapMarkerSetsToggleable);
            JmeConfig.setBlueMapBaseLayerDefaultHidden(blueMapBaseLayerDefaultHidden);
            JmeConfig.setBlueMapSpeedLayerDefaultHidden(blueMapSpeedLayerDefaultHidden);
            JmeConfig.setBlueMapMarkersListed(blueMapMarkersListed);
            JmeConfig.setBlueMapDepthTestEnabled(blueMapDepthTestEnabled);
            JmeConfig.setBlueMapBaseLineWidth(blueMapBaseLineWidth);
            JmeConfig.setBlueMapSpeedLineWidth(blueMapSpeedLineWidth);
            JmeConfig.setBlueMapBaseColorRgb(blueMapBaseColorRgb);
            JmeConfig.setBlueMapBasePlatformColorRgb(blueMapBasePlatformColorRgb);
            JmeConfig.setBlueMapBaseSidingColorRgb(blueMapBaseSidingColorRgb);
            JmeConfig.setBlueMapBaseTurnBackColorRgb(blueMapBaseTurnBackColorRgb);
            JmeConfig.setBlueMapPlatformColorRgb(blueMapPlatformColorRgb);
            JmeConfig.setBlueMapPlatformRailsForceRedEnabled(blueMapPlatformRailsForceRedEnabled);
            JmeConfig.setBlueMapHighSpeedThresholdKmh(blueMapHighSpeedThresholdKmh);
            JmeConfig.setBlueMapHighSpeedColorRgb(blueMapHighSpeedColorRgb);
            JmeConfig.setBlueMapHighSpeedRailsForceRedEnabled(blueMapHighSpeedRailsForceRedEnabled);
            JmeConfig.setBlueMapBaseMarkerSetId(blueMapBaseMarkerSetId);
            JmeConfig.setBlueMapSpeedMarkerSetId(blueMapSpeedMarkerSetId);
            JmeConfig.setBlueMapBaseMarkerSetLabel(blueMapBaseMarkerSetLabel);
            JmeConfig.setBlueMapSpeedMarkerSetLabel(blueMapSpeedMarkerSetLabel);
            JmeConfig.setBlueMapBaseMarkerSetSorting(blueMapBaseMarkerSetSorting);
            JmeConfig.setBlueMapSpeedMarkerSetSorting(blueMapSpeedMarkerSetSorting);
            JmeConfig.setBlueMapLineYBias(blueMapLineYBias);
            JmeConfig.setBlueMapCurveSampleTargetPoints(blueMapCurveSampleTargetPoints);
            JmeConfig.setBlueMapCurveSampleIntervalMin(blueMapCurveSampleIntervalMin);
            JmeConfig.setBlueMapCurveSampleIntervalMax(blueMapCurveSampleIntervalMax);

            JmeConfig.save();
        }
    }
}
