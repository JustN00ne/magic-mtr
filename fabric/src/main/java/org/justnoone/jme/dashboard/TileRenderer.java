package org.justnoone.jme.dashboard;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.justnoone.jme.config.MagicConfigPaths;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Renders the Minecraft world as web map tiles.
 *
 * <p>The web map uses a WebMercator-style projection on the coordinates {@code lng = x / BLOCKS_PER_DEGREE} and
 * {@code lat = -z / BLOCKS_PER_DEGREE}, so tiles are generated with the inverse mercator mapping (exact alignment
 * with vector overlays at every zoom level).</p>
 *
 * <p>Rendering runs on a small dedicated worker pool so multiple tiles are produced in parallel, cache lookups serve
 * instantly, and stale entries are refreshed asynchronously in the background (stale-while-revalidate).</p>
 *
 * <p>Tiles are persisted to disk as LZMA2-compressed PNG files so they survive restarts. On startup the
 * in-memory cache is populated from disk; new renders are written back asynchronously.</p>
 */
public class TileRenderer {

    private static final int TILE_SIZE = 256;
    private static final double BLOCKS_PER_DEGREE = 64.0;
    private static final long FRESH_MILLIS = 120_000L;
    private static final long STALE_LIMIT_MILLIS = 30 * 60_000L;
    private static final int VOID_COLOR = 0xFF15171d;
    private static final long SAVE_DEBOUNCE_MILLIS = 5000L;
    private static final int MAX_TRANSPARENT_SCAN_DEPTH = 32;

    private static volatile java.lang.reflect.Method cachedGetMapColorMethod;
    private static volatile java.lang.reflect.Field cachedMapColorField;
    private static volatile java.lang.reflect.Method cachedGetBottomYMethod;
    private static volatile boolean mapColorResolved;
    private static volatile boolean bottomYResolved;
    private static volatile Integer cachedBottomY;

    private static class CachedTile {
        final byte[] bytes;
        final long renderedAt;
        volatile boolean dirty;
        CachedTile(byte[] bytes, long renderedAt) {
            this.bytes = bytes;
            this.renderedAt = renderedAt;
        }
    }

    private static final ConcurrentHashMap<String, CachedTile> tileCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> pendingRenders = new ConcurrentHashMap<>();
    private static volatile long lastDiskSaveMillis;
    private static final Path TILES_DIR = MagicConfigPaths.resolveMapFile("dashboard_tiles");
    private static final ExecutorService tileExecutor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    final Thread thread = new Thread(runnable, "MAGIC-TileRenderer");
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    private TileRenderer() {
    }

    /**
     * Returns the PNG bytes for the tile at the given XYZ coordinates (standard slippy map scheme, y = 0 at the top).
     */
    public static byte[] getTile(Object serverObj, int tileX, int tileY, int zoom) {
        if (!(serverObj instanceof MinecraftServer)) {
            return null;
        }
        final MinecraftServer server = (MinecraftServer) serverObj;
        if (zoom < 0) {
            zoom = 0;
        }
        final int effectiveZoom = zoom;

        final String cacheKey = tileX + "_" + tileY + "_" + zoom;
        final long now = System.currentTimeMillis();
        cleanupCache(now);

        final CachedTile cached = tileCache.get(cacheKey);
        if (cached != null && now - cached.renderedAt < FRESH_MILLIS) {
            return cached.bytes;
        }
        if (cached != null && now - cached.renderedAt < STALE_LIMIT_MILLIS) {
            scheduleRefresh(server, cacheKey, tileX, tileY, effectiveZoom);
            return cached.bytes;
        }

        // Try loading from disk before rendering.
        final byte[] diskBytes = loadTileFromDisk(tileX, tileY, zoom);
        if (diskBytes != null) {
            // Serve disk tiles as fresh; revalidate only after FRESH_MILLIS on a later request.
            tileCache.put(cacheKey, new CachedTile(diskBytes, now));
            return diskBytes;
        }

        // Never block HTTP worker threads on a full world render (can peg CPU and stall the pool).
        // Serve a short-lived placeholder while the async render completes.
        scheduleRefresh(server, cacheKey, tileX, tileY, effectiveZoom);
        final byte[] placeholder = createVoidTilePng();
        tileCache.putIfAbsent(cacheKey, new CachedTile(placeholder, now - FRESH_MILLIS + 2_000L));
        return placeholder;
    }

    private static void scheduleRefresh(MinecraftServer server, String cacheKey, int tileX, int tileY, int zoom) {
        if (pendingRenders.putIfAbsent(cacheKey, Boolean.TRUE) != null) {
            return;
        }
        tileExecutor.execute(() -> {
            try {
                renderTile(server, cacheKey, tileX, tileY, zoom);
            } finally {
                pendingRenders.remove(cacheKey);
            }
        });
    }

