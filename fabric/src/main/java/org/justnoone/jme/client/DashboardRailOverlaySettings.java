package org.justnoone.jme.client;

import org.justnoone.jme.config.JmeConfig;

/**
 * Client-only settings for the in-game dashboard rail overlay.
 */
public final class DashboardRailOverlaySettings {

    public enum Mode {
        ALL,
        CULL,
        OFF
    }

    private static volatile Mode railOverlayMode = Mode.ALL;

    static {
        try {
            railOverlayMode = Mode.valueOf(JmeConfig.dashboardRailOverlayMode().name());
        } catch (Exception ignored) {
            railOverlayMode = Mode.ALL;
        }
    }

    private DashboardRailOverlaySettings() {
    }

    public static Mode getRailOverlayMode() {
        return railOverlayMode;
    }

    public static void setRailOverlayMode(Mode mode) {
        railOverlayMode = mode == null ? Mode.ALL : mode;
        try {
            JmeConfig.setDashboardRailOverlayMode(JmeConfig.DashboardRailOverlayMode.valueOf(railOverlayMode.name()));
            JmeConfig.save();
        } catch (Exception ignored) {
        }
    }
}
