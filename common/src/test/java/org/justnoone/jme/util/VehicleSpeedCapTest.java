package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleSpeedCapTest {

    // MTR speeds are meters per millisecond: km/h / 3600.
    private static final double KMH_80 = 80 / 3600D;
    private static final double KMH_120 = 120 / 3600D;
    private static final double KMH_300 = 300 / 3600D;

    @Test
    void capsRailSpeedAboveVehicleMaximum() {
        assertEquals(KMH_120, VehicleSpeedCap.cap(KMH_300, KMH_120));
    }

    @Test
    void keepsRailSpeedBelowVehicleMaximum() {
        assertEquals(KMH_80, VehicleSpeedCap.cap(KMH_80, KMH_120));
    }

    @Test
    void invalidMaximumMeansNoCap() {
        assertEquals(KMH_300, VehicleSpeedCap.cap(KMH_300, 0));
        assertEquals(KMH_300, VehicleSpeedCap.cap(KMH_300, -1));
        assertEquals(KMH_300, VehicleSpeedCap.cap(KMH_300, Double.NaN));
        assertEquals(KMH_300, VehicleSpeedCap.cap(KMH_300, Double.POSITIVE_INFINITY));
    }

    @Test
    void noUpcomingSlowerSpeedMarkerIsPreserved() {
        // Siding#getUpcomingSlowerSpeed returns -1 when there is no slower section ahead.
        assertEquals(-1, VehicleSpeedCap.cap(-1, KMH_120));
    }

    @Test
    void timetableIsRebuiltOnlyForRealSpeedChanges() {
        assertTrue(VehicleSpeedCap.hasChanged(KMH_80, KMH_120));
        assertTrue(VehicleSpeedCap.hasChanged(0, KMH_120));
        assertTrue(VehicleSpeedCap.hasChanged(KMH_120, 0));
        assertFalse(VehicleSpeedCap.hasChanged(KMH_120, KMH_120));
        // Different spellings of "no cap" are the same schedule.
        assertFalse(VehicleSpeedCap.hasChanged(0, -1));
        assertFalse(VehicleSpeedCap.hasChanged(Double.NaN, 0));
    }

    @Test
    void fasterVehicleNeedsLessTimeOnTheSameLine() {
        // Constant-speed approximation of one 3 km section limited to 300 km/h.
        final double distanceMeters = 3000;
        final double slowMillis = distanceMeters / VehicleSpeedCap.cap(KMH_300, KMH_80);
        final double fastMillis = distanceMeters / VehicleSpeedCap.cap(KMH_300, KMH_120);
        assertTrue(fastMillis < slowMillis);
        // Never faster than the rail allows.
        assertEquals(distanceMeters / KMH_300, distanceMeters / VehicleSpeedCap.cap(KMH_300, 1000 / 3600D));
    }
}
