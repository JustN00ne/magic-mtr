package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingStateMachineTest {

    private static final long TIMEOUT = CrossingStateMachine.DEFAULT_TIMEOUT_TICKS;

    private static boolean step(CrossingStateMachine machine, boolean activator, boolean deactivator) {
        return machine.update(true, activator, true, deactivator, 1, TIMEOUT);
    }

    @Test
    void activatesOnApproachStaysActiveWhileOccupiedAndClearsAfterExit() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        assertFalse(step(machine, false, false));
        assertTrue(step(machine, true, false), "approach");
        assertTrue(step(machine, false, false), "between the counters");
        assertTrue(step(machine, false, true), "at the exit counter");
        assertFalse(step(machine, false, false), "cleared");
    }

    @Test
    void worksInTheReverseDirection() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        assertTrue(step(machine, false, true), "a reverse train reaches the deactivator first");
        assertTrue(step(machine, false, false));
        assertTrue(step(machine, true, false));
        assertFalse(step(machine, false, false), "and clears once it left the activator");
    }

    @Test
    void staysActiveForAFollowingTrain() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        step(machine, true, false);
        step(machine, false, false);
        assertTrue(step(machine, true, true), "second train approaches while the first is at the exit");
        assertTrue(step(machine, false, false), "first train left, second still between the counters");
        assertEquals(1, machine.getTrainsInside());
        assertTrue(step(machine, false, true));
        assertFalse(step(machine, false, false));
    }

    @Test
    void longTrainCoveringBothCountersClearsOnlyWhenGone() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        step(machine, true, false);
        assertTrue(step(machine, true, true));
        assertTrue(step(machine, false, true));
        assertFalse(step(machine, false, false));
    }

    @Test
    void singleCounterIsActiveExactlyWhileOccupied() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        assertTrue(machine.update(true, true, false, false, 1, TIMEOUT));
        assertTrue(machine.update(true, true, false, false, 1, TIMEOUT));
        assertFalse(machine.update(true, false, false, false, 1, TIMEOUT));
    }

    @Test
    void failSafeTimeoutClearsATrainThatNeverReachedTheExit() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        step(machine, true, false);
        // The train reversed back out over the activator and left.
        assertTrue(step(machine, false, false));
        assertTrue(machine.update(true, false, true, false, TIMEOUT - 2, TIMEOUT));
        assertFalse(machine.update(true, false, true, false, 1, TIMEOUT));
    }

    @Test
    void detectionResetsTheTimeout() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        step(machine, true, false);
        machine.update(true, false, true, false, TIMEOUT - 1, TIMEOUT);
        assertTrue(step(machine, true, false), "a new train at the entry");
        assertTrue(machine.update(true, false, true, false, TIMEOUT - 1, TIMEOUT));
    }

    @Test
    void restoreKeepsAnActiveCrossingActiveAfterReload() {
        final CrossingStateMachine original = new CrossingStateMachine();
        step(original, false, true);
        step(original, false, false);

        final CrossingStateMachine restored = new CrossingStateMachine();
        restored.restore(original.isActive(), original.getEntrySide(), original.getTrainsInside(), original.getLastActivator(), original.getLastDeactivator(), original.getTicksWithoutDetection());
        assertTrue(restored.isActive());
        assertEquals(CrossingStateMachine.SIDE_DEACTIVATOR, restored.getEntrySide());
        assertTrue(step(restored, true, false));
        assertFalse(step(restored, false, false));
    }

    @Test
    void restoreFromOldSaveWithOnlyTheActiveFlag() {
        final CrossingStateMachine restored = new CrossingStateMachine();
        restored.restore(true, 0, 0, false, false, 0);
        assertTrue(restored.isActive());
        assertTrue(step(restored, false, true));
        assertFalse(step(restored, false, false), "an old active crossing clears when the next train leaves the deactivator");
    }

    @Test
    void trainsReachingTwoEntryCountersAtOnceAreBothCounted() {
        final CrossingStateMachine machine = new CrossingStateMachine();
        // Two tracks: both start counters see a train in the same tick.
        assertTrue(machine.update(true, true, 2, 0, true, false, 0, 0, 1, TIMEOUT));
        assertEquals(2, machine.getTrainsInside());
        assertTrue(machine.update(true, false, 0, 2, true, false, 0, 0, 1, TIMEOUT));
        // The first train passes its end counter; the second is still between the counters.
        assertTrue(machine.update(true, false, 0, 0, true, true, 1, 0, 1, TIMEOUT));
        assertTrue(machine.update(true, false, 0, 0, true, false, 0, 1, 1, TIMEOUT));
        assertEquals(1, machine.getTrainsInside());
        assertTrue(machine.update(true, false, 0, 0, true, true, 1, 0, 1, TIMEOUT));
        assertFalse(machine.update(true, false, 0, 0, true, false, 0, 1, 1, TIMEOUT));
    }
}
