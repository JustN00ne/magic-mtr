package org.justnoone.jme.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a {@link CronExpression} into the departure lists MTR's depots use.
 * <p>
 * MTR depots repeat one list of departures every day. In real-time mode that list holds UTC milliseconds after
 * midnight, matched against the real clock; in Minecraft-time mode it holds times of the Minecraft day. A cron
 * schedule is expanded into that list from its minute and hour fields. What a daily list cannot express (the day,
 * month and weekday fields, and daylight-saving changes, which move local times relative to UTC) is decided per
 * departure by {@link #isScheduled}: the list contains every candidate time for every UTC offset the zone uses during
 * the next year, and a candidate only runs (and only shows on departure boards) if the cron expression matches its
 * actual local date and time.
 */
public final class CronTimetables {

    public static final long MILLIS_PER_DAY = 86_400_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long OFFSET_LOOKAHEAD_MILLIS = 400L * MILLIS_PER_DAY;

    private CronTimetables() {
    }

    /**
     * @return the UTC times of day (milliseconds after UTC midnight, ascending, no duplicates) a real-time depot needs
     */
    public static long[] realTimeDepartures(CronExpression cron, ZoneId zone, long nowMillis) {
        final TreeSet<Long> departures = new TreeSet<>();
        final int[] minutesOfDay = cron.getMinutesOfDay();
        for (final int offsetSeconds : zoneOffsetsSeconds(zone, nowMillis)) {
            for (final int minuteOfDay : minutesOfDay) {
                departures.add(Math.floorMod(minuteOfDay * MILLIS_PER_MINUTE - offsetSeconds * 1000L, MILLIS_PER_DAY));
            }
        }
        return toArray(departures);
    }

    /**
     * @return the times of the Minecraft day (milliseconds after Minecraft midnight on a 24-hour scale, ascending) a
     * Minecraft-time depot needs; the day, month and weekday fields do not apply to Minecraft time
     */
    public static long[] gameTimeDepartures(CronExpression cron) {
        final int[] minutesOfDay = cron.getMinutesOfDay();
        final long[] departures = new long[minutesOfDay.length];
        for (int i = 0; i < minutesOfDay.length; i++) {
            departures[i] = minutesOfDay[i] * MILLIS_PER_MINUTE;
        }
        return departures;
    }

    /**
     * @param departureMillis the instant a real-time departure is (or was) scheduled to leave
     * @return whether the cron expression really schedules a departure at that instant in the given zone
     */
    public static boolean isScheduled(CronExpression cron, ZoneId zone, long departureMillis) {
        return cron.matches(LocalDateTime.ofInstant(Instant.ofEpochMilli(departureMillis), zone));
    }

    /**
     * @return the latest instant at or before {@code referenceMillis} whose UTC time of day is {@code timeOfDayMillis}
     */
    public static long latestOccurrence(long referenceMillis, long timeOfDayMillis) {
        return referenceMillis - Math.floorMod(referenceMillis - timeOfDayMillis, MILLIS_PER_DAY);
    }

    /**
     * @return up to {@code count} upcoming departures, for previews
     */
    public static List<ZonedDateTime> preview(CronExpression cron, ZoneId zone, long nowMillis, int count) {
        final List<ZonedDateTime> result = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        while (result.size() < count) {
            final LocalDateTime next = cron.next(cursor);
            if (next == null) {
                break;
            }
            // Local times skipped by a daylight-saving change do not exist, so nothing departs then.
            if (zone.getRules().isValidOffset(next, zone.getRules().getOffset(next))) {
                result.add(next.atZone(zone));
            }
            cursor = next;
        }
        return result;
    }

    /**
     * @return the UTC offsets (in seconds) the zone uses from now until a little over a year from now
     */
    public static Set<Integer> zoneOffsetsSeconds(ZoneId zone, long nowMillis) {
        final Set<Integer> offsets = new LinkedHashSet<>();
        final ZoneRules rules = zone.getRules();
        Instant instant = Instant.ofEpochMilli(nowMillis);
        offsets.add(rules.getOffset(instant).getTotalSeconds());
        final Instant end = Instant.ofEpochMilli(nowMillis + OFFSET_LOOKAHEAD_MILLIS);
        for (int i = 0; i < 16; i++) {
            final ZoneOffsetTransition transition = rules.nextTransition(instant);
            if (transition == null || transition.getInstant().isAfter(end)) {
                break;
            }
            final ZoneOffset offsetAfter = transition.getOffsetAfter();
            offsets.add(offsetAfter.getTotalSeconds());
            instant = transition.getInstant();
        }
        return offsets;
    }

    /**
     * @return the zone with this ID, or the server's default zone if the ID is blank or unknown
     */
    public static ZoneId resolveZone(String zoneId) {
        if (zoneId != null && !zoneId.trim().isEmpty()) {
            try {
                return ZoneId.of(zoneId.trim());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.systemDefault();
    }

    /**
     * @return {@code null} if the zone ID is blank (server default) or valid, otherwise the reason it is not
     */
    public static String validateZone(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return null;
        }
        try {
            ZoneId.of(zoneId.trim());
            return null;
        } catch (Exception e) {
            return "Unknown time zone '" + zoneId.trim() + "' (use eg Europe/London, America/New_York, UTC or +08:00)";
        }
    }

    private static long[] toArray(Set<Long> values) {
        final long[] result = new long[values.size()];
        int index = 0;
        for (final long value : values) {
            result[index++] = value;
        }
        return result;
    }
}
