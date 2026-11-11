package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.RouteType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTypeKeysTest {

    @Test
    void everyDropdownEntryMapsToAStorableType() {
        for (final String key : RouteTypeKeys.TRAIN_ROUTE_TYPE_ORDER) {
            assertEquals(key, RouteTypeKeys.normalizeTrainType(key));
            final RouteType baseType = RouteTypeKeys.getBaseRouteType(key);
            if (RouteTypeKeys.isBaseTrainKey(key)) {
                // Natively stored by MTR: the route type alone round-trips.
                assertEquals(key, RouteTypeKeys.getEffectiveKey(baseType, ""));
            } else {
                // Extended type: stored as an override on top of a base type.
                assertEquals(key, RouteTypeKeys.getEffectiveKey(baseType, key));
            }
        }
    }

    @Test
    void selectingABaseTypeIgnoresStaleOverrideOnlyWhenCleared() {
        assertEquals(RouteTypeKeys.TRAIN_METRO, RouteTypeKeys.getEffectiveKey(RouteType.NORMAL, RouteTypeKeys.TRAIN_METRO));
        assertEquals(RouteTypeKeys.TRAIN_NORMAL, RouteTypeKeys.getEffectiveKey(RouteType.NORMAL, ""));
        assertEquals(RouteTypeKeys.TRAIN_HIGH_SPEED, RouteTypeKeys.getEffectiveKey(RouteType.HIGH_SPEED, ""));
        assertEquals(RouteTypeKeys.TRAIN_NORMAL, RouteTypeKeys.getEffectiveKey(RouteType.NORMAL, "not-a-type"));
    }

    @Test
    void normalizesLegacySpellings() {
        assertEquals(RouteTypeKeys.TRAIN_SBAHN, RouteTypeKeys.normalizeTrainType("S-Bahn"));
        assertEquals(RouteTypeKeys.TRAIN_LIGHT_RAIL, RouteTypeKeys.normalizeTrainType("light rail"));
        assertEquals(RouteTypeKeys.TRAIN_BUS, RouteTypeKeys.normalizeTrainType(" BUS "));
        assertEquals("", RouteTypeKeys.normalizeTrainType("monorail"));
        assertEquals("", RouteTypeKeys.normalizeTrainType(null));
    }

    @Test
    void baseKeys() {
        assertTrue(RouteTypeKeys.isBaseTrainKey(RouteTypeKeys.TRAIN_NORMAL));
        assertTrue(RouteTypeKeys.isBaseTrainKey(""));
        assertFalse(RouteTypeKeys.isBaseTrainKey(RouteTypeKeys.TRAIN_TRAM));
    }

    @Test
    void dropdownClickMapsToTheRowUnderTheCursor() {
        final int rowHeight = 12;
        final int gap = 1;
        final int rows = RouteTypeKeys.TRAIN_ROUTE_TYPE_ORDER.length;
        for (int row = 0; row < rows; row++) {
            final int top = row * (rowHeight + gap);
            assertEquals(row, RouteTypeKeys.rowAt(top, rowHeight, gap, rows));
            assertEquals(row, RouteTypeKeys.rowAt(top + rowHeight - 0.5, rowHeight, gap, rows));
            // The 1px gap below each row selects nothing.
            assertEquals(-1, RouteTypeKeys.rowAt(top + rowHeight + 0.5, rowHeight, gap, rows));
        }
        assertEquals(-1, RouteTypeKeys.rowAt(-1, rowHeight, gap, rows));
        assertEquals(-1, RouteTypeKeys.rowAt(rows * (rowHeight + gap) + 1, rowHeight, gap, rows));
    }

    @Test
    void webTypeKeysMatchMtrWebMapKeys() {
        // Dashboard and System Map filter buttons are keyed by these; every route used to be reported as train_normal.
        assertEquals("train_normal", RouteTypeKeys.getWebTypeKey("TRAIN", RouteType.NORMAL, ""));
        assertEquals("train_regional", RouteTypeKeys.getWebTypeKey("TRAIN", RouteType.NORMAL, RouteTypeKeys.TRAIN_REGIONAL));
        assertEquals("train_high_speed", RouteTypeKeys.getWebTypeKey("TRAIN", RouteType.HIGH_SPEED, ""));
        assertEquals("boat_light_rail", RouteTypeKeys.getWebTypeKey("BOAT", RouteType.LIGHT_RAIL, RouteTypeKeys.TRAIN_METRO));
        assertEquals("cable_car_normal", RouteTypeKeys.getWebTypeKey("CABLE_CAR", null, ""));
        for (final String key : RouteTypeKeys.TRAIN_ROUTE_TYPE_ORDER) {
            assertEquals(key, RouteTypeKeys.getWebTypeKey("TRAIN", RouteTypeKeys.getBaseRouteType(key), RouteTypeKeys.isBaseTrainKey(key) ? "" : key));
        }
    }

    @Test
    void routeIdHexMatchesStringFormat() {
        final long[] ids = {0, 1, 0xABCDEFL, Long.MAX_VALUE, Long.MIN_VALUE, -1, 0x0123456789ABCDEFL};
        for (final long id : ids) {
            assertEquals(String.format("%016X", id), RouteTypeKeys.toRouteIdHex(id));
        }
    }
}
