package org.justnoone.jme.config;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.justnoone.jme.rail.PlatformStopPositionRegistry;

public final class MagicConfigReloader {

    private MagicConfigReloader() {
    }

    public static synchronized ReloadResult reloadAllFromDisk() {
        JmeConfig.reload();
        RouteTypeOverrideConfig.reload();
        MagicRailTiltRegistry.reloadFromDisk();
        AlternativePlatformRegistry.reloadFromDisk();
        DepotCancellationRegistry.reloadFromDisk();
        PlatformStopPositionRegistry.reloadFromDisk();
        return new ReloadResult(
                JmeConfig.useMph(),
                JmeConfig.dashboardRouteListMode().name(),
                JmeConfig.dashboardRailOverlayMode().name(),
                JmeConfig.alternativePlatformsEnabled(),
                JmeConfig.systemMapOverlayCacheEnabled(),
                JmeConfig.blueMapEnabled()
        );
    }

    public static synchronized ReloadResult reloadMainConfigFromDisk() {
        JmeConfig.reload();
        return current();
    }

    public static synchronized ReloadResult current() {
        return new ReloadResult(
                JmeConfig.useMph(),
                JmeConfig.dashboardRouteListMode().name(),
                JmeConfig.dashboardRailOverlayMode().name(),
                JmeConfig.alternativePlatformsEnabled(),
                JmeConfig.systemMapOverlayCacheEnabled(),
                JmeConfig.blueMapEnabled()
        );
    }

    public static final class ReloadResult {

        public final boolean useMph;
        public final String dashboardRouteListMode;
        public final String dashboardRailOverlayMode;
        public final boolean alternativePlatformsEnabled;
        public final boolean systemMapOverlayCacheEnabled;
        public final boolean blueMapEnabled;

        public ReloadResult(
                boolean useMph,
                String dashboardRouteListMode,
                String dashboardRailOverlayMode,
                boolean alternativePlatformsEnabled,
                boolean systemMapOverlayCacheEnabled,
                boolean blueMapEnabled
        ) {
            this.useMph = useMph;
            this.dashboardRouteListMode = dashboardRouteListMode;
            this.dashboardRailOverlayMode = dashboardRailOverlayMode;
            this.alternativePlatformsEnabled = alternativePlatformsEnabled;
            this.systemMapOverlayCacheEnabled = systemMapOverlayCacheEnabled;
            this.blueMapEnabled = blueMapEnabled;
        }

        public String toDebugString() {
            return "use_mph=" + useMph
                    + ", dashboard_route_list_mode=" + dashboardRouteListMode
                    + ", dashboard_rail_overlay_mode=" + dashboardRailOverlayMode
                    + ", alternative_platforms_enabled=" + alternativePlatformsEnabled
                    + ", system_map_overlay_cache_enabled=" + systemMapOverlayCacheEnabled
                    + ", blue_map_enabled=" + blueMapEnabled;
        }
    }
}
