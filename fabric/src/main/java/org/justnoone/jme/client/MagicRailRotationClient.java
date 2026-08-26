package org.justnoone.jme.client;

import org.justnoone.jme.rail.MagicRailRotationRegistry;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

public final class MagicRailRotationClient {

    private MagicRailRotationClient() {
    }

    public static double getRotationDegreesOnRail(Rail rail, double x, double y, double z) {
        if (rail == null) {
            return 0;
        }

        final MagicRailRotationRegistry.RotationSettings settings = MagicRailRotationRegistry.getRotation(rail.getHexId());
        if (settings == null) {
            return 0;
        }

        final double progress = getProgressOnRail(rail, x, y, z);
        return MagicRailRotationRegistry.interpolateDegrees(settings, progress);
    }

    private static double getProgressOnRail(Rail rail, double x, double y, double z) {
        try {
            final double length = rail.railMath.getLength();
            if (length < 1.0E-4) {
                return 0;
            }

            double bestDistanceSquared = Double.MAX_VALUE;
            double bestDistance = 0;
            final int samples = Math.max(8, Math.min(64, (int) Math.ceil(length / 1.25)));
            for (int i = 0; i <= samples; i++) {
                final double distance = length * i / samples;
                final Vector position = rail.railMath.getPosition(distance, false);
                if (position == null) {
                    continue;
                }

                final double dx = position.x() - x;
                final double dy = position.y() - y;
                final double dz = position.z() - z;
                final double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    bestDistance = distance;
                }
            }

            return bestDistance / length;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
