package org.justnoone.jme.rail;

import org.mtr.core.data.PathData;

import java.util.List;

/**
 * Implemented by MTR's {@code Vehicle} (mixin): keeps a train on its siding's timetable after its path was rebuilt for
 * an alternative platform (see {@link TimetableProgressMap}).
 */
public interface TimetableAligned {

    /**
     * Call right after replacing the vehicle's path: {@code oldPath} is the path it had, {@code newPath} the new one,
     * identical before {@code fromIndex}.
     */
    void jme$alignTimetable(List<PathData> oldPath, List<PathData> newPath, int fromIndex);

    /**
     * @return the position on the timetable's path that corresponds to {@code railProgress} on the current path
     */
    double jme$toTimetableProgress(double railProgress);
}
