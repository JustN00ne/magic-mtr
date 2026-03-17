package org.justnoone.jme.rail;

public final class MagicRailSpeedColor {

    private static final int[] SPEED_STOPS = {5, 100, 180, 220, 300, 400};
    private static final int[] COLOR_STOPS = {
            0xFF102A8A, // deep blue
            0xFF25C977, // light green
            0xFFD9E344, // yellowish
            0xFFFFE028, // yellow
            0xFFEF3A26, // red
            0xFFB42AE6  // purple
    };

    private MagicRailSpeedColor() {
    }

    public static int colorForSpeed(int speedKmh) {
        final int speed = MagicRailConstants.clampToStep(speedKmh);

        for (int i = 0; i < SPEED_STOPS.length - 1; i++) {
            final int start = SPEED_STOPS[i];
            final int end = SPEED_STOPS[i + 1];
            if (speed <= end) {
                final float t = (speed - start) / (float) (end - start);
                return lerp(COLOR_STOPS[i], COLOR_STOPS[i + 1], t);
            }
        }

        return COLOR_STOPS[COLOR_STOPS.length - 1];
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
