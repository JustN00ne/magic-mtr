package org.justnoone.jme.systemmap;

import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.libraries.com.google.gson.Gson;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists System Map rails/vehicles snapshots to disk and merges them with live snapshots.
 * <p>
 * This keeps previously-seen rails/vehicles visible on the :8888 map even after chunks unload.
 */
public final class SystemMapOverlayCacheStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final long SAVE_DEBOUNCE_MILLIS = 15000;

    private static final Map<String, DimensionCache> CACHE_BY_DIMENSION = new LinkedHashMap<>();

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-SystemMap-Cache");
        thread.setDaemon(true);
        return thread;
    });

    private SystemMapOverlayCacheStore() {
    }

    public static synchronized void mergeLiveRails(String dimension, JsonArray rails, long snapshotTimeMillis) {
        if (dimension == null) {
            dimension = "";
        }

        final DimensionCache cache = ensureLoaded(dimension);
        final long normalizedSnapshotTime = snapshotTimeMillis <= 0 ? 0 : snapshotTimeMillis;
        if (normalizedSnapshotTime != 0 && normalizedSnapshotTime == cache.lastMergedRailsSnapshotTimeMillis) {
            return;
        }
        cache.lastMergedRailsSnapshotTimeMillis = normalizedSnapshotTime;

        if (rails == null || rails.size() == 0) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < rails.size(); i++) {
            final JsonElement element = rails.get(i);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            final JsonObject railJson = element.getAsJsonObject();
            final String id = getId(railJson);
            if (id.isEmpty()) {
                continue;
            }
            final JsonObject previous = cache.railsById.put(id, railJson);
            if (previous != railJson) {
                changed = true;
            }
        }

        if (changed) {
            cache.railsArrayDirty = true;
            cache.dirtyForSave = true;
            maybeScheduleSave(dimension, cache);
        }
    }

    public static synchronized void mergeLiveVehicles(String dimension, JsonArray vehicles, long snapshotTimeMillis) {
        if (dimension == null) {
            dimension = "";
        }

        final DimensionCache cache = ensureLoaded(dimension);
        final long normalizedSnapshotTime = snapshotTimeMillis <= 0 ? 0 : snapshotTimeMillis;
        if (normalizedSnapshotTime != 0 && normalizedSnapshotTime == cache.lastMergedVehiclesSnapshotTimeMillis) {
            return;
        }
        cache.lastMergedVehiclesSnapshotTimeMillis = normalizedSnapshotTime;

        if (vehicles == null || vehicles.size() == 0) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < vehicles.size(); i++) {
            final JsonElement element = vehicles.get(i);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            final JsonObject vehicleJson = element.getAsJsonObject();
            final String id = getId(vehicleJson);
            if (id.isEmpty()) {
                continue;
            }
            final JsonObject previous = cache.vehiclesById.put(id, vehicleJson);
            if (previous != vehicleJson) {
                changed = true;
            }
        }

        if (changed) {
            cache.vehiclesArrayDirty = true;
            cache.dirtyForSave = true;
            maybeScheduleSave(dimension, cache);
        }
    }

    public static synchronized JsonArray getRailsForResponse(String dimension) {
        if (dimension == null) {
            dimension = "";
        }
        final DimensionCache cache = ensureLoaded(dimension);
        if (cache.railsArrayDirty || cache.railsArrayCache == null) {
            cache.railsArrayCache = new JsonArray();
            cache.railsById.values().forEach(cache.railsArrayCache::add);
            cache.railsArrayDirty = false;
        }
        return cache.railsArrayCache;
    }

    public static synchronized JsonArray getVehiclesForResponse(String dimension) {
        if (dimension == null) {
            dimension = "";
        }
        final DimensionCache cache = ensureLoaded(dimension);
        if (cache.vehiclesArrayDirty || cache.vehiclesArrayCache == null) {
            cache.vehiclesArrayCache = new JsonArray();
            cache.vehiclesById.values().forEach(cache.vehiclesArrayCache::add);
            cache.vehiclesArrayDirty = false;
        }
        return cache.vehiclesArrayCache;
    }

    private static String getId(JsonObject object) {
        if (object == null) {
            return "";
        }
        try {
            if (object.has("id") && object.get("id").isJsonPrimitive()) {
                return object.get("id").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static DimensionCache ensureLoaded(String dimension) {
        DimensionCache cache = CACHE_BY_DIMENSION.get(dimension);
        if (cache == null) {
            cache = new DimensionCache();
            CACHE_BY_DIMENSION.put(dimension, cache);
        }

        if (!cache.loaded) {
            loadFromDisk(dimension, cache);
            cache.loaded = true;
        }

        return cache;
    }

    private static void maybeScheduleSave(String dimension, DimensionCache cache) {
        final long now = System.currentTimeMillis();
        if (now - cache.lastSaveStartMillis < SAVE_DEBOUNCE_MILLIS) {
            return;
        }
        cache.lastSaveStartMillis = now;

        final LinkedHashMap<String, JsonObject> railsSnapshot = new LinkedHashMap<>(cache.railsById);
        final LinkedHashMap<String, JsonObject> vehiclesSnapshot = new LinkedHashMap<>(cache.vehiclesById);

        SAVE_EXECUTOR.execute(() -> saveToDisk(dimension, railsSnapshot, vehiclesSnapshot));
    }

    private static void loadFromDisk(String dimension, DimensionCache cache) {
        final Path path = getCachePath(dimension);
        if (!Files.exists(path)) {
            return;
        }

        try {
            final byte[] bytes = Files.readAllBytes(path);
            final String json = readLzma2Text(bytes);
            if (json == null || json.isEmpty()) {
                return;
            }

            final JsonElement parsed = new JsonParser().parse(json);
            if (!parsed.isJsonObject()) {
                return;
            }

            final JsonObject root = parsed.getAsJsonObject();

            if (root.has("rails") && root.get("rails").isJsonArray()) {
                final JsonArray rails = root.getAsJsonArray("rails");
                for (int i = 0; i < rails.size(); i++) {
                    final JsonElement element = rails.get(i);
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    final JsonObject railJson = element.getAsJsonObject();
                    final String id = getId(railJson);
                    if (!id.isEmpty()) {
                        cache.railsById.put(id, railJson);
                    }
                }
            }

            if (root.has("vehicles") && root.get("vehicles").isJsonArray()) {
                final JsonArray vehicles = root.getAsJsonArray("vehicles");
                for (int i = 0; i < vehicles.size(); i++) {
                    final JsonElement element = vehicles.get(i);
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    final JsonObject vehicleJson = element.getAsJsonObject();
                    final String id = getId(vehicleJson);
                    if (!id.isEmpty()) {
                        cache.vehiclesById.put(id, vehicleJson);
                    }
                }
            }

            cache.railsArrayDirty = true;
            cache.vehiclesArrayDirty = true;
        } catch (Exception ignored) {
        }
    }

    private static void saveToDisk(String dimension, LinkedHashMap<String, JsonObject> railsById, LinkedHashMap<String, JsonObject> vehiclesById) {
        try {
            final JsonObject root = new JsonObject();
            root.addProperty("savedAt", System.currentTimeMillis());

            final JsonArray rails = new JsonArray();
            railsById.values().forEach(rails::add);
            root.add("rails", rails);

            final JsonArray vehicles = new JsonArray();
            vehiclesById.values().forEach(vehicles::add);
            root.add("vehicles", vehicles);

            final Path path = getCachePath(dimension);
            Files.createDirectories(path.getParent());
            final byte[] rawJson = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            Files.write(path, writeLzma2Bytes(rawJson));
        } catch (Exception ignored) {
        }
    }

    private static Path getCachePath(String dimension) {
        final String safeDimension = dimension == null ? "" : dimension.replaceAll("[^a-zA-Z0-9\\-_.]+", "_");
        return MagicConfigPaths.resolveMapFile("system_map_overlay_cache_" + safeDimension + ".lzma2");
    }

    private static String readLzma2Text(byte[] compressedBytes) {
        if (compressedBytes == null || compressedBytes.length == 0) {
            return "";
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBytes);
             XZInputStream xzInputStream = new XZInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = xzInputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            // Backward-compatibility fallback for uncompressed legacy data.
            return new String(compressedBytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] writeLzma2Bytes(byte[] rawBytes) throws IOException {
        final LZMA2Options options = new LZMA2Options();
        options.setPreset(6);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             XZOutputStream xzOutputStream = new XZOutputStream(outputStream, options)) {
            xzOutputStream.write(rawBytes);
            xzOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private static final class DimensionCache {
        private boolean loaded;
        private final LinkedHashMap<String, JsonObject> railsById = new LinkedHashMap<>();
        private final LinkedHashMap<String, JsonObject> vehiclesById = new LinkedHashMap<>();
        private JsonArray railsArrayCache;
        private JsonArray vehiclesArrayCache;
        private boolean railsArrayDirty = true;
        private boolean vehiclesArrayDirty = true;
        private boolean dirtyForSave;
        private long lastSaveStartMillis;
        private long lastMergedRailsSnapshotTimeMillis;
        private long lastMergedVehiclesSnapshotTimeMillis;
    }
}
