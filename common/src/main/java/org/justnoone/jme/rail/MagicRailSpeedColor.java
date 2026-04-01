package org.justnoone.jme.rail;

import org.justnoone.jme.config.JmeConfig;
import org.mtr.mod.data.RailType;

public final class MagicRailSpeedColor {

    private static final int COPPER_250_COLOR = 0xFFB87333;

    // OpenRailwayMap-like palette (MAGIC historic default).
    private static final int[] ORM_SPEED_STOPS = {5, 100, 180, 220, 300, 400};
    private static final int[] ORM_COLOR_STOPS = {
            0xFF102A8A, // deep blue
            0xFF25C977, // light green
            0xFFD9E344, // yellowish
            0xFFFFE028, // yellow
            0xFFEF3A26, // red
            0xFFB42AE6  // magenta (purpur connector for 400 km/h)
    };

    // MTR default rail colors blended together.
    private static final int[] MTR_SPEED_STOPS;
    private static final int[] MTR_COLOR_STOPS;

    static {
        final int[][] palette = buildMtrPaletteStops();
        MTR_SPEED_STOPS = palette[0];
        MTR_COLOR_STOPS = palette[1];
    }

    private MagicRailSpeedColor() {
    }

    public static int colorForSpeed(int speedKmh) {
        final int speed = MagicRailConstants.clampToStep(speedKmh <= 0 ? 1 : speedKmh);

        final JmeConfig.TrackColorMode mode;
        try {
            mode = JmeConfig.trackColorMode();
        } catch (Throwable ignored) {
            // Avoid crashing in odd load orders; fall back to the historic default.
            return lerpByStops(speed, ORM_SPEED_STOPS, ORM_COLOR_STOPS);
        }

        if (mode == JmeConfig.TrackColorMode.MTR_DEFAULT) {
            return lerpByStops(speed, MTR_SPEED_STOPS, MTR_COLOR_STOPS);
        }

        if (mode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT) {
            return lerpByCustomStops(speed, JmeConfig.trackColorCustomGradientStops());
        }

        return lerpByStops(speed, ORM_SPEED_STOPS, ORM_COLOR_STOPS);
    }

    private static int[][] buildMtrPaletteStops() {
        // Ensure stops are sorted by speed and always have opaque colors.
        final java.util.TreeMap<Integer, Integer> bySpeed = new java.util.TreeMap<>();

        // Keep the classic MTR "tier" colors as anchors.
        putStop(bySpeed, RailType.WOODEN.speedLimit, RailType.WOODEN.color);
        putStop(bySpeed, RailType.STONE.speedLimit, RailType.STONE.color);
        putStop(bySpeed, RailType.EMERALD.speedLimit, RailType.EMERALD.color);
        putStop(bySpeed, RailType.IRON.speedLimit, RailType.IRON.color);
        putStop(bySpeed, RailType.BRICKS.speedLimit, RailType.BRICKS.color);
        putStop(bySpeed, RailType.OBSIDIAN.speedLimit, RailType.OBSIDIAN.color);
        putStop(bySpeed, RailType.PRISMARINE.speedLimit, RailType.PRISMARINE.color);
        putStop(bySpeed, RailType.BLAZE.speedLimit, RailType.BLAZE.color);
        putStop(bySpeed, RailType.QUARTZ.speedLimit, RailType.QUARTZ.color);
        putStop(bySpeed, RailType.DIAMOND.speedLimit, RailType.DIAMOND.color);

        // Requested: make 250 km/h clearly copper.
        bySpeed.put(250, COPPER_250_COLOR);
        // Requested: 400 km/h is the purpur connector (magenta).
        bySpeed.put(400, 0xFFB42AE6);

        if (bySpeed.size() < 2) {
            return new int[][]{ORM_SPEED_STOPS, ORM_COLOR_STOPS};
        }

        final int[] speeds = new int[bySpeed.size()];
        final int[] colors = new int[bySpeed.size()];
        int idx = 0;
        for (final java.util.Map.Entry<Integer, Integer> entry : bySpeed.entrySet()) {
            speeds[idx] = entry.getKey();
            colors[idx] = entry.getValue();
            idx++;
        }

        return new int[][]{speeds, colors};
    }

