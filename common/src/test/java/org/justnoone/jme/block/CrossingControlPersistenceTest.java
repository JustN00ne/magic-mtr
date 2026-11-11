package org.justnoone.jme.block;

import org.junit.jupiter.api.Test;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.CompoundTag;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingControlPersistenceTest {

    @Test
    void railCountersSurviveSaveAndLoad() {
        final UUID startId = UUID.randomUUID();
        final UUID endId = UUID.randomUUID();
        final CompoundTag saved = new CompoundTag();
        saved.data.putUuid("jme_crossing_start_uuid", startId);
        saved.data.putDouble("jme_crossing_start_x", 10.5);
        saved.data.putDouble("jme_crossing_start_y", 64);
        saved.data.putDouble("jme_crossing_start_z", -3.25);
        saved.data.putUuid("jme_crossing_end_uuid", endId);

        final CrossingControl control = roundTrip(load(saved));
        assertEquals(startId, control.getStart().getEntityId());
        assertFalse(control.getStart().isLegacyBlock());
        assertEquals(10.5, control.getStart().getX());
        assertEquals(64, control.getStart().getY());
        assertEquals(-3.25, control.getStart().getZ());
        assertEquals(endId, control.getEnd().getEntityId());
    }

    @Test
    void legacyBlockCountersStillLoad() {
        final CompoundTag saved = new CompoundTag();
        saved.putBoolean("jme_crossing_has_activator", true);
        saved.putInt("jme_crossing_activator_x", 4);
        saved.putInt("jme_crossing_activator_y", 70);
        saved.putInt("jme_crossing_activator_z", -9);

        final CrossingControl control = roundTrip(load(saved));
        assertTrue(control.getStart().isLegacyBlock());
        assertNull(control.getStart().getEntityId());
        assertEquals(4.5, control.getStart().getX());
        assertEquals(70.5, control.getStart().getY());
        assertEquals(-8.5, control.getStart().getZ());
        assertNull(control.getEnd());
    }

    @Test
    void objectsToggleAndPersist() {
        final CrossingControl control = new CrossingControl(() -> {
        });
        assertEquals(1, control.toggleObject(null, new BlockPos(1, 2, 3)));
        assertEquals(1, control.toggleObject(null, new BlockPos(4, 5, 6)));
        // Linking an object again unlinks it.
        assertEquals(-1, control.toggleObject(null, new BlockPos(1, 2, 3)));

        final CrossingControl loaded = roundTrip(control);
        assertEquals(1, loaded.getLinkedObjectsCount());
        assertEquals(4, loaded.getObjects().get(0).getX());
        assertEquals(6, loaded.getObjects().get(0).getZ());
    }

    @Test
    void objectsSavedByOldVersionsStillLoad() {
        final CompoundTag saved = new CompoundTag();
        saved.putBoolean("jme_crossing_has_lights", true);
        saved.putInt("jme_crossing_lights_x", 7);
        saved.putInt("jme_crossing_lights_y", 8);
        saved.putInt("jme_crossing_lights_z", 9);
        final CrossingControl control = load(saved);
        assertEquals(1, control.getLinkedObjectsCount());
        assertEquals(8, control.getObjects().get(0).getY());
    }

    @Test
    void limitsTheNumberOfObjects() {
        final CrossingControl control = new CrossingControl(() -> {
        });
        for (int i = 0; i < 16; i++) {
            assertEquals(1, control.toggleObject(null, new BlockPos(i, 0, 0)));
        }
        assertEquals(0, control.toggleObject(null, new BlockPos(99, 0, 0)));
        assertEquals(16, control.getLinkedObjectsCount());
    }

    @Test
    void unlinksARemovedCounter() {
        final UUID startId = UUID.randomUUID();
        final CompoundTag saved = new CompoundTag();
        saved.data.putUuid("jme_crossing_start_uuid", startId);
        final int[] dirty = {0};
        final CrossingControl control = new CrossingControl(() -> dirty[0]++);
        control.read(saved);
        control.onCounterRemoved(UUID.randomUUID());
        assertEquals(0, dirty[0]);
        control.onCounterRemoved(startId);
        assertNull(control.getStart());
        assertEquals(1, dirty[0]);
    }

    @Test
    void severalCountersPerRoleSurviveSaveAndLoad() {
        final CrossingControl control = new CrossingControl(() -> {
        });
        final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        final BlockPos controllerPos = new BlockPos(0, 64, 0);
        control.linkCounter(controllerPos, true, a, 1.5, 64, 2.5, "", 0, null);
        control.linkCounter(controllerPos, true, b, 5000.5, 70, -12000.25, "", 0, null);
        control.linkCounter(controllerPos, false, c, -3, 64, 9, "", 0, null);

        final CrossingControl loaded = roundTrip(control);
        assertEquals(2, loaded.getStarts().size());
        assertEquals(a, loaded.getStarts().get(0).getEntityId());
        assertEquals(b, loaded.getStarts().get(1).getEntityId());
        assertEquals(5000.5, loaded.getStarts().get(1).getX());
        assertEquals(-12000.25, loaded.getStarts().get(1).getZ());
        assertEquals(1, loaded.getEnds().size());
        assertEquals(c, loaded.getEnd().getEntityId());
        // The first counter is still in the old keys, for older MAGIC versions.
        final CompoundTag written = new CompoundTag();
        control.write(written);
        assertEquals(a, written.data.getUuid("jme_crossing_start_uuid"));
    }

    @Test
    void timelineAndManualModeSurviveSaveAndLoad() {
        final CrossingControl control = new CrossingControl(() -> {
        });
        control.configure(true, 5, 70, 10, 120);
        final CrossingControl loaded = roundTrip(control);
        assertTrue(loaded.isManualClosed());
        assertTrue(loaded.isActive());
        assertEquals(5, loaded.getTimeline().getLightsOnDelay());
        assertEquals(70, loaded.getTimeline().getGatesLowerDelay());
        assertEquals(10, loaded.getTimeline().getGatesRaiseDelay());
        assertEquals(120, loaded.getTimeline().getLightsOffDelay());
    }

    @Test
    void controllersFromOlderVersionsGetTheDefaultTimeline() {
        final CrossingControl control = load(new CompoundTag());
        assertFalse(control.isManualClosed());
        assertEquals(org.justnoone.jme.util.CrossingTimeline.DEFAULT_GATES_LOWER_DELAY, control.getTimeline().getGatesLowerDelay());
    }

    private static CrossingControl load(CompoundTag compoundTag) {
        final CrossingControl control = new CrossingControl(() -> {
        });
        control.read(compoundTag);
        return control;
    }

    private static CrossingControl roundTrip(CrossingControl control) {
        final CompoundTag written = new CompoundTag();
        control.write(written);
        return load(written);
    }
}
