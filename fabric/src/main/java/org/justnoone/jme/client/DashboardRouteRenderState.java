package org.justnoone.jme.client;

import org.mtr.core.data.Route;

public final class DashboardRouteRenderState {

    private static Route editingRoute;
    private static int editingRoutePlatformIndex = -1;

    private DashboardRouteRenderState() {
    }

    public static void update(Route route, int routePlatformIndex) {
        editingRoute = route;
        editingRoutePlatformIndex = routePlatformIndex;
    }

    public static Route getEditingRoute() {
        return editingRoute;
    }

    public static int getEditingRoutePlatformIndex() {
        return editingRoutePlatformIndex;
    }

    public static void clear() {
        editingRoute = null;
        editingRoutePlatformIndex = -1;
    }
}
