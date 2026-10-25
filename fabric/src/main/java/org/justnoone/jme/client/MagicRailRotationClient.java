package org.justnoone.jme.client;

import org.justnoone.jme.rail.MagicRailRotationRegistry;
import org.mtr.core.data.Rail;

public final class MagicRailRotationClient {

    private MagicRailRotationClient() {
    }

    public static double getRotationDegreesOnRail(Rail rail, double x, double y, double z) {
        if (rail == null) {
            return 0;
        }
        return getRotationDegreesOnRail(rail, MagicRailRotationRegistry.getRotation(rail.getHexId()), x, y, z);
    }

    /**
     * Variant for callers that already looked up the rail's rotation (eg once per rail instead of once per slice).
     */
    public static double getRotationDegreesOnRail(Rail rail, MagicRailRotationRegistry.RotationSettings settings, double x, double y, double z) {
        if (rail == null || settings == null) {
            return 0;
        }
        // Uses the tilt client's cached rail samples; this used to resample the rail curve (up to 65 points) for every
        // rendered slice of every rotated rail, every frame.
        return MagicRailRotationRegistry.interpolateDegrees(settings, MagicRailTiltClient.getProgressOnRail(rail, x, y, z));
    }
}
