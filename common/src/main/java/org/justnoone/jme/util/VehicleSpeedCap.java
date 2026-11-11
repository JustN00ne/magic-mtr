package org.justnoone.jme.util;

/**
 * Applies a vehicle's maximum speed to a rail speed. Shared by the simulation ({@code VehicleSpeedLimitMixin}) and the
 * timetable ({@code SidingSpeedLimitMixin}) so both always use the same operating assumption.
 * <p>
 * All values are in MTR's native unit, meters per millisecond.
 */
public final class VehicleSpeedCap {

    private VehicleSpeedCap() {
    }

    /**
     * @param speed    a rail speed limit, or a negative value meaning "no upcoming slower speed" (passed through unchanged)
     * @param maxSpeed the vehicle's maximum speed; missing, zero, negative or non-finite values mean "no cap"
     * @return {@code speed}, limited to {@code maxSpeed}
     */
    public static double cap(double speed, double maxSpeed) {
        if (!isValidMaxSpeed(maxSpeed) || !(speed > maxSpeed)) {
            return speed;
        }
        return maxSpeed;
    }

    public static boolean isValidMaxSpeed(double maxSpeed) {
        return Double.isFinite(maxSpeed) && maxSpeed > 0;
    }

    /**
     * Whether a speed or acceleration setting changed enough to affect the timetable. Values that both mean "no cap"
     * (zero, negative or non-finite) count as equal.
     */
    public static boolean hasChanged(double before, double after) {
        final boolean validBefore = Double.isFinite(before) && before > 0;
        final boolean validAfter = Double.isFinite(after) && after > 0;
        if (validBefore != validAfter) {
            return true;
        }
        return validBefore && Math.abs(before - after) > Math.max(before, after) * 1e-9;
    }
}