    private static void renderTile(MinecraftServer server, String cacheKey, int tileX, int tileY, int zoom) {
        try {
            final ServerWorld world = server.getOverworld();
            if (world == null) {
                return;
            }

            final BufferedImage image = renderImage(world, tileX, tileY, zoom);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            final byte[] bytes = baos.toByteArray();
            final CachedTile cached = new CachedTile(bytes, System.currentTimeMillis());
            cached.dirty = true;
            tileCache.put(cacheKey, cached);
            scheduleDiskSave();
        } catch (Exception ignored) {
        }
    }

    // ---- Persistent disk cache (LZMA2-compressed PNG) ----

    private static byte[] loadTileFromDisk(int tileX, int tileY, int zoom) {
        try {
            final Path path = tilePath(tileX, tileY, zoom);
            if (!Files.exists(path)) {
                return null;
            }
            final byte[] compressed = Files.readAllBytes(path);
            return decompressLzma2(compressed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void scheduleDiskSave() {
        final long now = System.currentTimeMillis();
        if (now - lastDiskSaveMillis < SAVE_DEBOUNCE_MILLIS) {
            return;
        }
        lastDiskSaveMillis = now;
        tileExecutor.execute(TileRenderer::saveDirtyTilesToDisk);
    }

    private static void saveDirtyTilesToDisk() {
        try {
            Files.createDirectories(TILES_DIR);
        } catch (Exception ignored) {
            return;
        }
        for (final ConcurrentHashMap.Entry<String, CachedTile> entry : tileCache.entrySet()) {
            final CachedTile tile = entry.getValue();
            if (tile == null || !tile.dirty) {
                continue;
            }
            tile.dirty = false;
            try {
                final String[] parts = entry.getKey().split("_");
                if (parts.length < 3) continue;
                final int tx = Integer.parseInt(parts[0]);
                final int ty = Integer.parseInt(parts[1]);
                final int z = Integer.parseInt(parts[2]);
                final byte[] compressed = compressLzma2(tile.bytes);
                Files.write(tilePath(tx, ty, z), compressed);
            } catch (Exception ignored) {
            }
        }
    }

    private static Path tilePath(int tileX, int tileY, int zoom) {
        return TILES_DIR.resolve(zoom + "_" + tileX + "_" + tileY + ".lzma2");
    }

    private static byte[] compressLzma2(byte[] raw) throws IOException {
        final LZMA2Options options = new LZMA2Options();
        options.setPreset(3);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             XZOutputStream xz = new XZOutputStream(out, options)) {
            xz.write(raw);
            xz.finish();
            return out.toByteArray();
        }
    }

    private static byte[] decompressLzma2(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return null;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(compressed);
             XZInputStream xz = new XZInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[4096];
            int n;
            while ((n = xz.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception ignored) {
            // Backward-compat: treat as uncompressed PNG.
            return compressed;
        }
    }

    private static BufferedImage renderImage(ServerWorld world, int tileX, int tileY, int zoom) {
        final BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int pixel = 0; pixel < TILE_SIZE * TILE_SIZE; pixel++) {
            image.setRGB(pixel % TILE_SIZE, pixel / TILE_SIZE, VOID_COLOR);
        }

        // Standard XYZ tile scheme on the mercator-projected world: tile (x, y) at zoom z covers
        // mercatorY in [y / 2^z, (y + 1) / 2^z] and lng in [(x / 2^z - 0.5) * 360, ((x + 1) / 2^z - 0.5) * 360].
        final double tileCount = Math.pow(2, zoom);

        final int bottomY = getBottomY(world);

        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        WorldChunk currentChunk = null;
        boolean currentChunkLoaded = false;
        final int[] lastY = new int[TILE_SIZE];
        final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int pixelZ = 0; pixelZ < TILE_SIZE; pixelZ++) {
            final double mercatorY = (tileY + (pixelZ + 0.5) / TILE_SIZE) / tileCount;
            final double latitudeRadians = Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * mercatorY)));
            final double worldZ = -Math.toDegrees(latitudeRadians) * BLOCKS_PER_DEGREE;

            for (int pixelX = 0; pixelX < TILE_SIZE; pixelX++) {
                final double longitudeDegrees = ((tileX + (pixelX + 0.5) / TILE_SIZE) / tileCount - 0.5) * 360.0;
                final double worldX = longitudeDegrees * BLOCKS_PER_DEGREE;

                final int blockX = (int) Math.floor(worldX);
                final int blockZ = (int) Math.floor(worldZ);
                final int chunkX = blockX >> 4;
                final int chunkZ = blockZ >> 4;

                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    currentChunkLoaded = world.isChunkLoaded(chunkX, chunkZ);
                    currentChunk = currentChunkLoaded ? world.getChunk(chunkX, chunkZ) : null;
                }

