package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexibleTimetablesTest {

    private static final long N = FlexibleTimetables.NO_OFFSET;

    @Test
    void dwellTimesMakeTrainsLeaveAtTheirDepartureTimes() {
        // Arrivals with 20 s dwells: 100 s, 220 s (100 s travel), 400 s (160 s travel).
        final long[] dwellTimes = FlexibleTimetables.computeDwellTimes(
                new long[]{100_000, 220_000, 400_000},
                new long[]{20_000, 20_000, 20_000},
                new long[]{300_000, N, 930_000},
                new long[]{0, 0, 0}
        );
        // Leaves stop 1 at 300 s; stop 2 is reached at 400 s and keeps 20 s; stop 3 at 580 s, leaves at 930 s.
        assertArrayEquals(new long[]{200_000, 20_000, 350_000}, dwellTimes);
    }

    @Test
    void lateStopsUseTheMinimumDwellTime() {
        final long[] dwellTimes = FlexibleTimetables.computeDwellTimes(
                new long[]{100_000, 300_000},
                new long[]{20_000, 20_000},
                new long[]{110_000, 200_000},
                new long[]{30_000, 30_000}
        );
        assertArrayEquals(new long[]{30_000, 30_000}, dwellTimes);
    }

    @Test
    void untimedStopsAreRaisedToTheMinimumDwellTime() {
        final long[] dwellTimes = FlexibleTimetables.computeDwellTimes(
                new long[]{0, 0},
                new long[]{10_000, 60_000},
                new long[]{N, N},
                new long[]{30_000, 30_000}
        );
        assertArrayEquals(new long[]{30_000, 60_000}, dwellTimes);
    }

    @Test
    void timedStopsAlwaysStop() {
        final long[] dwellTimes = FlexibleTimetables.computeDwellTimes(new long[]{100_000}, new long[]{20_000}, new long[]{50_000}, new long[]{0});
        assertEquals(FlexibleTimetables.MIN_STOP_DWELL_MILLIS, dwellTimes[0]);
    }

    @Test
    void parsesAndFormatsDurations() {
        assertEquals(330_000, FlexibleTimetables.parseDuration("+00:05:30"));
        assertEquals(930_000, FlexibleTimetables.parseDuration("15:30"));
        assertEquals(45_000, FlexibleTimetables.parseDuration("45"));
        assertEquals(N, FlexibleTimetables.parseDuration("  "));
        assertEquals("0:15:30", FlexibleTimetables.formatDuration(930_000));
        assertEquals("", FlexibleTimetables.formatDuration(N));
        assertThrows(IllegalArgumentException.class, () -> FlexibleTimetables.parseDuration("5:75"));
        assertThrows(IllegalArgumentException.class, () -> FlexibleTimetables.parseDuration("1:2:3:4"));
        assertThrows(IllegalArgumentException.class, () -> FlexibleTimetables.parseDuration("abc"));
    }

    @Test
    void departuresMustIncrease() {
        assertNull(FlexibleTimetables.validate(new long[]{N, 300_000, N, 900_000}, 30_000));
        assertNotNull(FlexibleTimetables.validate(new long[]{300_000, 300_000}, 0));
        assertNotNull(FlexibleTimetables.validate(new long[]{0}, -1));
    }
}
