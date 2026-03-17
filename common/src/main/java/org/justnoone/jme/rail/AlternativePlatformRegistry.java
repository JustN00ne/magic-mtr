package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AlternativePlatformRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("alternative_platforms.json", "jme_alternative_platforms.json");
    private static final Map<String, Set<Long>> ALTERNATIVES = new ConcurrentHashMap<>();
    // Keep the feature implemented in code, but disabled at runtime until it is ready again.
    private static final boolean FEATURE_ENABLED = true;
    private static boolean loaded;

    private AlternativePlatformRegistry() {
    }

    public static boolean isEnabled() {
        return FEATURE_ENABLED;
    }

    public static synchronized boolean setAlternative(long routeId, long primaryPlatformId, long alternativePlatformId, boolean enabled) {
        if (!isEnabled()) {
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
        if (!isEnabled()) {
            return false;
        }
        ensureLoaded();
        final List<Long> alternatives = getAlternatives(routeId, primaryPlatformId);
        return setAlternative(routeId, primaryPlatformId, alternativePlatformId, !alternatives.contains(alternativePlatformId));
    }

    public static synchronized List<Long> getAlternatives(long routeId, long primaryPlatformId) {
        if (!isEnabled()) {
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
        if (!isEnabled()) {
            return false;
        }
        ensureLoaded();
        if (primaryPlatformId == 0) {
            return false;
        }

        final LinkedHashSet<Long> sanitizedAlternatives = new LinkedHashSet<>();
        if (alternativePlatformIds != null) {
            alternativePlatformIds.forEach(alternativePlatformId -> {
                if (alternativePlatformId != 0 && alternativePlatformId != primaryPlatformId) {
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
        if (!isEnabled()) {
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
        if (!isEnabled()) {
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

    public static List<Long> getCandidatePlatformIds(Route route, Platform primaryPlatform) {
        if (route == null || primaryPlatform == null) {
            return Collections.emptyList();
        }

        if (!isEnabled()) {
            return new ArrayList<>(Collections.singletonList(primaryPlatform.getId()));
        }

        final LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        final long primaryPlatformId = primaryPlatform.getId();
        candidateIds.add(primaryPlatformId);

        final List<Long> configuredAlternatives = getAlternatives(route.getId(), primaryPlatformId);
        if (configuredAlternatives.isEmpty()) {
            return new ArrayList<>(candidateIds);
        }

        if (primaryPlatform.area == null) {
            candidateIds.addAll(configuredAlternatives);
            return new ArrayList<>(candidateIds);
        }

        final LinkedHashSet<Long> stationPlatformIds = new LinkedHashSet<>();
        primaryPlatform.area.savedRails.forEach(savedRail -> {
            if (savedRail instanceof Platform) {
                stationPlatformIds.add(((Platform) savedRail).getId());
            }
        });

        // Wildcard support for big stations:
        // If the alternatives list contains -1, treat "all platforms in this station" as candidates.
        // This lets users support 100+ platforms without enumerating every ID.
        if (configuredAlternatives.contains(-1L)) {
            candidateIds.addAll(stationPlatformIds);
            return new ArrayList<>(candidateIds);
        }

        configuredAlternatives.forEach(candidateId -> {
            if (stationPlatformIds.contains(candidateId)) {
                candidateIds.add(candidateId);
            }
        });

        return new ArrayList<>(candidateIds);
    }

    public static Platform choosePlatform(Route route, Platform primaryPlatform, long previousPlatformId, Map<Long, Platform> platformIdMap) {
        return choosePlatform(route, primaryPlatform, previousPlatformId, platformIdMap, Collections.emptyMap());
    }

    public static Platform choosePlatform(Route route, Platform primaryPlatform, long previousPlatformId, Map<Long, Platform> platformIdMap, Map<Long, Integer> reservationCounts) {
        if (!isEnabled()) {
            return primaryPlatform;
        }
        if (route == null || primaryPlatform == null || platformIdMap == null || platformIdMap.isEmpty()) {
            return primaryPlatform;
        }

        final List<Long> candidateIds = getCandidatePlatformIds(route, primaryPlatform);
        if (candidateIds.isEmpty()) {
            return primaryPlatform;
        }

        final long primaryPlatformId = primaryPlatform.getId();
        final boolean primaryRepeatsPrevious = primaryPlatformId == previousPlatformId;
        final int primaryReservations = reservationCounts.getOrDefault(primaryPlatformId, 0);

        // Keep vanilla behavior for turnback/reversal compatibility: if this segment
        // naturally repeats the previous platform, keep the primary platform.
        if (primaryRepeatsPrevious) {
            return primaryPlatform;
        }

        // Primary platform is always first priority unless it is currently reserved.
        if (primaryReservations <= 0) {
            return primaryPlatform;
        }

        Platform fallbackAlternative = null;

        for (int i = 0; i < candidateIds.size(); i++) {
            final long candidateId = candidateIds.get(i);
            if (candidateId == primaryPlatformId) {
                continue;
            }

            final Platform candidate = platformIdMap.get(candidateId);
            if (candidate == null) {
                continue;
            }

            if (reservationCounts.getOrDefault(candidateId, 0) <= 0) {
                return candidate;
            }

            if (fallbackAlternative == null) {
                fallbackAlternative = candidate;
            }
        }

        return fallbackAlternative == null ? primaryPlatform : fallbackAlternative;
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        if (!isEnabled()) {
            ALTERNATIVES.clear();
            return;
        }
        load();
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
