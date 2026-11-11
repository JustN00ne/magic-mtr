package org.justnoone.jme.rail;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlternativePlatformSelectionTest {

    private static final long PRIMARY = 10;
    private static final List<Long> CANDIDATES = Arrays.asList(30L, PRIMARY, 20L, 40L);

    @Test
    void freePrimaryPlatformAlwaysWins() {
        // All platforms equally free: the primary platform must come first for every departure (previously ties were
        // broken randomly, diverting trains even when the primary platform was available).
        for (long seed = 0; seed < 1_000; seed++) {
            assertEquals(PRIMARY, AlternativePlatformSelection.orderCandidates(CANDIDATES, PRIMARY, new HashMap<>(), seed).get(0));
        }
    }

    @Test
    void busyPrimaryPlatformYieldsToAFreeAlternative() {
        final Map<Long, Integer> scores = new HashMap<>();
        scores.put(PRIMARY, 1);
        scores.put(30L, 1);
        scores.put(40L, 2);
        final List<Long> ordered = AlternativePlatformSelection.orderCandidates(CANDIDATES, PRIMARY, scores, 7);
        assertEquals(20L, ordered.get(0));
        assertEquals(PRIMARY, ordered.get(1)); // primary preferred over the equally busy alternative 30
        assertEquals(40L, ordered.get(3));
    }

    @Test
    void orderingIsDeterministic() {
        final Map<Long, Integer> scores = new HashMap<>();
        scores.put(PRIMARY, 5);
        assertEquals(
                AlternativePlatformSelection.orderCandidates(CANDIDATES, PRIMARY, scores, 42),
                AlternativePlatformSelection.orderCandidates(CANDIDATES, PRIMARY, scores, 42)
        );
    }
}
