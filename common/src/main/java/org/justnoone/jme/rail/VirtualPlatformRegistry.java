package org.justnoone.jme.rail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logical platform presence created by manually opened train doors.
 *
 * <p>This intentionally does not place world blocks. It gives platform-aware code a stable query point
 * while doors are open, and falls back to TTL cleanup if a close-door path is not intercepted.
 */
public final class VirtualPlatformRegistry {

    private static final long DEFAULT_TTL_MILLIS = 45_000L;
    private static final double DEFAULT_RADIUS = 1.75D;
    private static final Map<Long, Entry> OPEN_DOORS_BY_VEHICLE = new ConcurrentHashMap<>();

    private VirtualPlatformRegistry() {
    }

    public static void registerOpenDoor(long vehicleId, long platformId, double x, double y, double z) {
        if (vehicleId == 0 || platformId == 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        pruneExpired();
        OPEN_DOORS_BY_VEHICLE.put(vehicleId, new Entry(platformId, x, y, z, System.currentTimeMillis() + DEFAULT_TTL_MILLIS));
    }

    
    public static boolean hasActiveVirtualPlatform(long vehicleId) {
        if (vehicleId == 0) return false;
        pruneExpired();
        return OPEN_DOORS_BY_VEHICLE.containsKey(vehicleId);
    }

    public static void clearVehicle(long vehicleId) {
        if (vehicleId != 0) {
            OPEN_DOORS_BY_VEHICLE.remove(vehicleId);
        }
    }

    public static boolean isVirtualPlatformAt(long platformId, double x, double y, double z) {
        if (platformId == 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }
        pruneExpired();
        final double maxDistanceSq = DEFAULT_RADIUS * DEFAULT_RADIUS;
        for (final Entry entry : OPEN_DOORS_BY_VEHICLE.values()) {
            if (entry.platformId != platformId) {
                continue;
            }
            final double dx = entry.x - x;
            final double dy = entry.y - y;
            final double dz = entry.z - z;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    public static void pruneExpired() {
        final long now = System.currentTimeMillis();
        OPEN_DOORS_BY_VEHICLE.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
    }

    private static final class Entry {
        private final long platformId;
        private final double x;
        private final double y;
        private final double z;
        private final long expiresAtMillis;

        private Entry(long platformId, double x, double y, double z, long expiresAtMillis) {
            this.platformId = platformId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
