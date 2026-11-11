package org.justnoone.jme.rail;

import org.mtr.core.data.PathData;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Maps a train's progress along a rebuilt path (in metres) to progress along the path its siding's timetable was
 * computed for.
 * <p>
 * A train sent to an alternative platform drives real rail lengths, which differ a little from the scheduled way. MTR
 * looks up the timetable by distance, so without this a train standing at the end of an alternative platform that ends
 * 1 m before the scheduled one counts as not yet arrived: its dwell is not credited and its delay jumps by the dwell time
 * when it departs (which made delay-based cancellation remove it). Stops are matched by their stop index and mapped
 * exactly onto each other; between them the distance is scaled linearly.
 */
public final class TimetableProgressMap {

    private final double[] from;
    private final double[] to;

    private TimetableProgressMap(double[] from, double[] to) {
        this.from = from;
        this.to = to;
    }

    /**
     * @param previous     the map of {@code oldPath} (if it was itself rebuilt), or {@code null}
     * @param fromIndex    first path index that differs between the paths (the start of that segment is the same in both)
     * @return the map for {@code newPath}, or {@code null} if both paths line up already
     */
    @Nullable
    public static TimetableProgressMap create(@Nullable TimetableProgressMap previous, List<PathData> oldPath, List<PathData> newPath, int fromIndex) {
        if (fromIndex < 0 || fromIndex >= oldPath.size() || fromIndex >= newPath.size()) {
            return previous;
        }

        final double[] fromList = new double[Math.min(oldPath.size(), newPath.size()) + 1];
        final double[] toList = new double[fromList.length];
        int count = 0;
        // Before the first changed segment both paths are identical.
        fromList[count] = newPath.get(fromIndex).getStartDistance();
        toList[count] = map(previous, oldPath.get(fromIndex).getStartDistance());
        count++;

        int oldIndex = fromIndex;
        int newIndex = fromIndex;
        while (true) {
            oldIndex = nextStop(oldPath, oldIndex);
            newIndex = nextStop(newPath, newIndex);
            if (oldIndex < 0 || newIndex < 0 || oldPath.get(oldIndex).getStopIndex() != newPath.get(newIndex).getStopIndex()) {
                break;
            }
            final double newEnd = newPath.get(newIndex).getEndDistance();
            if (newEnd == Double.MAX_VALUE || newEnd <= fromList[count - 1]) {
                break;
            }
            fromList[count] = newEnd;
            toList[count] = map(previous, oldPath.get(oldIndex).getEndDistance());
            count++;
            oldIndex++;
            newIndex++;
        }

        boolean identity = true;
        for (int i = 0; i < count; i++) {
            if (Math.abs(fromList[i] - toList[i]) > 1e-6) {
                identity = false;
                break;
            }
        }
        if (identity) {
            return null;
        }
        final double[] from = new double[count];
        final double[] to = new double[count];
        System.arraycopy(fromList, 0, from, 0, count);
        System.arraycopy(toList, 0, to, 0, count);
        return new TimetableProgressMap(from, to);
    }

    public static double map(@Nullable TimetableProgressMap map, double progress) {
        return map == null ? progress : map.map(progress);
    }

    public double map(double progress) {
        if (progress <= from[0]) {
            return progress - from[0] + to[0];
        }
        for (int i = 1; i < from.length; i++) {
            if (progress <= from[i]) {
                final double ratio = (progress - from[i - 1]) / (from[i] - from[i - 1]);
                return to[i - 1] + ratio * (to[i] - to[i - 1]);
            }
        }
        // After the last matched stop the rebuilt path continues with the scheduled one, shifted.
        return progress - from[from.length - 1] + to[to.length - 1];
    }

    private static int nextStop(List<PathData> path, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData.getDwellTime() > 0 && pathData.getSavedRailBaseId() != 0) {
                return i;
            }
        }
        return -1;
    }
}
