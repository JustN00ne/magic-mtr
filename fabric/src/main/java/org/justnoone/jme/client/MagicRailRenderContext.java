package org.justnoone.jme.client;

public final class MagicRailRenderContext {

    private static final ThreadLocal<Integer> OVERRIDE_SPEED_KMH = new ThreadLocal<>();

    private MagicRailRenderContext() {
    }

    public static void pushOverrideSpeed(int speedKmh) {
        OVERRIDE_SPEED_KMH.set(speedKmh);
    }

    public static Integer getOverrideSpeed() {
        return OVERRIDE_SPEED_KMH.get();
    }

    public static void clear() {
        OVERRIDE_SPEED_KMH.remove();
    }
}
