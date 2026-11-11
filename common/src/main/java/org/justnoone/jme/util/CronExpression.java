package org.justnoone.jme.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * A standard five-field cron expression: {@code minute hour day-of-month month day-of-week}.
 * <p>
 * Supported syntax (the usual Vixie/POSIX cron syntax):
 * <ul>
 *     <li>{@code *} any value ({@code ?} is accepted as a synonym in the two day fields);</li>
 *     <li>single values ({@code 5}), lists ({@code 0,15,30,45}), ranges ({@code 5-23}) and steps
 *     ({@code *}{@code /30}, {@code 5-23/2}, {@code 10/15} meaning "from 10 to the end, every 15");</li>
 *     <li>month names {@code JAN}-{@code DEC} and weekday names {@code SUN}-{@code SAT} (case-insensitive);</li>
 *     <li>weekday {@code 0} and {@code 7} are both Sunday;</li>
 *     <li>macros {@code @yearly}/{@code @annually}, {@code @monthly}, {@code @weekly}, {@code @daily}/{@code @midnight}
 *     and {@code @hourly}.</li>
 * </ul>
 * As in standard cron, when both the day-of-month and the day-of-week fields are restricted (neither starts with
 * {@code *}), a day matches if <em>either</em> matches; otherwise both must match.
 */
public final class CronExpression {

    private static final String[] MONTH_NAMES = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final String[] WEEKDAY_NAMES = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final long ALL_MINUTES = (1L << 60) - 1;
    private static final int ALL_HOURS = (1 << 24) - 1;
    private static final long ALL_DAYS_OF_MONTH = ((1L << 32) - 1) & ~1L;
    private static final int ALL_MONTHS = ((1 << 13) - 1) & ~1;
    private static final int ALL_DAYS_OF_WEEK = (1 << 7) - 1;
    private static final int MAX_SEARCH_DAYS = 366 * 8 + 2;

    private final String expression;
    private final long minutes;
    private final int hours;
    private final long daysOfMonth;
    private final int months;
    private final int daysOfWeek;
    private final boolean dayOfMonthStar;
    private final boolean dayOfWeekStar;

