package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingTimelineTest {

    private static void run(CrossingTimeline timeline, boolean active, int ticks) {
        for (int i = 0; i < ticks; i++) {
            timeline.update(active);
        }
    }

    @Test
    void lightsThenGatesThenOpenAgain() {
        final CrossingTimeline timeline = new CrossingTimeline();
        timeline.setDelays(10, 50, 0, 100);
        timeline.update(true);
        assertFalse(timeline.isLightsOn(), "the lights wait for their delay");
        run(timeline, true, 10);
        assertTrue(timeline.isLightsOn());
        assertFalse(timeline.isGatesDown());
        run(timeline, true, 40);
        assertTrue(timeline.isGatesDown());

        timeline.update(false);
        assertFalse(timeline.isGatesDown(), "the gates rise as soon as the crossing clears");
        assertTrue(timeline.isLightsOn(), "the lights keep flashing while the gates rise");
        run(timeline, false, 100);
        assertFalse(timeline.isLightsOn());
    }

    @Test
    void zeroDelaysSwitchAtOnceLikeCrossingsWithoutGates() {
        final CrossingTimeline timeline = new CrossingTimeline();
        timeline.setDelays(0, 0, 0, 0);
        timeline.update(true);
        assertTrue(timeline.isLightsOn());
        assertTrue(timeline.isGatesDown());
        timeline.update(false);
        assertFalse(timeline.isLightsOn());
        assertFalse(timeline.isGatesDown());
    }

    @Test
    void aTrainArrivingWhileOpeningKeepsEverythingClosed() {
        final CrossingTimeline timeline = new CrossingTimeline();
        timeline.setDelays(20, 60, 40, 80);
        run(timeline, true, 100);
        run(timeline, false, 10);
        assertTrue(timeline.isGatesDown());
        timeline.update(true);
        assertTrue(timeline.isLightsOn(), "no flicker");
        assertTrue(timeline.isGatesDown(), "no flicker");
    }

    @Test
    void delaysAreClamped() {
        final CrossingTimeline timeline = new CrossingTimeline();
        timeline.setDelays(-5, 100000, 0, 0);
        run(timeline, true, CrossingTimeline.MAX_DELAY_TICKS + 1);
        assertTrue(timeline.isLightsOn());
        assertTrue(timeline.isGatesDown());
    }
}
