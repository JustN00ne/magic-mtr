package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.util.RouteTypeKeys;
import org.justnoone.jme.mixin.RoutePlatformDataAccessor;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class AlternativePlatformRegistry {

    /**
     * Stored in an alternatives list to mean "every compatible platform in the primary platform's station".
     */
    public static final long ALL_STATION_PLATFORMS = -1L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("alternative_platforms.json", "jme_alternative_platforms.json");
    private static final Map<String, Set<Long>> ALTERNATIVES = new ConcurrentHashMap<>();
    // Keep the feature implemented in code, but disabled at runtime until it is ready again.
    private static final boolean FEATURE_ENABLED = true;
    private static boolean loaded;
    // False while connected to a remote server: the in-memory state then mirrors the server's snapshot and must not overwrite the local file.
    private static volatile boolean persistenceEnabled = true;

    private AlternativePlatformRegistry() {
    }

    public static boolean isEnabled() {
        return FEATURE_ENABLED && JmeConfig.alternativePlatformsEnabled();
    }

    private static boolean isEnabledForRoute(long routeId) {
        if (!isEnabled()) {
            return false;
        }
        if (routeId == 0) {
            return true;
        }
        return RouteTypeOverrideConfig.isAlternativePlatformsEnabled(RouteTypeKeys.toRouteIdHex(routeId));
    }

    /**
     * A platform can stand in for the primary platform of a route stop only if it is a different platform
     * in the same station and serves the same transport mode.
     */
    public static boolean isCompatibleAlternative(Platform primaryPlatform, Platform candidatePlatform) {
        if (primaryPlatform == null || candidatePlatform == null || primaryPlatform.getId() == candidatePlatform.getId()) {
            return false;
        }
        if (primaryPlatform.area == null || candidatePlatform.area == null || primaryPlatform.area.getId() != candidatePlatform.area.getId()) {
            return false;
        }
        return primaryPlatform.getTransportMode() == candidatePlatform.getTransportMode();
    }

    /**
     * @return every platform that may be configured as an alternative for the given primary platform, in station order
     */
    public static List<Platform> getCompatiblePlatforms(Platform primaryPlatform) {
        if (primaryPlatform == null || primaryPlatform.area == null) {
            return Collections.emptyList();
        }
        final List<Platform> platforms = new ArrayList<>();
        primaryPlatform.area.savedRails.forEach(savedRail -> {
            if (savedRail instanceof Platform && isCompatibleAlternative(primaryPlatform, (Platform) savedRail)) {
                platforms.add((Platform) savedRail);
            }
        });
        return platforms;
    }

    public static synchronized boolean setAlternative(long routeId, long primaryPlatformId, long alternativePlatformId, boolean enabled) {
        if (!isEnabledForRoute(routeId)) {
            return false;
        }
        ensureLoaded();
        if (primaryPlatformId == 0 || alternativePlatformId == 0 || primaryPlatformId == alternativePlatformId) {
            return false;
        }

        final String key = getKey(routeId, primaryPlatformId);
        final Set<Long> alternatives = new LinkedHashSet<>(ALTERNATIVES.getOrDefault(key, Collections.emptySet()));
        final boolean changed = enabled ? alternatives.add(alternativePlatformId) : alternatives.remove(alternativePlatformId);

        if (!changed) {
            return false;
        }

        if (alternatives.isEmpty()) {
            ALTERNATIVES.remove(key);
        } else {
            ALTERNATIVES.put(key, alternatives);
        }

        save();
        return true;
    }

    public static synchronized boolean toggleAlternative(long routeId, long primaryPlatformId, long alternativePlatformId) {
        if (!isEnabledForRoute(routeId)) {
            return false;
        }
        ensureLoaded();
        final List<Long> alternatives = getAlternatives(routeId, primaryPlatformId);
        return setAlternative(routeId, primaryPlatformId, alternativePlatformId, !alternatives.contains(alternativePlatformId));
    }

    public static synchronized List<Long> getAlternatives(long routeId, long primaryPlatformId) {
        if (!isEnabledForRoute(routeId)) {
            return Collections.emptyList();
        }
        ensureLoaded();
        final Set<Long> alternatives = ALTERNATIVES.get(getKey(routeId, primaryPlatformId));
        if (alternatives == null || alternatives.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(alternatives);
    }

    public static synchronized boolean setAlternatives(long routeId, long primaryPlatformId, List<Long> alternativePlatformIds) {
        if (!isEnabledForRoute(routeId)) {
            return false;
        }
        ensureLoaded();
        if (primaryPlatformId == 0) {
            return false;
        }

        final LinkedHashSet<Long> sanitizedAlternatives = new LinkedHashSet<>();
        if (alternativePlatformIds != null) {
            alternativePlatformIds.forEach(alternativePlatformId -> {
                if (alternativePlatformId != null && alternativePlatformId != 0 && alternativePlatformId != primaryPlatformId) {
                    sanitizedAlternatives.add(alternativePlatformId);
                }
            });
        }

        final String key = getKey(routeId, primaryPlatformId);
        final Set<Long> previousAlternatives = ALTERNATIVES.get(key);
        if (previousAlternatives != null && previousAlternatives.equals(sanitizedAlternatives)) {
            return false;
        }
        if (previousAlternatives == null && sanitizedAlternatives.isEmpty()) {
            return false;
        }

        if (sanitizedAlternatives.isEmpty()) {
            ALTERNATIVES.remove(key);
        } else {
            ALTERNATIVES.put(key, sanitizedAlternatives);
        }

        save();
        return true;
    }

    public static synchronized List<Long> getPrimaryPlatformIds(long routeId) {
        if (!isEnabledForRoute(routeId)) {
            return Collections.emptyList();
        }
        ensureLoaded();
        final String prefix = routeId + ":";
        final LinkedHashSet<Long> primaryPlatformIds = new LinkedHashSet<>();
        ALTERNATIVES.keySet().forEach(key -> {
            if (!key.startsWith(prefix)) {
                return;
            }
            final int separatorIndex = key.indexOf(':');
            if (separatorIndex < 0 || separatorIndex + 1 >= key.length()) {
                return;
            }
            try {
                primaryPlatformIds.add(Long.parseLong(key.substring(separatorIndex + 1)));
            } catch (Exception ignored) {
            }
        });
        return new ArrayList<>(primaryPlatformIds);
    }

    public static synchronized long getPrimaryForAlternative(long routeId, long alternativePlatformId) {
        if (!isEnabledForRoute(routeId)) {
            return 0;
        }
        ensureLoaded();
        if (routeId == 0 || alternativePlatformId == 0) {
            return 0;
        }

        final String prefix = routeId + ":";
        for (final Map.Entry<String, Set<Long>> entry : ALTERNATIVES.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                continue;
            }

            final Set<Long> alternatives = entry.getValue();
            if (alternatives == null || !alternatives.contains(alternativePlatformId)) {
                continue;
            }

            final int separatorIndex = key.indexOf(':');
            if (separatorIndex < 0 || separatorIndex + 1 >= key.length()) {
                continue;
            }

            try {
                final long primaryPlatformId = Long.parseLong(key.substring(separatorIndex + 1));
                if (primaryPlatformId != alternativePlatformId) {
                    return primaryPlatformId;
                }
            } catch (Exception ignored) {
            }
        }

        return 0;
    }

    /**
     * Resolves the configured alternatives of a route stop against the live station data. The primary platform is
     * always first. Alternatives that no longer exist, moved to another station or serve another transport mode are
     * dropped, so stale config entries can never send a train to an invalid platform.
     */
    public static List<Long> getCandidatePlatformIds(Route route, Platform primaryPlatform) {
        if (route == null || primaryPlatform == null) {
            return Collections.emptyList();
        }

        if (!isEnabledForRoute(route.getId())) {
            return new ArrayList<>(Collections.singletonList(primaryPlatform.getId()));
        }

        final LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        final long primaryPlatformId = primaryPlatform.getId();
        candidateIds.add(primaryPlatformId);

        final List<Long> configuredAlternatives = new ArrayList<>(getAlternatives(route.getId(), primaryPlatformId));
        // At a terminus where the next route of a depot starts (or the previous one ended), the train runs both
        // routes at this one stop: the alternatives configured on either route apply.
        for (final Route linkedRoute : getLinkedTerminusRoutes(route, primaryPlatformId)) {
            getAlternatives(linkedRoute.getId(), primaryPlatformId).forEach(alternativeId -> {
                if (!configuredAlternatives.contains(alternativeId)) {
                    configuredAlternatives.add(alternativeId);
                }
            });
        }
        if (configuredAlternatives.isEmpty() || primaryPlatform.area == null) {
            return new ArrayList<>(candidateIds);
        }

        final Map<Long, Platform> compatiblePlatforms = new LinkedHashMap<>();
        getCompatiblePlatforms(primaryPlatform).forEach(platform -> compatiblePlatforms.put(platform.getId(), platform));

        // Wildcard support for big stations: "all platforms in this station" without enumerating every ID.
        if (configuredAlternatives.contains(ALL_STATION_PLATFORMS)) {
            candidateIds.addAll(compatiblePlatforms.keySet());
            return new ArrayList<>(candidateIds);
        }

        configuredAlternatives.forEach(candidateId -> {
            if (compatiblePlatforms.containsKey(candidateId)) {
                candidateIds.add(candidateId);
            }
        });

        return new ArrayList<>(candidateIds);
    }

    /**
     * The routes that share a terminus stop with {@code route} at this platform: if it is the route's last stop, the
     * routes of the same depots that start there; if it is its first stop, those that end there.
     */
    public static List<Route> getLinkedTerminusRoutes(Route route, long platformId) {
        final List<Route> linkedRoutes = new ArrayList<>();
        final List<RoutePlatformData> stops = route == null ? null : route.getRoutePlatforms();
        if (stops == null || stops.isEmpty() || platformId == 0) {
            return linkedRoutes;
        }
        final boolean endsHere = getStopPlatformId(stops.get(stops.size() - 1)) == platformId;
        final boolean startsHere = getStopPlatformId(stops.get(0)) == platformId;
        if (!endsHere && !startsHere) {
            return linkedRoutes;
        }
        for (final Depot depot : route.depots) {
            for (final Route otherRoute : depot.routes) {
                final List<RoutePlatformData> otherStops = otherRoute == null ? null : otherRoute.getRoutePlatforms();
                if (otherRoute == route || otherStops == null || otherStops.isEmpty() || linkedRoutes.contains(otherRoute)) {
                    continue;
                }
                if (endsHere && getStopPlatformId(otherStops.get(0)) == platformId || startsHere && getStopPlatformId(otherStops.get(otherStops.size() - 1)) == platformId) {
                    linkedRoutes.add(otherRoute);
                }
            }
        }
        return linkedRoutes;
    }

    /**
     * @return whether {@code platformId} is configured as an alternative of {@code primaryPlatformId} on any route (or
     * all of its station's platforms are), eg for a train that took it at a terminus shared by two routes
     */
    public static synchronized boolean isAlternativeOnAnyRoute(long primaryPlatformId, long platformId) {
        if (!isEnabled()) {
            return false;
        }
        ensureLoaded();
        final String suffix = ":" + primaryPlatformId;
        for (final Map.Entry<String, Set<Long>> entry : ALTERNATIVES.entrySet()) {
            if (entry.getKey().endsWith(suffix) && (entry.getValue().contains(platformId) || entry.getValue().contains(ALL_STATION_PLATFORMS))) {
                return true;
            }
        }
        return false;
    }

    private static long getStopPlatformId(RoutePlatformData routePlatformData) {
        if (routePlatformData == null) {
            return 0;
        }
        return routePlatformData.platform != null ? routePlatformData.platform.getId() : ((RoutePlatformDataAccessor) (Object) routePlatformData).jme$getPlatformId();
    }

    public static synchronized Map<String, List<Long>> getSnapshot() {
        ensureLoaded();
        final Map<String, List<Long>> snapshot = new TreeMap<>();
        ALTERNATIVES.forEach((key, alternatives) -> snapshot.put(key, new ArrayList<>(alternatives)));
        return snapshot;
    }

    /**
     * Replaces the in-memory state with a snapshot received from the server. Nothing is written to disk.
     */
    public static synchronized void applySyncedSnapshot(Map<String, List<Long>> snapshot) {
        ALTERNATIVES.clear();
        if (snapshot != null) {
            snapshot.forEach((key, alternatives) -> {
                if (key != null && alternatives != null && !alternatives.isEmpty()) {
                    ALTERNATIVES.put(key, new LinkedHashSet<>(alternatives));
                }
            });
        }
        loaded = true;
    }

    public static void setPersistenceEnabled(boolean enabled) {
        persistenceEnabled = enabled;
    }

    public static synchronized void reloadFromDisk() {
        if (!isEnabled()) {
            ALTERNATIVES.clear();
            loaded = false;
            return;
        }
        load();
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private static String getKey(long routeId, long primaryPlatformId) {
        return routeId + ":" + primaryPlatformId;
    }

    private static void load() {
        ALTERNATIVES.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonElement element = new JsonParser().parse(readText(CONFIG_PATH));
            if (!element.isJsonObject()) {
                return;
            }
            final JsonObject root = element.getAsJsonObject();
            root.entrySet().forEach(entry -> {
                final JsonElement value = entry.getValue();
                if (!value.isJsonArray()) {
                    return;
                }
                final LinkedHashSet<Long> alternatives = new LinkedHashSet<>();
                final JsonArray alternativesArray = value.getAsJsonArray();
                alternativesArray.forEach(alternativeElement -> {
                    try {
                        alternatives.add(alternativeElement.getAsLong());
                    } catch (Exception ignored) {
                    }
                });
                if (!alternatives.isEmpty()) {
                    ALTERNATIVES.put(entry.getKey(), alternatives);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        if (!persistenceEnabled) {
            return;
        }

        final JsonObject root = new JsonObject();
        ALTERNATIVES.forEach((key, alternatives) -> {
            final JsonArray alternativesArray = new JsonArray();
            alternatives.forEach(alternativesArray::add);
            root.add(key, alternativesArray);
        });

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