    private static void putStop(java.util.TreeMap<Integer, Integer> bySpeed, int speedKmh, int color) {
        if (bySpeed == null) {
            return;
        }
        final int speed = Math.max(1, Math.min(400, speedKmh));
        final int argb = 0xFF000000 | (color & 0xFFFFFF);
        bySpeed.put(speed, argb);
    }

    private static int lerpByCustomStops(int speed, JmeConfig.TrackColorStop[] stops) {
        if (stops == null || stops.length < 2) {
            return lerpByStops(speed, ORM_SPEED_STOPS, ORM_COLOR_STOPS);
        }

        int minSpeed = Integer.MAX_VALUE;
        int maxSpeed = Integer.MIN_VALUE;
        for (final JmeConfig.TrackColorStop stop : stops) {
            if (stop == null) {
                continue;
            }
            minSpeed = Math.min(minSpeed, stop.speedKmh);
            maxSpeed = Math.max(maxSpeed, stop.speedKmh);
        }

        if (minSpeed == Integer.MAX_VALUE || maxSpeed == Integer.MIN_VALUE || maxSpeed <= minSpeed) {
            return lerpByStops(speed, ORM_SPEED_STOPS, ORM_COLOR_STOPS);
        }

        final int clampedSpeed = Math.max(minSpeed, Math.min(maxSpeed, speed));

        JmeConfig.TrackColorStop previous = null;
        for (final JmeConfig.TrackColorStop current : stops) {
            if (current == null) {
                continue;
            }

            if (clampedSpeed <= current.speedKmh) {
                if (previous == null) {
                    return current.colorArgb;
                }
                final int start = previous.speedKmh;
                final int end = current.speedKmh;
                final float t = end == start ? 0F : (clampedSpeed - start) / (float) (end - start);
                return lerp(previous.colorArgb, current.colorArgb, t);
            }

            previous = current;
        }

        return previous == null ? lerpByStops(speed, ORM_SPEED_STOPS, ORM_COLOR_STOPS) : previous.colorArgb;
    }

    private static int lerpByStops(int speed, int[] speedStops, int[] colorStops) {
        if (speedStops == null || colorStops == null || speedStops.length < 2 || colorStops.length < 2) {
            return 0xFFFFFFFF;
        }

        final int lastSpeed = speedStops[speedStops.length - 1];
        final int firstSpeed = speedStops[0];
        final int clampedSpeed = Math.max(firstSpeed, Math.min(lastSpeed, speed));

        for (int i = 0; i < speedStops.length - 1 && i < colorStops.length - 1; i++) {
            final int start = speedStops[i];
            final int end = speedStops[i + 1];
            if (clampedSpeed <= end) {
                final float t = end == start ? 0F : (clampedSpeed - start) / (float) (end - start);
                return lerp(colorStops[i], colorStops[i + 1], t);
            }
        }

        return colorStops[colorStops.length - 1];
    }

    private static int lerp(int a, int b, float t) {
        final float clamped = Math.max(0, Math.min(1, t));

        final int aa = (a >>> 24) & 0xFF;
        final int ar = (a >>> 16) & 0xFF;
        final int ag = (a >>> 8) & 0xFF;
        final int ab = a & 0xFF;

        final int ba = (b >>> 24) & 0xFF;
        final int br = (b >>> 16) & 0xFF;
        final int bg = (b >>> 8) & 0xFF;
        final int bb = b & 0xFF;

        final int ra = (int) (aa + (ba - aa) * clamped);
        final int rr = (int) (ar + (br - ar) * clamped);
        final int rg = (int) (ag + (bg - ag) * clamped);
        final int rb = (int) (ab + (bb - ab) * clamped);

        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
