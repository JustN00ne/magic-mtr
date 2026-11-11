package org.justnoone.jme.api;

/**
 * Immutable snapshot of a train that has been cancelled.
 */
public final class CanceledTrainInfo {

    private final long vehicleId;
    private final long routeId;
    private final long platformId;
    private final String destination;
    private final String struckDestination;
    private final long canceledAtMillis;
    private final long expiresAtMillis;

    public CanceledTrainInfo(long vehicleId, long routeId, long platformId, String destination, String struckDestination, long canceledAtMillis, long expiresAtMillis) {
        this.vehicleId = vehicleId;
        this.routeId = routeId;
        this.platformId = platformId;
        this.destination = destination;
        this.struckDestination = struckDestination;
        this.canceledAtMillis = canceledAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public long getVehicleId() {
        return vehicleId;
    }

    public long getRouteId() {
        return routeId;
    }

    public long getPlatformId() {
        return platformId;
    }

    public String getDestination() {
        return destination;
    }

    /**
     * PIDS display string: struck-through destination and "Service Cancelled" separated by {@code |}.
     */
    public String getStruckDestination() {
        return struckDestination;
    }

    public long getCanceledAtMillis() {
        return canceledAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }
}
