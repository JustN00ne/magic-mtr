package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadcodePriorityTest {

    private static final long ON_TIME = 0;
    private static final long LATE = HeadcodePriority.LATE_MILLIS;

    @Test
    void followsTheBritishOrder() {
        // 9 late > 9 > 1 late > 1 > 2 late > 2 > 3 late > 3
        final int[] order = {
                HeadcodePriority.priority(9, false, LATE),
                HeadcodePriority.priority(9, false, ON_TIME),
                HeadcodePriority.priority(1, false, LATE),
                HeadcodePriority.priority(1, false, ON_TIME),
                HeadcodePriority.priority(2, false, LATE),
                HeadcodePriority.priority(2, false, ON_TIME),
                HeadcodePriority.priority(3, false, LATE),
                HeadcodePriority.priority(3, false, ON_TIME),
        };
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i - 1] > order[i], "position " + i);
        }
        assertTrue(order[order.length - 1] > 0);
    }

    @Test
    void lateMeansAtLeastAMinute() {
        assertEquals(HeadcodePriority.priority(1, false, ON_TIME), HeadcodePriority.priority(1, false, LATE - 1));
    }

    @Test
    void trainsFromTheDepotRunAsClass3() {
        assertEquals(HeadcodePriority.priority(3, false, ON_TIME), HeadcodePriority.priority(9, true, ON_TIME));
        assertEquals(HeadcodePriority.priority(3, false, LATE), HeadcodePriority.priority(1, true, LATE));
    }

    @Test
    void routesWithoutAClassTakeNoPart() {
        assertEquals(0, HeadcodePriority.priority(HeadcodePriority.NONE, false, LATE));
        assertEquals(0, HeadcodePriority.priority(5, true, ON_TIME));
        assertFalse(HeadcodePriority.isValid(5));
        assertTrue(HeadcodePriority.isValid(HeadcodePriority.NONE));
    }

    @Test
    void cyclesThroughTheClasses() {
        int headcodeClass = HeadcodePriority.NONE;
        final StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            headcodeClass = HeadcodePriority.next(headcodeClass);
            seen.append(headcodeClass);
        }
        assertEquals("91230", seen.toString());
    }
}
