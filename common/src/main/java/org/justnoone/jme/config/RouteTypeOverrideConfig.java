package org.justnoone.jme.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteTypeOverrideConfig {

    public static final String TRAIN_NORMAL = "train_normal";
    public static final String TRAIN_LIGHT_RAIL = "train_light_rail";
    public static final String TRAIN_HIGH_SPEED = "train_high_speed";
    public static final String TRAIN_METRO = "train_metro";
    public static final String TRAIN_BUS = "train_bus";
    public static final String TRAIN_TRAM = "train_tram";
    public static final String TRAIN_SBAHN = "train_sbahn";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("route_types.json", "route_types.json", "jme_route_types.json");
    private static final Map<String, String> ROUTE_TYPE_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ALTERNATIVE_PLATFORMS_ENABLED_BY_ID = new ConcurrentHashMap<>();

    static {
        reload();
    }

    private RouteTypeOverrideConfig() {
    }

    public static synchronized void reload() {
        ROUTE_TYPE_BY_ID.clear();
        ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
            final JsonObject routeTypes = root.has("route_types") && root.get("route_types").isJsonObject()
                    ? root.getAsJsonObject("route_types")
                    : root;

            // Route type overrides (strings).
            for (final Map.Entry<String, JsonElement> entry : routeTypes.entrySet()) {
                final String routeId = normalizeRouteId(entry.getKey());
                if (routeId.isEmpty()) {
                    continue;
                }

                final JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    continue;
                }

                final String routeType = normalizeTrainType(value.getAsString());
                if (!routeType.isEmpty()) {
                    ROUTE_TYPE_BY_ID.put(routeId, routeType);
                }
            }

            // Alternative platforms route overrides (booleans).
            if (root.has("alternative_platforms_enabled") && root.get("alternative_platforms_enabled").isJsonObject()) {
                final JsonObject enabledByRoute = root.getAsJsonObject("alternative_platforms_enabled");
                for (final Map.Entry<String, JsonElement> entry : enabledByRoute.entrySet()) {
                    final String routeId = normalizeRouteId(entry.getKey());
                    if (routeId.isEmpty()) {
                        continue;
                    }
                    final JsonElement value = entry.getValue();
                    if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.put(routeId, value.getAsBoolean());
                    } catch (Exception ignored) {
                    }
                }
            } else {
                // Backward-compatible fallback: allow per-route booleans at the root level.
                for (final Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    final String routeId = normalizeRouteId(entry.getKey());
                    if (routeId.isEmpty()) {
                        continue;
                    }
                    final JsonElement value = entry.getValue();
                    if (value == null || value.isJsonNull() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        continue;
                    }
                    ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.put(routeId, value.getAsBoolean());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void save() {
        try {
            final JsonObject root = new JsonObject();
            final JsonObject routeTypes = new JsonObject();
            ROUTE_TYPE_BY_ID.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> routeTypes.addProperty(entry.getKey(), entry.getValue()));
            root.add("route_types", routeTypes);

            if (!ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.isEmpty()) {
                final JsonObject enabledByRoute = new JsonObject();
                ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> enabledByRoute.addProperty(entry.getKey(), entry.getValue()));
                root.add("alternative_platforms_enabled", enabledByRoute);
            }

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static synchronized String getRouteType(String routeId) {
        final String normalizedRouteId = normalizeRouteId(routeId);
        if (normalizedRouteId.isEmpty()) {
            return "";
        }
        return ROUTE_TYPE_BY_ID.getOrDefault(normalizedRouteId, "");
    }

    public static synchronized void setRouteType(String routeId, String routeType) {
        final String normalizedRouteId = normalizeRouteId(routeId);
        final String normalizedRouteType = normalizeTrainType(routeType);
        if (normalizedRouteId.isEmpty()) {
            return;
        }

        if (normalizedRouteType.isEmpty()) {
            // Empty/unknown values mean "clear override" (the base MTR enum type will be used).
            if (ROUTE_TYPE_BY_ID.remove(normalizedRouteId) != null) {
                save();
            }
            return;
        }

        ROUTE_TYPE_BY_ID.put(normalizedRouteId, normalizedRouteType);
        save();
    }

    public static synchronized void clearRouteType(String routeId) {
        setRouteType(routeId, "");
    }

    public static synchronized Boolean getAlternativePlatformsEnabledOverride(String routeId) {
        final String normalizedRouteId = normalizeRouteId(routeId);
        if (normalizedRouteId.isEmpty()) {
            return null;
        }
        return ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.get(normalizedRouteId);
    }

    public static synchronized boolean isAlternativePlatformsEnabled(String routeId) {
        final Boolean override = getAlternativePlatformsEnabledOverride(routeId);
        return override == null || override;
    }

    public static String normalizeRouteId(String routeId) {
        if (routeId == null) {
            return "";
        }
        return routeId.trim().toUpperCase(Locale.ENGLISH);
    }

    public static String normalizeTrainType(String routeType) {
        final String normalized = routeType == null ? "" : routeType.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "normal":
            case TRAIN_NORMAL:
                return TRAIN_NORMAL;
            case "light_rail":
            case "lightrail":
            case TRAIN_LIGHT_RAIL:
                return TRAIN_LIGHT_RAIL;
            case "high_speed":
            case "highspeed":
            case TRAIN_HIGH_SPEED:
                return TRAIN_HIGH_SPEED;
            case "metro":
            case TRAIN_METRO:
                return TRAIN_METRO;
            case "bus":
            case TRAIN_BUS:
                return TRAIN_BUS;
            case "tram":
            case TRAIN_TRAM:
                return TRAIN_TRAM;
            case "sbahn":
            case "s_bahn":
            case TRAIN_SBAHN:
                return TRAIN_SBAHN;
            default:
                return "";
        }
    }
}