                int blockY = bottomY;
                int color = 0;
                if (currentChunkLoaded && currentChunk != null) {
                    blockY = currentChunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, blockX & 15, blockZ & 15);
                    if (blockY < bottomY) {
                        blockY = bottomY;
                    }

                    // Skip transparent blocks (like glass) and find the top solid color.
                    mutablePos.set(blockX, blockY, blockZ);
                    int scanned = 0;
                    while (mutablePos.getY() >= bottomY && scanned < MAX_TRANSPARENT_SCAN_DEPTH) {
                        try {
                            color = getMapColor(currentChunk.getBlockState(mutablePos), world, mutablePos);
                        } catch (Exception ignored) {
                            color = 0;
                        }
                        if (color != 0) {
                            break;
                        }
                        mutablePos.setY(mutablePos.getY() - 1);
                        scanned++;
                    }
                    blockY = mutablePos.getY();
                }

                final int northY = pixelZ == 0 ? blockY : lastY[pixelX];
                lastY[pixelX] = blockY;

                int multiplier = 220; // Flat
                final int diff = blockY - northY;
                if (diff > 0) {
                    multiplier = 255; // Upward slope (brighter)
                } else if (diff < 0) {
                    multiplier = 180; // Downward slope (darker)
                }

                if (color != 0) {
                    final int red = ((color >> 16) & 0xFF) * multiplier / 255;
                    final int green = ((color >> 8) & 0xFF) * multiplier / 255;
                    final int blue = (color & 0xFF) * multiplier / 255;
                    image.setRGB(pixelX, pixelZ, 0xFF000000 | (red << 16) | (green << 8) | blue);
                }
            }
        }

        return image;
    }

    private static void cleanupCache(long now) {
        if (tileCache.size() <= 2000) {
            return;
        }
        tileCache.entrySet().removeIf(entry -> now - entry.getValue().renderedAt > STALE_LIMIT_MILLIS);
    }

    /** Returns the bottom Y of the world. Uses getBottomY() on 1.17+; falls back to 0 on 1.16. */
    private static int getBottomY(ServerWorld world) {
        final Integer cached = cachedBottomY;
        if (cached != null) {
            return cached;
        }

        if (!bottomYResolved) {
            try {
                cachedGetBottomYMethod = world.getClass().getMethod("getBottomY");
            } catch (Exception ignored) {
                cachedGetBottomYMethod = null;
            }
            bottomYResolved = true;
        }

        try {
            final java.lang.reflect.Method method = cachedGetBottomYMethod;
            final int value = method == null ? 0 : (int) method.invoke(world);
            cachedBottomY = value;
            return value;
        } catch (Exception e) {
            cachedBottomY = 0;
            return 0;
        }
    }

    /** Returns the map color int for a block state. Handles API differences between MC versions. */
    private static int getMapColor(BlockState state, ServerWorld world, BlockPos pos) {
        resolveMapColorAccessors(state);
        final java.lang.reflect.Method method = cachedGetMapColorMethod;
        final java.lang.reflect.Field colorField = cachedMapColorField;
        if (method == null || colorField == null) {
            return 0;
        }
        try {
            final Object color = method.invoke(state, world, pos);
            if (color == null) {
                return 0;
            }
            return colorField.getInt(color);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void resolveMapColorAccessors(BlockState state) {
        if (mapColorResolved) {
            return;
        }
        synchronized (TileRenderer.class) {
            if (mapColorResolved) {
                return;
            }
            try {
                final java.lang.reflect.Method method = state.getClass().getMethod(
                        "getMapColor",
                        net.minecraft.world.BlockView.class,
                        net.minecraft.util.math.BlockPos.class);
                cachedGetMapColorMethod = method;
                cachedMapColorField = method.getReturnType().getField("color");
            } catch (Exception primary) {
                try {
                    for (final java.lang.reflect.Method method : state.getClass().getMethods()) {
                        if (!"getMapColor".equals(method.getName()) || method.getParameterCount() != 2) {
                            continue;
                        }
                        cachedGetMapColorMethod = method;
                        cachedMapColorField = method.getReturnType().getField("color");
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            mapColorResolved = true;
        }
    }

    private static volatile byte[] voidTilePng;

    private static byte[] createVoidTilePng() {
        final byte[] cached = voidTilePng;
        if (cached != null) {
            return cached;
        }
        try {
            final BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    image.setRGB(x, y, VOID_COLOR);
                }
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            final byte[] bytes = baos.toByteArray();
            voidTilePng = bytes;
            return bytes;
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
