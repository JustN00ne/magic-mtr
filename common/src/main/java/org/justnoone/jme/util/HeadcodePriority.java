package org.justnoone.jme.util;

/**
 * Overtaking priority by headcode class, as on British railways (and Stepford County Railway): 9 (high speed) before 1
 * (semi-fast) before 2 (stopping and suburban) before 3 (leaving the depot), and a late train before an on-time train
 * of its own class, but never before an on-time train of a higher class:
 * <p>
 * 9 late > 9 > 1 late > 1 > 2 late > 2 > 3 late > 3.
 */
public final class HeadcodePriority {

    /**
     * Routes without a headcode class neither overtake nor are overtaken.
     */
    public static final int NONE = 0;
    /**
     * The classes in the order a route's settings cycle through them, after {@link #NONE}.
     */
    public static final int[] CLASSES = {9, 1, 2, 3};
    public static final int FROM_DEPOT = 3;
    /**
     * A train counts as late from this delay on.
     */
    public static final long LATE_MILLIS = 60_000;

    private HeadcodePriority() {
    }

    public static boolean isValid(int headcodeClass) {
        return headcodeClass == NONE || rank(headcodeClass) > 0;
    }

    /**
     * @param headcodeClass the class of the train's route
     * @param fromDepot     whether the train is still on its way from the depot to its first stop (it then runs as a
     *                      class 3 train)
     * @param delayMillis   how late the train is
     * @return the train's priority: a train may overtake another one of a lower priority. {@code 0} for a route without
     * a class.
     */
    public static int priority(int headcodeClass, boolean fromDepot, long delayMillis) {
        final int rank = rank(headcodeClass);
        if (rank == 0) {
            return 0;
        }
        return (fromDepot ? rank(FROM_DEPOT) : rank) * 2 + (delayMillis >= LATE_MILLIS ? 1 : 0);
    }

    /**
     * @return the class after {@code headcodeClass} in {@link #CLASSES}, then {@link #NONE} again
     */
    public static int next(int headcodeClass) {
        for (int i = 0; i < CLASSES.length; i++) {
            if (CLASSES[i] == headcodeClass) {
                return i + 1 < CLASSES.length ? CLASSES[i + 1] : NONE;
            }
        }
        return CLASSES[0];
    }

    public static String describe(int headcodeClass) {
        switch (headcodeClass) {
            case 9:
                return "9 (high speed)";
            case 1:
                return "1 (semi-fast)";
            case 2:
                return "2 (stopping)";
            case 3:
                return "3 (from depot)";
            default:
                return "none";
        }
    }

    private static int rank(int headcodeClass) {
        switch (headcodeClass) {
            case 9:
                return 4;
            case 1:
                return 3;
            case 2:
                return 2;
            case 3:
                return 1;
            default:
                return 0;
        }
    }
}
