package org.justnoone.jme.entity;

import org.justnoone.jme.util.RailPointPicker;
import org.mtr.core.data.Rail;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Vector;

import javax.annotation.Nullable;

/**
 * Attaching equipment to existing MTR rails without touching them: a rail is identified by its id
 * ({@link Rail#getHexId()}) and a point on it by the distance along its {@code RailMath} (not reversed). The rails are
 * read from MTR's simulator for the dimension, which runs on its own thread, so every read tolerates the data changing
 * underneath it.
 */
public final class RailAttachment {

    /**
     * How far beside the rail centre line, to the right of the counter's facing, the counter sits (on the right rail).
     */
    public static final double LATERAL_OFFSET = 0.72;
    private static final double TANGENT_STEP = 0.05;
    private static final double MAX_AIM_DISTANCE_FROM_RAIL = 1.0;

    private RailAttachment() {
    }

    @Nullable
    public static Rail getRail(@Nullable Simulator simulator, String railId) {
        if (simulator == null || railId.isEmpty()) {
            return null;
        }
        try {
            final Rail rail = simulator.railIdMap.get(railId);
            return rail != null && rail.isValid() ? rail : null;
        } catch (RuntimeException ignored) {
            // Read while the simulator thread was changing the map; try again next time.
            return null;
        }
    }

    /**
     * The rail point the player is aiming at.
     */
    @Nullable
    public static Target findTarget(Simulator simulator, double[] eye, double[] look, double reach) {
        final Rail[] rails;
        try {
            rails = simulator.rails.toArray(new Rail[0]);
        } catch (RuntimeException ignored) {
            return null;
        }
        final RailPointPicker.Pick best = new RailPointPicker.Pick();
        Rail bestRail = null;
        for (final Rail rail : rails) {
            if (rail == null || !rail.isValid()) {
                continue;
            }
            final double margin = reach + 2;
            if (eye[0] < rail.railMath.minX - margin || eye[0] > rail.railMath.maxX + 1 + margin || eye[1] < rail.railMath.minY - margin || eye[1] > rail.railMath.maxY + 1 + margin || eye[2] < rail.railMath.minZ - margin || eye[2] > rail.railMath.maxZ + 1 + margin) {
                continue;
            }
            if (RailPointPicker.pick(distance -> toArray(rail.railMath.getPosition(distance, false)), rail.railMath.getLength(), eye, look, reach, MAX_AIM_DISTANCE_FROM_RAIL, best)) {
                bestRail = rail;
            }
        }
        return bestRail == null ? null : new Target(bestRail, best.distance);
    }

    /**
     * Places the counter at its rail point, turned along the rail (and its slope) towards its facing, on the rail to
     * the right of that facing.
     */
    static void applyPose(AxleCounterEntity counter, Rail rail, double railProgress, boolean facingForward) {
        final double length = rail.railMath.getLength();
        final double progress = Math.max(0, Math.min(length, railProgress));
        final Vector point = rail.railMath.getPosition(progress, false);
        final Vector ahead = rail.railMath.getPosition(Math.min(length, progress + TANGENT_STEP), false);
        final Vector behind = rail.railMath.getPosition(Math.max(0, progress - TANGENT_STEP), false);
        double tx = ahead.x - behind.x, ty = ahead.y - behind.y, tz = ahead.z - behind.z;
        if (!facingForward) {
            tx = -tx;
            ty = -ty;
            tz = -tz;
        }
        final double horizontal = Math.sqrt(tx * tx + tz * tz);
        final float yaw = horizontal < 1E-6 ? 0 : (float) Math.toDegrees(Math.atan2(-tx, tz));
        final float pitch = (float) -Math.toDegrees(Math.atan2(ty, Math.max(horizontal, 1E-6)));
        // Minecraft yaw: facing (-sin, cos); its right-hand side is (-cos, -sin).
        final double yawRadians = Math.toRadians(yaw);
        final double rightX = -Math.cos(yawRadians), rightZ = -Math.sin(yawRadians);
        counter.refreshPositionAndAngles(point.x + rightX * LATERAL_OFFSET, point.y, point.z + rightZ * LATERAL_OFFSET, yaw, pitch);
    }

    /**
     * Whether the counter should face the direction of increasing distance along the rail, for a player looking along
     * {@code look}.
     */
    static boolean facesForward(Rail rail, double railProgress, double[] look) {
        final double length = rail.railMath.getLength();
        final Vector ahead = rail.railMath.getPosition(Math.min(length, railProgress + TANGENT_STEP), false);
        final Vector behind = rail.railMath.getPosition(Math.max(0, railProgress - TANGENT_STEP), false);
        return (ahead.x - behind.x) * look[0] + (ahead.z - behind.z) * look[2] >= 0;
    }

    private static double[] toArray(Vector vector) {
        return new double[]{vector.x, vector.y, vector.z};
    }

    public static final class Target {
        public final Rail rail;
        public final double railProgress;

        private Target(Rail rail, double railProgress) {
            this.rail = rail;
            this.railProgress = railProgress;
        }
    }
}
