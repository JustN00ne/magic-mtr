package org.justnoone.jme.util;

import java.util.Locale;

/**
 * Flexible timetables: each stop of a route can have a departure time given as an offset from the train's departure
 * from its depot, and the route can have a minimum dwell time.
 * <p>
 * They are applied by turning the offsets into dwell times (see {@link #computeDwellTimes}), so MTR's own timetable,
 * arrival boards and delay handling all follow them.
 */
public final class FlexibleTimetables {

    /**
     * A stop without a departure offset keeps its platform's dwell time.
     */
    public static final long NO_OFFSET = -1;
    /**
     * Shortest dwell given to a timed stop, so a train that would be late still stops (MTR treats a stop with no dwell
     * time as a stop it passes through).
     */
    public static final long MIN_STOP_DWELL_MILLIS = 1000;
    public static final long MAX_OFFSET_MILLIS = 48 * 60 * 60 * 1000L;
    public static final long MAX_MIN_DWELL_MILLIS = 60 * 60 * 1000L;

    private FlexibleTimetables() {
    }

    /**
     * Computes the dwell time of each stop so trains leave each timed stop at its departure time. Travel times between
     * stops do not depend on dwell times, so lengthening one dwell moves every later arrival by the same amount.
     *
     * @param arrivals         arrival time at each stop with the current dwell times (any time base)
     * @param dwellTimes       current dwell time of each stop
     * @param departures       wanted departure time of each stop, in the same time base as {@code arrivals}, or
     *                         {@link #NO_OFFSET}
     * @param minimumDwellTimes minimum dwell time of each stop ({@code 0} for none)
     * @return the new dwell times; a stop with no departure time keeps its dwell time, raised to its minimum. A timed
     * stop reached too late to leave on time gets its minimum dwell time instead.
     */
    public static long[] computeDwellTimes(long[] arrivals, long[] dwellTimes, long[] departures, long[] minimumDwellTimes) {
        final long[] result = new long[dwellTimes.length];
        long shift = 0;
        for (int i = 0; i < dwellTimes.length; i++) {
            final long minimum = Math.max(0, minimumDwellTimes[i]);
            if (departures[i] >= 0) {
                result[i] = Math.max(departures[i] - (arrivals[i] + shift), Math.max(minimum, MIN_STOP_DWELL_MILLIS));
            } else {
                result[i] = Math.max(dwellTimes[i], minimum);
            }
            shift += result[i] - dwellTimes[i];
        }
        return result;
    }

    /**
     * Parses {@code [+]H:MM:SS}, {@code M:SS} or plain seconds.
     *
     * @return the duration in milliseconds, or {@link #NO_OFFSET} if the text is blank
     * @throws IllegalArgumentException if the text is not a duration
     */
    public static long parseDuration(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("+")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            return NO_OFFSET;
        }
        final String[] parts = trimmed.split(":", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Too many ':' in \"" + text.trim() + "\"");
        }
        long seconds = 0;
        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (part.isEmpty() || part.length() > 6 || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("\"" + text.trim() + "\" is not a time (use H:MM:SS)");
            }
            final long value = Long.parseLong(part);
            if (i > 0 && value >= 60) {
                throw new IllegalArgumentException("Minutes and seconds must be below 60 in \"" + text.trim() + "\"");
            }
            seconds = seconds * 60 + value;
        }
        return seconds * 1000;
    }

    /**
     * @return {@code H:MM:SS}, or an empty string for {@link #NO_OFFSET}
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "";
        }
        final long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /**
     * @return {@code null} if the offsets are usable, otherwise why not
     */
    public static String validate(long[] offsets, long minimumDwellMillis) {
        if (minimumDwellMillis < 0 || minimumDwellMillis > MAX_MIN_DWELL_MILLIS) {
            return "The minimum dwell time must be at most 1 hour";
        }
        long previous = -1;
        int previousStop = -1;
        for (int i = 0; i < offsets.length; i++) {
            final long offset = offsets[i];
            if (offset == NO_OFFSET) {
                continue;
            }
            if (offset < 0 || offset > MAX_OFFSET_MILLIS) {
                return "Stop " + (i + 1) + ": departures must be within 48 hours of the depot departure";
            }
            if (offset <= previous) {
                return "Stop " + (i + 1) + " departs before or with stop " + (previousStop + 1);
            }
            previous = offset;
            previousStop = i;
        }
        return null;
    }
}
