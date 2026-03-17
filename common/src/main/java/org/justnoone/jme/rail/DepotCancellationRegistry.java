package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DepotCancellationRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("depot_cancellations.json", "jme_depot_cancellations.json");
    private static final int MIN_THRESHOLD_MINUTES = 1;
    private static final int MAX_THRESHOLD_MINUTES = 24 * 60;
    private static final int DEFAULT_THRESHOLD_MINUTES = 120;

    private static final Map<Long, Settings> SETTINGS_BY_DEPOT = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> RETURN_PENDING_BY_SIDING = new ConcurrentHashMap<>();
    private static boolean loaded;

    private DepotCancellationRegistry() {
    }

    public static synchronized Settings get(long depotId) {
        ensureLoaded();
        return SETTINGS_BY_DEPOT.getOrDefault(depotId, Settings.DEFAULT);
    }

    public static synchronized void set(long depotId, Settings settings) {
        ensureLoaded();
        if (depotId == 0 || settings == null) {
            return;
        }

        SETTINGS_BY_DEPOT.put(depotId, sanitize(settings));
        save();
    }

    public static synchronized Settings getForSiding(Siding siding) {
        ensureLoaded();
        if (siding == null || !(siding.area instanceof Depot)) {
            return Settings.DEFAULT;
        }
        return get(((Depot) siding.area).getId());
    }

    public static long getThresholdMillis(Settings settings) {
        if (settings == null) {
            return DEFAULT_THRESHOLD_MINUTES * 60_000L;
        }
        return (long) clampThresholdMinutes(settings.thresholdMinutes) * 60_000L;
    }

    public static void markReturnPending(long sidingId, long vehicleId) {
        if (sidingId == 0 || vehicleId == 0) {
            return;
        }
        RETURN_PENDING_BY_SIDING.computeIfAbsent(sidingId, ignored -> ConcurrentHashMap.newKeySet()).add(vehicleId);
    }

    public static boolean isReturnPending(long sidingId, long vehicleId) {
        final Set<Long> vehicleIds = RETURN_PENDING_BY_SIDING.get(sidingId);
        return vehicleIds != null && vehicleIds.contains(vehicleId);
    }

    public static void clearReturnPending(long sidingId, long vehicleId) {
        final Set<Long> vehicleIds = RETURN_PENDING_BY_SIDING.get(sidingId);
        if (vehicleIds == null) {
            return;
        }
        vehicleIds.remove(vehicleId);
        if (vehicleIds.isEmpty()) {
            RETURN_PENDING_BY_SIDING.remove(sidingId);
        }
    }

    public static void clearPendingForSiding(long sidingId) {
        RETURN_PENDING_BY_SIDING.remove(sidingId);
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
        RETURN_PENDING_BY_SIDING.clear();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private static Settings sanitize(Settings settings) {
        return new Settings(settings.enabled, clampThresholdMinutes(settings.thresholdMinutes), settings.action == null ? Action.DESPAWN : settings.action);
    }

    private static int clampThresholdMinutes(int thresholdMinutes) {
        return Math.max(MIN_THRESHOLD_MINUTES, Math.min(MAX_THRESHOLD_MINUTES, thresholdMinutes));
    }

    private static void load() {
        SETTINGS_BY_DEPOT.clear();
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
                try {
                    final long depotId = Long.parseLong(entry.getKey());
                    final JsonObject settingsObject = entry.getValue().getAsJsonObject();
                    final boolean enabled = settingsObject.has("enabled") && settingsObject.get("enabled").getAsBoolean();
                    final int thresholdMinutes = settingsObject.has("threshold_minutes") ? settingsObject.get("threshold_minutes").getAsInt() : DEFAULT_THRESHOLD_MINUTES;
                    final Action action = settingsObject.has("action") ? Action.fromId(settingsObject.get("action").getAsString()) : Action.DESPAWN;
                    SETTINGS_BY_DEPOT.put(depotId, sanitize(new Settings(enabled, thresholdMinutes, action)));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        final JsonObject root = new JsonObject();
        SETTINGS_BY_DEPOT.forEach((depotId, settings) -> {
            final Settings sanitizedSettings = sanitize(settings);
            final JsonObject settingsObject = new JsonObject();
            settingsObject.addProperty("enabled", sanitizedSettings.enabled);
            settingsObject.addProperty("threshold_minutes", sanitizedSettings.thresholdMinutes);
            settingsObject.addProperty("action", sanitizedSettings.action.serializedId);
            root.add(Long.toString(depotId), settingsObject);
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

    public static final class Settings {

        public static final Settings DEFAULT = new Settings(false, DEFAULT_THRESHOLD_MINUTES, Action.DESPAWN);

        public final boolean enabled;
        public final int thresholdMinutes;
        public final Action action;

        public Settings(boolean enabled, int thresholdMinutes, Action action) {
            this.enabled = enabled;
            this.thresholdMinutes = thresholdMinutes;
            this.action = action == null ? Action.DESPAWN : action;
        }
    }

    public enum Action {
        DESPAWN("despawn"),
        RETURN_TO_DEPOT("return_to_depot");

        private final String serializedId;

        Action(String serializedId) {
            this.serializedId = serializedId;
        }

        public String getSerializedId() {
            return serializedId;
        }

        public static Action fromId(String id) {
            if (id == null) {
                return DESPAWN;
            }
            for (final Action action : values()) {
                if (action.serializedId.equalsIgnoreCase(id)) {
                    return action;
                }
            }
            return DESPAWN;
        }
    }
}
