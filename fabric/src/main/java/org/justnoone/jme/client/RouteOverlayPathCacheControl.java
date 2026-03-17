package org.justnoone.jme.client;

public final class RouteOverlayPathCacheControl {

    private static volatile long invalidateToken;

    private RouteOverlayPathCacheControl() {
    }

    public static void invalidate() {
        invalidateToken++;
    }

    public static long getInvalidateToken() {
        return invalidateToken;
    }
}
