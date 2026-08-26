package org.justnoone.jme.rail;

import org.justnoone.jme.api.CanceledTrainInfo;
import org.justnoone.jme.api.CanceledTrainListener;
import org.mtr.core.data.Vehicle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CanceledTrainRegistry {

    public static final long DISPLAY_MILLIS = 5 * 60_000L;
    // Cancelled trains stay visible on the System Map for this long after cancellation, then despawn.
    public static final long MAP_DISPLAY_MILLIS = 30_000L;
    // "Service Cancelled" alternates with the struck-through destination on the PIDS (the `|` cycle).
    public static final String CANCELLED_TEXT = "Service Cancelled";

    // §m (strikethrough) + §r (reset) legacy formatting. The PIDS draws destinations through
    // TextRenderer.draw(String, ...) which parses § codes via TextVisitFactory, so the whole
    // destination is struck through while keeping the board's normal text color/urgency.
    private static final String STRIKE_PREFIX = "\u00a7m";
    private static final String STRIKE_SUFFIX = "\u00a7r";

    private static final Map<Long, CanceledTrain> CANCELED_BY_VEHICLE = new ConcurrentHashMap<>();
    private static final List<CanceledTrainListener> LISTENERS = new CopyOnWriteArrayList<>();

    private CanceledTrainRegistry() {
    }

    public static void markCanceled(Vehicle vehicle) {
        if (vehicle == null || vehicle.vehicleExtraData == null) {
            return;
        }

        final long vehicleId = vehicle.getId();
        final long routeId = vehicle.vehicleExtraData.getThisRouteId();
        final long platformId = vehicle.vehicleExtraData.getThisPlatformId();
        if (vehicleId == 0 || routeId == 0 || platformId == 0) {
            return;
        }

        final String destination = sanitizeDestination(readString(vehicle.vehicleExtraData, "getThisRouteDestination", "thisRouteDestination"));
        final long now = System.currentTimeMillis();
        final CanceledTrain canceledTrain = new CanceledTrain(
                vehicleId,
                routeId,
                platformId,
                destination,
                strike(destination) + "|" + CANCELLED_TEXT,
                now,
                now + DISPLAY_MILLIS
        );
        CANCELED_BY_VEHICLE.put(vehicleId, canceledTrain);
        fireCancelled(canceledTrain.toInfo());
    }

    public static List<CanceledTrain> activeForPlatformIds(Iterable<Long> platformIds) {
        cleanupExpired();
        final ArrayList<CanceledTrain> matches = new ArrayList<>();
        if (platformIds == null) {
            return matches;
        }

        for (final Long platformId : platformIds) {
            if (platformId == null || platformId == 0) {
                continue;
            }
            for (final CanceledTrain train : CANCELED_BY_VEHICLE.values()) {
                if (train.platformId == platformId) {
                    matches.add(train);
                }
            }
        }
        return matches;
    }

    public static List<CanceledTrain> active() {
        return activeWithin(DISPLAY_MILLIS);
    }

    public static List<CanceledTrain> activeForMap() {
        return activeWithin(MAP_DISPLAY_MILLIS);
    }

    private static List<CanceledTrain> activeWithin(long windowMillis) {
        cleanupExpired();
        final long cutoff = System.currentTimeMillis() - windowMillis;
        final ArrayList<CanceledTrain> result = new ArrayList<>(CANCELED_BY_VEHICLE.size());
        for (final CanceledTrain train : CANCELED_BY_VEHICLE.values()) {
            if (train.canceledAtMillis >= cutoff) {
                result.add(train);
            }
        }
        return result;
    }

    public static List<CanceledTrainInfo> getActiveCancellations() {
        final List<CanceledTrain> activeTrains = active();
        final ArrayList<CanceledTrainInfo> result = new ArrayList<>(activeTrains.size());
        for (final CanceledTrain train : activeTrains) {
            result.add(train.toInfo());
        }
        return result;
    }

    public static CanceledTrainInfo getCancellation(long vehicleId) {
        cleanupExpired();
        final CanceledTrain train = CANCELED_BY_VEHICLE.get(vehicleId);
        return train == null ? null : train.toInfo();
    }

    public static boolean isCancelled(long vehicleId) {
        cleanupExpired();
        return CANCELED_BY_VEHICLE.containsKey(vehicleId);
    }

    public static void addListener(CanceledTrainListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(CanceledTrainListener listener) {
        LISTENERS.remove(listener);
    }

    public static void cleanupExpired() {
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<Long, CanceledTrain>> iterator = CANCELED_BY_VEHICLE.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Long, CanceledTrain> entry = iterator.next();
            if (entry.getValue().expiresAtMillis <= now) {
                iterator.remove();
            }
        }
    }

    private static void fireCancelled(CanceledTrainInfo canceledTrain) {
        for (final CanceledTrainListener listener : LISTENERS) {
            try {
                listener.onTrainCancelled(canceledTrain);
            } catch (Exception ignored) {
            }
        }
    }

    private static String sanitizeDestination(String destination) {
        final String trimmed = destination == null ? "" : destination.trim();
        return trimmed.isEmpty() ? "Cancelled" : trimmed;
    }

    private static String strike(String value) {
        final String text = sanitizeDestination(value);
        return STRIKE_PREFIX + text + STRIKE_SUFFIX;
    }

    private static String readString(Object target, String methodName, String fieldName) {
        try {
            final Method method = target.getClass().getMethod(methodName);
            final Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
        }

        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            final Object value = field.get(target);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static final class CanceledTrain {
        public final long vehicleId;
        public final long routeId;
        public final long platformId;
        public final String destination;
        public final String struckDestination;
        public final long canceledAtMillis;
        public final long expiresAtMillis;

        private CanceledTrain(long vehicleId, long routeId, long platformId, String destination, String struckDestination, long canceledAtMillis, long expiresAtMillis) {
            this.vehicleId = vehicleId;
            this.routeId = routeId;
            this.platformId = platformId;
            this.destination = destination;
            this.struckDestination = struckDestination;
            this.canceledAtMillis = canceledAtMillis;
            this.expiresAtMillis = expiresAtMillis;
        }

        public CanceledTrainInfo toInfo() {
            return new CanceledTrainInfo(vehicleId, routeId, platformId, destination, struckDestination, canceledAtMillis, expiresAtMillis);
        }
    }
}
