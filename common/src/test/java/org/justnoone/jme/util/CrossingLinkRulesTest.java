package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossingLinkRulesTest {

    @Test
    void linksAnEmptyRole() {
        assertEquals(CrossingLinkRules.CounterLinkResult.LINKED, CrossingLinkRules.linkCounter("a", null, null));
        assertEquals(CrossingLinkRules.CounterLinkResult.LINKED, CrossingLinkRules.linkCounter("a", null, "b"));
    }

    @Test
    void replacesTheCounterInTheRole() {
        assertEquals(CrossingLinkRules.CounterLinkResult.REPLACED, CrossingLinkRules.linkCounter("a", "c", "b"));
    }

    @Test
    void linkingTheSameCounterAgainUnlinksIt() {
        assertEquals(CrossingLinkRules.CounterLinkResult.UNLINKED, CrossingLinkRules.linkCounter("a", "a", "b"));
    }

    @Test
    void aCounterCannotBeStartAndEnd() {
        assertEquals(CrossingLinkRules.CounterLinkResult.REJECTED_OTHER_ROLE, CrossingLinkRules.linkCounter("b", "a", "b"));
        assertEquals(CrossingLinkRules.CounterLinkResult.REJECTED_OTHER_ROLE, CrossingLinkRules.linkCounter("b", null, "b"));
    }

    @Test
    void rejectsAMissingCounter() {
        assertThrows(NullPointerException.class, () -> CrossingLinkRules.linkCounter(null, null, null));
    }

    @Test
    void severalCountersPerRole() {
        assertEquals(CrossingLinkRules.CounterLinkResult.LINKED, CrossingLinkRules.toggleCounter("c", Arrays.asList("a", "b"), Collections.emptyList(), 16));
        assertEquals(CrossingLinkRules.CounterLinkResult.UNLINKED, CrossingLinkRules.toggleCounter("b", Arrays.asList("a", "b"), Collections.emptyList(), 16));
        assertEquals(CrossingLinkRules.CounterLinkResult.REJECTED_OTHER_ROLE, CrossingLinkRules.toggleCounter("x", Collections.singletonList("a"), Arrays.asList("y", "x"), 16));
        assertEquals(CrossingLinkRules.CounterLinkResult.REJECTED_FULL, CrossingLinkRules.toggleCounter("c", Arrays.asList("a", "b"), Collections.emptyList(), 2));
        // Unlinking still works when the role is full.
        assertEquals(CrossingLinkRules.CounterLinkResult.UNLINKED, CrossingLinkRules.toggleCounter("a", Arrays.asList("a", "b"), Collections.emptyList(), 2));
    }
}
