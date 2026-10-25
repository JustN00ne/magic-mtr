package org.justnoone.jme.network;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;
import org.mtr.core.data.Depot;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side access to MTR's simulators (one per dimension). MTR keeps them private in {@code Init.main}.
 */
public final class MagicSimulatorAccess {

    private static volatile Field initMainField;
    private static volatile Field simulatorsField;
    private static volatile boolean loggedFailure;

    private MagicSimulatorAccess() {
    }

    public static List<Simulator> getSimulators() {
        final List<Simulator> result = new ArrayList<>();
        try {
            Field localInitMainField = initMainField;
            if (localInitMainField == null) {
                localInitMainField = Init.class.getDeclaredField("main");
                localInitMainField.setAccessible(true);
                initMainField = localInitMainField;
            }
            final Object main = localInitMainField.get(null);
            if (main == null) {
                return result;
            }

            Field localSimulatorsField = simulatorsField;
            if (localSimulatorsField == null) {
                localSimulatorsField = Main.class.getDeclaredField("simulators");
                localSimulatorsField.setAccessible(true);
                simulatorsField = localSimulatorsField;
            }
            final Object simulators = localSimulatorsField.get(main);
            if (simulators instanceof Iterable<?>) {
                for (final Object simulator : (Iterable<?>) simulators) {
                    if (simulator instanceof Simulator) {
                        result.add((Simulator) simulator);
                    }
                }
            }
        } catch (Exception e) {
            if (!loggedFailure) {
                loggedFailure = true;
                Main.LOGGER.warn("[{}] Failed to access MTR simulators", Jme.MOD_ID, e);
            }
        }
        return result;
    }

    /**
     * Regenerates a depot's paths and departures on its simulator thread, like MTR's "Refresh" button.
     *
     * @param useRealTime if not {@code null}, the depot's real-time flag is set first
     */
    public static void regenerateDepot(long depotId, @Nullable Boolean useRealTime) {
        for (final Simulator simulator : getSimulators()) {
            simulator.run(() -> {
                final Depot depot = simulator.depotIdMap.get(depotId);
                if (depot == null) {
                    return;
                }
                if (useRealTime != null) {
                    depot.setUseRealTime(useRealTime);
                }
                final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
                depots.add(depot);
                Depot.generateDepots(simulator, depots);
            });
        }
    }
}
