package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.util.FlexibleTimetables;
import org.justnoone.jme.util.HeadcodePriority;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Route settings: the flexible timetable (a departure offset from the train's depot departure for each stop, and a
 * minimum dwell time, see {@link FlexibleTimetables}) and the headcode class used for overtaking (see
 * {@link HeadcodePriority} and {@link Overtaking}).
 * <p>
 * Stored in {@code config/magic/route_timetables.json} on the server and pushed to clients with the route state
 * snapshot.
 */
public final class RouteTimetableRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("route_timetables.json");

    private static final Map<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean anyHeadcodeClass;
    private static volatile boolean loaded;
    private static volatile boolean persistenceEnabled = true;

    private RouteTimetableRegistry() {
    }

    @Nullable
    public static Entry get(long routeId) {
        ensureLoaded();
        return ENTRIES.isEmpty() ? null : ENTRIES.get(routeId);
    }

    public static boolean isEmpty() {
        ensureLoaded();
        return ENTRIES.isEmpty();
    }

    /**
     * @return the headcode class of the route, or {@link HeadcodePriority#NONE}
     */
    public static int getHeadcodeClass(long routeId) {
        final Entry entry = get(routeId);
        return entry == null ? HeadcodePriority.NONE : entry.headcodeClass;
    }

    /**
     * @return whether any route has a headcode class (otherwise no train overtakes)
     */
    public static boolean hasHeadcodeClasses() {
        ensureLoaded();
        return anyHeadcodeClass;
    }

    /**
     * @return {@code null} on success, otherwise why the timetable was rejected (nothing is changed then)
     */
    @Nullable
    public static synchronized String set(long routeId, Entry entry) {
        ensureLoaded();
        if (routeId == 0) {
            return "Unknown route";
        }
        final String error = FlexibleTimetables.validate(entry.offsets, entry.minimumDwellMillis);
        if (error != null) {
            return error;
        }
        if (!HeadcodePriority.isValid(entry.headcodeClass)) {
            return "Unknown headcode class " + entry.headcodeClass;
        }
        ENTRIES.put(routeId, entry);
        updateHeadcodeClasses();
        save();
        return null;
    }

    public static synchronized void remove(long routeId) {
        ensureLoaded();
        if (ENTRIES.remove(routeId) != null) {
            updateHeadcodeClasses();
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
        updateHeadcodeClasses();
        save();
    }

    /**
     * Turned off on clients connected to a remote server, so the server's snapshot never overwrites the client's own
     * file.
     */
    public static void setPersistenceEnabled(boolean enabled) {
        persistenceEnabled = enabled;
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
    }

    private static void updateHeadcodeClasses() {
        boolean any = false;
        for (final Entry entry : ENTRIES.values()) {
            any |= entry.headcodeClass != HeadcodePriority.NONE;
        }
        anyHeadcodeClass = any;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            synchronized (RouteTimetableRegistry.class) {
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
                    final long routeId = Long.parseLong(mapEntry.getKey());
                    final JsonObject object = mapEntry.getValue().getAsJsonObject();
                    final long minimumDwell = object.has("min_dwell_ms") ? object.get("min_dwell_ms").getAsLong() : 0;
                    final int headcodeClass = object.has("headcode_class") ? object.get("headcode_class").getAsInt() : HeadcodePriority.NONE;
                    final JsonArray stops = object.has("stops") ? object.getAsJsonArray("stops") : new JsonArray();
                    final long[] platformIds = new long[stops.size()];
                    final long[] offsets = new long[stops.size()];
                    for (int i = 0; i < stops.size(); i++) {
                        final JsonObject stop = stops.get(i).getAsJsonObject();
                        platformIds[i] = stop.has("platform") ? stop.get("platform").getAsLong() : 0;
                        offsets[i] = stop.has("departure_ms") ? stop.get("departure_ms").getAsLong() : FlexibleTimetables.NO_OFFSET;
                    }
                    ENTRIES.put(routeId, new Entry(minimumDwell, headcodeClass, platformIds, offsets));
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        updateHeadcodeClasses();
    }

    private static void save() {
        if (!persistenceEnabled) {
            return;
        }
        final JsonObject root = new JsonObject();
        ENTRIES.forEach((routeId, entry) -> {
            final JsonObject object = new JsonObject();
            object.addProperty("min_dwell_ms", entry.minimumDwellMillis);
            object.addProperty("headcode_class", entry.headcodeClass);
            final JsonArray stops = new JsonArray();
            for (int i = 0; i < entry.offsets.length; i++) {
                final JsonObject stop = new JsonObject();
                stop.addProperty("platform", entry.platformIds[i]);
                if (entry.offsets[i] != FlexibleTimetables.NO_OFFSET) {
                    stop.addProperty("departure_ms", entry.offsets[i]);
                }
                stops.add(stop);
            }
            object.add("stops", stops);
            root.add(Long.toString(routeId), object);
        });
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static final class Entry {

        public final long minimumDwellMillis;
        /**
         * The route's headcode class (9, 1, 2 or 3), or {@link HeadcodePriority#NONE}: trains of a higher priority
         * overtake those of a lower one at stations with alternative platforms.
         */
        public final int headcodeClass;
        /**
         * The platform of each stop of the route when the timetable was saved: an offset only applies while its stop
         * is still the same platform, so stops added to or removed from the route do not shift the times.
         */
        public final long[] platformIds;
        /**
         * Departure from each stop, after the depot departure, or {@link FlexibleTimetables#NO_OFFSET}.
         */
        public final long[] offsets;

        public Entry(long minimumDwellMillis, int headcodeClass, long[] platformIds, long[] offsets) {
            this.minimumDwellMillis = Math.max(0, minimumDwellMillis);
            this.headcodeClass = headcodeClass;
            final int count = Math.min(platformIds.length, offsets.length);
            this.platformIds = Arrays.copyOf(platformIds, count);
            this.offsets = Arrays.copyOf(offsets, count);
        }

        /**
         * @return the departure offset of the route's stop {@code stopIndex}, or {@link FlexibleTimetables#NO_OFFSET}
         */
        public long getOffset(int stopIndex, long platformId) {
            return stopIndex >= 0 && stopIndex < offsets.length && platformIds[stopIndex] == platformId ? offsets[stopIndex] : FlexibleTimetables.NO_OFFSET;
        }
    }
}
