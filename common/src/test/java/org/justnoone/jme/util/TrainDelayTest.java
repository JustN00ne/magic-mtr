package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;
import org.mtr.core.tool.Utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainDelayTest {

    private static final long DAY = 86_400_000L;

    @Test
    void matchesMtrCircularDifference() {
        final long[] values = {0, 1, 59_999, 3_600_000, DAY / 2 - 1, DAY / 2, DAY / 2 + 1, DAY - 1, DAY, DAY + 5, -5, -DAY, 3 * DAY + 17};
        for (final long value1 : values) {
            for (final long value2 : values) {
                assertEquals(Utilities.circularDifference(value1, value2, DAY), TrainDelay.circularDifference(value1, value2, DAY), value1 + " - " + value2);
            }
        }
    }

    @Test
    void trainHeldAtSignalKeepsGettingLater() {
        // Departed at t=0 and scheduled to be at its current position 10 minutes in, but held there.
        final long scheduled = 10 * 60_000L;
        assertEquals(0, TrainDelay.liveDelayMillis(scheduled, 0, scheduled, DAY));
        assertEquals(30 * 60_000L, TrainDelay.liveDelayMillis(scheduled + 30 * 60_000L, 0, scheduled, DAY));
        assertEquals(150 * 60_000L, TrainDelay.liveDelayMillis(scheduled + 150 * 60_000L, 0, scheduled, DAY));
    }

    @Test
    void earlyTrainHasNegativeDelay() {
        assertEquals(-60_000L, TrainDelay.liveDelayMillis(9 * 60_000L, 0, 10 * 60_000L, DAY));
    }

    @Test
    void nonPositiveRepeatIntervalDoesNotWrap() {
        assertEquals(5 * DAY, TrainDelay.liveDelayMillis(5 * DAY, 0, 0, 0));
    }
}
