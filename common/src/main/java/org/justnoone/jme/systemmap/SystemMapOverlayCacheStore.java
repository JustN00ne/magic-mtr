package org.justnoone.jme.systemmap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import org.justnoone.jme.config.JmeConfig;
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
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
    private static final long VEHICLE_STALE_MILLIS = 5000;
    private static final long RAIL_PRUNE_GRACE_MILLIS = 0;

    private static final Map<String, DimensionCache> CACHE_BY_DIMENSION = new LinkedHashMap<>();

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-SystemMap-Cache");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Used to prevent cache leakage between different world saves (eg singleplayer world A -> world B without restarting).
     * This string is incorporated into on-disk cache filenames, and when it changes we clear the in-memory cache.
     */
    private static volatile String worldKey = "";
    private static volatile MinecraftServer serverInstance;

    private SystemMapOverlayCacheStore() {
    }

    public static String getWorldKey() {
        return worldKey;
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        serverInstance = server;
        setWorldKey(computeWorldKey(server));
    }

    public static synchronized void onServerStopping() {
        serverInstance = null;
        setWorldKey("");
    }

    private static void setWorldKey(String newWorldKey) {
        final String normalized = newWorldKey == null ? "" : newWorldKey.trim();
        if (Objects.equals(worldKey, normalized)) {
            return;
        }
        worldKey = normalized;
        CACHE_BY_DIMENSION.clear();
    }

    public static synchronized void mergeLiveRails(String dimension, JsonArray rails, long snapshotTimeMillis) {
        if (dimension == null) {
            dimension = "";
        }
        dimension = normalizeDimensionId(dimension);

        final DimensionCache cache = ensureLoaded(dimension);
        final long normalizedSnapshotTime = snapshotTimeMillis <= 0 ? 0 : snapshotTimeMillis;
        if (normalizedSnapshotTime != 0 && normalizedSnapshotTime == cache.lastMergedRailsSnapshotTimeMillis) {
            return;
        }
        cache.lastMergedRailsSnapshotTimeMillis = normalizedSnapshotTime;

        final long now = System.currentTimeMillis();
        final long seenAtMillis = normalizedSnapshotTime == 0 ? now : normalizedSnapshotTime;
        final int expectedSize = rails == null ? 0 : rails.size();
        final HashSet<String> seenIds = new HashSet<>(Math.max(16, expectedSize * 2));

        boolean changed = false;
        if (rails != null && rails.size() > 0) {
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
                seenIds.add(id);
                cache.railLastSeenMillisById.put(id, seenAtMillis);
                final JsonObject previous = cache.railsById.put(id, railJson);
                if (previous != railJson) {
                    changed = true;
                }
            }
        }

        // Prune rails that disappeared from the live snapshot *and* are in currently-loaded chunks.
        // This avoids "ghost rails" after removal while still keeping rails cached for far-away unloaded areas.
        if (pruneMissingRailsInLoadedChunks(dimension, cache, seenIds, seenAtMillis)) {
            changed = true;
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
        dimension = normalizeDimensionId(dimension);

        final DimensionCache cache = ensureLoaded(dimension);
        final long normalizedSnapshotTime = snapshotTimeMillis <= 0 ? 0 : snapshotTimeMillis;
        if (normalizedSnapshotTime != 0 && normalizedSnapshotTime == cache.lastMergedVehiclesSnapshotTimeMillis) {
            return;
        }
        cache.lastMergedVehiclesSnapshotTimeMillis = normalizedSnapshotTime;

        final long now = System.currentTimeMillis();
        final long seenAtMillis = normalizedSnapshotTime == 0 ? now : normalizedSnapshotTime;
        final int expectedSize = vehicles == null ? 0 : vehicles.size();
        final HashSet<String> seenIds = new HashSet<>(Math.max(16, expectedSize * 2));

        boolean changed = false;
        if (vehicles != null && vehicles.size() > 0) {
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
                seenIds.add(id);
                cache.vehicleLastSeenMillisById.put(id, seenAtMillis);
                final JsonObject previous = cache.vehiclesById.put(id, vehicleJson);
                if (previous != vehicleJson) {
                    changed = true;
                }
            }
        }

        if (pruneStaleVehicles(cache, seenIds, now)) {
            changed = true;
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
        dimension = normalizeDimensionId(dimension);
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
        dimension = normalizeDimensionId(dimension);
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
            if (JmeConfig.systemMapOverlayCachePersistEnabled()) {
                loadFromDisk(dimension, cache);
            }
            cache.loaded = true;
        }

        return cache;
    }

    private static void maybeScheduleSave(String dimension, DimensionCache cache) {
        if (!JmeConfig.systemMapOverlayCachePersistEnabled()) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - cache.lastSaveStartMillis < SAVE_DEBOUNCE_MILLIS) {
            return;
        }
        cache.lastSaveStartMillis = now;

        final String worldKeySnapshot = worldKey;
        final LinkedHashMap<String, JsonObject> railsSnapshot = new LinkedHashMap<>(cache.railsById);
        final LinkedHashMap<String, JsonObject> vehiclesSnapshot = new LinkedHashMap<>(cache.vehiclesById);

        SAVE_EXECUTOR.execute(() -> saveToDisk(worldKeySnapshot, dimension, railsSnapshot, vehiclesSnapshot));
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

    private static void saveToDisk(String worldKeySnapshot, String dimension, LinkedHashMap<String, JsonObject> railsById, LinkedHashMap<String, JsonObject> vehiclesById) {
        try {
            final JsonObject root = new JsonObject();
            root.addProperty("savedAt", System.currentTimeMillis());
            if (worldKeySnapshot != null && !worldKeySnapshot.isEmpty()) {
                root.addProperty("worldKey", worldKeySnapshot);
            }

            final JsonArray rails = new JsonArray();
            railsById.values().forEach(rails::add);
            root.add("rails", rails);

            final JsonArray vehicles = new JsonArray();
            vehiclesById.values().forEach(vehicles::add);
            root.add("vehicles", vehicles);

            final Path path = getCachePath(worldKeySnapshot, dimension);
            Files.createDirectories(path.getParent());
            final byte[] rawJson = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            Files.write(path, writeLzma2Bytes(rawJson));
        } catch (Exception ignored) {
        }
    }

    private static Path getCachePath(String dimension) {
        return getCachePath(worldKey, dimension);
    }

    private static Path getCachePath(String worldKeyValue, String dimension) {
        final String safeDimension = dimension == null ? "" : dimension.replaceAll("[^a-zA-Z0-9\\-_.]+", "_");
        final String safeWorldKey = worldKeyValue == null || worldKeyValue.isEmpty()
                ? ""
                : worldKeyValue.replaceAll("[^a-zA-Z0-9\\-_.]+", "_") + "_";
        return MagicConfigPaths.resolveMapFile("system_map_overlay_cache_" + safeWorldKey + safeDimension + ".lzma2");
    }

    private static boolean pruneStaleVehicles(DimensionCache cache, HashSet<String> liveIds, long nowMillis) {
        if (cache == null || cache.vehiclesById.isEmpty()) {
            return false;
        }

        final boolean[] removed = {false};
        cache.vehiclesById.entrySet().removeIf(entry -> {
            if (entry == null) {
                return false;
            }
            final String id = entry.getKey();
            if (id == null || id.isEmpty() || (liveIds != null && liveIds.contains(id))) {
                return false;
            }

            final Long lastSeen = cache.vehicleLastSeenMillisById.get(id);
            final long age = lastSeen == null ? Long.MAX_VALUE : nowMillis - lastSeen;
            if (age <= VEHICLE_STALE_MILLIS) {
                return false;
            }

            cache.vehicleLastSeenMillisById.remove(id);
            removed[0] = true;
            return true;
        });

        return removed[0];
    }

    private static boolean pruneMissingRailsInLoadedChunks(String dimension, DimensionCache cache, HashSet<String> liveIds, long snapshotTimeMillis) {
        if (cache == null || cache.railsById.isEmpty()) {
            return false;
        }

        final boolean[] removed = {false};
        cache.railsById.entrySet().removeIf(entry -> {
            if (entry == null) {
                return false;
            }
            final String id = entry.getKey();
            if (id == null || id.isEmpty() || (liveIds != null && liveIds.contains(id))) {
                return false;
            }

            final Long lastSeen = cache.railLastSeenMillisById.get(id);
            if (lastSeen != null && snapshotTimeMillis - lastSeen < RAIL_PRUNE_GRACE_MILLIS) {
                return false;
            }

            if (!isRailLikelyLoadedInWorld(dimension, id)) {
                return false;
            }

            cache.railLastSeenMillisById.remove(id);
            removed[0] = true;
            return true;
        });

        return removed[0];
    }

    private static boolean isRailLikelyLoadedInWorld(String dimension, String railId) {
        final MinecraftServer server = serverInstance;
        if (server == null || dimension == null || dimension.isEmpty() || railId == null || railId.isEmpty()) {
            return false;
        }

        final ServerWorld world = resolveWorld(server, dimension);
        if (world == null) {
            return false;
        }

        final long[] xz = parseRailXz(railId);
        if (xz == null) {
            return false;
        }

        final int chunkX1 = (int) (xz[0] >> 4);
        final int chunkZ1 = (int) (xz[1] >> 4);
        final int chunkX2 = (int) (xz[2] >> 4);
        final int chunkZ2 = (int) (xz[3] >> 4);

        return isChunkLoaded(world, chunkX1, chunkZ1) || isChunkLoaded(world, chunkX2, chunkZ2);
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isEmpty()) {
            return null;
        }

        final String normalizedTarget = normalizeDimensionId(dimension);
        try {
            for (final ServerWorld world : server.getWorlds()) {
                if (world == null) {
                    continue;
                }
                final String worldId = String.valueOf(world.getRegistryKey().getValue());
                if (normalizeDimensionId(worldId).equals(normalizedTarget)) {
                    return world;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String normalizeDimensionId(String id) {
        if (id == null) {
            return "";
        }

        final String normalized = id.trim().replace(':', '/');
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static long[] parseRailXz(String railId) {
        final String[] split = railId.split("-");
        if (split.length != 6) {
            return null;
        }

        try {
            final long x1 = Long.parseUnsignedLong(split[0], 16);
            final long z1 = Long.parseUnsignedLong(split[2], 16);
            final long x2 = Long.parseUnsignedLong(split[3], 16);
            final long z2 = Long.parseUnsignedLong(split[5], 16);
            return new long[]{x1, z1, x2, z2};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isChunkLoaded(ServerWorld world, int chunkX, int chunkZ) {
        if (world == null) {
            return false;
        }

        try {
            // Use direct calls so this keeps working after Loom remaps the mod to intermediary.
            return world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String computeWorldKey(MinecraftServer server) {
        final Path root = resolveWorldRoot(server);
        if (root == null) {
            return "";
        }

        final String absolute = root.toAbsolutePath().normalize().toString();
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] hash = digest.digest(absolute.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception ignored) {
            return Integer.toHexString(absolute.hashCode());
        }
    }

    private static Path resolveWorldRoot(MinecraftServer server) {
        if (server == null) {
            return null;
        }

        try {
            // Use direct calls so this keeps working after Loom remaps the mod to intermediary.
            return server.getSavePath(WorldSavePath.ROOT);
        } catch (Throwable ignored) {
        }

        // Fallback: use working directory, still enough to prevent most cross-world mixing in practice.
        try {
            // Must remain Java 8 compatible (builds target multiple MC versions).
            return Paths.get(".").toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
        private final LinkedHashMap<String, Long> railLastSeenMillisById = new LinkedHashMap<>();
        private final LinkedHashMap<String, Long> vehicleLastSeenMillisById = new LinkedHashMap<>();
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
