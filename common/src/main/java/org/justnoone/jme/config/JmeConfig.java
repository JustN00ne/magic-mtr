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

    public static boolean bluemapEnabled() {
        return data.bluemapEnabled;
    }

    public static void setBluemapEnabled(boolean enabled) {
        data.bluemapEnabled = enabled;
    }

    public static boolean bluemapShowRails() {
        return data.bluemapShowRails;
    }

    public static void setBluemapShowRails(boolean enabled) {
        data.bluemapShowRails = enabled;
    }

    public static boolean bluemapShowTrains() {
        return data.bluemapShowTrains;
    }

    public static void setBluemapShowTrains(boolean enabled) {
        data.bluemapShowTrains = enabled;
    }

    public static boolean bluemapRailLayerSpeedThreshold() {
        return data.bluemapRailLayerSpeedThreshold;
    }

    public static void setBluemapRailLayerSpeedThreshold(boolean enabled) {
        data.bluemapRailLayerSpeedThreshold = enabled;
    }

    public static boolean bluemapRailLayerSpeedGradient() {
        return data.bluemapRailLayerSpeedGradient;
    }

    public static void setBluemapRailLayerSpeedGradient(boolean enabled) {
        data.bluemapRailLayerSpeedGradient = enabled;
    }

    public static boolean bluemapRailLayerSignals() {
        return data.bluemapRailLayerSignals;
    }

    public static void setBluemapRailLayerSignals(boolean enabled) {
        data.bluemapRailLayerSignals = enabled;
    }

    public static boolean bluemapRailLayerSpeedLabels() {
        return data.bluemapRailLayerSpeedLabels;
    }

    public static void setBluemapRailLayerSpeedLabels(boolean enabled) {
        data.bluemapRailLayerSpeedLabels = enabled;
    }

    public static boolean bluemapSignalsDashedMulticolor() {
        return data.bluemapSignalsDashedMulticolor;
    }

    public static void setBluemapSignalsDashedMulticolor(boolean enabled) {
        data.bluemapSignalsDashedMulticolor = enabled;
    }

    public static int bluemapTrainsRefreshMs() {
        return data.bluemapTrainsRefreshMs;
    }

    public static void setBluemapTrainsRefreshMs(int refreshMs) {
        data.bluemapTrainsRefreshMs = clampRefreshMs(refreshMs, 200, 10000, 500);
    }

    public static int bluemapRailsRefreshMs() {
        return data.bluemapRailsRefreshMs;
    }

    public static void setBluemapRailsRefreshMs(int refreshMs) {
        data.bluemapRailsRefreshMs = clampRefreshMs(refreshMs, 1000, 300000, 15000);
    }

    public static int bluemapRoutesRefreshMs() {
        return data.bluemapRoutesRefreshMs;
    }

    public static void setBluemapRoutesRefreshMs(int refreshMs) {
        data.bluemapRoutesRefreshMs = clampRefreshMs(refreshMs, 1000, 300000, 30000);
    }

    public static int[] bluemapDimensions() {
        return data.bluemapDimensions;
    }

    public static void setBluemapDimensions(int[] dimensions) {
        data.bluemapDimensions = sanitizeBluemapDimensions(dimensions);
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
        root.addProperty("camera_tilt_enabled", data.cameraTiltEnabled);
        root.addProperty("camera_tilt_strength", data.cameraTiltStrength);
        root.addProperty("dashboard_route_list_mode", data.dashboardRouteListMode.name());
        root.addProperty("dashboard_map_auto_save_enabled", data.dashboardMapAutoSaveEnabled);
        root.addProperty("dashboard_rail_overlay_mode", data.dashboardRailOverlayMode.name());
        root.addProperty("dashboard_rail_overlay_cull_max_per_cell", data.dashboardRailOverlayCullMaxPerCell);
        root.addProperty("system_map_overlay_cache_enabled", data.systemMapOverlayCacheEnabled);
        root.addProperty("system_map_overlay_cache_persist_enabled", data.systemMapOverlayCachePersistEnabled);
        root.addProperty("system_map_language_display", data.systemMapLanguageDisplay.name());
        root.addProperty("track_color_mode", data.trackColorMode.name());
        root.addProperty("bluemap_enabled", data.bluemapEnabled);
        root.addProperty("bluemap_show_rails", data.bluemapShowRails);
        root.addProperty("bluemap_show_trains", data.bluemapShowTrains);
        root.addProperty("bluemap_rail_layer_speed_threshold", data.bluemapRailLayerSpeedThreshold);
        root.addProperty("bluemap_rail_layer_speed_gradient", data.bluemapRailLayerSpeedGradient);
        root.addProperty("bluemap_rail_layer_signals", data.bluemapRailLayerSignals);
        root.addProperty("bluemap_rail_layer_speed_labels", data.bluemapRailLayerSpeedLabels);
        root.addProperty("bluemap_signals_dashed_multicolor", data.bluemapSignalsDashedMulticolor);
        root.addProperty("bluemap_trains_refresh_ms", data.bluemapTrainsRefreshMs);
        root.addProperty("bluemap_rails_refresh_ms", data.bluemapRailsRefreshMs);
        root.addProperty("bluemap_routes_refresh_ms", data.bluemapRoutesRefreshMs);

        final JsonArray bluemapDimensions = new JsonArray();
        if (data.bluemapDimensions != null) {
            for (final int dim : data.bluemapDimensions) {
                bluemapDimensions.add(dim);
            }
        }
        root.add("bluemap_dimensions", bluemapDimensions);

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

    /**
     * Ensures the on-disk config exists so users can discover available options even if they never open the UI.
     */
    public static void ensureConfigExists() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                save();
            }
        } catch (Exception ignored) {
        }
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
            if (root.has("system_map_overlay_cache_enabled")) {
                loaded.systemMapOverlayCacheEnabled = root.get("system_map_overlay_cache_enabled").getAsBoolean();
            }
            if (root.has("system_map_overlay_cache_persist_enabled")) {
                loaded.systemMapOverlayCachePersistEnabled = root.get("system_map_overlay_cache_persist_enabled").getAsBoolean();
            }
            if (root.has("system_map_language_display")) {
                loaded.systemMapLanguageDisplay = SystemMapLanguageDisplay.fromString(root.get("system_map_language_display").getAsString());
            }

            if (root.has("track_color_mode")) {
                loaded.trackColorMode = TrackColorMode.fromString(root.get("track_color_mode").getAsString());
            }

            if (root.has("track_color_custom_gradient") && root.get("track_color_custom_gradient").isJsonArray()) {
                loaded.trackColorCustomGradientStops = sanitizeTrackColorStops(parseTrackColorStops(root.getAsJsonArray("track_color_custom_gradient")));
            }

            if (root.has("bluemap_enabled")) {
                loaded.bluemapEnabled = root.get("bluemap_enabled").getAsBoolean();
            }
            if (root.has("bluemap_show_rails")) {
                loaded.bluemapShowRails = root.get("bluemap_show_rails").getAsBoolean();
            }
            if (root.has("bluemap_show_trains")) {
                loaded.bluemapShowTrains = root.get("bluemap_show_trains").getAsBoolean();
            }
            if (root.has("bluemap_rail_layer_speed_threshold")) {
                loaded.bluemapRailLayerSpeedThreshold = root.get("bluemap_rail_layer_speed_threshold").getAsBoolean();
            }
            if (root.has("bluemap_rail_layer_speed_gradient")) {
                loaded.bluemapRailLayerSpeedGradient = root.get("bluemap_rail_layer_speed_gradient").getAsBoolean();
            }
            if (root.has("bluemap_rail_layer_signals")) {
                loaded.bluemapRailLayerSignals = root.get("bluemap_rail_layer_signals").getAsBoolean();
            }
            if (root.has("bluemap_rail_layer_speed_labels")) {
                loaded.bluemapRailLayerSpeedLabels = root.get("bluemap_rail_layer_speed_labels").getAsBoolean();
            }
            if (root.has("bluemap_signals_dashed_multicolor")) {
                loaded.bluemapSignalsDashedMulticolor = root.get("bluemap_signals_dashed_multicolor").getAsBoolean();
            }
            if (root.has("bluemap_trains_refresh_ms")) {
                loaded.bluemapTrainsRefreshMs = clampRefreshMs(root.get("bluemap_trains_refresh_ms").getAsInt(), 200, 10000, 500);
            }
            if (root.has("bluemap_rails_refresh_ms")) {
                loaded.bluemapRailsRefreshMs = clampRefreshMs(root.get("bluemap_rails_refresh_ms").getAsInt(), 1000, 300000, 15000);
            }
            if (root.has("bluemap_routes_refresh_ms")) {
                loaded.bluemapRoutesRefreshMs = clampRefreshMs(root.get("bluemap_routes_refresh_ms").getAsInt(), 1000, 300000, 30000);
            }
            if (root.has("bluemap_dimensions")) {
                loaded.bluemapDimensions = sanitizeBluemapDimensions(parseBluemapDimensions(root.get("bluemap_dimensions")));
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

    private static int clampRefreshMs(int refreshMs, int min, int max, int fallback) {
        if (refreshMs <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, refreshMs));
    }

    private static int[] parseBluemapDimensions(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new int[0];
        }
        if (!element.isJsonArray()) {
            return new int[0];
        }

        final JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) {
            return new int[0];
        }

        final int[] dims = new int[array.size()];
        int count = 0;
        for (int i = 0; i < array.size(); i++) {
            final JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive()) {
                continue;
            }
            try {
                dims[count++] = item.getAsInt();
            } catch (Exception ignored) {
            }
        }

        if (count <= 0) {
            return new int[0];
        }
        if (count == dims.length) {
            return dims;
        }
        final int[] trimmed = new int[count];
        System.arraycopy(dims, 0, trimmed, 0, count);
        return trimmed;
    }

    private static int[] sanitizeBluemapDimensions(int[] dimensions) {
        final int[] source = dimensions == null ? new int[0] : dimensions;

        final java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>();
        for (final int dim : source) {
            if (dim < 0 || dim > 64) {
                continue;
            }
            unique.add(dim);
        }

        if (unique.isEmpty()) {
            return new int[]{0};
        }

        final int[] sanitized = new int[unique.size()];
        int idx = 0;
        for (final Integer dim : unique) {
            sanitized[idx++] = dim;
        }
        return sanitized;
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
        private boolean cameraTiltEnabled = true;
        private double cameraTiltStrength = 1D;
        private DashboardRouteListMode dashboardRouteListMode = DashboardRouteListMode.FOLDERS;
        private boolean dashboardMapAutoSaveEnabled = true;
        private DashboardRailOverlayMode dashboardRailOverlayMode = DashboardRailOverlayMode.ALL;
        private int dashboardRailOverlayCullMaxPerCell = 8;
        private boolean systemMapOverlayCacheEnabled = false;
        private boolean systemMapOverlayCachePersistEnabled = false;
        private SystemMapLanguageDisplay systemMapLanguageDisplay = SystemMapLanguageDisplay.NORMAL;
        private TrackColorMode trackColorMode = TrackColorMode.OPEN_RAILWAY_MAP;
        private TrackColorStop[] trackColorCustomGradientStops = defaultTrackColorCustomGradientStops();
        private boolean bluemapEnabled;
        private boolean bluemapShowRails = true;
        private boolean bluemapShowTrains = true;
        private boolean bluemapRailLayerSpeedThreshold = true;
        private boolean bluemapRailLayerSpeedGradient;
        private boolean bluemapRailLayerSignals = true;
        private boolean bluemapRailLayerSpeedLabels = true;
        private boolean bluemapSignalsDashedMulticolor = true;
        private int bluemapTrainsRefreshMs = 500;
        private int bluemapRailsRefreshMs = 15000;
        private int bluemapRoutesRefreshMs = 30000;
        private int[] bluemapDimensions = new int[]{0};
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
