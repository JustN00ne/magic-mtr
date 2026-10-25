package org.justnoone.jme.client.yacl;

import dev.isxander.yacl.api.ButtonOption;
import dev.isxander.yacl.api.ConfigCategory;
import dev.isxander.yacl.api.Option;
import dev.isxander.yacl.api.OptionGroup;
import dev.isxander.yacl.api.YetAnotherConfigLib;
import dev.isxander.yacl.gui.controllers.ActionController;
import dev.isxander.yacl.gui.controllers.ColorController;
import dev.isxander.yacl.gui.controllers.TickBoxController;
import dev.isxander.yacl.gui.controllers.cycling.EnumController;
import dev.isxander.yacl.gui.controllers.slider.DoubleSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import dev.isxander.yacl.gui.controllers.string.StringController;
import dev.isxander.yacl.gui.controllers.string.number.DoubleFieldController;
import dev.isxander.yacl.gui.controllers.string.number.IntegerFieldController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.justnoone.jme.client.screen.TrackColorGradientEditorScreen;
import org.justnoone.jme.config.JmeConfig;

import java.awt.Color;

public final class JmeYaclConfigScreen {

    // YACL v2 implementation (Minecraft 1.19.2).
    private JmeYaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        final State state = new State();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("MAGIC"))
                .category(buildGeneralCategory(state))
                .category(buildDashboardCategory(state))
                .category(buildSystemMapCategory(state))
                .category(buildTrackColorsCategory(state))
                .category(buildBlueMapCategory(state))
                .save(() -> {
                    final boolean tiltChanged = state.cameraTiltEnabled != JmeConfig.cameraTiltEnabled()
                            || Math.abs(state.cameraTiltStrength - JmeConfig.cameraTiltStrength()) > 1.0e-6;
                    state.apply();
                    if (tiltChanged) {
                        MagicRailTiltClient.clearSmoothingCache();
                    }
                })
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory buildGeneralCategory(State state) {
        final Option<Boolean> useMph = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Use mph"))
                .tooltip(Text.literal("Display speeds in miles per hour instead of km/h."))
                .binding(false, () -> state.useMph, value -> state.useMph = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> inWorldSpeedText = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("In-world Speed Text"))
                .tooltip(Text.literal("Render speed labels on rails in-world (can reduce FPS on large networks)."))
                .binding(false, () -> state.inWorldSpeedTextEnabled, value -> state.inWorldSpeedTextEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> cameraTiltEnabled = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Camera Tilt"))
                .tooltip(Text.literal("Enable MAGIC's vehicle camera tilt smoothing."))
                .binding(true, () -> state.cameraTiltEnabled, value -> state.cameraTiltEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Double> cameraTiltStrength = Option.<Double>createBuilder(Double.class)
                .name(Text.literal("Camera Tilt Strength"))
                .tooltip(Text.literal("How strongly the camera tilt effect is applied (0.0 to 2.0)."))
                .binding(1D, () -> state.cameraTiltStrength, value -> state.cameraTiltStrength = value)
                .controller(option -> new DoubleSliderController(option, 0D, 2D, 0.05D))
                .build();

        return ConfigCategory.createBuilder()
                .name(Text.literal("General"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Speed Units"))
                        .option(useMph)
                        .option(inWorldSpeedText)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Camera"))
                        .option(cameraTiltEnabled)
                        .option(cameraTiltStrength)
                        .build())
                .build();
    }

    private static ConfigCategory buildDashboardCategory(State state) {
        final Option<JmeConfig.DashboardRouteListMode> routeListMode = Option.<JmeConfig.DashboardRouteListMode>createBuilder(JmeConfig.DashboardRouteListMode.class)
                .name(Text.literal("Route List Layout"))
                .binding(JmeConfig.DashboardRouteListMode.FOLDERS, () -> state.dashboardRouteListMode, value -> state.dashboardRouteListMode = value)
                .controller(EnumController::new)
                .build();

        final Option<Boolean> mapAutoSave = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Auto-save Dashboard Map"))
                .binding(true, () -> state.dashboardMapAutoSaveEnabled, value -> state.dashboardMapAutoSaveEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> alternativePlatforms = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Alternative Platforms"))
                .tooltip(Text.literal("Dynamic platform rerouting (can be CPU-heavy on large networks)."))
                .binding(true, () -> state.alternativePlatformsEnabled, value -> state.alternativePlatformsEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<JmeConfig.DashboardRailOverlayMode> railOverlayMode = Option.<JmeConfig.DashboardRailOverlayMode>createBuilder(JmeConfig.DashboardRailOverlayMode.class)
                .name(Text.literal("Rail Overlay Mode"))
                .binding(JmeConfig.DashboardRailOverlayMode.ALL, () -> state.dashboardRailOverlayMode, value -> state.dashboardRailOverlayMode = value)
                .controller(EnumController::new)
                .build();

        final Option<Integer> railCullMax = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Overlay Cull Max (Per Cell)"))
                .tooltip(Text.literal("Only used when Rail Overlay Mode is CULL."))
                .binding(8, () -> state.dashboardRailCullMaxPerCell, value -> state.dashboardRailCullMaxPerCell = value)
                .controller(option -> new IntegerSliderController(option, 1, 64, 1))
                .build();

        railCullMax.setAvailable(state.dashboardRailOverlayMode == JmeConfig.DashboardRailOverlayMode.CULL);
        railOverlayMode.addListener((ignored, value) -> railCullMax.setAvailable(value == JmeConfig.DashboardRailOverlayMode.CULL));

        return ConfigCategory.createBuilder()
                .name(Text.literal("Dashboard"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Routes"))
                        .option(routeListMode)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Routing"))
                        .option(alternativePlatforms)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Map"))
                        .option(mapAutoSave)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Rail Overlay"))
                        .option(railOverlayMode)
                        .option(railCullMax)
                        .build())
                .build();
    }

    private static ConfigCategory buildSystemMapCategory(State state) {
        final Option<Boolean> hidePlayer = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Hide Player"))
                .tooltip(Text.literal("Hide the player/clients layer on the System Map (port 8888)."))
                .binding(false, () -> state.systemMapHidePlayer, value -> state.systemMapHidePlayer = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> cacheEnabled = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Overlay Cache"))
                .tooltip(Text.literal("Keep rails/vehicles visible on the System Map even after chunks unload."))
                .binding(false, () -> state.systemMapOverlayCacheEnabled, value -> state.systemMapOverlayCacheEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> cachePersist = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Persist Overlay Cache"))
                .tooltip(Text.literal("Persist the System Map overlay cache to disk under config/MAGIC/map."))
                .binding(false, () -> state.systemMapOverlayCachePersistEnabled, value -> state.systemMapOverlayCachePersistEnabled = value)
                .controller(TickBoxController::new)
                .build();

        cachePersist.setAvailable(state.systemMapOverlayCacheEnabled);
        cacheEnabled.addListener((ignored, value) -> cachePersist.setAvailable(value));

        final Option<JmeConfig.SystemMapLanguageDisplay> languageDisplay = Option.<JmeConfig.SystemMapLanguageDisplay>createBuilder(JmeConfig.SystemMapLanguageDisplay.class)
                .name(Text.literal("Language Display"))
                .tooltip(Text.literal("Filters route/station names on the System Map."))
                .binding(JmeConfig.SystemMapLanguageDisplay.NORMAL, () -> state.systemMapLanguageDisplay, value -> state.systemMapLanguageDisplay = value)
                .controller(EnumController::new)
                .build();

        final Option<Boolean> showBaseRails = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Show Base Rails"))
                .binding(true, () -> state.systemMapOverlayShowBaseRails, value -> state.systemMapOverlayShowBaseRails = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> showDetails = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Show Details"))
                .tooltip(Text.literal("Show speed coloring, arrows, labels and other details on the overlay."))
                .binding(false, () -> state.systemMapOverlayShowDetails, value -> state.systemMapOverlayShowDetails = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> showSignals = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Show Signals"))
                .binding(true, () -> state.systemMapOverlayShowSignals, value -> state.systemMapOverlayShowSignals = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> showVehicles = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Show Vehicles"))
                .binding(true, () -> state.systemMapOverlayShowVehicles, value -> state.systemMapOverlayShowVehicles = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> respectRouteFilters = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Respect Route Filters"))
                .tooltip(Text.literal("Only render rails that belong to visible routes."))
                .binding(false, () -> state.systemMapOverlayRespectRouteFilters, value -> state.systemMapOverlayRespectRouteFilters = value)
                .controller(TickBoxController::new)
                .build();

        return ConfigCategory.createBuilder()
                .name(Text.literal("System Map"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Privacy"))
                        .option(hidePlayer)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Overlay"))
                        .option(showBaseRails)
                        .option(showDetails)
                        .option(showSignals)
                        .option(showVehicles)
                        .option(respectRouteFilters)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Cache"))
                        .option(cacheEnabled)
                        .option(cachePersist)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Text"))
                        .option(languageDisplay)
                        .build())
                .build();
    }

    private static ConfigCategory buildTrackColorsCategory(State state) {
        final Option<JmeConfig.TrackColorMode> mode = Option.<JmeConfig.TrackColorMode>createBuilder(JmeConfig.TrackColorMode.class)
                .name(Text.literal("Track Color Mode"))
                .tooltip(
                        Text.literal("Controls how MAGIC colors rails by speed (System Map and BlueMap speed layer)."),
                        Text.literal("CUSTOM_GRADIENT can be edited using the buttons below.")
                )
                .binding(JmeConfig.TrackColorMode.OPEN_RAILWAY_MAP, () -> state.trackColorMode, value -> state.trackColorMode = value)
                .controller(EnumController::new)
                .build();

        final boolean customSelected = state.trackColorMode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT;

        final ButtonOption editCustomGradient = ButtonOption.createBuilder()
                .name(Text.literal("Custom Gradient Editor"))
                .tooltip(Text.literal("Edit the speed-to-color gradient used when Track Color Mode is CUSTOM_GRADIENT."))
                .available(customSelected)
                .controller(option -> new ActionController(option, Text.literal("Open")))
                .action(yaclScreen -> MinecraftClient.getInstance().setScreen(new TrackColorGradientEditorScreen(
                        yaclScreen,
                        state.trackColorCustomGradientStops,
                        stops -> state.trackColorCustomGradientStops = stops
                )))
                .build();

        final ButtonOption resetCustomGradient = ButtonOption.createBuilder()
                .name(Text.literal("Reset Custom Gradient"))
                .tooltip(Text.literal("Restores the default OpenRailwayMap-like gradient stops."))
                .available(customSelected)
                .controller(option -> new ActionController(option, Text.literal("Reset")))
                .action(ignored -> state.trackColorCustomGradientStops = new JmeConfig.TrackColorStop[0])
                .build();

        mode.addListener((ignored, value) -> {
            final boolean custom = value == JmeConfig.TrackColorMode.CUSTOM_GRADIENT;
            editCustomGradient.setAvailable(custom);
            resetCustomGradient.setAvailable(custom);
        });

        return ConfigCategory.createBuilder()
                .name(Text.literal("Track Colors"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Mode"))
                        .option(mode)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Custom Gradient"))
                        .option(editCustomGradient)
                        .option(resetCustomGradient)
                        .build())
                .build();
    }

    private static ConfigCategory buildBlueMapCategory(State state) {
        final Option<Boolean> enabled = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Enable BlueMap Integration"))
                .binding(true, () -> state.blueMapEnabled, value -> state.blueMapEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Integer> refreshInterval = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Refresh Interval (Seconds)"))
                .binding(120, () -> state.blueMapRefreshIntervalSeconds, value -> state.blueMapRefreshIntervalSeconds = value)
                .controller(option -> new IntegerFieldController(option, 1, 86400))
                .build();

        final Option<Integer> initialDelay = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Initial Delay (Seconds)"))
                .binding(15, () -> state.blueMapRefreshInitialDelaySeconds, value -> state.blueMapRefreshInitialDelaySeconds = value)
                .controller(option -> new IntegerFieldController(option, 0, 86400))
                .build();

        final Option<Boolean> baseLayerEnabled = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Base Layer Enabled"))
                .binding(true, () -> state.blueMapBaseLayerEnabled, value -> state.blueMapBaseLayerEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> speedLayerEnabled = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Speed Layer Enabled"))
                .binding(true, () -> state.blueMapSpeedLayerEnabled, value -> state.blueMapSpeedLayerEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> toggleable = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Marker Sets Toggleable"))
                .binding(true, () -> state.blueMapMarkerSetsToggleable, value -> state.blueMapMarkerSetsToggleable = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> baseDefaultHidden = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Base Layer Default Hidden"))
                .binding(false, () -> state.blueMapBaseLayerDefaultHidden, value -> state.blueMapBaseLayerDefaultHidden = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> speedDefaultHidden = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Speed Layer Default Hidden"))
                .binding(true, () -> state.blueMapSpeedLayerDefaultHidden, value -> state.blueMapSpeedLayerDefaultHidden = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> markersListed = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Markers Listed"))
                .tooltip(Text.literal("Whether BlueMap lists these marker sets in the UI by default."))
                .binding(false, () -> state.blueMapMarkersListed, value -> state.blueMapMarkersListed = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Boolean> depthTest = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Depth Test"))
                .tooltip(Text.literal("If enabled, rails can be occluded by terrain/buildings (often undesirable)."))
                .binding(false, () -> state.blueMapDepthTestEnabled, value -> state.blueMapDepthTestEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Integer> baseLineWidth = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Base Line Width"))
                .binding(3, () -> state.blueMapBaseLineWidth, value -> state.blueMapBaseLineWidth = value)
                .controller(option -> new IntegerSliderController(option, 1, 12, 1))
                .build();

        final Option<Integer> speedLineWidth = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Speed Line Width"))
                .binding(2, () -> state.blueMapSpeedLineWidth, value -> state.blueMapSpeedLineWidth = value)
                .controller(option -> new IntegerSliderController(option, 1, 12, 1))
                .build();

        final Option<Color> baseColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("Base Color (Rails)"))
                .binding(new Color(0xFF0000), () -> state.blueMapBaseColor, value -> state.blueMapBaseColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<Color> basePlatformColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("Base Color (Platform Rails)"))
                .binding(new Color(0x8B0000), () -> state.blueMapBasePlatformColor, value -> state.blueMapBasePlatformColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<Color> baseSidingColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("Base Color (Siding Rails)"))
                .binding(new Color(0xFFD500), () -> state.blueMapBaseSidingColor, value -> state.blueMapBaseSidingColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<Color> baseTurnBackColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("Base Color (Turnback Rails)"))
                .binding(new Color(0x00008B), () -> state.blueMapBaseTurnBackColor, value -> state.blueMapBaseTurnBackColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<Boolean> platformOverride = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("Platform Override (Speed Layer)"))
                .tooltip(Text.literal("If enabled, platform rails are rendered using the platform color on the speed layer."))
                .binding(true, () -> state.blueMapPlatformRailsForceRedEnabled, value -> state.blueMapPlatformRailsForceRedEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Color> platformColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("Platform Color (Speed Layer)"))
                .binding(new Color(0xFF0000), () -> state.blueMapPlatformColor, value -> state.blueMapPlatformColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<Boolean> highSpeedOverride = Option.<Boolean>createBuilder(Boolean.class)
                .name(Text.literal("High Speed Highlight (Base Layer)"))
                .tooltip(Text.literal("If enabled, rails above the threshold are highlighted on the base layer using the high speed color."))
                .binding(true, () -> state.blueMapHighSpeedRailsForceRedEnabled, value -> state.blueMapHighSpeedRailsForceRedEnabled = value)
                .controller(TickBoxController::new)
                .build();

        final Option<Integer> highSpeedThreshold = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("High Speed Threshold (km/h)"))
                .binding(200, () -> state.blueMapHighSpeedThresholdKmh, value -> state.blueMapHighSpeedThresholdKmh = value)
                .controller(option -> new IntegerFieldController(option, 1, 20000))
                .build();

        final Option<Color> highSpeedColor = Option.<Color>createBuilder(Color.class)
                .name(Text.literal("High Speed Color"))
                .binding(new Color(0xFF0000), () -> state.blueMapHighSpeedColor, value -> state.blueMapHighSpeedColor = value)
                .controller(option -> new ColorController(option, false))
                .build();

        final Option<String> baseMarkerSetId = Option.<String>createBuilder(String.class)
                .name(Text.literal("Base Marker Set ID"))
                .binding("jme_rails", () -> state.blueMapBaseMarkerSetId, value -> state.blueMapBaseMarkerSetId = value)
                .controller(StringController::new)
                .build();

        final Option<String> speedMarkerSetId = Option.<String>createBuilder(String.class)
                .name(Text.literal("Speed Marker Set ID"))
                .binding("jme_rails_speeds", () -> state.blueMapSpeedMarkerSetId, value -> state.blueMapSpeedMarkerSetId = value)
                .controller(StringController::new)
                .build();

        final Option<String> baseMarkerSetLabel = Option.<String>createBuilder(String.class)
                .name(Text.literal("Base Marker Set Label"))
                .binding("MAGIC Rails", () -> state.blueMapBaseMarkerSetLabel, value -> state.blueMapBaseMarkerSetLabel = value)
                .controller(StringController::new)
                .build();

        final Option<String> speedMarkerSetLabel = Option.<String>createBuilder(String.class)
                .name(Text.literal("Speed Marker Set Label"))
                .binding("MAGIC Rails (Speed)", () -> state.blueMapSpeedMarkerSetLabel, value -> state.blueMapSpeedMarkerSetLabel = value)
                .controller(StringController::new)
                .build();

        final Option<Integer> baseMarkerSetSorting = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Base Marker Set Sorting"))
                .binding(110, () -> state.blueMapBaseMarkerSetSorting, value -> state.blueMapBaseMarkerSetSorting = value)
                .controller(IntegerFieldController::new)
                .build();

        final Option<Integer> speedMarkerSetSorting = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Speed Marker Set Sorting"))
                .binding(111, () -> state.blueMapSpeedMarkerSetSorting, value -> state.blueMapSpeedMarkerSetSorting = value)
                .controller(IntegerFieldController::new)
                .build();

        final Option<Double> yBias = Option.<Double>createBuilder(Double.class)
                .name(Text.literal("Line Y Bias"))
                .tooltip(Text.literal("Vertical offset applied to lines to avoid z-fighting."))
                .binding(0.05D, () -> state.blueMapLineYBias, value -> state.blueMapLineYBias = value)
                .controller(option -> new DoubleFieldController(option, -4D, 16D))
                .build();

        final Option<Integer> curveTargetPoints = Option.<Integer>createBuilder(Integer.class)
                .name(Text.literal("Curve Target Points"))
                .tooltip(Text.literal("Higher values draw smoother curves but cost more CPU."))
                .binding(12, () -> state.blueMapCurveSampleTargetPoints, value -> state.blueMapCurveSampleTargetPoints = value)
                .controller(option -> new IntegerSliderController(option, 4, 256, 1))
                .build();

        final Option<Double> curveIntervalMin = Option.<Double>createBuilder(Double.class)
                .name(Text.literal("Curve Interval Min"))
                .binding(1.0D, () -> state.blueMapCurveSampleIntervalMin, value -> state.blueMapCurveSampleIntervalMin = value)
                .controller(option -> new DoubleFieldController(option, 0.01D, 50D))
                .build();

        final Option<Double> curveIntervalMax = Option.<Double>createBuilder(Double.class)
                .name(Text.literal("Curve Interval Max"))
                .binding(4.0D, () -> state.blueMapCurveSampleIntervalMax, value -> state.blueMapCurveSampleIntervalMax = value)
                .controller(option -> new DoubleFieldController(option, 0.01D, 50D))
                .build();

        return ConfigCategory.createBuilder()
                .name(Text.literal("BlueMap"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("General"))
                        .option(enabled)
                        .option(refreshInterval)
                        .option(initialDelay)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Layers"))
                        .option(baseLayerEnabled)
                        .option(speedLayerEnabled)
                        .option(toggleable)
                        .option(baseDefaultHidden)
                        .option(speedDefaultHidden)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Rendering"))
                        .option(markersListed)
                        .option(depthTest)
                        .option(baseLineWidth)
                        .option(speedLineWidth)
                        .option(baseColor)
                        .option(basePlatformColor)
                        .option(baseSidingColor)
                        .option(baseTurnBackColor)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Speed Overrides"))
                        .option(platformOverride)
                        .option(platformColor)
                        .option(highSpeedOverride)
                        .option(highSpeedThreshold)
                        .option(highSpeedColor)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Marker Sets"))
                        .option(baseMarkerSetId)
                        .option(speedMarkerSetId)
                        .option(baseMarkerSetLabel)
                        .option(speedMarkerSetLabel)
                        .option(baseMarkerSetSorting)
                        .option(speedMarkerSetSorting)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Sampling"))
                        .option(yBias)
                        .option(curveTargetPoints)
                        .option(curveIntervalMin)
                        .option(curveIntervalMax)
                        .build())
                .build();
    }

    private static final class State {
        private boolean useMph = JmeConfig.useMph();
        private boolean inWorldSpeedTextEnabled = JmeConfig.inWorldSpeedTextEnabled();
        private boolean cameraTiltEnabled = JmeConfig.cameraTiltEnabled();
        private double cameraTiltStrength = JmeConfig.cameraTiltStrength();

        private JmeConfig.DashboardRouteListMode dashboardRouteListMode = JmeConfig.dashboardRouteListMode();
        private boolean dashboardMapAutoSaveEnabled = JmeConfig.dashboardMapAutoSaveEnabled();
        private JmeConfig.DashboardRailOverlayMode dashboardRailOverlayMode = JmeConfig.dashboardRailOverlayMode();
        private int dashboardRailCullMaxPerCell = JmeConfig.dashboardRailOverlayCullMaxPerCell();
        private boolean alternativePlatformsEnabled = JmeConfig.alternativePlatformsEnabled();

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
        private Color blueMapBaseColor = new Color(JmeConfig.blueMapBaseColorRgb() & 0xFFFFFF);
        private Color blueMapBasePlatformColor = new Color(JmeConfig.blueMapBasePlatformColorRgb() & 0xFFFFFF);
        private Color blueMapBaseSidingColor = new Color(JmeConfig.blueMapBaseSidingColorRgb() & 0xFFFFFF);
        private Color blueMapBaseTurnBackColor = new Color(JmeConfig.blueMapBaseTurnBackColorRgb() & 0xFFFFFF);
        private Color blueMapPlatformColor = new Color(JmeConfig.blueMapPlatformColorRgb() & 0xFFFFFF);
        private boolean blueMapPlatformRailsForceRedEnabled = JmeConfig.blueMapPlatformRailsForceRedEnabled();
        private int blueMapHighSpeedThresholdKmh = JmeConfig.blueMapHighSpeedThresholdKmh();
        private Color blueMapHighSpeedColor = new Color(JmeConfig.blueMapHighSpeedColorRgb() & 0xFFFFFF);
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
            JmeConfig.setInWorldSpeedTextEnabled(inWorldSpeedTextEnabled);
            JmeConfig.setCameraTiltEnabled(cameraTiltEnabled);
            JmeConfig.setCameraTiltStrength(cameraTiltStrength);

            JmeConfig.setDashboardRouteListMode(dashboardRouteListMode);
            JmeConfig.setDashboardMapAutoSaveEnabled(dashboardMapAutoSaveEnabled);
            JmeConfig.setDashboardRailOverlayMode(dashboardRailOverlayMode);
            JmeConfig.setDashboardRailOverlayCullMaxPerCell(dashboardRailCullMaxPerCell);
            JmeConfig.setAlternativePlatformsEnabled(alternativePlatformsEnabled);

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
            JmeConfig.setBlueMapBaseColorRgb(blueMapBaseColor.getRGB() & 0xFFFFFF);
            JmeConfig.setBlueMapBasePlatformColorRgb(blueMapBasePlatformColor.getRGB() & 0xFFFFFF);
            JmeConfig.setBlueMapBaseSidingColorRgb(blueMapBaseSidingColor.getRGB() & 0xFFFFFF);
            JmeConfig.setBlueMapBaseTurnBackColorRgb(blueMapBaseTurnBackColor.getRGB() & 0xFFFFFF);
            JmeConfig.setBlueMapPlatformColorRgb(blueMapPlatformColor.getRGB() & 0xFFFFFF);
            JmeConfig.setBlueMapPlatformRailsForceRedEnabled(blueMapPlatformRailsForceRedEnabled);
            JmeConfig.setBlueMapHighSpeedThresholdKmh(blueMapHighSpeedThresholdKmh);
            JmeConfig.setBlueMapHighSpeedColorRgb(blueMapHighSpeedColor.getRGB() & 0xFFFFFF);
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
