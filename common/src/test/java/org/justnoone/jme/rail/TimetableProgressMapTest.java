package org.justnoone.jme.rail;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimetableProgressMapTest {

    /**
     * Segments of the given lengths; {@code stops[i] > 0} makes segment i a stop with that stop index.
     */
    private static List<PathData> path(double[] lengths, int[] stops) {
        final List<PathData> path = new ArrayList<>();
        double distance = 0;
        int stopIndex = 0;
        for (int i = 0; i < lengths.length; i++) {
            final boolean stop = stops[i] > 0;
            if (stop) {
                stopIndex = stops[i];
            }
            path.add(new PathData(null, stop ? 100 + i : 0, stop ? 30_000 : 0, stopIndex, distance, distance + lengths[i], new Position(i, 0, 0), Angle.E, new Position(i + 1, 0, 0), Angle.E));
            distance += lengths[i];
        }
        return path;
    }

    @Test
    void stopsOfTheRebuiltPathLandOnTheScheduledStops() {
        // Scheduled: approach 100 m, platform 60 m (stop 1), 200 m, platform 60 m (stop 2), 50 m.
        final List<PathData> scheduled = path(new double[]{100, 60, 200, 60, 50}, new int[]{0, 1, 0, 2, 0});
        // Rebuilt from segment 0: the alternative platform is reached 1.1 m earlier, the next stop 3 m later.
        final List<PathData> rebuilt = path(new double[]{98.9, 60, 204.1, 60, 50}, new int[]{0, 1, 0, 2, 0});
        final TimetableProgressMap map = TimetableProgressMap.create(null, scheduled, rebuilt, 0);

        // The end of the alternative platform (where the train stands) is the end of the scheduled platform.
        assertEquals(160, map.map(158.9), 1e-9);
        assertEquals(420, map.map(423), 1e-9);
        // Start unchanged, beyond the last matched stop the offset is kept.
        assertEquals(0, map.map(0), 1e-9);
        assertEquals(450, map.map(453), 1e-9);
        // In between, distances scale linearly.
        assertEquals(80, map.map(79.45), 1e-9);
    }

    @Test
    void identicalPathsNeedNoMap() {
        final List<PathData> scheduled = path(new double[]{100, 60, 200}, new int[]{0, 1, 0});
        assertNull(TimetableProgressMap.create(null, scheduled, path(new double[]{100, 60, 200}, new int[]{0, 1, 0}), 0));
    }

    @Test
    void aSecondRebuildStaysOnTheOriginalTimetable() {
        final List<PathData> scheduled = path(new double[]{100, 60, 200, 60}, new int[]{0, 1, 0, 2});
        final List<PathData> first = path(new double[]{98, 60, 200, 60}, new int[]{0, 1, 0, 2});
        final List<PathData> second = path(new double[]{98, 60, 190, 60}, new int[]{0, 1, 0, 2});
        final TimetableProgressMap firstMap = TimetableProgressMap.create(null, scheduled, first, 0);
        final TimetableProgressMap secondMap = TimetableProgressMap.create(firstMap, first, second, 2);
        assertEquals(160, secondMap.map(158), 1e-9);
        assertEquals(420, secondMap.map(408), 1e-9);
    }
}
