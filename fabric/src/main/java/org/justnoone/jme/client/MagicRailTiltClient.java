package org.justnoone.jme.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.VehicleExtension;

import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MagicRailTiltClient {

    // Needs to cover typical vertical offset between the camera/vehicle pivot and the rail path.
    // Keep reasonably small to avoid snapping to adjacent tracks in stations.
    private static final double MAX_LOOKUP_DISTANCE_SQUARED = 16.0;
    private static final double CURVE_SAMPLE_SPACING = 1.25;
    private static final int MIN_CURVE_SAMPLES = 8;
    private static final int MAX_CURVE_SAMPLES = 64;
    // Shared by the riding car, the riding player and the camera so all three roll in sync.
    private static final long RIDING_CAR_SMOOTHING_KEY = 0x5249444E47434152L;
    private static final double SMOOTHING_ALPHA = 0.25;
    private static final long SMOOTHING_PRUNE_INTERVAL_MILLIS = 2000;
    private static final long SMOOTHING_ENTRY_TIMEOUT_MILLIS = 5000;
    private static final long RAIL_SAMPLE_CACHE_PRUNE_INTERVAL_MILLIS = 5000;
    private static final long RAIL_SAMPLE_CACHE_ENTRY_TIMEOUT_MILLIS = 15000;
    private static final int MAX_RAIL_SAMPLE_CACHE_ENTRIES = 512;
    private static final int RECENT_RAIL_CANDIDATE_COUNT = 6;
    private static final long FULL_SCAN_VALIDATE_INTERVAL_MILLIS = 1000;
    private static final long FORCE_FULL_SCAN_INTERVAL_MILLIS = 10000;
    private static final double CONFIDENT_DISTANCE_SQUARED = 6.25;
    private static final ConcurrentHashMap<Long, SmoothedTiltEntry> SMOOTHED_TILT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, RailSamples> RAIL_SAMPLES_BY_ID = new ConcurrentHashMap<>();
    private static final ThreadLocal<RecentRails> RECENT_RAILS = ThreadLocal.withInitial(RecentRails::new);
    private static volatile long lastPruneMillis;
    private static volatile long lastFullScanMillis;
    private static volatile long lastRailSamplePruneMillis;
    private static volatile Field jme$previousVehicleYawField;
    private static volatile boolean jme$previousVehicleYawFieldSearched;
    private static volatile boolean ridingCarRollComputedThisFrame;
    private static volatile double lastRidingCarRollDegrees;

    private MagicRailTiltClient() {
    }

    public static boolean isPlayerRidingMtrVehicle() {
        for (VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
            if (VehicleRidingMovement.isRiding(vehicle.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks the start of a new render frame so the riding car roll is recomputed once per frame
     * (instead of once per vehicle/camera consumer), keeping every consumer in sync.
     */
    public static void beginRenderFrame() {
        ridingCarRollComputedThisFrame = false;
    }

    /**
     * The smoothed roll of the car the local player is currently riding, recomputed at most once per
     * render frame. Returns 0 when the player is not riding an MTR vehicle.
     */
    public static double getRidingCarRollDegrees() {
        if (!isPlayerRidingMtrVehicle()) {
            ridingCarRollComputedThisFrame = false;
            lastRidingCarRollDegrees = 0;
            return 0;
        }
        if (ridingCarRollComputedThisFrame) {
            return lastRidingCarRollDegrees;
        }
        ridingCarRollComputedThisFrame = true;

        final MinecraftClient client = MinecraftClient.getInstance();
        final Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return lastRidingCarRollDegrees = 0;
        }
        final Vec3d cameraPos = cameraEntity.getPos();
        final Double ridingVehicleYaw = jme$getRidingVehicleYawRadians();
        final double roll;
        if (ridingVehicleYaw != null) {
            roll = getSmoothedSignedTiltDegreesAt(
                    RIDING_CAR_SMOOTHING_KEY,
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z,
                    Math.sin(ridingVehicleYaw),
                    Math.cos(ridingVehicleYaw)
            );
        } else {
            roll = getSmoothedTiltDegreesAt(RIDING_CAR_SMOOTHING_KEY, cameraPos.x, cameraPos.y, cameraPos.z);
        }
        return lastRidingCarRollDegrees = roll;
    }

    /**
     * Roll to apply to the local camera. Gated by the riding check; the caller (GameRendererTiltMixin)
     * additionally checks the accessibility config before applying any camera roll.
     */
    public static double getCameraTiltDegrees() {
        return getRidingCarRollDegrees();
    }

    public static double getTiltDegreesAt(double x, double y, double z) {
        final TiltLookup lookup = findNearestTiltLookup(x, y, z);
        return lookup == null ? 0 : lookup.tiltDegrees;
    }

    /**
     * Returns the tilt at the nearest rail, but with the sign corrected so that "left" is relative to the supplied
     * forward direction. This makes vehicle roll match the rail's physical bank even when the vehicle is facing the
     * opposite direction.
     *
     * @param forwardX vehicle forward X component in world space (does not need to be normalized)
     * @param forwardZ vehicle forward Z component in world space (does not need to be normalized)
     */
    public static double getSignedTiltDegreesAt(double x, double y, double z, double forwardX, double forwardZ) {
        final TiltLookup lookup = findNearestTiltLookup(x, y, z);
        return lookup == null ? 0 : applyDirectionSign(lookup, forwardX, forwardZ);
    }

    public static double getSmoothedTiltDegreesAt(double x, double y, double z) {
        return getSmoothedTiltDegreesAt(getBucketKey(x, y, z), x, y, z);
    }

    public static double getSmoothedTiltDegreesAt(long smoothingKey, double x, double y, double z) {
        final TiltLookup lookup = findNearestTiltLookup(x, y, z);
        final long key = lookup == null ? smoothingKey : combineSmoothingKeys(smoothingKey, lookup.railSmoothingKey);
        final double target = lookup == null ? 0 : lookup.tiltDegrees;
        return smoothTilt(key, target);
    }

    /**
     * Smoothed variant of {@link #getSignedTiltDegreesAt(double, double, double, double, double)}.
     *
     * @param forwardX vehicle forward X component in world space (does not need to be normalized)
     * @param forwardZ vehicle forward Z component in world space (does not need to be normalized)
     */
    public static double getSmoothedSignedTiltDegreesAt(long smoothingKey, double x, double y, double z, double forwardX, double forwardZ) {
        final TiltLookup lookup = findNearestTiltLookup(x, y, z);
        final long key = lookup == null ? smoothingKey : combineSmoothingKeys(smoothingKey, lookup.railSmoothingKey);
        final double target = lookup == null ? 0 : applyDirectionSign(lookup, forwardX, forwardZ);
        return smoothTilt(key, target);
    }

    public static double getTiltDegreesOnRail(Rail rail, double x, double y, double z) {
        if (rail == null) {
            return 0;
        }

        final MagicRailTiltRegistry.TiltSettings settings = MagicRailTiltRegistry.getTilt(rail.getHexId());
        if (settings == null) {
            return 0;
        }

        final RailProjection projection = projectToRailSegment(rail, x, y, z);
        if (projection == null) {
            return 0;
        }

        return MagicRailTiltRegistry.interpolateDegrees(settings, projection.progress);
    }

    public static double getSmoothedTiltDegreesOnRail(Rail rail, double x, double y, double z) {
        return getSmoothedTiltDegreesOnRail(getBucketKey(x, y, z), rail, x, y, z);
    }

    public static double getSmoothedTiltDegreesOnRail(long smoothingKey, Rail rail, double x, double y, double z) {
        if (rail == null) {
            return smoothTilt(smoothingKey, 0);
        }

        final long key = combineSmoothingKeys(smoothingKey, hashRailId(MagicRailTiltRegistry.normalizeRailId(rail.getHexId())));
        return smoothTilt(key, getTiltDegreesOnRail(rail, x, y, z));
    }

    public static void clearSmoothingCache() {
        SMOOTHED_TILT.clear();
        RAIL_SAMPLES_BY_ID.clear();
        lastPruneMillis = 0;
        lastFullScanMillis = 0;
        lastRailSamplePruneMillis = 0;
        ridingCarRollComputedThisFrame = false;
        lastRidingCarRollDegrees = 0;
        RECENT_RAILS.remove();
    }

    private static TiltLookup findNearestTiltLookup(double x, double y, double z) {
        final MinecraftClientData clientData = MinecraftClientData.getInstance();
        RailProjection nearestProjection = null;
        Rail nearestRail = null;
        long nearestRailKey = 0;
        MagicRailTiltRegistry.TiltSettings nearestSettings = null;

        // Fast-path: most calls occur near the same few rails (camera + a handful of rendered vehicles).
        // Try recent candidates first to avoid scanning the entire rail graph each frame.
        final RecentRails recentRails = RECENT_RAILS.get();
        final String[] recentRailIds = recentRails.railIds;
        for (int i = 0; i < recentRailIds.length; i++) {
            final String railId = recentRailIds[i];
            if (railId == null || railId.isEmpty()) {
                continue;
            }
            final MagicRailTiltRegistry.TiltSettings settings = MagicRailTiltRegistry.getTilt(railId);
            if (settings == null) {
                continue;
            }
            final Rail rail = clientData.railIdMap.get(railId);
            if (rail == null) {
                continue;
            }

            final RailProjection projection = projectToRailSegment(rail, x, y, z);
            if (projection == null || projection.distanceSquared > MAX_LOOKUP_DISTANCE_SQUARED) {
                continue;
            }

            if (nearestProjection == null || projection.distanceSquared < nearestProjection.distanceSquared) {
                nearestProjection = projection;
                nearestRail = rail;
                nearestRailKey = hashRailId(MagicRailTiltRegistry.normalizeRailId(rail.getHexId()));
                nearestSettings = settings;
            }
        }

        final long now = System.currentTimeMillis();
        if (nearestProjection != null && nearestRail != null) {
            // If we recently did a full scan, the recent-rails list is likely fresh for the current area.
            // Otherwise, validate periodically to avoid getting "stuck" on a nearby-but-not-nearest cached rail.
            final long sinceFullScan = now - lastFullScanMillis;
            if (sinceFullScan < FULL_SCAN_VALIDATE_INTERVAL_MILLIS
                    || (nearestProjection.distanceSquared <= CONFIDENT_DISTANCE_SQUARED && sinceFullScan < FORCE_FULL_SCAN_INTERVAL_MILLIS)) {
                recentRails.record(nearestRail.getHexId());
                final double tiltDegrees = nearestSettings == null ? 0 : MagicRailTiltRegistry.interpolateDegrees(nearestSettings, nearestProjection.progress);
                return new TiltLookup(tiltDegrees, nearestRailKey, nearestRail, nearestProjection.progress);
            }
        }

        lastFullScanMillis = now;
        // Full scan is expensive; only consider rails that actually have tilt configured.
        for (final Map.Entry<String, MagicRailTiltRegistry.TiltSettings> tiltEntry : MagicRailTiltRegistry.getAll().entrySet()) {
            if (tiltEntry == null) {
                continue;
            }

            final String railId = tiltEntry.getKey();
            final MagicRailTiltRegistry.TiltSettings settings = tiltEntry.getValue();
            if (railId == null || railId.isEmpty() || settings == null) {
                continue;
            }

            final Rail rail = clientData.railIdMap.get(railId);
            if (rail == null) {
                continue;
            }

            final RailProjection projection = projectToRailSegment(rail, x, y, z);
            if (projection == null || projection.distanceSquared > MAX_LOOKUP_DISTANCE_SQUARED) {
                continue;
            }

            if (nearestProjection == null || projection.distanceSquared < nearestProjection.distanceSquared) {
                nearestProjection = projection;
                nearestRail = rail;
                nearestRailKey = hashRailId(MagicRailTiltRegistry.normalizeRailId(rail.getHexId()));
                nearestSettings = settings;
            }
        }

        if (nearestProjection == null || nearestRail == null) {
            return null;
        }

        recentRails.record(nearestRail.getHexId());
        final double tiltDegrees = nearestSettings == null ? 0 : MagicRailTiltRegistry.interpolateDegrees(nearestSettings, nearestProjection.progress);
        return new TiltLookup(tiltDegrees, nearestRailKey, nearestRail, nearestProjection.progress);
    }

    private static Double jme$getRidingVehicleYawRadians() {
        if (!isPlayerRidingMtrVehicle()) {
            return null;
        }

        if (jme$previousVehicleYawFieldSearched && jme$previousVehicleYawField == null) {
            return null;
        }

        try {
            if (jme$previousVehicleYawField == null) {
                final Field field = VehicleRidingMovement.class.getDeclaredField("previousVehicleYaw");
                field.setAccessible(true);
                jme$previousVehicleYawField = field;
                jme$previousVehicleYawFieldSearched = true;
            }

            return jme$previousVehicleYawField.getDouble(null);
        } catch (Exception ignored) {
            jme$previousVehicleYawFieldSearched = true;
            jme$previousVehicleYawField = null;
            return null;
        }
    }

    private static double applyDirectionSign(TiltLookup lookup, double forwardX, double forwardZ) {
        if (lookup == null || Math.abs(lookup.tiltDegrees) < 1.0E-6) {
            return lookup == null ? 0 : lookup.tiltDegrees;
        }
        if (Math.abs(forwardX) + Math.abs(forwardZ) < 1.0E-6) {
            return lookup.tiltDegrees;
        }

        final Rail rail = lookup.rail;
        if (rail == null) {
            return lookup.tiltDegrees;
        }

        try {
            final double length = rail.railMath.getLength();
            if (length < 1.0E-4) {
                return lookup.tiltDegrees;
            }

            final double distance = length * clamp01(lookup.railProgress);
            final double delta = Math.min(CURVE_SAMPLE_SPACING, length * 0.25);
            final double d1 = Math.max(0, distance - delta);
            final double d2 = Math.min(length, distance + delta);

            Vector p1 = rail.railMath.getPosition(d1, false);
            Vector p2 = rail.railMath.getPosition(d2, false);

            if (p1 == null || p2 == null || Math.abs(p2.x() - p1.x()) + Math.abs(p2.z() - p1.z()) < 1.0E-6) {
                final Vector start = rail.railMath.getPosition(0, false);
                final Vector end = rail.railMath.getPosition(length, false);
                p1 = start;
                p2 = end;
            }

            if (p1 == null || p2 == null) {
                return lookup.tiltDegrees;
            }

            final double railForwardX = p2.x() - p1.x();
            final double railForwardZ = p2.z() - p1.z();
            if (Math.abs(railForwardX) + Math.abs(railForwardZ) < 1.0E-6) {
                return lookup.tiltDegrees;
            }

            final double dot = railForwardX * forwardX + railForwardZ * forwardZ;
            return dot < 0 ? -lookup.tiltDegrees : lookup.tiltDegrees;
        } catch (Exception ignored) {
            return lookup.tiltDegrees;
        }
    }

    private static RailProjection projectToRailSegment(Rail rail, double x, double y, double z) {
        final RailSamples samples = getRailSamples(rail);
        if (samples == null) {
            return null;
        }

        final double[] pointsX = samples.pointsX;
        final double[] pointsY = samples.pointsY;
        final double[] pointsZ = samples.pointsZ;
        final int segments = samples.segments;

        if (segments <= 0 || pointsX.length < 2 || pointsX.length != pointsY.length || pointsX.length != pointsZ.length) {
            return null;
        }

        double bestProgress = 0;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i < segments; i++) {
            final double startX = pointsX[i];
            final double startY = pointsY[i];
            final double startZ = pointsZ[i];
            final double endX = pointsX[i + 1];
            final double endY = pointsY[i + 1];
            final double endZ = pointsZ[i + 1];
            final SegmentProjection segmentProjection = projectOnSegment(
                    x, y, z,
                    startX, startY, startZ,
                    endX, endY, endZ
            );

            if (segmentProjection.distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = segmentProjection.distanceSquared;
                bestProgress = (i + segmentProjection.segmentProgress) / (double) segments;
            }
        }

        if (bestDistanceSquared == Double.MAX_VALUE) {
            return null;
        }

        return new RailProjection(bestProgress, bestDistanceSquared);
    }

    private static RailSamples getRailSamples(Rail rail) {
        if (rail == null || rail.railMath == null) {
            return null;
        }

        final String railId = rail.getHexId();
        if (railId == null || railId.isEmpty()) {
            return null;
        }

        final long signature = computeRailSignature(rail);
        final long now = System.currentTimeMillis();

        final RailSamples existing = RAIL_SAMPLES_BY_ID.get(railId);
        if (existing != null && existing.signature == signature) {
            existing.lastUsedMillis = now;
            maybePruneRailSamples(now);
            return existing;
        }

        final RailSamples rebuilt = buildRailSamples(rail, signature, now);
        if (rebuilt != null) {
            RAIL_SAMPLES_BY_ID.put(railId, rebuilt);
            maybePruneRailSamples(now);
            return rebuilt;
        }

        // If rebuild fails, keep using the previous samples if any (better than falling back to "no tilt").
        if (existing != null) {
            existing.lastUsedMillis = now;
            maybePruneRailSamples(now);
            return existing;
        }

        maybePruneRailSamples(now);
        return null;
    }

    private static RailSamples buildRailSamples(Rail rail, long signature, long nowMillis) {
        if (rail == null || rail.railMath == null) {
            return null;
        }

        final double railLength;
        try {
            railLength = rail.railMath.getLength();
        } catch (Throwable ignored) {
            return null;
        }

        if (!(railLength > 1.0E-4)) {
            return null;
        }

        final int segments = Math.max(MIN_CURVE_SAMPLES, Math.min(MAX_CURVE_SAMPLES, (int) Math.ceil(railLength / CURVE_SAMPLE_SPACING)));
        final int pointCount = segments + 1;
        final double[] pointsX = new double[pointCount];
        final double[] pointsY = new double[pointCount];
        final double[] pointsZ = new double[pointCount];

        for (int i = 0; i <= segments; i++) {
            final double progress = i / (double) segments;
            final Vector point;
            try {
                point = rail.railMath.getPosition(railLength * progress, false);
            } catch (Throwable ignored) {
                return null;
            }
            if (point == null) {
                return null;
            }
            pointsX[i] = point.x();
            pointsY[i] = point.y();
            pointsZ[i] = point.z();
        }

        return new RailSamples(signature, nowMillis, segments, pointsX, pointsY, pointsZ);
    }

    private static long computeRailSignature(Rail rail) {
        if (rail == null || rail.railMath == null) {
            return 0;
        }

        try {
            long hash = 1469598103934665603L;

            final double length = rail.railMath.getLength();
            hash = (hash ^ Double.doubleToLongBits(length)) * 1099511628211L;

            final Rail.Shape shape = rail.railMath.getShape();
            hash = (hash ^ (shape == null ? 0 : shape.ordinal())) * 1099511628211L;

            hash = (hash ^ Double.doubleToLongBits(rail.railMath.getVerticalRadius())) * 1099511628211L;
            hash = (hash ^ Double.doubleToLongBits(rail.railMath.getMaxVerticalRadius())) * 1099511628211L;

            hash = (hash ^ rail.railMath.minX) * 1099511628211L;
            hash = (hash ^ rail.railMath.minY) * 1099511628211L;
            hash = (hash ^ rail.railMath.minZ) * 1099511628211L;
            hash = (hash ^ rail.railMath.maxX) * 1099511628211L;
            hash = (hash ^ rail.railMath.maxY) * 1099511628211L;
            hash = (hash ^ rail.railMath.maxZ) * 1099511628211L;

            return hash;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void maybePruneRailSamples(long nowMillis) {
        if (nowMillis - lastRailSamplePruneMillis < RAIL_SAMPLE_CACHE_PRUNE_INTERVAL_MILLIS) {
            return;
        }
        lastRailSamplePruneMillis = nowMillis;

        // Remove entries that haven't been used recently. This bounds memory usage.
        RAIL_SAMPLES_BY_ID.entrySet().removeIf(entry -> {
            final RailSamples samples = entry.getValue();
            return samples == null || nowMillis - samples.lastUsedMillis > RAIL_SAMPLE_CACHE_ENTRY_TIMEOUT_MILLIS;
        });

        final int oversize = RAIL_SAMPLES_BY_ID.size() - MAX_RAIL_SAMPLE_CACHE_ENTRIES;
        if (oversize <= 0) {
            return;
        }

        int removed = 0;
        for (final String key : RAIL_SAMPLES_BY_ID.keySet()) {
            if (removed >= oversize) {
                break;
            }
            if (RAIL_SAMPLES_BY_ID.remove(key) != null) {
                removed++;
            }
        }
    }

    private static SegmentProjection projectOnSegment(
            double pointX, double pointY, double pointZ,
            double startX, double startY, double startZ,
            double endX, double endY, double endZ
    ) {
        final double segmentX = endX - startX;
        final double segmentY = endY - startY;
        final double segmentZ = endZ - startZ;
        final double segmentLengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (segmentLengthSquared < 1.0E-6) {
            final double dx = pointX - startX;
            final double dy = pointY - startY;
            final double dz = pointZ - startZ;
            return new SegmentProjection(0, dx * dx + dy * dy + dz * dz);
        }

        final double pointOffsetX = pointX - startX;
        final double pointOffsetY = pointY - startY;
        final double pointOffsetZ = pointZ - startZ;
        final double segmentProgress = clamp01((pointOffsetX * segmentX + pointOffsetY * segmentY + pointOffsetZ * segmentZ) / segmentLengthSquared);

        final double nearestX = startX + segmentX * segmentProgress;
        final double nearestY = startY + segmentY * segmentProgress;
        final double nearestZ = startZ + segmentZ * segmentProgress;
        final double nearestDeltaX = pointX - nearestX;
        final double nearestDeltaY = pointY - nearestY;
        final double nearestDeltaZ = pointZ - nearestZ;
        return new SegmentProjection(segmentProgress, nearestDeltaX * nearestDeltaX + nearestDeltaY * nearestDeltaY + nearestDeltaZ * nearestDeltaZ);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static long getBucketKey(double x, double y, double z) {
        final long ix = Math.round(x * 2);
        final long iy = Math.round(y * 2);
        final long iz = Math.round(z * 2);
        long key = 1469598103934665603L;
        key = (key ^ ix) * 1099511628211L;
        key = (key ^ iy) * 1099511628211L;
        key = (key ^ iz) * 1099511628211L;
        return key;
    }

    private static long hashRailId(String railId) {
        long key = 1469598103934665603L;
        for (int i = 0; i < railId.length(); i++) {
            key = (key ^ railId.charAt(i)) * 1099511628211L;
        }
        return key;
    }

    private static long combineSmoothingKeys(long primaryKey, long secondaryKey) {
        long key = 1469598103934665603L;
        key = (key ^ primaryKey) * 1099511628211L;
        key = (key ^ secondaryKey) * 1099511628211L;
        return key;
    }

    private static double smoothTilt(long key, double targetTiltDegrees) {
        final long now = System.currentTimeMillis();
        maybePrune(now);
        final SmoothedTiltEntry smoothedTiltEntry = SMOOTHED_TILT.compute(key, (unused, existing) -> {
            if (existing == null) {
                return new SmoothedTiltEntry(targetTiltDegrees, now);
            }

            final double alpha = now - existing.updatedMillis > 250 ? 1 : SMOOTHING_ALPHA;
            existing.value = existing.value + (targetTiltDegrees - existing.value) * alpha;
            existing.updatedMillis = now;
            return existing;
        });
        return smoothedTiltEntry == null ? targetTiltDegrees : smoothedTiltEntry.value;
    }

    private static void maybePrune(long now) {
        if (now - lastPruneMillis < SMOOTHING_PRUNE_INTERVAL_MILLIS) {
            return;
        }
        lastPruneMillis = now;
        SMOOTHED_TILT.entrySet().removeIf(entry -> now - entry.getValue().updatedMillis > SMOOTHING_ENTRY_TIMEOUT_MILLIS);
        maybePruneRailSamples(now);
    }

    private static final class RailProjection {
        private final double progress;
        private final double distanceSquared;

        private RailProjection(double progress, double distanceSquared) {
            this.progress = progress;
            this.distanceSquared = distanceSquared;
        }
    }

    private static final class SegmentProjection {
        private final double segmentProgress;
        private final double distanceSquared;

        private SegmentProjection(double segmentProgress, double distanceSquared) {
            this.segmentProgress = segmentProgress;
            this.distanceSquared = distanceSquared;
        }
    }

    private static final class TiltLookup {
        private final double tiltDegrees;
        private final long railSmoothingKey;
        private final Rail rail;
        private final double railProgress;

        private TiltLookup(double tiltDegrees, long railSmoothingKey, Rail rail, double railProgress) {
            this.tiltDegrees = tiltDegrees;
            this.railSmoothingKey = railSmoothingKey;
            this.rail = rail;
            this.railProgress = railProgress;
        }
    }

    private static final class SmoothedTiltEntry {
        private double value;
        private long updatedMillis;

        private SmoothedTiltEntry(double value, long updatedMillis) {
            this.value = value;
            this.updatedMillis = updatedMillis;
        }
    }

    private static final class RailSamples {
        private final long signature;
        private volatile long lastUsedMillis;
        private final int segments;
        private final double[] pointsX;
        private final double[] pointsY;
        private final double[] pointsZ;

        private RailSamples(long signature, long lastUsedMillis, int segments, double[] pointsX, double[] pointsY, double[] pointsZ) {
            this.signature = signature;
            this.lastUsedMillis = lastUsedMillis;
            this.segments = segments;
            this.pointsX = pointsX;
            this.pointsY = pointsY;
            this.pointsZ = pointsZ;
        }
    }

    private static final class RecentRails {
        private final String[] railIds = new String[RECENT_RAIL_CANDIDATE_COUNT];
        private int cursor;

        private void record(String railId) {
            if (railId == null || railId.isEmpty()) {
                return;
            }

            for (int i = 0; i < railIds.length; i++) {
                if (railId.equals(railIds[i])) {
                    return;
                }
            }

            railIds[cursor] = railId;
            cursor = (cursor + 1) % railIds.length;
        }

        @SuppressWarnings("unused")
        private void clear() {
            Arrays.fill(railIds, null);
            cursor = 0;
        }
    }
}
