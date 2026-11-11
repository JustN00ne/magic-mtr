package org.justnoone.jme.util;

/**
 * Live schedule deviation of a vehicle, computed the same way as MTR's {@code Vehicle#updateDeviation}.
 * <p>
 * MTR only refreshes a vehicle's stored deviation when it starts or stops, so a train held at a red signal keeps
 * reporting the delay it had when it stopped. Delay-based cancellation needs the current value.
 */
public final class TrainDelay {

    private TrainDelay() {
    }

    /**
     * @param currentMillis           simulation time now
     * @param sidingDepartureTime     when this vehicle's run departed the siding
     * @param scheduledTimeAlongRoute scheduled time (ms after departure) to reach the vehicle's current position
     * @param repeatInterval          schedule repeat interval; non-positive disables wrapping
     * @return positive when late, negative when early
     */
    public static long liveDelayMillis(long currentMillis, long sidingDepartureTime, long scheduledTimeAlongRoute, long repeatInterval) {
        return circularDifference(currentMillis - sidingDepartureTime, scheduledTimeAlongRoute, repeatInterval);
    }

    /**
     * Port of MTR's {@code Utilities#circularDifference}: {@code value1 - value2}, wrapped into
     * {@code [-total/2, total/2)}.
     */
    public static long circularDifference(long value1, long value2, long total) {
        if (total <= 0) {
            return value1 - value2;
        }

        long tempValue1 = value1;
        final long halfTotal = total / 2;
        if (value1 - halfTotal > value2 || value1 + halfTotal <= value2) {
            tempValue1 = value1 - (value1 - halfTotal - value2) / total * total;
        }
        while (tempValue1 - halfTotal > value2) {
            tempValue1 -= total;
        }
        while (tempValue1 + halfTotal <= value2) {
            tempValue1 += total;
        }
        return tempValue1 - value2;
    }
}