    private CronExpression(String expression, long minutes, int hours, long daysOfMonth, int months, int daysOfWeek, boolean dayOfMonthStar, boolean dayOfWeekStar) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
        this.dayOfMonthStar = dayOfMonthStar;
        this.dayOfWeekStar = dayOfWeekStar;
    }

    /**
     * @throws IllegalArgumentException with a message suitable for showing to the player if the expression is invalid
     */
    public static CronExpression parse(String rawExpression) {
        if (rawExpression == null || rawExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("The cron expression is empty");
        }

        final String normalized = rawExpression.trim().replaceAll("\\s+", " ");
        final String expanded = expandMacro(normalized);
        final String[] fields = expanded.split(" ");
        if (fields.length != 5) {
            throw new IllegalArgumentException("Expected 5 fields (minute hour day month weekday) but found " + fields.length);
        }

        final long minutes = parseField(fields[0], "minute", 0, 59, null, false);
        final long hours = parseField(fields[1], "hour", 0, 23, null, false);
        final long daysOfMonth = parseField(fields[2], "day of month", 1, 31, null, true);
        final long months = parseField(fields[3], "month", 1, 12, MONTH_NAMES, false);
        long daysOfWeek = parseField(fields[4], "weekday", 0, 7, WEEKDAY_NAMES, true);
        if ((daysOfWeek & (1L << 7)) != 0) {
            daysOfWeek = (daysOfWeek & ~(1L << 7)) | 1;
        }

        return new CronExpression(normalized, minutes, (int) hours, daysOfMonth, (int) months, (int) daysOfWeek, isStar(fields[2]), isStar(fields[4]));
    }

    /**
     * @return {@code null} if the expression is valid, otherwise the reason it is not
     */
    public static String validate(String rawExpression) {
        try {
            parse(rawExpression);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public String getExpression() {
        return expression;
    }

    /**
     * @return whether the expression fires at this (local) minute; seconds are ignored
     */
    public boolean matches(LocalDateTime dateTime) {
        return (minutes & (1L << dateTime.getMinute())) != 0
                && (hours & (1 << dateTime.getHour())) != 0
                && (months & (1 << dateTime.getMonthValue())) != 0
                && matchesDay(dateTime);
    }

    /**
     * @return whether the day, month or weekday fields restrict which days the expression fires on
     */
    public boolean hasDateRestriction() {
        return daysOfMonth != ALL_DAYS_OF_MONTH || months != ALL_MONTHS || daysOfWeek != ALL_DAYS_OF_WEEK;
    }

    /**
     * @return every time of day (in minutes after midnight, ascending) the minute and hour fields allow
     */
    public int[] getMinutesOfDay() {
        final int[] result = new int[Long.bitCount(minutes) * Integer.bitCount(hours)];
        int index = 0;
        for (int hour = 0; hour < 24; hour++) {
            if ((hours & (1 << hour)) == 0) {
                continue;
            }
            for (int minute = 0; minute < 60; minute++) {
                if ((minutes & (1L << minute)) != 0) {
                    result[index++] = hour * 60 + minute;
                }
            }
        }
        return result;
    }

    /**
     * @return the first time strictly after {@code after} (truncated to the minute) at which the expression fires, or
     * {@code null} if it never fires (eg {@code 0 0 31 2 *})
     */
    public LocalDateTime next(LocalDateTime after) {
        LocalDateTime candidate = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        final LocalDateTime limit = candidate.plusDays(MAX_SEARCH_DAYS);
        while (candidate.isBefore(limit)) {
            if ((months & (1 << candidate.getMonthValue())) == 0) {
                candidate = candidate.withDayOfMonth(1).toLocalDate().atStartOfDay().plusMonths(1);
            } else if (!matchesDay(candidate)) {
                candidate = candidate.toLocalDate().plusDays(1).atStartOfDay();
            } else if ((hours & (1 << candidate.getHour())) == 0) {
                candidate = candidate.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            } else if ((minutes & (1L << candidate.getMinute())) == 0) {
                candidate = candidate.plusMinutes(1);
            } else {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return expression;
    }

    private boolean matchesDay(LocalDateTime dateTime) {
        final boolean dayOfMonthMatches = (daysOfMonth & (1L << dateTime.getDayOfMonth())) != 0;
        final boolean dayOfWeekMatches = (daysOfWeek & (1 << (dateTime.getDayOfWeek().getValue() % 7))) != 0;
        return dayOfMonthStar || dayOfWeekStar ? dayOfMonthMatches && dayOfWeekMatches : dayOfMonthMatches || dayOfWeekMatches;
    }

    private static boolean isStar(String field) {
        return field.startsWith("*") || field.startsWith("?");
    }

    private static String expandMacro(String expression) {
        if (!expression.startsWith("@")) {
            return expression;
        }
        switch (expression.toLowerCase(Locale.ROOT)) {
            case "@yearly":
            case "@annually":
                return "0 0 1 1 *";
            case "@monthly":
                return "0 0 1 * *";
            case "@weekly":
                return "0 0 * * 0";
            case "@daily":
            case "@midnight":
                return "0 0 * * *";
            case "@hourly":
                return "0 * * * *";
            default:
                throw new IllegalArgumentException("Unknown macro " + expression + " (supported: @yearly, @monthly, @weekly, @daily, @hourly)");
        }
    }

    private static long parseField(String field, String fieldName, int min, int max, String[] names, boolean allowQuestionMark) {
        if (field.isEmpty()) {
            throw new IllegalArgumentException("The " + fieldName + " field is empty");
        }
        long bits = 0;
        for (final String part : field.split(",", -1)) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Empty list item in the " + fieldName + " field '" + field + "'");
            }

            final String[] rangeAndStep = part.split("/", -1);
            if (rangeAndStep.length > 2) {
                throw new IllegalArgumentException("Too many '/' in the " + fieldName + " field '" + part + "'");
            }
            final String range = rangeAndStep[0];
            final int step = rangeAndStep.length == 2 ? parseStep(rangeAndStep[1], fieldName, max - min + 1) : 1;

            final int start;
            final int end;
            if (range.equals("*") || allowQuestionMark && range.equals("?")) {
                start = min;
                end = max;
            } else {
                final int dash = range.indexOf('-', 1);
                if (dash >= 0) {
                    start = parseValue(range.substring(0, dash), fieldName, min, max, names);
                    end = parseValue(range.substring(dash + 1), fieldName, min, max, names);
                    if (start > end) {
                        throw new IllegalArgumentException("Range " + range + " in the " + fieldName + " field goes backwards");
                    }
                } else {
                    start = parseValue(range, fieldName, min, max, names);
                    // "10/15" means from 10 to the end of the range; a plain value is just itself.
                    end = rangeAndStep.length == 2 ? max : start;
                }
            }

            for (int value = start; value <= end; value += step) {
                bits |= 1L << value;
            }
        }
        return bits;
    }

    private static int parseStep(String step, String fieldName, int span) {
        final int value;
        try {
            value = Integer.parseInt(step);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid step '" + step + "' in the " + fieldName + " field");
        }
        if (value <= 0 || value > span) {
            throw new IllegalArgumentException("Step " + value + " in the " + fieldName + " field must be between 1 and " + span);
        }
        return value;
    }

    private static int parseValue(String text, String fieldName, int min, int max, String[] names) {
        if (names != null) {
            final String upper = text.toUpperCase(Locale.ROOT);
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(upper)) {
                    return names == MONTH_NAMES ? i + 1 : i;
                }
            }
        }
        final int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " value '" + text + "'");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("The " + fieldName + " value " + value + " is outside " + min + "-" + max);
        }
        return value;
    }
}
