package org.justnoone.jme.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class JmeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("magic.json", "jme.json");
    private static final Path SYSTEM_MAP_CSS_PATH = MagicConfigPaths.resolveMapFile("system_map.css", "jme_system_map.css");
    private static final Path SYSTEM_MAP_JS_PATH = MagicConfigPaths.resolveMapFile("system_map.js", "jme_system_map.js");

    private static Data data = load();

    private JmeConfig() {
    }

    public static boolean useMph() {
        return data.useMph;
    }

    public static void setUseMph(boolean useMph) {
        data.useMph = useMph;
    }

    /**
     * In-world rail speed labels (client-side).
     * <p>
     * Rendering lots of floating text can be expensive on large rail networks, so this is disabled by default.
     */
    public static boolean inWorldSpeedTextEnabled() {
        return data.inWorldSpeedTextEnabled;
    }

    public static void setInWorldSpeedTextEnabled(boolean enabled) {
        data.inWorldSpeedTextEnabled = enabled;
    }

    public static boolean cameraTiltEnabled() {
        return data.cameraTiltEnabled;
    }

    public static void setCameraTiltEnabled(boolean enabled) {
        data.cameraTiltEnabled = enabled;
    }

    public static double cameraTiltStrength() {
        return data.cameraTiltStrength;
    }

    public static void setCameraTiltStrength(double strength) {
        data.cameraTiltStrength = clampTiltStrength(strength);
    }

    public static DashboardRouteListMode dashboardRouteListMode() {
        return data.dashboardRouteListMode;
    }

    public static void setDashboardRouteListMode(DashboardRouteListMode mode) {
        data.dashboardRouteListMode = mode == null ? DashboardRouteListMode.FOLDERS : mode;
    }

    public static boolean dashboardMapAutoSaveEnabled() {
        return data.dashboardMapAutoSaveEnabled;
    }

    public static void setDashboardMapAutoSaveEnabled(boolean enabled) {
        data.dashboardMapAutoSaveEnabled = enabled;
    }

    public static DashboardRailOverlayMode dashboardRailOverlayMode() {
        return data.dashboardRailOverlayMode;
    }

    public static void setDashboardRailOverlayMode(DashboardRailOverlayMode mode) {
        data.dashboardRailOverlayMode = mode == null ? DashboardRailOverlayMode.ALL : mode;
    }

    public static int dashboardRailOverlayCullMaxPerCell() {
        return data.dashboardRailOverlayCullMaxPerCell;
    }

    public static void setDashboardRailOverlayCullMaxPerCell(int maxPerCell) {
        data.dashboardRailOverlayCullMaxPerCell = clampCullMaxPerCell(maxPerCell);
    }

    /**
     * Alternative platforms (dynamic platform rerouting).
     * <p>
     * This can be CPU-heavy on large networks; disable to reduce server lag.
     */
    public static boolean alternativePlatformsEnabled() {
        return data.alternativePlatformsEnabled;
    }

    public static void setAlternativePlatformsEnabled(boolean enabled) {
        data.alternativePlatformsEnabled = enabled;
    }

    /**
     * Server-side System Map overlay cache.
     * <p>
     * When enabled, MAGIC merges rails/vehicles snapshots into a long-lived cache so the HTTP system map
     * (port 8888) keeps showing content even after chunks unload.
     */
    public static boolean systemMapOverlayCacheEnabled() {
        return data.systemMapOverlayCacheEnabled;
    }

    public static void setSystemMapOverlayCacheEnabled(boolean enabled) {
        data.systemMapOverlayCacheEnabled = enabled;
    }

    /**
     * Whether the server-side System Map overlay cache is persisted to disk under {@code config/MAGIC/map}.
     * <p>
     * Disabling this prevents large on-disk cache files from being created/updated.
     */
    public static boolean systemMapOverlayCachePersistEnabled() {
        return data.systemMapOverlayCachePersistEnabled;
    }

    public static void setSystemMapOverlayCachePersistEnabled(boolean enabled) {
        data.systemMapOverlayCachePersistEnabled = enabled;
    }

    public static SystemMapLanguageDisplay systemMapLanguageDisplay() {
        return data.systemMapLanguageDisplay;
    }

    public static void setSystemMapLanguageDisplay(SystemMapLanguageDisplay display) {
        data.systemMapLanguageDisplay = display == null ? SystemMapLanguageDisplay.NORMAL : display;
    }

    public static boolean systemMapHidePlayer() {
        return data.systemMapHidePlayer;
    }

    public static void setSystemMapHidePlayer(boolean hide) {
        data.systemMapHidePlayer = hide;
    }

    public static boolean systemMapOverlayShowBaseRails() {
        return data.systemMapOverlayShowBaseRails;
    }

    public static void setSystemMapOverlayShowBaseRails(boolean enabled) {
        data.systemMapOverlayShowBaseRails = enabled;
    }

    public static boolean systemMapOverlayShowDetails() {
        return data.systemMapOverlayShowDetails;
    }

    public static void setSystemMapOverlayShowDetails(boolean enabled) {
        data.systemMapOverlayShowDetails = enabled;
    }

    public static boolean systemMapOverlayShowSignals() {
        return data.systemMapOverlayShowSignals;
    }

    public static void setSystemMapOverlayShowSignals(boolean enabled) {
        data.systemMapOverlayShowSignals = enabled;
    }

    public static boolean systemMapOverlayShowVehicles() {
        return data.systemMapOverlayShowVehicles;
    }

    public static void setSystemMapOverlayShowVehicles(boolean enabled) {
        data.systemMapOverlayShowVehicles = enabled;
    }

    public static boolean systemMapOverlayRespectRouteFilters() {
        return data.systemMapOverlayRespectRouteFilters;
    }

    public static void setSystemMapOverlayRespectRouteFilters(boolean enabled) {
        data.systemMapOverlayRespectRouteFilters = enabled;
    }

    public static boolean blueMapEnabled() {
        return data.blueMapEnabled;
    }

    public static void setBlueMapEnabled(boolean enabled) {
        data.blueMapEnabled = enabled;
    }

    public static int blueMapRefreshIntervalSeconds() {
        return data.blueMapRefreshIntervalSeconds;
    }

    public static void setBlueMapRefreshIntervalSeconds(int seconds) {
        data.blueMapRefreshIntervalSeconds = clampPositiveSeconds(seconds, 120);
    }

    public static int blueMapRefreshInitialDelaySeconds() {
        return data.blueMapRefreshInitialDelaySeconds;
    }

    public static void setBlueMapRefreshInitialDelaySeconds(int seconds) {
        data.blueMapRefreshInitialDelaySeconds = clampNonNegativeSeconds(seconds);
    }

    public static boolean blueMapBaseLayerEnabled() {
        return data.blueMapBaseLayerEnabled;
    }

    public static void setBlueMapBaseLayerEnabled(boolean enabled) {
        data.blueMapBaseLayerEnabled = enabled;
    }

    public static boolean blueMapSpeedLayerEnabled() {
        return data.blueMapSpeedLayerEnabled;
    }

    public static void setBlueMapSpeedLayerEnabled(boolean enabled) {
        data.blueMapSpeedLayerEnabled = enabled;
    }

    public static boolean blueMapMarkerSetsToggleable() {
        return data.blueMapMarkerSetsToggleable;
    }

    public static void setBlueMapMarkerSetsToggleable(boolean toggleable) {
        data.blueMapMarkerSetsToggleable = toggleable;
    }

    public static boolean blueMapBaseLayerDefaultHidden() {
        return data.blueMapBaseLayerDefaultHidden;
    }

    public static void setBlueMapBaseLayerDefaultHidden(boolean hidden) {
        data.blueMapBaseLayerDefaultHidden = hidden;
    }

    public static boolean blueMapSpeedLayerDefaultHidden() {
        return data.blueMapSpeedLayerDefaultHidden;
    }

    public static void setBlueMapSpeedLayerDefaultHidden(boolean hidden) {
        data.blueMapSpeedLayerDefaultHidden = hidden;
    }

    public static boolean blueMapMarkersListed() {
        return data.blueMapMarkersListed;
    }

    public static void setBlueMapMarkersListed(boolean listed) {
        data.blueMapMarkersListed = listed;
    }

    public static boolean blueMapDepthTestEnabled() {
        return data.blueMapDepthTestEnabled;
    }

    public static void setBlueMapDepthTestEnabled(boolean enabled) {
        data.blueMapDepthTestEnabled = enabled;
    }

    public static int blueMapBaseLineWidth() {
        return data.blueMapBaseLineWidth;
    }

    public static void setBlueMapBaseLineWidth(int width) {
        data.blueMapBaseLineWidth = clampLineWidth(width, 3);
    }

    public static int blueMapSpeedLineWidth() {
        return data.blueMapSpeedLineWidth;
    }

    public static void setBlueMapSpeedLineWidth(int width) {
        data.blueMapSpeedLineWidth = clampLineWidth(width, 2);
    }

    public static int blueMapBaseColorRgb() {
        return data.blueMapBaseColorRgb;
    }

    public static void setBlueMapBaseColorRgb(int rgb) {
        data.blueMapBaseColorRgb = rgb & 0xFFFFFF;
    }

    public static int blueMapBasePlatformColorRgb() {
        return data.blueMapBasePlatformColorRgb;
    }

    public static void setBlueMapBasePlatformColorRgb(int rgb) {
        data.blueMapBasePlatformColorRgb = rgb & 0xFFFFFF;
    }

    public static int blueMapBaseSidingColorRgb() {
        return data.blueMapBaseSidingColorRgb;
    }

    public static void setBlueMapBaseSidingColorRgb(int rgb) {
        data.blueMapBaseSidingColorRgb = rgb & 0xFFFFFF;
    }

    public static int blueMapBaseTurnBackColorRgb() {
        return data.blueMapBaseTurnBackColorRgb;
    }

    public static void setBlueMapBaseTurnBackColorRgb(int rgb) {
        data.blueMapBaseTurnBackColorRgb = rgb & 0xFFFFFF;
    }

    public static int blueMapPlatformColorRgb() {
        return data.blueMapPlatformColorRgb;
    }

    public static void setBlueMapPlatformColorRgb(int rgb) {
        data.blueMapPlatformColorRgb = rgb & 0xFFFFFF;
    }

    public static boolean blueMapPlatformRailsForceRedEnabled() {
        return data.blueMapPlatformRailsForceRedEnabled;
    }

    public static void setBlueMapPlatformRailsForceRedEnabled(boolean enabled) {
        data.blueMapPlatformRailsForceRedEnabled = enabled;
    }

    public static int blueMapHighSpeedThresholdKmh() {
        return data.blueMapHighSpeedThresholdKmh;
    }

    public static void setBlueMapHighSpeedThresholdKmh(int thresholdKmh) {
        data.blueMapHighSpeedThresholdKmh = clampSpeedKmh(thresholdKmh, 200);
    }

    public static int blueMapHighSpeedColorRgb() {
        return data.blueMapHighSpeedColorRgb;
    }

    public static void setBlueMapHighSpeedColorRgb(int rgb) {
        data.blueMapHighSpeedColorRgb = rgb & 0xFFFFFF;
    }

    public static boolean blueMapHighSpeedRailsForceRedEnabled() {
        return data.blueMapHighSpeedRailsForceRedEnabled;
    }

    public static void setBlueMapHighSpeedRailsForceRedEnabled(boolean enabled) {
        data.blueMapHighSpeedRailsForceRedEnabled = enabled;
    }

    public static String blueMapBaseMarkerSetId() {
        return data.blueMapBaseMarkerSetId;
    }

    public static void setBlueMapBaseMarkerSetId(String id) {
        data.blueMapBaseMarkerSetId = sanitizeMarkerSetId(id, "jme_rails");
    }

    public static String blueMapSpeedMarkerSetId() {
        return data.blueMapSpeedMarkerSetId;
    }

    public static void setBlueMapSpeedMarkerSetId(String id) {
        data.blueMapSpeedMarkerSetId = sanitizeMarkerSetId(id, "jme_rails_speeds");
    }

    public static String blueMapBaseMarkerSetLabel() {
        return data.blueMapBaseMarkerSetLabel;
    }

    public static void setBlueMapBaseMarkerSetLabel(String label) {
        data.blueMapBaseMarkerSetLabel = sanitizeNonEmpty(label, "MAGIC Rails");
    }

    public static String blueMapSpeedMarkerSetLabel() {
        return data.blueMapSpeedMarkerSetLabel;
    }

    public static void setBlueMapSpeedMarkerSetLabel(String label) {
        data.blueMapSpeedMarkerSetLabel = sanitizeNonEmpty(label, "MAGIC Rails (Speed)");
    }

    public static int blueMapBaseMarkerSetSorting() {
        return data.blueMapBaseMarkerSetSorting;
    }

    public static void setBlueMapBaseMarkerSetSorting(int sorting) {
        data.blueMapBaseMarkerSetSorting = sorting;
    }

    public static int blueMapSpeedMarkerSetSorting() {
        return data.blueMapSpeedMarkerSetSorting;
    }

    public static void setBlueMapSpeedMarkerSetSorting(int sorting) {
        data.blueMapSpeedMarkerSetSorting = sorting;
    }

    public static double blueMapLineYBias() {
        return data.blueMapLineYBias;
    }

    public static void setBlueMapLineYBias(double bias) {
        data.blueMapLineYBias = clampLineYBias(bias);
    }

    public static int blueMapCurveSampleTargetPoints() {
        return data.blueMapCurveSampleTargetPoints;
    }

    public static void setBlueMapCurveSampleTargetPoints(int points) {
        data.blueMapCurveSampleTargetPoints = clampCurveTargetPoints(points);
    }

    public static double blueMapCurveSampleIntervalMin() {
        return data.blueMapCurveSampleIntervalMin;
    }

    public static void setBlueMapCurveSampleIntervalMin(double interval) {
        data.blueMapCurveSampleIntervalMin = clampPositiveDouble(interval, 0.4D);
    }

    public static double blueMapCurveSampleIntervalMax() {
        return data.blueMapCurveSampleIntervalMax;
    }

    public static void setBlueMapCurveSampleIntervalMax(double interval) {
        data.blueMapCurveSampleIntervalMax = clampPositiveDouble(interval, 1.25D);
    }

    public static TrackColorMode trackColorMode() {
        return data.trackColorMode;
    }

    public static void setTrackColorMode(TrackColorMode mode) {
        data.trackColorMode = mode == null ? TrackColorMode.OPEN_RAILWAY_MAP : mode;
    }

    public static TrackColorStop[] trackColorCustomGradientStops() {
        return data.trackColorCustomGradientStops;
    }

    public static void setTrackColorCustomGradientStops(TrackColorStop[] stops) {
        data.trackColorCustomGradientStops = sanitizeTrackColorStops(stops);
    }

    public static String formatSpeedLabel(int speedKmh) {
        if (useMph()) {
            return toMph(speedKmh) + " mph";
        }
        return speedKmh + " km/h";
    }

    public static int toMph(int kmh) {
        return Math.max(1, (int) Math.round(kmh * 0.621371D));
    }

    public static int toKmh(int mph) {
        return (int) Math.round(mph / 0.621371D);
    }

    public static void save() {
        final JsonObject root = new JsonObject();
        root.addProperty("use_mph", data.useMph);
        root.addProperty("in_world_speed_text_enabled", data.inWorldSpeedTextEnabled);
        root.addProperty("camera_tilt_enabled", data.cameraTiltEnabled);
        root.addProperty("camera_tilt_strength", data.cameraTiltStrength);
        root.addProperty("dashboard_route_list_mode", data.dashboardRouteListMode.name());
        root.addProperty("dashboard_map_auto_save_enabled", data.dashboardMapAutoSaveEnabled);
        root.addProperty("dashboard_rail_overlay_mode", data.dashboardRailOverlayMode.name());
        root.addProperty("dashboard_rail_overlay_cull_max_per_cell", data.dashboardRailOverlayCullMaxPerCell);
        root.addProperty("alternative_platforms_enabled", data.alternativePlatformsEnabled);
        root.addProperty("system_map_overlay_cache_enabled", data.systemMapOverlayCacheEnabled);
        root.addProperty("system_map_overlay_cache_persist_enabled", data.systemMapOverlayCachePersistEnabled);
        root.addProperty("system_map_language_display", data.systemMapLanguageDisplay.name());
        root.addProperty("system_map_hide_player", data.systemMapHidePlayer);
        root.addProperty("system_map_overlay_show_base_rails", data.systemMapOverlayShowBaseRails);
        root.addProperty("system_map_overlay_show_details", data.systemMapOverlayShowDetails);
        root.addProperty("system_map_overlay_show_signals", data.systemMapOverlayShowSignals);
        root.addProperty("system_map_overlay_show_vehicles", data.systemMapOverlayShowVehicles);
        root.addProperty("system_map_overlay_respect_route_filters", data.systemMapOverlayRespectRouteFilters);
        root.addProperty("track_color_mode", data.trackColorMode.name());
        root.addProperty("blue_map_enabled", data.blueMapEnabled);
        root.addProperty("blue_map_refresh_interval_seconds", data.blueMapRefreshIntervalSeconds);
        root.addProperty("blue_map_refresh_initial_delay_seconds", data.blueMapRefreshInitialDelaySeconds);
        root.addProperty("blue_map_base_layer_enabled", data.blueMapBaseLayerEnabled);
        root.addProperty("blue_map_speed_layer_enabled", data.blueMapSpeedLayerEnabled);
        root.addProperty("blue_map_marker_sets_toggleable", data.blueMapMarkerSetsToggleable);
        root.addProperty("blue_map_base_layer_default_hidden", data.blueMapBaseLayerDefaultHidden);
        root.addProperty("blue_map_speed_layer_default_hidden", data.blueMapSpeedLayerDefaultHidden);
        root.addProperty("blue_map_markers_listed", data.blueMapMarkersListed);
        root.addProperty("blue_map_depth_test_enabled", data.blueMapDepthTestEnabled);
        root.addProperty("blue_map_base_line_width", data.blueMapBaseLineWidth);
        root.addProperty("blue_map_speed_line_width", data.blueMapSpeedLineWidth);
        root.addProperty("blue_map_base_color", String.format(Locale.ROOT, "#%06X", data.blueMapBaseColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_base_platform_color", String.format(Locale.ROOT, "#%06X", data.blueMapBasePlatformColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_base_siding_color", String.format(Locale.ROOT, "#%06X", data.blueMapBaseSidingColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_base_turn_back_color", String.format(Locale.ROOT, "#%06X", data.blueMapBaseTurnBackColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_platform_color", String.format(Locale.ROOT, "#%06X", data.blueMapPlatformColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_platform_rails_force_red_enabled", data.blueMapPlatformRailsForceRedEnabled);
        root.addProperty("blue_map_high_speed_threshold_kmh", data.blueMapHighSpeedThresholdKmh);
        root.addProperty("blue_map_high_speed_color", String.format(Locale.ROOT, "#%06X", data.blueMapHighSpeedColorRgb & 0xFFFFFF));
        root.addProperty("blue_map_high_speed_rails_force_red_enabled", data.blueMapHighSpeedRailsForceRedEnabled);
        root.addProperty("blue_map_base_marker_set_id", data.blueMapBaseMarkerSetId);
        root.addProperty("blue_map_speed_marker_set_id", data.blueMapSpeedMarkerSetId);
        root.addProperty("blue_map_base_marker_set_label", data.blueMapBaseMarkerSetLabel);
        root.addProperty("blue_map_speed_marker_set_label", data.blueMapSpeedMarkerSetLabel);
        root.addProperty("blue_map_base_marker_set_sorting", data.blueMapBaseMarkerSetSorting);
        root.addProperty("blue_map_speed_marker_set_sorting", data.blueMapSpeedMarkerSetSorting);
        root.addProperty("blue_map_line_y_bias", data.blueMapLineYBias);
        root.addProperty("blue_map_curve_sample_target_points", data.blueMapCurveSampleTargetPoints);
        root.addProperty("blue_map_curve_sample_interval_min", data.blueMapCurveSampleIntervalMin);
        root.addProperty("blue_map_curve_sample_interval_max", data.blueMapCurveSampleIntervalMax);

        final JsonArray customGradient = new JsonArray();
        if (data.trackColorCustomGradientStops != null) {
            for (final TrackColorStop stop : data.trackColorCustomGradientStops) {
                if (stop == null) {
                    continue;
                }
                final JsonObject stopObject = new JsonObject();
                stopObject.addProperty("speed_kmh", stop.speedKmh);
                stopObject.addProperty("color", formatColorRgb(stop.colorArgb));
                customGradient.add(stopObject);
            }
        }
        root.add("track_color_custom_gradient", customGradient);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static synchronized void reload() {
        data = load();
    }

    private static Data load() {
        ensureSystemMapOverrideFilesExist();

        final Data loaded = new Data();
        if (!Files.exists(CONFIG_PATH)) {
            return loaded;
        }

        try {
            final JsonObject root = new JsonParser().parse(jme$readText(CONFIG_PATH)).getAsJsonObject();
            if (root.has("use_mph")) {
                loaded.useMph = root.get("use_mph").getAsBoolean();
            }
            if (root.has("in_world_speed_text_enabled")) {
                loaded.inWorldSpeedTextEnabled = root.get("in_world_speed_text_enabled").getAsBoolean();
            }
            if (root.has("camera_tilt_enabled")) {
                loaded.cameraTiltEnabled = root.get("camera_tilt_enabled").getAsBoolean();
            }
            if (root.has("camera_tilt_strength")) {
                loaded.cameraTiltStrength = clampTiltStrength(root.get("camera_tilt_strength").getAsDouble());
            }
            if (root.has("dashboard_route_list_mode")) {
                loaded.dashboardRouteListMode = DashboardRouteListMode.fromString(root.get("dashboard_route_list_mode").getAsString());
            }
            if (root.has("dashboard_map_auto_save_enabled")) {
                loaded.dashboardMapAutoSaveEnabled = root.get("dashboard_map_auto_save_enabled").getAsBoolean();
            }
            if (root.has("dashboard_rail_overlay_mode")) {
                loaded.dashboardRailOverlayMode = DashboardRailOverlayMode.fromString(root.get("dashboard_rail_overlay_mode").getAsString());
            }
            if (root.has("dashboard_rail_overlay_cull_max_per_cell")) {
                loaded.dashboardRailOverlayCullMaxPerCell = clampCullMaxPerCell(root.get("dashboard_rail_overlay_cull_max_per_cell").getAsInt());
            }
            if (root.has("alternative_platforms_enabled")) {
                loaded.alternativePlatformsEnabled = root.get("alternative_platforms_enabled").getAsBoolean();
            }
            if (root.has("system_map_overlay_cache_enabled")) {
                loaded.systemMapOverlayCacheEnabled = root.get("system_map_overlay_cache_enabled").getAsBoolean();
            }
            if (root.has("system_map_overlay_cache_persist_enabled")) {
                loaded.systemMapOverlayCachePersistEnabled = root.get("system_map_overlay_cache_persist_enabled").getAsBoolean();
            }
            if (root.has("system_map_language_display")) {
                loaded.systemMapLanguageDisplay = SystemMapLanguageDisplay.fromString(root.get("system_map_language_display").getAsString());
            }

            if (root.has("system_map_hide_player")) {
                loaded.systemMapHidePlayer = root.get("system_map_hide_player").getAsBoolean();
            }
            if (root.has("system_map_overlay_show_base_rails")) {
                loaded.systemMapOverlayShowBaseRails = root.get("system_map_overlay_show_base_rails").getAsBoolean();
            }
            if (root.has("system_map_overlay_show_details")) {
                loaded.systemMapOverlayShowDetails = root.get("system_map_overlay_show_details").getAsBoolean();
            }
            if (root.has("system_map_overlay_show_signals")) {
                loaded.systemMapOverlayShowSignals = root.get("system_map_overlay_show_signals").getAsBoolean();
            }
            if (root.has("system_map_overlay_show_vehicles")) {
                loaded.systemMapOverlayShowVehicles = root.get("system_map_overlay_show_vehicles").getAsBoolean();
            }
            if (root.has("system_map_overlay_respect_route_filters")) {
                loaded.systemMapOverlayRespectRouteFilters = root.get("system_map_overlay_respect_route_filters").getAsBoolean();
            }

            if (root.has("track_color_mode")) {
                loaded.trackColorMode = TrackColorMode.fromString(root.get("track_color_mode").getAsString());
            }

            if (root.has("track_color_custom_gradient") && root.get("track_color_custom_gradient").isJsonArray()) {
                loaded.trackColorCustomGradientStops = sanitizeTrackColorStops(parseTrackColorStops(root.getAsJsonArray("track_color_custom_gradient")));
            }

            if (root.has("blue_map_enabled")) {
                loaded.blueMapEnabled = root.get("blue_map_enabled").getAsBoolean();
            }
            if (root.has("blue_map_refresh_interval_seconds")) {
                loaded.blueMapRefreshIntervalSeconds = clampPositiveSeconds(root.get("blue_map_refresh_interval_seconds").getAsInt(), 120);
            }
            if (root.has("blue_map_refresh_initial_delay_seconds")) {
                loaded.blueMapRefreshInitialDelaySeconds = clampNonNegativeSeconds(root.get("blue_map_refresh_initial_delay_seconds").getAsInt());
            }
            if (root.has("blue_map_base_layer_enabled")) {
                loaded.blueMapBaseLayerEnabled = root.get("blue_map_base_layer_enabled").getAsBoolean();
            }
            if (root.has("blue_map_speed_layer_enabled")) {
                loaded.blueMapSpeedLayerEnabled = root.get("blue_map_speed_layer_enabled").getAsBoolean();
            }
            if (root.has("blue_map_marker_sets_toggleable")) {
                loaded.blueMapMarkerSetsToggleable = root.get("blue_map_marker_sets_toggleable").getAsBoolean();
            }
            if (root.has("blue_map_base_layer_default_hidden")) {
                loaded.blueMapBaseLayerDefaultHidden = root.get("blue_map_base_layer_default_hidden").getAsBoolean();
            }
            if (root.has("blue_map_speed_layer_default_hidden")) {
                loaded.blueMapSpeedLayerDefaultHidden = root.get("blue_map_speed_layer_default_hidden").getAsBoolean();
            }
            if (root.has("blue_map_markers_listed")) {
                loaded.blueMapMarkersListed = root.get("blue_map_markers_listed").getAsBoolean();
            }
            if (root.has("blue_map_depth_test_enabled")) {
                loaded.blueMapDepthTestEnabled = root.get("blue_map_depth_test_enabled").getAsBoolean();
            }
            if (root.has("blue_map_base_line_width")) {
                loaded.blueMapBaseLineWidth = clampLineWidth(root.get("blue_map_base_line_width").getAsInt(), 3);
            }
            if (root.has("blue_map_speed_line_width")) {
                loaded.blueMapSpeedLineWidth = clampLineWidth(root.get("blue_map_speed_line_width").getAsInt(), 2);
            }
            if (root.has("blue_map_base_color")) {
                loaded.blueMapBaseColorRgb = parseColorRgb(root.get("blue_map_base_color").getAsString(), loaded.blueMapBaseColorRgb);
            }
            if (root.has("blue_map_base_platform_color")) {
                loaded.blueMapBasePlatformColorRgb = parseColorRgb(root.get("blue_map_base_platform_color").getAsString(), loaded.blueMapBasePlatformColorRgb);
            }
            if (root.has("blue_map_base_siding_color")) {
                loaded.blueMapBaseSidingColorRgb = parseColorRgb(root.get("blue_map_base_siding_color").getAsString(), loaded.blueMapBaseSidingColorRgb);
            }
            if (root.has("blue_map_base_turn_back_color")) {
                loaded.blueMapBaseTurnBackColorRgb = parseColorRgb(root.get("blue_map_base_turn_back_color").getAsString(), loaded.blueMapBaseTurnBackColorRgb);
            }
            if (root.has("blue_map_platform_color")) {
                loaded.blueMapPlatformColorRgb = parseColorRgb(root.get("blue_map_platform_color").getAsString(), loaded.blueMapPlatformColorRgb);
            }
            if (root.has("blue_map_platform_rails_force_red_enabled")) {
                loaded.blueMapPlatformRailsForceRedEnabled = root.get("blue_map_platform_rails_force_red_enabled").getAsBoolean();
            }
            if (root.has("blue_map_high_speed_threshold_kmh")) {
                loaded.blueMapHighSpeedThresholdKmh = clampSpeedKmh(root.get("blue_map_high_speed_threshold_kmh").getAsInt(), 200);
            }
            if (root.has("blue_map_high_speed_color")) {
                loaded.blueMapHighSpeedColorRgb = parseColorRgb(root.get("blue_map_high_speed_color").getAsString(), loaded.blueMapHighSpeedColorRgb);
            }
            if (root.has("blue_map_high_speed_rails_force_red_enabled")) {
                loaded.blueMapHighSpeedRailsForceRedEnabled = root.get("blue_map_high_speed_rails_force_red_enabled").getAsBoolean();
            }
            if (root.has("blue_map_base_marker_set_id")) {
                loaded.blueMapBaseMarkerSetId = sanitizeMarkerSetId(root.get("blue_map_base_marker_set_id").getAsString(), loaded.blueMapBaseMarkerSetId);
            }
            if (root.has("blue_map_speed_marker_set_id")) {
                loaded.blueMapSpeedMarkerSetId = sanitizeMarkerSetId(root.get("blue_map_speed_marker_set_id").getAsString(), loaded.blueMapSpeedMarkerSetId);
            }
            if (root.has("blue_map_base_marker_set_label")) {
                loaded.blueMapBaseMarkerSetLabel = sanitizeNonEmpty(root.get("blue_map_base_marker_set_label").getAsString(), loaded.blueMapBaseMarkerSetLabel);
            }
            if (root.has("blue_map_speed_marker_set_label")) {
                loaded.blueMapSpeedMarkerSetLabel = sanitizeNonEmpty(root.get("blue_map_speed_marker_set_label").getAsString(), loaded.blueMapSpeedMarkerSetLabel);
            }
            if (root.has("blue_map_base_marker_set_sorting")) {
                loaded.blueMapBaseMarkerSetSorting = root.get("blue_map_base_marker_set_sorting").getAsInt();
            }
            if (root.has("blue_map_speed_marker_set_sorting")) {
                loaded.blueMapSpeedMarkerSetSorting = root.get("blue_map_speed_marker_set_sorting").getAsInt();
            }
            if (root.has("blue_map_line_y_bias")) {
                loaded.blueMapLineYBias = clampLineYBias(root.get("blue_map_line_y_bias").getAsDouble());
            }
            if (root.has("blue_map_curve_sample_target_points")) {
                loaded.blueMapCurveSampleTargetPoints = clampCurveTargetPoints(root.get("blue_map_curve_sample_target_points").getAsInt());
            }
            if (root.has("blue_map_curve_sample_interval_min")) {
                loaded.blueMapCurveSampleIntervalMin = clampPositiveDouble(root.get("blue_map_curve_sample_interval_min").getAsDouble(), 0.4D);
            }
            if (root.has("blue_map_curve_sample_interval_max")) {
                loaded.blueMapCurveSampleIntervalMax = clampPositiveDouble(root.get("blue_map_curve_sample_interval_max").getAsDouble(), 1.25D);
            }
        } catch (Exception ignored) {
        }

        return loaded;
    }

    private static void ensureSystemMapOverrideFilesExist() {
        // Create empty/stub override files on first run so users can discover and edit them.
        // Keep them separate from the bundled JS/CSS so updates to MAGIC still take effect.
        try {
            if (!Files.exists(SYSTEM_MAP_CSS_PATH)) {
                writeOptionalText(SYSTEM_MAP_CSS_PATH, "/* MAGIC System Map custom CSS\n"
                        + " *\n"
                        + " * This file is injected into the MTR System Map (port 8888).\n"
                        + " * Edit and refresh the page to apply changes.\n"
                        + " */\n");
            }
        } catch (Exception ignored) {
        }

        try {
            if (!Files.exists(SYSTEM_MAP_JS_PATH)) {
                writeOptionalText(SYSTEM_MAP_JS_PATH, "// MAGIC System Map custom JS\n"
                        + "//\n"
                        + "// This file is injected into the MTR System Map (port 8888).\n"
                        + "// Save and refresh the page to run.\n");
            }
        } catch (Exception ignored) {
        }
    }

    public static String getSystemMapCustomCss() {
        return readOptionalText(SYSTEM_MAP_CSS_PATH);
    }

    public static String getSystemMapCustomJs() {
        return readOptionalText(SYSTEM_MAP_JS_PATH);
    }

    public static void setSystemMapCustomCss(String css) {
        writeOptionalText(SYSTEM_MAP_CSS_PATH, css);
    }

    public static void setSystemMapCustomJs(String js) {
        writeOptionalText(SYSTEM_MAP_JS_PATH, js);
    }

    private static String readOptionalText(Path path) {
        try {
            return Files.exists(path) ? jme$readText(path).trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void writeOptionalText(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static String jme$readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static double clampTiltStrength(double strength) {
        if (!Double.isFinite(strength)) {
            return 1D;
        }
        return Math.max(0D, Math.min(2D, strength));
    }

    private static int clampCullMaxPerCell(int maxPerCell) {
        if (maxPerCell <= 0) {
            return 8;
        }
        return Math.max(1, Math.min(64, maxPerCell));
    }

    private static int clampPositiveSeconds(int seconds, int fallback) {
        if (seconds <= 0) {
            return fallback;
        }
        return Math.max(1, Math.min(86400, seconds));
    }

    private static int clampNonNegativeSeconds(int seconds) {
        return Math.max(0, Math.min(86400, seconds));
    }

    private static int clampLineWidth(int width, int fallback) {
        if (width <= 0) {
            return fallback;
        }
        return Math.max(1, Math.min(20, width));
    }

    private static int clampSpeedKmh(int speedKmh, int fallback) {
        if (speedKmh <= 0) {
            return fallback;
        }
        return Math.max(1, Math.min(400, speedKmh));
    }

    private static double clampLineYBias(double bias) {
        if (!Double.isFinite(bias)) {
            return 0.05D;
        }
        return Math.max(-4D, Math.min(16D, bias));
    }

    private static int clampCurveTargetPoints(int points) {
        if (points <= 0) {
            return 24;
        }
        return Math.max(4, Math.min(256, points));
    }

    private static double clampPositiveDouble(double value, double fallback) {
        if (!Double.isFinite(value) || value <= 0D) {
            return fallback;
        }
        return Math.max(1.0E-6D, Math.min(1000D, value));
    }

    private static String sanitizeNonEmpty(String value, String fallback) {
        final String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String sanitizeMarkerSetId(String value, String fallback) {
        final String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        // BlueMap marker-set ids are used as map keys; keep them predictable.
        final String normalized = trimmed.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9_\\-]+", "_");
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static TrackColorStop[] parseTrackColorStops(JsonArray array) {
        if (array == null || array.size() == 0) {
            return new TrackColorStop[0];
        }

        final TrackColorStop[] stops = new TrackColorStop[array.size()];
        int count = 0;

        for (int i = 0; i < array.size(); i++) {
            final JsonElement element = array.get(i);
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            final JsonObject obj = element.getAsJsonObject();

            int speedKmh = 0;
            if (obj.has("speed_kmh") && obj.get("speed_kmh").isJsonPrimitive()) {
                speedKmh = obj.get("speed_kmh").getAsInt();
            } else if (obj.has("speed") && obj.get("speed").isJsonPrimitive()) {
                speedKmh = obj.get("speed").getAsInt();
            }

            int rgb = 0xFFFFFF;
            if (obj.has("color") && obj.get("color").isJsonPrimitive()) {
                rgb = parseColorRgb(obj.get("color").getAsString(), rgb);
            } else if (obj.has("color_rgb") && obj.get("color_rgb").isJsonPrimitive()) {
                rgb = obj.get("color_rgb").getAsInt();
            }

            stops[count++] = new TrackColorStop(speedKmh, 0xFF000000 | (rgb & 0xFFFFFF));
        }

        if (count <= 0) {
            return new TrackColorStop[0];
        }

        if (count == stops.length) {
            return stops;
        }

        final TrackColorStop[] trimmed = new TrackColorStop[count];
        System.arraycopy(stops, 0, trimmed, 0, count);
        return trimmed;
    }

    private static TrackColorStop[] sanitizeTrackColorStops(TrackColorStop[] stops) {
        final TrackColorStop[] source = stops == null ? new TrackColorStop[0] : stops;

        // 1..400 is the MAGIC connector range.
        final java.util.TreeMap<Integer, Integer> bySpeed = new java.util.TreeMap<>();
        for (final TrackColorStop stop : source) {
            if (stop == null) {
                continue;
            }

            final int speed = Math.max(1, Math.min(400, stop.speedKmh));
            final int argb = 0xFF000000 | (stop.colorArgb & 0xFFFFFF);
            bySpeed.put(speed, argb);
        }

        if (bySpeed.size() < 2) {
            return defaultTrackColorCustomGradientStops();
        }

        final TrackColorStop[] sanitized = new TrackColorStop[bySpeed.size()];
        int idx = 0;
        for (final java.util.Map.Entry<Integer, Integer> entry : bySpeed.entrySet()) {
            sanitized[idx++] = new TrackColorStop(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }

    private static TrackColorStop[] defaultTrackColorCustomGradientStops() {
        // OpenRailwayMap-like gradient, consistent with MAGIC's historic defaults.
        return new TrackColorStop[]{
                new TrackColorStop(5, 0xFF102A8A),
                new TrackColorStop(100, 0xFF25C977),
                new TrackColorStop(180, 0xFFD9E344),
                new TrackColorStop(220, 0xFFFFE028),
                new TrackColorStop(300, 0xFFEF3A26),
                new TrackColorStop(400, 0xFFB42AE6)
        };
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

        // Support #RRGGBB and #AARRGGBB; ignore alpha.
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

    private static final class Data {
        private boolean useMph;
        private boolean inWorldSpeedTextEnabled = false;
        private boolean cameraTiltEnabled = true;
        private double cameraTiltStrength = 1D;
        private DashboardRouteListMode dashboardRouteListMode = DashboardRouteListMode.FOLDERS;
        private boolean dashboardMapAutoSaveEnabled = true;
        // Drawing every rail segment on the dashboard map can be very expensive on large networks.
        // Default to a culled overlay to keep FPS reasonable.
        private DashboardRailOverlayMode dashboardRailOverlayMode = DashboardRailOverlayMode.CULL;
        private int dashboardRailOverlayCullMaxPerCell = 8;
        // Dynamic platform rerouting is powerful but can be CPU-heavy. Default off for performance.
        private boolean alternativePlatformsEnabled = false;
        private boolean systemMapOverlayCacheEnabled = false;
        private boolean systemMapOverlayCachePersistEnabled = false;
        private SystemMapLanguageDisplay systemMapLanguageDisplay = SystemMapLanguageDisplay.NORMAL;
        private boolean systemMapHidePlayer = false;
        private boolean systemMapOverlayShowBaseRails = true;
        private boolean systemMapOverlayShowDetails = false;
        private boolean systemMapOverlayShowSignals = true;
        private boolean systemMapOverlayShowVehicles = true;
        private boolean systemMapOverlayRespectRouteFilters = false;
        private TrackColorMode trackColorMode = TrackColorMode.OPEN_RAILWAY_MAP;
        private TrackColorStop[] trackColorCustomGradientStops = defaultTrackColorCustomGradientStops();

        // BlueMap integration (server-side).
        private boolean blueMapEnabled = true;
        private int blueMapRefreshIntervalSeconds = 120;
        private int blueMapRefreshInitialDelaySeconds = 15;
        private boolean blueMapBaseLayerEnabled = true;
        private boolean blueMapSpeedLayerEnabled = true;
        private boolean blueMapMarkerSetsToggleable = true;
        private boolean blueMapBaseLayerDefaultHidden = false;
        private boolean blueMapSpeedLayerDefaultHidden = true;
        private boolean blueMapMarkersListed = false;
        private boolean blueMapDepthTestEnabled = false;
        private int blueMapBaseLineWidth = 3;
        private int blueMapSpeedLineWidth = 2;
        // Base (type) layer colors.
        private int blueMapBaseColorRgb = 0xFF0000; // normal rails
        private int blueMapBasePlatformColorRgb = 0x8B0000; // dark red
        private int blueMapBaseSidingColorRgb = 0xFFD500; // yellow
        private int blueMapBaseTurnBackColorRgb = 0x00008B; // dark blue

        // Speed layer override colors.
        private int blueMapPlatformColorRgb = 0xFF0000;
        private boolean blueMapPlatformRailsForceRedEnabled = true;
        private int blueMapHighSpeedThresholdKmh = 200;
        private int blueMapHighSpeedColorRgb = 0xFF0000;
        private boolean blueMapHighSpeedRailsForceRedEnabled = true;
        private String blueMapBaseMarkerSetId = "jme_rails";
        private String blueMapSpeedMarkerSetId = "jme_rails_speeds";
        private String blueMapBaseMarkerSetLabel = "MAGIC Rails";
        private String blueMapSpeedMarkerSetLabel = "MAGIC Rails (Speed)";
        private int blueMapBaseMarkerSetSorting = 110;
        private int blueMapSpeedMarkerSetSorting = 111;
        private double blueMapLineYBias = 0.05D;
        private int blueMapCurveSampleTargetPoints = 24;
        private double blueMapCurveSampleIntervalMin = 0.4D;
        private double blueMapCurveSampleIntervalMax = 1.25D;
    }

    public enum DashboardRouteListMode {
        FOLDERS,
        FLAT;

        public static DashboardRouteListMode fromString(String value) {
            if (value == null) {
                return FOLDERS;
            }
            for (final DashboardRouteListMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return FOLDERS;
        }
    }

    public enum DashboardRailOverlayMode {
        ALL,
        CULL,
        OFF;

        public static DashboardRailOverlayMode fromString(String value) {
            if (value == null) {
                return ALL;
            }
            for (final DashboardRailOverlayMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return ALL;
        }
    }

    public enum SystemMapLanguageDisplay {
        NORMAL,
        CJK_ONLY,
        NON_CJK_ONLY;

        public static SystemMapLanguageDisplay fromString(String value) {
            if (value == null) {
                return NORMAL;
            }
            for (final SystemMapLanguageDisplay display : values()) {
                if (display.name().equalsIgnoreCase(value)) {
                    return display;
                }
            }
            return NORMAL;
        }
    }

    public enum TrackColorMode {
        OPEN_RAILWAY_MAP,
        MTR_DEFAULT,
        CUSTOM_GRADIENT;

        public static TrackColorMode fromString(String value) {
            if (value == null) {
                return OPEN_RAILWAY_MAP;
            }
            for (final TrackColorMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return OPEN_RAILWAY_MAP;
        }
    }

    public static final class TrackColorStop {
        public final int speedKmh;
        public final int colorArgb;

        public TrackColorStop(int speedKmh, int colorArgb) {
            this.speedKmh = speedKmh;
            this.colorArgb = colorArgb;
        }
    }

}
