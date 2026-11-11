package org.justnoone.jme.api;

import org.justnoone.jme.rail.CanceledTrainRegistry;
import org.mtr.core.data.Vehicle;

import java.util.List;

/**
 * Public API for other mods to query and trigger train cancellations.
 */
public final class JmeRailApi {

    private JmeRailApi() {
    }

    /**
     * All trains that are currently cancelled (PIDS-visible window).
     */
    public static List<CanceledTrainInfo> getActiveCancellations() {
        return CanceledTrainRegistry.getActiveCancellations();
    }

    public static CanceledTrainInfo getCancellation(long vehicleId) {
        return CanceledTrainRegistry.getCancellation(vehicleId);
    }

    public static boolean isCancelled(long vehicleId) {
        return CanceledTrainRegistry.isCancelled(vehicleId);
    }

    /**
     * Cancels a train, removing it from active service while keeping it on the PIDS and System Map.
     *
     * @return {@code true} if the vehicle was registered as cancelled.
     */
    public static boolean cancelVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            return false;
        }
        CanceledTrainRegistry.markCanceled(vehicle);
        return CanceledTrainRegistry.isCancelled(vehicle.getId());
    }

    public static void addCanceledTrainListener(CanceledTrainListener listener) {
        CanceledTrainRegistry.addListener(listener);
    }

    public static void removeCanceledTrainListener(CanceledTrainListener listener) {
        CanceledTrainRegistry.removeListener(listener);
    }
}
