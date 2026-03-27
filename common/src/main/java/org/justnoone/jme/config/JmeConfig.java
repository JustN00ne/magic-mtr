package org.justnoone.jme.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static String formatSpeedLabel(int speedKmh) {
        if (useMph()) {
            return toMph(speedKmh) + " mph";
        }
        return speedKmh + " km/h";
    }

    public static int toMph(int kmh) {
        return Math.max(1, (int) Math.round(kmh * 0.621371D));
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
        } catch (Exception ignored) {
        }

        return loaded;
    }

    public static String getSystemMapCustomCss() {
        return readOptionalText(SYSTEM_MAP_CSS_PATH);
    }

    public static String getSystemMapCustomJs() {
        return readOptionalText(SYSTEM_MAP_JS_PATH);
    }

    private static String readOptionalText(Path path) {
        try {
            return Files.exists(path) ? jme$readText(path).trim() : "";
        } catch (Exception ignored) {
            return "";
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

    private static final class Data {
        private boolean useMph;
        private boolean cameraTiltEnabled = true;
        private double cameraTiltStrength = 1D;
        private DashboardRouteListMode dashboardRouteListMode = DashboardRouteListMode.FOLDERS;
        private boolean dashboardMapAutoSaveEnabled = true;
        private DashboardRailOverlayMode dashboardRailOverlayMode = DashboardRailOverlayMode.ALL;
        private int dashboardRailOverlayCullMaxPerCell = 8;
        private boolean systemMapOverlayCacheEnabled = true;
        private boolean systemMapOverlayCachePersistEnabled = true;
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
}
