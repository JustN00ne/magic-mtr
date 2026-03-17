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

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class MagicRailTiltClient {

    // Needs to cover typical vertical offset between the camera/vehicle pivot and the rail path.
    // Keep reasonably small to avoid snapping to adjacent tracks in stations.
    private static final double MAX_LOOKUP_DISTANCE_SQUARED = 16.0;
    private static final double CURVE_SAMPLE_SPACING = 0.75;
    private static final int MIN_CURVE_SAMPLES = 8;
    private static final int MAX_CURVE_SAMPLES = 96;
    private static final long CAMERA_SMOOTHING_KEY = 0x43414D455241544CL;
    private static final double SMOOTHING_ALPHA = 0.25;
    private static final long SMOOTHING_PRUNE_INTERVAL_MILLIS = 2000;
    private static final long SMOOTHING_ENTRY_TIMEOUT_MILLIS = 5000;
    private static final ConcurrentHashMap<Long, SmoothedTiltEntry> SMOOTHED_TILT = new ConcurrentHashMap<>();
    private static volatile long lastPruneMillis;
    private static volatile Field jme$previousVehicleYawField;
    private static volatile boolean jme$previousVehicleYawFieldSearched;

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

    public static double getCameraTiltDegrees() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return 0;
        }
        final Vec3d cameraPos = cameraEntity.getPos();
        final Double ridingVehicleYaw = jme$getRidingVehicleYawRadians();
        if (ridingVehicleYaw != null) {
            return getSmoothedSignedTiltDegreesAt(
                    CAMERA_SMOOTHING_KEY,
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z,
                    Math.sin(ridingVehicleYaw),
                    Math.cos(ridingVehicleYaw)
            );
        } else {
            return getSmoothedTiltDegreesAt(CAMERA_SMOOTHING_KEY, cameraPos.x, cameraPos.y, cameraPos.z);
        }
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
        lastPruneMillis = 0;
    }

    private static TiltLookup findNearestTiltLookup(double x, double y, double z) {
        final MinecraftClientData clientData = MinecraftClientData.getInstance();
        RailProjection nearestProjection = null;
        Rail nearestRail = null;
        long nearestRailKey = 0;

        for (Rail rail : clientData.railIdMap.values()) {
            final RailProjection projection = projectToRailSegment(rail, x, y, z);
            if (projection == null || projection.distanceSquared > MAX_LOOKUP_DISTANCE_SQUARED) {
                continue;
            }

            if (nearestProjection == null || projection.distanceSquared < nearestProjection.distanceSquared) {
                nearestProjection = projection;
                nearestRail = rail;
                nearestRailKey = hashRailId(MagicRailTiltRegistry.normalizeRailId(rail.getHexId()));
            }
        }

        if (nearestProjection == null || nearestRail == null) {
            return null;
        }

        final MagicRailTiltRegistry.TiltSettings nearestSettings = MagicRailTiltRegistry.getTilt(nearestRail.getHexId());
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
        final double railLength = rail.railMath.getLength();
        if (railLength < 1.0E-4) {
            return null;
        }

        final int samples = Math.max(MIN_CURVE_SAMPLES, Math.min(MAX_CURVE_SAMPLES, (int) Math.ceil(railLength / CURVE_SAMPLE_SPACING)));
        final Vector firstPoint = rail.railMath.getPosition(0, false);
        if (firstPoint == null) {
            return null;
        }

        double previousX = firstPoint.x();
        double previousY = firstPoint.y();
        double previousZ = firstPoint.z();
        double bestProgress = 0;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int i = 1; i <= samples; i++) {
            final double segmentStartProgress = (i - 1D) / samples;
            final double segmentEndProgress = i / (double) samples;
            final Vector currentPoint = rail.railMath.getPosition(railLength * segmentEndProgress, false);
            if (currentPoint == null) {
                continue;
            }

            final SegmentProjection segmentProjection = projectOnSegment(
                    x, y, z,
                    previousX, previousY, previousZ,
                    currentPoint.x(), currentPoint.y(), currentPoint.z()
            );

            if (segmentProjection.distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = segmentProjection.distanceSquared;
                bestProgress = segmentStartProgress + (segmentEndProgress - segmentStartProgress) * segmentProjection.segmentProgress;
            }

            previousX = currentPoint.x();
            previousY = currentPoint.y();
            previousZ = currentPoint.z();
        }

        if (bestDistanceSquared == Double.MAX_VALUE) {
            return null;
        }

        return new RailProjection(bestProgress, bestDistanceSquared);
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
}
