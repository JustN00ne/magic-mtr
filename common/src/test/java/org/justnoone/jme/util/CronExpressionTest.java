package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CronExpressionTest {

    // 2026-10-19 is a Monday.
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 10, 19, 0, 0);

    @Test
    void everyThirtyMinutesFromFiveToElevenOnWeekdays() {
        final CronExpression cron = CronExpression.parse("*/30 5-23 * * 1-5");
        assertEquals(38, cron.getMinutesOfDay().length);
        assertTrue(cron.matches(MONDAY.withHour(5)));
        assertTrue(cron.matches(MONDAY.withHour(23).withMinute(30)));
        assertFalse(cron.matches(MONDAY.withHour(4).withMinute(30)));
        assertFalse(cron.matches(MONDAY.withHour(5).withMinute(15)));
        assertFalse(cron.matches(MONDAY.plusDays(5).withHour(5)), "Saturday");
        assertFalse(cron.matches(MONDAY.plusDays(6).withHour(5)), "Sunday");
        assertTrue(cron.hasDateRestriction());
    }

    @Test
    void quarterHourList() {
        final CronExpression cron = CronExpression.parse("0,15,30,45 * * * *");
        assertEquals(96, cron.getMinutesOfDay().length);
        assertFalse(cron.hasDateRestriction());
        assertTrue(cron.matches(MONDAY.withHour(13).withMinute(45)));
        assertFalse(cron.matches(MONDAY.withHour(13).withMinute(46)));
    }

    @Test
    void christmasMidnight() {
        final CronExpression cron = CronExpression.parse("0 0 25 12 *");
        assertArrayEquals(new int[]{0}, cron.getMinutesOfDay());
        assertTrue(cron.matches(LocalDateTime.of(2026, 12, 25, 0, 0)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 12, 24, 0, 0)));
        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), cron.next(MONDAY));
        assertEquals(LocalDateTime.of(2027, 12, 25, 0, 0), cron.next(LocalDateTime.of(2026, 12, 25, 0, 0)));
    }

    @Test
    void namesStepsAndMacros() {
        assertTrue(CronExpression.parse("0 8 * jan-mar MON,wed").matches(LocalDateTime.of(2027, 2, 3, 8, 0)));
        assertTrue(CronExpression.parse("0 0 * * 7").matches(LocalDateTime.of(2026, 10, 25, 0, 0)), "7 is Sunday");
        assertArrayEquals(new int[]{10, 25, 40, 55}, CronExpression.parse("10/15 0 * * *").getMinutesOfDay());
        assertArrayEquals(new int[]{60, 180, 300}, CronExpression.parse("0 1-5/2 * * *").getMinutesOfDay());
        assertEquals(24, CronExpression.parse("@hourly").getMinutesOfDay().length);
        assertEquals(1, CronExpression.parse("@daily").getMinutesOfDay().length);
        assertTrue(CronExpression.parse("@weekly").matches(LocalDateTime.of(2026, 10, 25, 0, 0)));
        assertTrue(CronExpression.parse("  0   12 * * ? ").matches(MONDAY.withHour(12)));
    }

    @Test
    void dayOfMonthOrDayOfWeekWhenBothRestricted() {
        // Standard cron: the 1st of the month OR any Friday.
        final CronExpression cron = CronExpression.parse("0 9 1 * 5");
        assertTrue(cron.matches(LocalDateTime.of(2026, 10, 1, 9, 0)), "the 1st (a Thursday)");
        assertTrue(cron.matches(LocalDateTime.of(2026, 10, 23, 9, 0)), "a Friday");
        assertFalse(cron.matches(LocalDateTime.of(2026, 10, 22, 9, 0)));
        // With a star day-of-month, the weekday alone decides.
        assertFalse(CronExpression.parse("0 9 * * 5").matches(LocalDateTime.of(2026, 10, 1, 9, 0)));
    }

    @Test
    void rejectsInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("60 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* 24 * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * 0 * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * 13 *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * * 8"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("*/0 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("10-5 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("1,,2 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("abc * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("@reboot"));
        assertNotNull(CronExpression.validate("* * * * * *"));
        assertNull(CronExpression.validate("*/30 5-23 * * 1-5"));
    }

    @Test
    void impossibleDateNeverFires() {
        assertNull(CronExpression.parse("0 0 31 2 *").next(MONDAY));
    }

    @Test
    void realTimeDeparturesAreUtcTimesOfDay() {
        final CronExpression cron = CronExpression.parse("0 8 * * *");
        final long now = ZonedDateTime.of(MONDAY, ZoneOffset.UTC).toInstant().toEpochMilli();
        assertArrayEquals(new long[]{6 * 3_600_000L}, CronTimetables.realTimeDepartures(cron, ZoneId.of("+02:00"), now));
        // Negative offsets wrap around midnight.
        assertArrayEquals(new long[]{2 * 3_600_000L}, CronTimetables.realTimeDepartures(CronExpression.parse("0 22 * * *"), ZoneId.of("-04:00"), now));
    }

    @Test
    void daylightSavingZonesGetCandidatesForBothOffsets() {
        final CronExpression cron = CronExpression.parse("0 8 * * *");
        final long now = ZonedDateTime.of(MONDAY, ZoneOffset.UTC).toInstant().toEpochMilli();
        final ZoneId london = ZoneId.of("Europe/London");
        // 08:00 London is 07:00 UTC in summer and 08:00 UTC in winter.
        assertArrayEquals(new long[]{7 * 3_600_000L, 8 * 3_600_000L}, CronTimetables.realTimeDepartures(cron, london, now));

        // Only the candidate matching the actual local time runs on a given day.
        final long summerDeparture = ZonedDateTime.of(2026, 7, 1, 7, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        final long summerWrongCandidate = ZonedDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        assertTrue(CronTimetables.isScheduled(cron, london, summerDeparture));
        assertFalse(CronTimetables.isScheduled(cron, london, summerWrongCandidate));
        final long winterDeparture = ZonedDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        assertTrue(CronTimetables.isScheduled(cron, london, winterDeparture));
    }

    @Test
    void weekdayFilterUsesTheLocalDate() {
        final CronExpression cron = CronExpression.parse("30 23 * * 5");
        final ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        // Friday 23:30 in Tokyo is Friday 14:30 UTC.
        final long fridayTokyo = ZonedDateTime.of(2026, 10, 23, 23, 30, 0, 0, tokyo).toInstant().toEpochMilli();
        assertTrue(CronTimetables.isScheduled(cron, tokyo, fridayTokyo));
        assertFalse(CronTimetables.isScheduled(cron, tokyo, fridayTokyo + CronTimetables.MILLIS_PER_DAY));
    }

    @Test
    void latestOccurrence() {
        final long day = CronTimetables.MILLIS_PER_DAY;
        assertEquals(10 * day + 5_000, CronTimetables.latestOccurrence(10 * day + 7_000, 5_000));
        assertEquals(9 * day + 8_000, CronTimetables.latestOccurrence(10 * day + 7_000, 8_000));
        assertEquals(10 * day + 7_000, CronTimetables.latestOccurrence(10 * day + 7_000, 7_000));
    }

    @Test
    void previewSkipsWeekends() {
        final CronExpression cron = CronExpression.parse("0 9 * * 1-5");
        final long fridayEvening = ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        final List<ZonedDateTime> preview = CronTimetables.preview(cron, ZoneOffset.UTC, fridayEvening, 2);
        assertEquals(2, preview.size());
        assertEquals(26, preview.get(0).getDayOfMonth(), "next Monday");
        assertEquals(27, preview.get(1).getDayOfMonth());
    }

    @Test
    void gameTimeDepartures() {
        assertArrayEquals(new long[]{5 * 3_600_000L, 5 * 3_600_000L + 30 * 60_000L}, CronTimetables.gameTimeDepartures(CronExpression.parse("0,30 5 * * *")));
    }

    @Test
    void zoneValidation() {
        assertNull(CronTimetables.validateZone(""));
        assertNull(CronTimetables.validateZone("Europe/Lisbon"));
        assertNull(CronTimetables.validateZone("+08:00"));
        assertNotNull(CronTimetables.validateZone("Mars/Olympus"));
        assertEquals(ZoneId.systemDefault(), CronTimetables.resolveZone("Mars/Olympus"));
    }
}
