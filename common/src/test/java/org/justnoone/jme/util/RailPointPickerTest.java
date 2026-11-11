package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailPointPickerTest {

    // A straight rail along +x at y = 0, z = 0, 20 blocks long.
    private static final RailPointPicker.Curve STRAIGHT = distance -> new double[]{distance, 0, 0};
    private static final double[] DOWN = normalize(0, -1, 0);

    @Test
    void picksThePointUnderTheCrosshair() {
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        assertTrue(RailPointPicker.pick(STRAIGHT, 20, new double[]{7.3, 2, 0}, DOWN, 6, 1, pick));
        assertEquals(7.3, pick.distance, 1E-3);
        assertEquals(0, pick.rayDistanceSquared, 1E-6);
    }

    @Test
    void picksTheClosestPointOfASlantedLook() {
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        // Looking from (2, 1.6, 0.4) towards (5, 0, 0).
        assertTrue(RailPointPicker.pick(STRAIGHT, 20, new double[]{2, 1.6, 0.4}, normalize(3, -1.6, -0.4), 6, 1, pick));
        assertEquals(5, pick.distance, 0.05);
    }

    @Test
    void ignoresRailsOutOfReachOrBehind() {
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        // The rail is 10 blocks below, out of a 6 block reach.
        assertFalse(RailPointPicker.pick(STRAIGHT, 20, new double[]{5, 10, 0}, DOWN, 6, 1, pick));
        // Looking up, away from the rail.
        assertFalse(RailPointPicker.pick(STRAIGHT, 20, new double[]{5, 2, 0}, normalize(0, 1, 0), 6, 1, pick));
        assertFalse(pick.found());
    }

    @Test
    void ignoresRailsTooFarFromTheRay() {
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        // Looking down 1.5 blocks beside the rail.
        assertFalse(RailPointPicker.pick(STRAIGHT, 20, new double[]{5, 2, 1.5}, DOWN, 6, 1, pick));
    }

    @Test
    void keepsTheCloserOfTwoRails() {
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        final RailPointPicker.Curve parallel = distance -> new double[]{distance, 0, 0.6};
        assertTrue(RailPointPicker.pick(STRAIGHT, 20, new double[]{4, 2, 0.5}, DOWN, 6, 1, pick));
        // The parallel rail at z = 0.6 is closer to the ray at z = 0.5.
        assertTrue(RailPointPicker.pick(parallel, 20, new double[]{4, 2, 0.5}, DOWN, 6, 1, pick));
        // A third rail further away does not replace it.
        assertFalse(RailPointPicker.pick(distance -> new double[]{distance, 0, -0.5}, 20, new double[]{4, 2, 0.5}, DOWN, 6, 1, pick));
        assertEquals(4, pick.distance, 1E-3);
    }

    @Test
    void followsACurve() {
        // A quarter circle of radius 10 around (0, 0, 0), from (10, 0, 0) to (0, 0, 10).
        final double radius = 10;
        final RailPointPicker.Curve arc = distance -> new double[]{radius * Math.cos(distance / radius), 0, radius * Math.sin(distance / radius)};
        final double length = Math.PI / 2 * radius;
        final double angle = Math.PI / 6;
        final RailPointPicker.Pick pick = new RailPointPicker.Pick();
        assertTrue(RailPointPicker.pick(arc, length, new double[]{radius * Math.cos(angle), 2, radius * Math.sin(angle)}, DOWN, 6, 1, pick));
        assertEquals(angle * radius, pick.distance, 1E-3);
    }

    private static double[] normalize(double x, double y, double z) {
        final double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }
}
