package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingGateMotionTest {

    @Test
    void lowersAndRaisesInFiveSeconds() {
        final CrossingGateMotion motion = new CrossingGateMotion();
        assertEquals(0, motion.update(false, 1));
        motion.update(true, 0);
        assertTrue(motion.isMoving());
        final float halfway = motion.update(true, 2.5F);
        assertTrue(halfway > 0.3 && halfway < 1, "part of the way down: " + halfway);
        assertEquals(1, motion.update(true, 2.5F), 1E-4);
        assertFalse(motion.isMoving());
        motion.update(false, 0);
        assertEquals(0, motion.update(false, 5), 1E-4);
    }

    @Test
    void overshootsLikeTheAnimation() {
        final CrossingGateMotion motion = new CrossingGateMotion();
        motion.update(true, 0);
        assertTrue(motion.update(true, 3.45F) > 1, "the arm dips past horizontal before settling");
    }

    @Test
    void turnsAroundFromWhereTheArmIs() {
        final CrossingGateMotion motion = new CrossingGateMotion();
        motion.update(true, 0);
        final float partway = motion.update(true, 2);
        final float justAfter = motion.update(false, 0.05F);
        assertTrue(justAfter <= partway && justAfter > partway * 0.9, "no jump when the gate turns around");
        assertEquals(0, motion.update(false, 5), 1E-4);
    }

    @Test
    void snapsWithoutAnimating() {
        final CrossingGateMotion motion = new CrossingGateMotion();
        motion.snap(true);
        assertEquals(1, motion.getFraction());
        assertFalse(motion.isMoving());
        assertEquals(1, motion.update(true, 0.1F));
    }
}
