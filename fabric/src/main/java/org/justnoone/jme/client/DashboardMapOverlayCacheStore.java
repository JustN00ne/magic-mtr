package org.justnoone.jme.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mod.data.VehicleExtension;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side persistent overlay cache for the in-game dashboard map.
 * <p>
 * Stores simplified rails and vehicle markers on disk so moving away does not make them disappear.
 */
public final class DashboardMapOverlayCacheStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final long SAVE_DEBOUNCE_MILLIS = 15000;
    private static final double VEHICLE_UPDATE_EPS_SQUARED = 0.25D * 0.25D;

    private static final Map<String, DimensionCache> CACHE_BY_DIMENSION = new LinkedHashMap<>();

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-DashboardMap-Cache");
        thread.setDaemon(true);
        return thread;
    });

    private DashboardMapOverlayCacheStore() {
    }

    public static synchronized void mergeLiveRails(String dimension, Iterable<Rail> rails) {
        if (dimension == null) {
            dimension = "";
        }

        final DimensionCache cache = ensureLoaded(dimension);
        if (rails == null) {
            return;
        }

        boolean changed = false;
        for (final Rail rail : rails) {
            if (rail == null) {
                continue;
            }

            final String id = rail.getHexId();
            if (id == null || id.isEmpty()) {
                continue;
            }

            final RailSnapshot existing = cache.railsById.get(id);

            // Cheap "metadata" check first: avoid rebuilding polylines every frame.
            // If something important changed (speed, direction, signals), rebuild the snapshot.
            if (existing != null) {
                final Position[] positions = parseRailPositions(id);
                final Position from = positions[0];
                final Position to = positions[1];
                if (from == null || to == null) {
                    continue;
                }

                final boolean allowForward = rail.getSpeedLimitMetersPerMillisecond(from) > 0;
                final boolean allowReverse = rail.getSpeedLimitMetersPerMillisecond(to) > 0;
                boolean hasSignals = false;
                try {
                    hasSignals = rail.getSignalColors() != null && !rail.getSignalColors().isEmpty();
                } catch (Exception ignored) {
                    hasSignals = false;
                }

                final long speedForward = rail.getSpeedLimitKilometersPerHour(false);
                final long speedReverse = rail.getSpeedLimitKilometersPerHour(true);
                final int speedKmh = (int) Math.max(1L, Math.min(1000L, Math.max(speedForward, speedReverse)));

                if (existing.speedKmh == speedKmh
                        && existing.allowForward == allowForward
                        && existing.allowReverse == allowReverse
                        && existing.hasSignals == hasSignals) {
                    continue;
                }
            }

            final RailSnapshot snapshot = RailSnapshot.fromRail(rail);
            if (snapshot == null || snapshot.points == null || snapshot.points.size() < 2) {
                continue;
            }

            cache.railsById.put(id, snapshot);
            final Integer index = cache.railIndexById.get(id);
            if (index != null && index >= 0 && index < cache.railsList.size()) {
                cache.railsList.set(index, snapshot);
            } else {
                cache.railIndexById.put(id, cache.railsList.size());
                cache.railsList.add(snapshot);
            }
            changed = true;
        }

        if (changed) {
            cache.dirtyForSave = true;
            maybeScheduleSave(dimension, cache);
        }
    }

    public static synchronized void mergeLiveVehicles(String dimension, Iterable<VehicleExtension> vehicles) {
        if (dimension == null) {
            dimension = "";
        }

        final DimensionCache cache = ensureLoaded(dimension);
        if (vehicles == null) {
            return;
        }

        boolean changed = false;
        final long now = System.currentTimeMillis();
        for (final VehicleExtension vehicle : vehicles) {
            if (vehicle == null) {
                continue;
            }

            final long id;
            try {
                id = vehicle.getId();
            } catch (Exception ignored) {
                continue;
            }
            if (id == 0) {
                continue;
            }

            final Vector headPosition;
            try {
                headPosition = vehicle.getHeadPosition();
            } catch (Exception ignored) {
                continue;
            }
            if (headPosition == null) {
                continue;
            }

            final double x = headPosition.x();
            final double z = headPosition.z();
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                continue;
            }

            final long routeId = vehicle.vehicleExtraData == null ? 0 : vehicle.vehicleExtraData.getThisRouteId();

            final VehicleSnapshot existing = cache.vehiclesById.get(id);
            if (existing == null) {
                final VehicleSnapshot created = new VehicleSnapshot(id, routeId, x, z, now);
                cache.vehiclesById.put(id, created);
                cache.vehiclesList.add(created);
                changed = true;
                continue;
            }

            final double dx = existing.x - x;
            final double dz = existing.z - z;
            final boolean needsUpdate = existing.routeId != routeId || (dx * dx + dz * dz) >= VEHICLE_UPDATE_EPS_SQUARED;
            if (!needsUpdate) {
                continue;
            }

            existing.routeId = routeId;
            existing.x = x;
            existing.z = z;
            existing.updatedAtMillis = now;
            changed = true;
        }

        if (changed) {
            cache.dirtyForSave = true;
            maybeScheduleSave(dimension, cache);
        }
    }

    public static synchronized List<RailSnapshot> getRailsForRender(String dimension) {
        if (dimension == null) {
            dimension = "";
        }
        return ensureLoaded(dimension).railsList;
    }

    public static synchronized List<VehicleSnapshot> getVehiclesForRender(String dimension) {
        if (dimension == null) {
            dimension = "";
        }
        return ensureLoaded(dimension).vehiclesList;
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

        final ArrayList<RailSnapshot> railsSnapshot = new ArrayList<>(cache.railsList);
        final ArrayList<VehicleSnapshot> vehiclesSnapshot = new ArrayList<>(cache.vehiclesList.size());
        for (final VehicleSnapshot vehicleSnapshot : cache.vehiclesList) {
            vehiclesSnapshot.add(vehicleSnapshot.copy());
        }

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
                    final JsonElement railElement = rails.get(i);
                    if (railElement == null || !railElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject railObject = railElement.getAsJsonObject();
                    final String id = railObject.has("id") ? railObject.get("id").getAsString() : "";
                    if (id == null || id.isEmpty()) {
                        continue;
                    }

                    final List<double[]> points = new ArrayList<>();
                    if (railObject.has("points") && railObject.get("points").isJsonArray()) {
                        final JsonArray pointsArray = railObject.getAsJsonArray("points");
                        for (int p = 0; p < pointsArray.size(); p++) {
                            final JsonElement pointElement = pointsArray.get(p);
                            if (pointElement == null || !pointElement.isJsonArray()) {
                                continue;
                            }
                            final JsonArray pointArray = pointElement.getAsJsonArray();
                            if (pointArray.size() < 2) {
                                continue;
                            }
                            final double x = pointArray.get(0).getAsDouble();
                            final double z = pointArray.get(1).getAsDouble();
                            if (Double.isFinite(x) && Double.isFinite(z)) {
                                points.add(new double[]{x, z});
                            }
                        }
                    }

                    final int speedKmh = railObject.has("speed_kmh") ? railObject.get("speed_kmh").getAsInt() : 1;
                    final boolean allowForward = !railObject.has("allow_fwd") || railObject.get("allow_fwd").getAsBoolean();
                    final boolean allowReverse = !railObject.has("allow_rev") || railObject.get("allow_rev").getAsBoolean();
                    final boolean hasSignals = railObject.has("has_signals") && railObject.get("has_signals").getAsBoolean();

                    final RailSnapshot snapshot = new RailSnapshot(id, points, Math.max(1, speedKmh), allowForward, allowReverse, hasSignals);
                    cache.railsById.put(id, snapshot);
                    cache.railIndexById.put(id, cache.railsList.size());
                    cache.railsList.add(snapshot);
                }
            }

            if (root.has("vehicles") && root.get("vehicles").isJsonArray()) {
                final JsonArray vehicles = root.getAsJsonArray("vehicles");
                for (int i = 0; i < vehicles.size(); i++) {
                    final JsonElement vehicleElement = vehicles.get(i);
                    if (vehicleElement == null || !vehicleElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject vehicleObject = vehicleElement.getAsJsonObject();
                    final long id = vehicleObject.has("id") ? vehicleObject.get("id").getAsLong() : 0;
                    if (id == 0) {
                        continue;
                    }

                    final long routeId = vehicleObject.has("route_id") ? vehicleObject.get("route_id").getAsLong() : 0;
                    final double x = vehicleObject.has("x") ? vehicleObject.get("x").getAsDouble() : Double.NaN;
                    final double z = vehicleObject.has("z") ? vehicleObject.get("z").getAsDouble() : Double.NaN;
                    final long updatedAt = vehicleObject.has("updated_at") ? vehicleObject.get("updated_at").getAsLong() : 0;
                    if (!Double.isFinite(x) || !Double.isFinite(z)) {
                        continue;
                    }

                    final VehicleSnapshot snapshot = new VehicleSnapshot(id, routeId, x, z, updatedAt);
                    cache.vehiclesById.put(id, snapshot);
                    cache.vehiclesList.add(snapshot);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveToDisk(String dimension, List<RailSnapshot> rails, List<VehicleSnapshot> vehicles) {
        try {
            final JsonObject root = new JsonObject();
            root.addProperty("savedAt", System.currentTimeMillis());

            final JsonArray railsArray = new JsonArray();
            for (final RailSnapshot rail : rails) {
                if (rail == null || rail.id == null || rail.id.isEmpty() || rail.points == null || rail.points.size() < 2) {
                    continue;
                }

                final JsonObject railObject = new JsonObject();
                railObject.addProperty("id", rail.id);
                railObject.addProperty("speed_kmh", rail.speedKmh);
                railObject.addProperty("allow_fwd", rail.allowForward);
                railObject.addProperty("allow_rev", rail.allowReverse);
                railObject.addProperty("has_signals", rail.hasSignals);

                final JsonArray pointsArray = new JsonArray();
                for (final double[] point : rail.points) {
                    if (point == null || point.length < 2) {
                        continue;
                    }
                    final JsonArray pointArray = new JsonArray();
                    pointArray.add(point[0]);
                    pointArray.add(point[1]);
                    pointsArray.add(pointArray);
                }
                railObject.add("points", pointsArray);
                railsArray.add(railObject);
            }
            root.add("rails", railsArray);

            final JsonArray vehiclesArray = new JsonArray();
            for (final VehicleSnapshot vehicle : vehicles) {
                if (vehicle == null || vehicle.id == 0 || !Double.isFinite(vehicle.x) || !Double.isFinite(vehicle.z)) {
                    continue;
                }
                final JsonObject vehicleObject = new JsonObject();
                vehicleObject.addProperty("id", vehicle.id);
                vehicleObject.addProperty("route_id", vehicle.routeId);
                vehicleObject.addProperty("x", vehicle.x);
                vehicleObject.addProperty("z", vehicle.z);
                vehicleObject.addProperty("updated_at", vehicle.updatedAtMillis);
                vehiclesArray.add(vehicleObject);
            }
            root.add("vehicles", vehiclesArray);

            final Path path = getCachePath(dimension);
            Files.createDirectories(path.getParent());
            final byte[] rawJson = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            Files.write(path, writeLzma2Bytes(rawJson));
        } catch (Exception ignored) {
        }
    }

    private static Path getCachePath(String dimension) {
        final String safeDimension = dimension == null ? "" : dimension.replaceAll("[^a-zA-Z0-9\\-_.]+", "_");
        return MagicConfigPaths.resolveMapFile("dashboard_overlay_cache_" + safeDimension + ".lzma2");
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

    public static final class RailSnapshot {
        public final String id;
        public final List<double[]> points;
        public final int speedKmh;
        public final boolean allowForward;
        public final boolean allowReverse;
        public final boolean hasSignals;

        private RailSnapshot(String id, List<double[]> points, int speedKmh, boolean allowForward, boolean allowReverse, boolean hasSignals) {
            this.id = id;
            this.points = points;
            this.speedKmh = speedKmh;
            this.allowForward = allowForward;
            this.allowReverse = allowReverse;
            this.hasSignals = hasSignals;
        }

        public static RailSnapshot fromRail(Rail rail) {
            if (rail == null) {
                return null;
            }

            final String id = rail.getHexId();
            if (id == null || id.isEmpty()) {
                return null;
            }

            final Position[] positions = parseRailPositions(id);
            final Position from = positions[0];
            final Position to = positions[1];
            if (from == null || to == null) {
                return null;
            }

            final boolean allowForward = rail.getSpeedLimitMetersPerMillisecond(from) > 0;
            final boolean allowReverse = rail.getSpeedLimitMetersPerMillisecond(to) > 0;
            boolean hasSignals = false;
            try {
                hasSignals = rail.getSignalColors() != null && !rail.getSignalColors().isEmpty();
            } catch (Exception ignored) {
                // Some versions may not expose signal color data; treat as no signals.
                hasSignals = false;
            }

            final long speedForward = rail.getSpeedLimitKilometersPerHour(false);
            final long speedReverse = rail.getSpeedLimitKilometersPerHour(true);
            final int speedKmh = (int) Math.max(1L, Math.min(1000L, Math.max(speedForward, speedReverse)));

            final List<double[]> points = buildRailPolyline(rail);
            if (points.size() < 2) {
                return new RailSnapshot(id, buildFallbackPoints(from, to), speedKmh, allowForward, allowReverse, hasSignals);
            }

            // Orient points so they start near the first node in the rail id.
            final double fromX = from.getX() + 0.5D;
            final double fromZ = from.getZ() + 0.5D;
            final double[] first = points.get(0);
            final double[] last = points.get(points.size() - 1);
            if (Math.hypot(first[0] - fromX, first[1] - fromZ) > Math.hypot(last[0] - fromX, last[1] - fromZ)) {
                Collections.reverse(points);
            }

            return new RailSnapshot(id, points, speedKmh, allowForward, allowReverse, hasSignals);
        }

        private static List<double[]> buildFallbackPoints(Position from, Position to) {
            final ArrayList<double[]> fallback = new ArrayList<>(2);
            fallback.add(new double[]{from.getX() + 0.5D, from.getZ() + 0.5D});
            fallback.add(new double[]{to.getX() + 0.5D, to.getZ() + 0.5D});
            return fallback;
        }
    }

    public static final class VehicleSnapshot {
        public final long id;
        public long routeId;
        public double x;
        public double z;
        public long updatedAtMillis;

        private VehicleSnapshot(long id, long routeId, double x, double z, long updatedAtMillis) {
            this.id = id;
            this.routeId = routeId;
            this.x = x;
            this.z = z;
            this.updatedAtMillis = updatedAtMillis;
        }

        private VehicleSnapshot copy() {
            return new VehicleSnapshot(id, routeId, x, z, updatedAtMillis);
        }
    }

    private static final class DimensionCache {
        private boolean loaded;
        private boolean dirtyForSave;
        private long lastSaveStartMillis;

        private final LinkedHashMap<String, RailSnapshot> railsById = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> railIndexById = new LinkedHashMap<>();
        private final ArrayList<RailSnapshot> railsList = new ArrayList<>();

        private final LinkedHashMap<Long, VehicleSnapshot> vehiclesById = new LinkedHashMap<>();
        private final ArrayList<VehicleSnapshot> vehiclesList = new ArrayList<>();
    }

    private static List<double[]> buildRailPolyline(Rail rail) {
        final List<double[]> points = new ArrayList<>();
        if (rail == null) {
            return points;
        }

        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerXPrevious = (x1 + x2) * 0.5D;
            final double centerZPrevious = (z1 + z2) * 0.5D;
            final double centerXCurrent = (x3 + x4) * 0.5D;
            final double centerZCurrent = (z3 + z4) * 0.5D;
            appendPolylinePoint(points, centerXPrevious, centerZPrevious);
            appendPolylinePoint(points, centerXCurrent, centerZCurrent);
        }, 1, 0, 0);

        return points;
    }

    private static void appendPolylinePoint(List<double[]> points, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        if (points.isEmpty()) {
            points.add(new double[]{x, z});
            return;
        }
        final double[] previousPoint = points.get(points.size() - 1);
        if (Math.hypot(previousPoint[0] - x, previousPoint[1] - z) > 0.08D) {
            points.add(new double[]{x, z});
        }
    }

    private static Position[] parseRailPositions(String railId) {
        try {
            if (railId == null) {
                return new Position[]{null, null};
            }
            final String[] split = railId.split("-");
            if (split.length != 6) {
                return new Position[]{null, null};
            }
            return new Position[]{
                    new Position(Long.parseUnsignedLong(split[0], 16), Long.parseUnsignedLong(split[1], 16), Long.parseUnsignedLong(split[2], 16)),
                    new Position(Long.parseUnsignedLong(split[3], 16), Long.parseUnsignedLong(split[4], 16), Long.parseUnsignedLong(split[5], 16))
            };
        } catch (Exception ignored) {
            return new Position[]{null, null};
        }
    }
}
