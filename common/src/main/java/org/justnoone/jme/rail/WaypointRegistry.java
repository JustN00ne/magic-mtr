package org.justnoone.jme.rail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Position;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for custom waypoint nodes on the dashboard map.
 * <p>
 * Waypoints act identically to standard MTR railway nodes for track placement and routing,
 * but display on the dashboard map using the station icon/label style. Trains pass straight
 * through without stopping since waypoints are not platform entities.
 * <p>
 * Persisted to disk as a JSON file so waypoints survive world reloads.
 */
public final class WaypointRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("waypoints.json", "jme_waypoints.json");

    private static final Map<Long, Waypoint> WAYPOINTS_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Position, Waypoint> WAYPOINTS_BY_POSITION = new ConcurrentHashMap<>();
    private static boolean loaded;

    private WaypointRegistry() {
    }

    public static synchronized Waypoint get(long id) {
        ensureLoaded();
        return WAYPOINTS_BY_ID.get(id);
    }

    public static synchronized Waypoint getByPosition(Position position) {
        ensureLoaded();
        return position == null ? null : WAYPOINTS_BY_POSITION.get(position);
    }

    public static synchronized boolean isWaypoint(Position position) {
        ensureLoaded();
        return position != null && WAYPOINTS_BY_POSITION.containsKey(position);
    }

    public static synchronized Collection<Waypoint> getAll() {
        ensureLoaded();
        return Collections.unmodifiableCollection(WAYPOINTS_BY_ID.values());
    }

    public static synchronized List<Waypoint> getAllAsList() {
        ensureLoaded();
        return new ArrayList<>(WAYPOINTS_BY_ID.values());
    }

    public static synchronized int size() {
        ensureLoaded();
        return WAYPOINTS_BY_ID.size();
    }

    public static synchronized void register(long id, String name, int color, Position position) {
        ensureLoaded();
        if (id == 0 || position == null) {
            return;
        }

        final String sanitizedName = name == null || name.trim().isEmpty() ? "Waypoint" : name.trim();
        final int sanitizedColor = color == 0 ? 0xFFFFFF : (color & 0x00FFFFFF);

        final Waypoint existing = WAYPOINTS_BY_ID.get(id);
        if (existing != null) {
            WAYPOINTS_BY_POSITION.remove(existing.position);
        }

        final Waypoint waypoint = new Waypoint(id, sanitizedName, sanitizedColor, position);
        WAYPOINTS_BY_ID.put(id, waypoint);
        WAYPOINTS_BY_POSITION.put(position, waypoint);
        save();
    }

    public static synchronized void unregister(long id) {
        ensureLoaded();
        final Waypoint removed = WAYPOINTS_BY_ID.remove(id);
        if (removed != null) {
            WAYPOINTS_BY_POSITION.remove(removed.position);
            save();
        }
    }

    public static synchronized void clear() {
        WAYPOINTS_BY_ID.clear();
        WAYPOINTS_BY_POSITION.clear();
        save();
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    private static void load() {
        WAYPOINTS_BY_ID.clear();
        WAYPOINTS_BY_POSITION.clear();

        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final String json = new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8);
            final JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            final JsonObject root = parsed.getAsJsonObject();
            if (!root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
                return;
            }

            final JsonArray waypoints = root.getAsJsonArray("waypoints");
            for (int i = 0; i < waypoints.size(); i++) {
                final JsonElement element = waypoints.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                final JsonObject wpObj = element.getAsJsonObject();
                final long id = wpObj.has("id") ? wpObj.get("id").getAsLong() : 0;
                if (id == 0) {
                    continue;
                }

                final String name = wpObj.has("name") ? wpObj.get("name").getAsString() : "Waypoint";
                final int color = wpObj.has("color") ? wpObj.get("color").getAsInt() : 0xFFFFFF;

                final Position position = parsePosition(wpObj);
                if (position == null) {
                    continue;
                }

                final Waypoint waypoint = new Waypoint(id, name, color, position);
                WAYPOINTS_BY_ID.put(id, waypoint);
                WAYPOINTS_BY_POSITION.put(position, waypoint);
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            final JsonObject root = new JsonObject();
            root.addProperty("savedAt", System.currentTimeMillis());

            final JsonArray waypointsArray = new JsonArray();
            for (final Waypoint waypoint : WAYPOINTS_BY_ID.values()) {
                final JsonObject wpObj = new JsonObject();
                wpObj.addProperty("id", waypoint.id);
                wpObj.addProperty("name", waypoint.name);
                wpObj.addProperty("color", waypoint.color);
                wpObj.addProperty("x", waypoint.position.getX());
                wpObj.addProperty("y", waypoint.position.getY());
                wpObj.addProperty("z", waypoint.position.getZ());
                waypointsArray.add(wpObj);
            }
            root.add("waypoints", waypointsArray);

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static Position parsePosition(JsonObject obj) {
        try {
            if (!obj.has("x") || !obj.has("y") || !obj.has("z")) {
                return null;
            }
            final long x = obj.get("x").getAsLong();
            final long y = obj.get("y").getAsLong();
            final long z = obj.get("z").getAsLong();
            return new Position(x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class Waypoint {
        public final long id;
        public final String name;
        public final int color;
        public final Position position;

        Waypoint(long id, String name, int color, Position position) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.position = position;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Waypoint)) return false;
            return id == ((Waypoint) obj).id;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }
}
