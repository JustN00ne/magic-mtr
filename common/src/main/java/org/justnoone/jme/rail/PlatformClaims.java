package org.justnoone.jme.rail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which train has claimed a platform: a train approaching its next stop (within the early-assignment distance) or
 * standing at it claims that platform, and the first claim wins. Trains refresh their claim every simulation tick and
 * release it when they leave; a claim not refreshed for {@link #STALE_AFTER_MILLIS} (removed train, unloaded
 * dimension) no longer counts.
 */
public final class PlatformClaims {

    public static final long STALE_AFTER_MILLIS = 5_000;

    private static final Map<Long, Claim> CLAIMS = new ConcurrentHashMap<>();

    private PlatformClaims() {
    }

    /**
     * Claims (or refreshes the claim of) a platform for a train.
     *
     * @return {@code false} if another train holds a live claim on it
     */
    public static boolean claim(long platformId, long vehicleId, long nowMillis) {
        if (platformId == 0) {
            return false;
        }
        final boolean[] claimed = {false};
        CLAIMS.compute(platformId, (id, claim) -> {
            if (claim == null || claim.vehicleId == vehicleId || nowMillis - claim.refreshedAtMillis > STALE_AFTER_MILLIS) {
                claimed[0] = true;
                return new Claim(vehicleId, nowMillis);
            }
            return claim;
        });
        return claimed[0];
    }

    public static boolean isClaimedByOther(long platformId, long vehicleId, long nowMillis) {
        final Claim claim = CLAIMS.get(platformId);
        return claim != null && claim.vehicleId != vehicleId && nowMillis - claim.refreshedAtMillis <= STALE_AFTER_MILLIS;
    }

    /**
     * @return the train holding a live claim on the platform, or 0
     */
    public static long getHolder(long platformId, long nowMillis) {
        final Claim claim = CLAIMS.get(platformId);
        return claim == null || nowMillis - claim.refreshedAtMillis > STALE_AFTER_MILLIS ? 0 : claim.vehicleId;
    }

    public static void release(long platformId, long vehicleId) {
        if (platformId != 0) {
            CLAIMS.computeIfPresent(platformId, (id, claim) -> claim.vehicleId == vehicleId ? null : claim);
        }
    }

    public static void clear() {
        CLAIMS.clear();
    }

    private static final class Claim {
        private final long vehicleId;
        private final long refreshedAtMillis;

        private Claim(long vehicleId, long refreshedAtMillis) {
            this.vehicleId = vehicleId;
            this.refreshedAtMillis = refreshedAtMillis;
        }
    }
}
