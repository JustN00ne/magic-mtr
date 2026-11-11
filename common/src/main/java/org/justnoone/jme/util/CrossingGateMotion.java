package org.justnoone.jme.util;

/**
 * How far a crossing gate's arm is lowered over time, from the {@code lower_gate} and {@code raise_gate} animations of
 * its Blockbench model ({@code crossing_gate.bbmodel} at repo root): the arm takes 5 seconds either way, overshoots a
 * little and settles. Measured as the fraction of the way down (0 = up, 1 = down, 90 degrees). A gate that turns
 * around halfway continues from where its arm is. Pure logic (no Minecraft types) so it can be unit tested; the client
 * runs it while drawing the gate.
 */
public final class CrossingGateMotion {

    public static final float DURATION_SECONDS = 5;
    /**
     * Keyframes (seconds, fraction down) of the lowering animation.
     */
    private static final float[][] LOWER = {{0, 0}, {2.8F, 85.5F / 90}, {3.45F, 92F / 90}, {4.25F, 89F / 90}, {5, 1}};
    /**
     * Keyframes of the raising animation, as the fraction of the way still down.
     */
    private static final float[][] RAISE = {{0, 1}, {3.3F, -2F / 90}, {5, 0}};

    private boolean lowering;
    private float startFraction;
    private float fraction;
    private float seconds = DURATION_SECONDS;

    /**
     * Jumps straight to a position, eg when a gate is first loaded.
     */
    public void snap(boolean lowered) {
        lowering = lowered;
        fraction = startFraction = lowered ? 1 : 0;
        seconds = DURATION_SECONDS;
    }

    /**
     * Advances the arm.
     *
     * @param lowered        whether the gate should be down
     * @param elapsedSeconds time since the previous update
     * @return the fraction of the way down
     */
    public float update(boolean lowered, float elapsedSeconds) {
        if (lowered != lowering) {
            lowering = lowered;
            startFraction = fraction;
            seconds = 0;
        }
        seconds = Math.min(DURATION_SECONDS, seconds + Math.max(0, elapsedSeconds));
        if (lowering) {
            fraction = startFraction + (1 - startFraction) * sample(LOWER, seconds);
        } else {
            fraction = startFraction * sample(RAISE, seconds);
        }
        return fraction;
    }

    public float getFraction() {
        return fraction;
    }

    public boolean isMoving() {
        return seconds < DURATION_SECONDS;
    }

    /**
     * Eases between the keyframes, like the bezier keyframes of the animation.
     */
    static float sample(float[][] keyframes, float time) {
        if (time <= keyframes[0][0]) {
            return keyframes[0][1];
        }
        for (int i = 1; i < keyframes.length; i++) {
            if (time <= keyframes[i][0]) {
                final float t = (time - keyframes[i - 1][0]) / (keyframes[i][0] - keyframes[i - 1][0]);
                final float eased = t * t * (3 - 2 * t);
                return keyframes[i - 1][1] + (keyframes[i][1] - keyframes[i - 1][1]) * eased;
            }
        }
        return keyframes[keyframes.length - 1][1];
    }
}
