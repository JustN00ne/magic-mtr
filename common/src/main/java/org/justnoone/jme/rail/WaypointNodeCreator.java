package org.justnoone.jme.rail;

import org.mtr.core.data.Data;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.Init;
import org.mtr.mapping.holder.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class WaypointNodeCreator {

    private static final Logger LOGGER = LogManager.getLogger("MAGIC");

    private WaypointNodeCreator() {
    }

    public static Platform createWaypointPlatform(Data data, String name, Position position) {
        if (data == null || name == null || name.isEmpty() || position == null) {
            return null;
        }

        try {
            final Station station = createStation(TransportMode.TRAIN, data);
            if (station == null) {
                LOGGER.error("[MAGIC] Could not create Station object");
                return null;
            }
            station.setName(name);
            station.setColor(0x5555FF);
            setPositionField(station, "position1", position);
            setPositionField(station, "position2", position);

            final Position pos2 = new Position(position.getX(), position.getY(), position.getZ() + 1);
            final Platform platform = createPlatform(position, pos2, TransportMode.TRAIN, data);
            if (platform == null) {
                LOGGER.error("[MAGIC] Could not create Platform object");
                return null;
            }
            platform.setName(name);
            platform.setColor(0x5555FF);
            setField(platform, "dwellTime", 0L);

            data.stations.add(station);
            data.platforms.add(platform);
            data.stationIdMap.put(station.getId(), station);
            data.platformIdMap.put(platform.getId(), platform);
            data.platformIdToPosition.put(platform.getId(), position);

            LOGGER.info("[MAGIC] Created waypoint station '{}' with platform id={} at ({}, {}, {})",
                    name, platform.getId(), position.getX(), position.getY(), position.getZ());

            return platform;
        } catch (Throwable t) {
            LOGGER.error("[MAGIC] Failed to create waypoint platform '{}': {}", name, t.getMessage(), t);
            return null;
        }
    }

    public static boolean removeWaypointPlatform(Data data, long platformId) {
        if (data == null || platformId == 0) {
            return false;
        }

        try {
            final Platform platform = data.platformIdMap.get(platformId);
            if (platform == null) {
                return false;
            }

            final Station station = platform.area;
            if (station != null) {
                data.stationIdMap.remove(station.getId());
                data.stations.remove(station);
            }

            data.platformIdMap.remove(platformId);
            data.platforms.remove(platform);
            data.platformIdToPosition.remove(platformId);

            LOGGER.info("[MAGIC] Removed waypoint platform id={}", platformId);
            return true;
        } catch (Throwable t) {
            LOGGER.error("[MAGIC] Failed to remove waypoint platform id={}: {}", platformId, t.getMessage(), t);
            return false;
        }
    }

    public static Data resolveSimulator(World world) {
        try {
            final String worldId = Init.getWorldId(world);
            if (worldId == null || worldId.isEmpty()) {
                return null;
            }

            final Field initMainField = Init.class.getDeclaredField("main");
            initMainField.setAccessible(true);
            final Object main = initMainField.get(null);
            if (main == null) {
                return null;
            }

            final Field simulatorsField = main.getClass().getDeclaredField("simulators");
            simulatorsField.setAccessible(true);
            final Object simulators = simulatorsField.get(main);
            if (!(simulators instanceof Iterable)) {
                return null;
            }

            Data fallback = null;
            int count = 0;
            for (final Object simObj : (Iterable<?>) simulators) {
                if (!(simObj instanceof Data)) {
                    continue;
                }
                final Data sim = (Data) simObj;
                count++;
                if (fallback == null) {
                    fallback = sim;
                }
                final String simDimension = getFieldString(sim, "dimension");
                if (worldId.equals(simDimension)) {
                    return sim;
                }
            }

            return count == 1 ? fallback : null;
        } catch (Throwable t) {
            LOGGER.warn("[MAGIC] Failed to resolve simulator: {}", t.getMessage());
            return null;
        }
    }

    public static Position getPlatformPosition(Platform platform) {
        try {
            final Field f1 = findField(platform.getClass(), "position1");
            if (f1 != null) {
                f1.setAccessible(true);
                final Object val = f1.get(platform);
                if (val instanceof Position) {
                    return (Position) val;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Station createStation(TransportMode transportMode, Data data) {
        try {
            final Constructor<?>[] constructors = Station.class.getDeclaredConstructors();
            for (final Constructor<?> ctor : constructors) {
                final Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && TransportMode.class.isAssignableFrom(params[0]) && Data.class.isAssignableFrom(params[1])) {
                    ctor.setAccessible(true);
                    return (Station) ctor.newInstance(transportMode, data);
                }
            }
            for (final Constructor<?> ctor : constructors) {
                final Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 1 && Data.class.isAssignableFrom(params[0])) {
                    ctor.setAccessible(true);
                    return (Station) ctor.newInstance(data);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[MAGIC] Failed to create Station via reflection: {}", t.getMessage());
        }
        return null;
    }

    private static Platform createPlatform(Position pos1, Position pos2, TransportMode transportMode, Data data) {
        try {
            final Constructor<?>[] constructors = Platform.class.getDeclaredConstructors();
            for (final Constructor<?> ctor : constructors) {
                final Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 4
                        && Position.class.isAssignableFrom(params[0])
                        && Position.class.isAssignableFrom(params[1])
                        && TransportMode.class.isAssignableFrom(params[2])
                        && Data.class.isAssignableFrom(params[3])) {
                    ctor.setAccessible(true);
                    return (Platform) ctor.newInstance(pos1, pos2, transportMode, data);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[MAGIC] Failed to create Platform via reflection: {}", t.getMessage());
        }
        return null;
    }

    private static void setPositionField(Object obj, String fieldName, Position value) {
        try {
            final Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setField(Object obj, String fieldName, long value) {
        try {
            final Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.setLong(obj, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String getFieldString(Object obj, String fieldName) {
        try {
            final Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                final Object val = field.get(obj);
                return val instanceof String ? (String) val : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
