package org.justnoone.jme.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.util.RouteTypeKeys;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteTypeOverrideConfig {

    public static final String TRAIN_NORMAL = RouteTypeKeys.TRAIN_NORMAL;
    public static final String TRAIN_LIGHT_RAIL = RouteTypeKeys.TRAIN_LIGHT_RAIL;
    public static final String TRAIN_HIGH_SPEED = RouteTypeKeys.TRAIN_HIGH_SPEED;
    public static final String TRAIN_METRO = RouteTypeKeys.TRAIN_METRO;
    public static final String TRAIN_BUS = RouteTypeKeys.TRAIN_BUS;
    public static final String TRAIN_TRAM = RouteTypeKeys.TRAIN_TRAM;
    public static final String TRAIN_SBAHN = RouteTypeKeys.TRAIN_SBAHN;
    public static final String TRAIN_REGIONAL = RouteTypeKeys.TRAIN_REGIONAL;
    public static final String TRAIN_INTERCITY = RouteTypeKeys.TRAIN_INTERCITY;
    public static final String TRAIN_TROLLEYBUS = RouteTypeKeys.TRAIN_TROLLEYBUS;
    public static final String TRAIN_FREIGHT = RouteTypeKeys.TRAIN_FREIGHT;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("route_types.json", "route_types.json", "jme_route_types.json");
    private static final Map<String, String> ROUTE_TYPE_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ALTERNATIVE_PLATFORMS_ENABLED_BY_ID = new ConcurrentHashMap<>();
    // False while connected to a remote server: the in-memory state then mirrors the server's snapshot and must not overwrite the local file.
    private static volatile boolean persistenceEnabled = true;

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
        if (!persistenceEnabled) {
            return;
        }
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

    public static synchronized Map<String, String> getRouteTypesSnapshot() {
        return new java.util.TreeMap<>(ROUTE_TYPE_BY_ID);
    }

    public static synchronized Map<String, Boolean> getAlternativePlatformsEnabledSnapshot() {
        return new java.util.TreeMap<>(ALTERNATIVE_PLATFORMS_ENABLED_BY_ID);
    }

    /**
     * Replaces the in-memory state with a snapshot received from the server. Nothing is written to disk.
     */
    public static synchronized void applySyncedSnapshot(Map<String, String> routeTypes, Map<String, Boolean> alternativePlatformsEnabled) {
        ROUTE_TYPE_BY_ID.clear();
        if (routeTypes != null) {
            routeTypes.forEach((routeId, routeType) -> {
                final String normalizedRouteId = normalizeRouteId(routeId);
                final String normalizedRouteType = normalizeTrainType(routeType);
                if (!normalizedRouteId.isEmpty() && !normalizedRouteType.isEmpty()) {
                    ROUTE_TYPE_BY_ID.put(normalizedRouteId, normalizedRouteType);
                }
            });
        }
        ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.clear();
        if (alternativePlatformsEnabled != null) {
            alternativePlatformsEnabled.forEach((routeId, enabled) -> {
                final String normalizedRouteId = normalizeRouteId(routeId);
                if (!normalizedRouteId.isEmpty() && enabled != null) {
                    ALTERNATIVE_PLATFORMS_ENABLED_BY_ID.put(normalizedRouteId, enabled);
                }
            });
        }
    }

    public static void setPersistenceEnabled(boolean enabled) {
        persistenceEnabled = enabled;
    }

    public static String normalizeRouteId(String routeId) {
        return RouteTypeKeys.normalizeRouteId(routeId);
    }

    public static String normalizeTrainType(String routeType) {
        return RouteTypeKeys.normalizeTrainType(routeType);
    }
}