package org.justnoone.jme.util;

/**
 * Finds the point of a rail a player is aiming at: the point of the rail curve closest to the look ray, within reach.
 * The curve is sampled every {@link #SAMPLE_STEP} blocks and the best sample refined. Pure logic (the rail is a
 * function from distance along it to a point) so it can be unit tested.
 */
public final class RailPointPicker {

    public static final double SAMPLE_STEP = 0.25;
    private static final int REFINE_ITERATIONS = 24;

    public interface Curve {
        /**
         * @param distance distance along the rail, from 0 to its length
         * @return x, y, z of the rail at that distance
         */
        double[] at(double distance);
    }

    /**
     * The best point found so far; {@link #distance} is NaN while nothing is within {@code maxRayDistance}.
     */
    public static final class Pick {
        public double distance = Double.NaN;
        public double rayDistanceSquared = Double.POSITIVE_INFINITY;
        public double alongRay = Double.POSITIVE_INFINITY;

        public boolean found() {
            return !Double.isNaN(distance);
        }
    }

    private RailPointPicker() {
    }

    /**
     * Updates {@code best} if this rail has a point closer to the ray (ties go to the point nearer the eye).
     *
     * @param look           normalized look direction
     * @param reach          how far along the ray points are accepted
     * @param maxRayDistance how far from the ray a point may be
     * @return whether {@code best} now holds a point of this rail
     */
    public static boolean pick(Curve curve, double length, double[] eye, double[] look, double reach, double maxRayDistance, Pick best) {
        if (!(length > 0)) {
            return false;
        }
        final double maxSquared = maxRayDistance * maxRayDistance;
        double bestDistance = Double.NaN;
        double bestSquared = Math.min(best.rayDistanceSquared, maxSquared);
        double bestAlong = best.alongRay;
        final int samples = (int) Math.ceil(length / SAMPLE_STEP);
        for (int i = 0; i <= samples; i++) {
            final double distance = Math.min(length, i * SAMPLE_STEP);
            final double[] result = evaluate(curve, distance, eye, look, reach);
            if (result != null && isBetter(result[0], result[1], bestSquared, bestAlong)) {
                bestSquared = result[0];
                bestAlong = result[1];
                bestDistance = distance;
            }
        }
        if (Double.isNaN(bestDistance)) {
            return false;
        }

        // Golden-section search around the best sample for the closest point.
        double low = Math.max(0, bestDistance - SAMPLE_STEP);
        double high = Math.min(length, bestDistance + SAMPLE_STEP);
        for (int i = 0; i < REFINE_ITERATIONS; i++) {
            final double m1 = low + (high - low) * 0.382;
            final double m2 = low + (high - low) * 0.618;
            if (squaredOrInfinity(curve, m1, eye, look, reach) < squaredOrInfinity(curve, m2, eye, look, reach)) {
                high = m2;
            } else {
                low = m1;
            }
        }
        final double refined = (low + high) / 2;
        final double[] result = evaluate(curve, refined, eye, look, reach);
        if (result != null && result[0] <= bestSquared) {
            bestSquared = result[0];
            bestAlong = result[1];
            bestDistance = refined;
        }

        best.distance = bestDistance;
        best.rayDistanceSquared = bestSquared;
        best.alongRay = bestAlong;
        return true;
    }

    private static boolean isBetter(double squared, double along, double bestSquared, double bestAlong) {
        // Within a few centimetres, prefer the point nearer the eye (eg the near rail of two parallel tracks).
        return squared < bestSquared - 1E-4 || squared <= bestSquared + 1E-4 && along < bestAlong;
    }

    private static double squaredOrInfinity(Curve curve, double distance, double[] eye, double[] look, double reach) {
        final double[] result = evaluate(curve, distance, eye, look, reach);
        return result == null ? Double.POSITIVE_INFINITY : result[0];
    }

    /**
     * @return squared distance from the ray and distance along the ray, or {@code null} if behind the eye or out of reach
     */
    private static double[] evaluate(Curve curve, double distance, double[] eye, double[] look, double reach) {
        final double[] point = curve.at(distance);
        final double dx = point[0] - eye[0], dy = point[1] - eye[1], dz = point[2] - eye[2];
        final double along = dx * look[0] + dy * look[1] + dz * look[2];
        if (along < 0 || along > reach) {
            return null;
        }
        return new double[]{Math.max(0, dx * dx + dy * dy + dz * dz - along * along), along};
    }
}
