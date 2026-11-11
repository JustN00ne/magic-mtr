package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.util.CronExpression;
import org.justnoone.jme.util.CronTimetables;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cron timetables, per depot. A depot with an enabled cron timetable takes its departures from the cron expression
 * instead of MTR's hourly frequencies or real-time departure list; every other depot keeps working exactly as before.
 * <p>
 * Stored in {@code config/magic/cron_timetables.json} on the server and pushed to clients with the route state
 * snapshot, so the depot screen shows the server's timetable in multiplayer.
 */
public final class CronTimetableRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("cron_timetables.json", "jme_cron_timetables.json");

    private static final Map<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;
    private static volatile boolean persistenceEnabled = true;

    private CronTimetableRegistry() {
    }

    /**
     * @return the enabled, valid timetable of this depot, or {@code null} if the depot uses MTR's own scheduling
     */
    @Nullable
    public static Entry getActive(long depotId) {
        ensureLoaded();
        if (ENTRIES.isEmpty()) {
            return null;
        }
        final Entry entry = ENTRIES.get(depotId);
        return entry != null && entry.enabled && entry.cron != null ? entry : null;
    }

    /**
     * @return the stored timetable of this depot (enabled or not), or {@code null}
     */
    @Nullable
    public static Entry get(long depotId) {
        ensureLoaded();
        return ENTRIES.get(depotId);
    }

    public static boolean isEmpty() {
        ensureLoaded();
        return ENTRIES.isEmpty();
    }

    /**
     * @return {@code null} on success, otherwise why the timetable was rejected (nothing is changed then)
     */
    @Nullable
    public static synchronized String set(long depotId, String expression, String zoneId, boolean enabled) {
        ensureLoaded();
        if (depotId == 0) {
            return "Unknown depot";
        }
        final String expressionError = CronExpression.validate(expression);
        if (expressionError != null) {
            return expressionError;
        }
        final String zoneError = CronTimetables.validateZone(zoneId);
        if (zoneError != null) {
            return zoneError;
        }
        ENTRIES.put(depotId, new Entry(expression, zoneId, enabled));
        save();
        return null;
    }

    public static synchronized void remove(long depotId) {
        ensureLoaded();
        if (ENTRIES.remove(depotId) != null) {
            save();
        }
    }

    public static synchronized Map<Long, Entry> getSnapshot() {
        ensureLoaded();
        return new HashMap<>(ENTRIES);
    }

    /**
     * Replaces the timetables with the server's (client side, multiplayer).
     */
    public static synchronized void applySyncedSnapshot(Map<Long, Entry> snapshot) {
        loaded = true;
        ENTRIES.clear();
        if (snapshot != null) {
            ENTRIES.putAll(snapshot);
        }
        save();
    }

    /**
     * Turned off on clients connected to a remote server, so the server's snapshot never overwrites the client's own
     * file (the client may host its own worlds too).
     */
    public static void setPersistenceEnabled(boolean enabled) {
        persistenceEnabled = enabled;
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (CronTimetableRegistry.class) {
                if (!loaded) {
                    load();
                    loaded = true;
                }
            }
        }
    }

    private static void load() {
        ENTRIES.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            final JsonElement element = new JsonParser().parse(new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return;
            }
            element.getAsJsonObject().entrySet().forEach(mapEntry -> {
                try {
                    final long depotId = Long.parseLong(mapEntry.getKey());
                    final JsonObject object = mapEntry.getValue().getAsJsonObject();
                    final String expression = object.has("cron") ? object.get("cron").getAsString() : "";
                    final String zone = object.has("time_zone") ? object.get("time_zone").getAsString() : "";
                    final boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
                    // Invalid entries (eg hand-edited) are kept, so they are not lost, but stay inactive.
                    ENTRIES.put(depotId, new Entry(expression, zone, enabled));
                } catch (Exception ignored) {
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
        ENTRIES.forEach((depotId, entry) -> {
            final JsonObject object = new JsonObject();
            object.addProperty("cron", entry.expression);
            object.addProperty("time_zone", entry.zoneId);
            object.addProperty("enabled", entry.enabled);
            root.add(Long.toString(depotId), object);
        });
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static final class Entry {

        public final String expression;
        /**
         * Blank means the server's time zone.
         */
        public final String zoneId;
        public final boolean enabled;
        /**
         * {@code null} if the stored expression is invalid.
         */
        @Nullable
        public final CronExpression cron;
        public final ZoneId zone;

        public Entry(String expression, String zoneId, boolean enabled) {
            this.expression = expression == null ? "" : expression.trim();
            this.zoneId = zoneId == null ? "" : zoneId.trim();
            this.enabled = enabled;
            CronExpression parsed;
            try {
                parsed = CronExpression.parse(this.expression);
            } catch (IllegalArgumentException e) {
                parsed = null;
            }
            cron = parsed;
            zone = CronTimetables.resolveZone(this.zoneId);
        }
    }
}
