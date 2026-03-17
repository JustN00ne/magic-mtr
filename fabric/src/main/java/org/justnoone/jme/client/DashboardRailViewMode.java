package org.justnoone.jme.client;

public final class DashboardRailViewMode {

    public enum Mode {
        SPEED
    }

    private static final Mode currentMode = Mode.SPEED;

    private DashboardRailViewMode() {
    }

    public static Mode get() {
        return currentMode;
    }

    public static boolean isSpeedMode() {
        return true;
    }

    public static void cycle() {
        // Only speed coloring is supported.
    }
}
