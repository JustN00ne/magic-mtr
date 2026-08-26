package org.justnoone.jme.block;

import org.mtr.core.Main;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class WmtaStateManager {

    private static final FieldCache FIELDS = new FieldCache();
    private static final ConcurrentHashMap<String, WmtaStateManager> INSTANCES = new ConcurrentHashMap<>();
    private static final int DETECTION_INTERVAL = 20;
    private static final int BLINK_INTERVAL = 10;
    private static final double ARRIVAL_BLINK_WINDOW_SECONDS = 30D;
    private static final double STOPPING_POINT_EPSILON = 1.0E-3D;

    private boolean trainDetected;
    private boolean blinkOn;
    private int tickCounter;
    private int blinkCounter;
    private volatile long lastTickMillis;

    public static WmtaStateManager getOrCreate(String dimensionId) {
        return INSTANCES.computeIfAbsent(dimensionId, k -> new WmtaStateManager());
    }

    public synchronized void tick(Simulator simulator) {
        final long now = System.currentTimeMillis();
        if (now - lastTickMillis < 40) return;
        lastTickMillis = now;

        tickCounter++;

        // Re-check arrivals on an interval, but always drop blink the instant doors open.
        if (tickCounter % DETECTION_INTERVAL == 0 || trainDetected) {
            trainDetected = hasTrainArrivingSoon(simulator);
        }

        if (trainDetected) {
            blinkCounter++;
            if (blinkCounter >= BLINK_INTERVAL) {
                blinkCounter = 0;
                blinkOn = !blinkOn;
            }
        } else {
            blinkCounter = 0;
            blinkOn = false;
        }
    }

    public boolean isBlinking() {
        return blinkOn;
    }

    private static boolean hasTrainArrivingSoon(Simulator simulator) {
        if (simulator == null) return false;

        for (final Siding siding : simulator.sidings) {
            if (siding == null) continue;
            final Collection<?> vehicles = FIELDS.getSidingVehicles(siding);
            if (vehicles == null || vehicles.isEmpty()) continue;

            for (final Object obj : vehicles) {
                if (!(obj instanceof Vehicle)) continue;
                final Vehicle vehicle = (Vehicle) obj;
                if (!vehicle.getIsOnRoute() || vehicle.vehicleExtraData == null || vehicle.vehicleExtraData.immutablePath == null) continue;
                // Door open command is the hard stop: turn indicators off immediately.
                // getDoorMultiplier() > 0 mirrors doorTarget without needing a fragile field accessor.
                if (vehicle.vehicleExtraData.getDoorMultiplier() > 0) continue;

                final double railProgress = FIELDS.getVehicleRailProgress(vehicle);
                final double stoppingPoint = vehicle.vehicleExtraData.getStoppingPoint();
                if (!Double.isFinite(railProgress) || !Double.isFinite(stoppingPoint)) continue;
                if (!isDwellStop(vehicle, stoppingPoint)) continue;

                final double distanceToStop = stoppingPoint - railProgress;
                if (distanceToStop < -STOPPING_POINT_EPSILON) continue;

                // Keep blinking while stopped at the platform until doors actually open.
                if (Math.abs(distanceToStop) <= STOPPING_POINT_EPSILON) {
                    return true;
                }

                final double speed = FIELDS.getVehicleSpeed(vehicle);
                if (!(speed > 0D) || Double.isNaN(speed)) continue;

                final double secondsToDoors = distanceToStop / (speed * 1000D);
                if (secondsToDoors >= 0D && secondsToDoors <= ARRIVAL_BLINK_WINDOW_SECONDS) return true;
            }
        }
        return false;
    }

    private static boolean isDwellStop(Vehicle vehicle, double stoppingPoint) {
        for (final PathData pathData : vehicle.vehicleExtraData.immutablePath) {
            if (pathData != null && pathData.getDwellTime() > 0 && Math.abs(pathData.getEndDistance() - stoppingPoint) <= STOPPING_POINT_EPSILON) {
                return true;
            }
        }
        return false;
    }

    public static Simulator getSimulator(World world) {
        return FIELDS.getSimulator(world);
    }

    private static class FieldCache {
        private Field initMainField;
        private Field mainSimulatorsField;
        private Field sidingVehiclesField;
        private Field vehicleSpeedField;
        private Field vehicleRailProgressField;
        private boolean loggedSimulatorFailure;
        private boolean loggedVehiclesFailure;
        private boolean loggedSpeedFailure;

        Simulator getSimulator(World world) {
            final String worldId = Init.getWorldId(world);
            if (worldId == null || worldId.isEmpty()) return null;

            try {
                Field f = initMainField;
                if (f == null) {
                    f = Init.class.getDeclaredField("main");
                    f.setAccessible(true);
                    initMainField = f;
                }

                final Object main = f.get(null);
                if (main == null) return null;

                Field sf = mainSimulatorsField;
                if (sf == null) {
                    sf = main.getClass().getDeclaredField("simulators");
                    sf.setAccessible(true);
                    mainSimulatorsField = sf;
                }

                final Object simulators = sf.get(main);
                if (!(simulators instanceof Iterable<?>)) return null;

                Simulator fallback = null;
                int count = 0;
                for (final Object sim : (Iterable<?>) simulators) {
                    if (!(sim instanceof Simulator)) continue;
                    final Simulator s = (Simulator) sim;
                    count++;
                    if (fallback == null) fallback = s;
                    if (worldId.equals(s.dimension)) return s;
                }
                return count == 1 ? fallback : null;
            } catch (Exception e) {
                if (!loggedSimulatorFailure) {
                    loggedSimulatorFailure = true;
                    Main.LOGGER.warn("[MAGIC] Failed resolving simulator for WMTA", e);
                }
                return null;
            }
        }

        Collection<?> getSidingVehicles(Siding siding) {
            try {
                Field f = sidingVehiclesField;
                if (f == null) {
                    f = findField(siding.getClass(), "vehicles");
                    f.setAccessible(true);
                    sidingVehiclesField = f;
                }
                final Object v = f.get(siding);
                return v instanceof Collection<?> ? (Collection<?>) v : null;
            } catch (Exception e) {
                if (!loggedVehiclesFailure) {
                    loggedVehiclesFailure = true;
                    Main.LOGGER.warn("[MAGIC] Failed reading siding vehicles for WMTA", e);
                }
                return null;
            }
        }

        double getVehicleSpeed(Vehicle vehicle) {
            try {
                Field f = vehicleSpeedField;
                if (f == null) {
                    f = findField(vehicle.getClass(), "speed");
                    f.setAccessible(true);
                    vehicleSpeedField = f;
                }
                return f.getDouble(vehicle);
            } catch (Exception e) {
                if (!loggedSpeedFailure) {
                    loggedSpeedFailure = true;
                    Main.LOGGER.warn("[MAGIC] Failed reading vehicle speed for WMTA", e);
                }
                return Double.NaN;
            }
        }

        double getVehicleRailProgress(Vehicle vehicle) {
            try {
                Field f = vehicleRailProgressField;
                if (f == null) {
                    f = findField(vehicle.getClass(), "railProgress");
                    f.setAccessible(true);
                    vehicleRailProgressField = f;
                }
                return f.getDouble(vehicle);
            } catch (Exception e) {
                return Double.NaN;
            }
        }

        private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
            Class<?> c = clazz;
            while (c != null) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
