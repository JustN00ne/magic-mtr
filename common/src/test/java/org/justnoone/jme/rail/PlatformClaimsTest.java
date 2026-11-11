package org.justnoone.jme.rail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformClaimsTest {

    private static final long PLATFORM = 10;
    private static final long NOW = 1_000_000;

    @BeforeEach
    void clear() {
        PlatformClaims.clear();
    }

    @Test
    void theFirstTrainKeepsThePlatform() {
        assertTrue(PlatformClaims.claim(PLATFORM, 1, NOW));
        assertFalse(PlatformClaims.claim(PLATFORM, 2, NOW + 100));
        assertTrue(PlatformClaims.isClaimedByOther(PLATFORM, 2, NOW + 100));
        assertFalse(PlatformClaims.isClaimedByOther(PLATFORM, 1, NOW + 100));
        // Refreshing its own claim always works.
        assertTrue(PlatformClaims.claim(PLATFORM, 1, NOW + 200));
    }

    @Test
    void leavingReleasesThePlatform() {
        PlatformClaims.claim(PLATFORM, 1, NOW);
        PlatformClaims.release(PLATFORM, 1);
        assertFalse(PlatformClaims.isClaimedByOther(PLATFORM, 2, NOW));
        assertTrue(PlatformClaims.claim(PLATFORM, 2, NOW));
    }

    @Test
    void anotherTrainCannotReleaseTheClaim() {
        PlatformClaims.claim(PLATFORM, 1, NOW);
        PlatformClaims.release(PLATFORM, 2);
        assertTrue(PlatformClaims.isClaimedByOther(PLATFORM, 2, NOW));
    }

    @Test
    void aRemovedTrainStopsBlockingAfterAWhile() {
        PlatformClaims.claim(PLATFORM, 1, NOW);
        final long later = NOW + PlatformClaims.STALE_AFTER_MILLIS + 1;
        assertFalse(PlatformClaims.isClaimedByOther(PLATFORM, 2, later));
        assertTrue(PlatformClaims.claim(PLATFORM, 2, later));
    }
}
