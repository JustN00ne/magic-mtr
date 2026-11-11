package org.justnoone.jme.rail;

import org.justnoone.jme.api.CanceledTrainInfo;
import org.justnoone.jme.api.CanceledTrainListener;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /**
     * Cancelled trips (one scheduled run of a siding), persisted so they stay cancelled across restarts. MTR keeps
     * listing a removed vehicle's remaining stops as normal scheduled arrivals; these entries suppress them.
     */
    private static final Map<String, CanceledTrip> CANCELED_TRIPS = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path TRIPS_PATH = MagicConfigPaths.resolveConfigFile("canceled_trips.json");
    private static volatile boolean tripsLoaded;

    private CanceledTrainRegistry() {
    }

    /**
     * Records a cancelled trip.
     *
     * @param routeIds            all routes the trip serves (a depot can chain several routes in one run)
     * @param departureIndex      the siding departure index of the trip ({@code ArrivalResponse#getDepartureIndex})
     * @param sidingDepartureTime when the trip left the siding (simulation millis)
     * @param tripWindowMillis    how long after departure the trip's scheduled arrivals can occur
     */
    public static void markTripCanceled(Iterable<Long> routeIds, long departureIndex, long sidingDepartureTime, long tripWindowMillis) {
        if (routeIds == null || departureIndex < 0 || sidingDepartureTime < 0) {
            return;
        }
        ensureTripsLoaded();
        final long windowEnd = sidingDepartureTime + Math.max(60_000L, tripWindowMillis);
        boolean changed = false;
        for (final Long routeId : routeIds) {
            if (routeId != null && routeId != 0) {
                CANCELED_TRIPS.put(tripKey(routeId, departureIndex, sidingDepartureTime), new CanceledTrip(routeId, departureIndex, sidingDepartureTime, windowEnd, System.currentTimeMillis()));
                changed = true;
            }
        }
        if (changed) {
            saveTrips();
        }
    }

    /**
     * @return whether a scheduled (non-realtime) arrival belongs to a cancelled trip
     */
    public static boolean isScheduledArrivalCanceled(long routeId, long departureIndex, long arrivalMillis) {
        ensureTripsLoaded();
        if (CANCELED_TRIPS.isEmpty() || departureIndex < 0) {
            return false;
        }
        for (final CanceledTrip trip : CANCELED_TRIPS.values()) {
            if (trip.routeId == routeId && trip.departureIndex == departureIndex && arrivalMillis >= trip.sidingDepartureTime && arrivalMillis <= trip.windowEndMillis) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops trips whose schedule window has passed (in simulation time). Called from the simulation thread.
     */
    public static void pruneTrips(long currentSimulationMillis) {
        ensureTripsLoaded();
        if (CANCELED_TRIPS.values().removeIf(trip -> trip.windowEndMillis < currentSimulationMillis)) {
            saveTrips();
        }
    }

    private static String tripKey(long routeId, long departureIndex, long sidingDepartureTime) {
        return routeId + ":" + departureIndex + ":" + sidingDepartureTime;
    }

    private static void ensureTripsLoaded() {
        if (!tripsLoaded) {
            loadTrips();
        }
    }

    private static synchronized void loadTrips() {
        if (tripsLoaded) {
            return;
        }
        tripsLoaded = true;
        try {
            if (!Files.exists(TRIPS_PATH)) {
                return;
            }
            final JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(TRIPS_PATH), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                return;
            }
            for (final JsonElement element : root.getAsJsonArray()) {
                try {
                    final JsonObject object = element.getAsJsonObject();
                    final CanceledTrip trip = new CanceledTrip(
                            object.get("route_id").getAsLong(),
                            object.get("departure_index").getAsLong(),
                            object.get("siding_departure_time").getAsLong(),
                            object.get("window_end").getAsLong(),
                            object.has("canceled_at") ? object.get("canceled_at").getAsLong() : 0
                    );
                    CANCELED_TRIPS.put(tripKey(trip.routeId, trip.departureIndex, trip.sidingDepartureTime), trip);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void saveTrips() {
        final JsonArray array = new JsonArray();
        for (final CanceledTrip trip : CANCELED_TRIPS.values()) {
            final JsonObject object = new JsonObject();
            object.addProperty("route_id", trip.routeId);
            object.addProperty("departure_index", trip.departureIndex);
            object.addProperty("siding_departure_time", trip.sidingDepartureTime);
            object.addProperty("window_end", trip.windowEndMillis);
            object.addProperty("canceled_at", trip.canceledAtMillis);
            array.add(object);
        }
        try {
            Files.createDirectories(TRIPS_PATH.getParent());
            Files.write(TRIPS_PATH, GSON.toJson(array).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
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

    private static final class CanceledTrip {
        private final long routeId;
        private final long departureIndex;
        private final long sidingDepartureTime;
        private final long windowEndMillis;
        private final long canceledAtMillis;

        private CanceledTrip(long routeId, long departureIndex, long sidingDepartureTime, long windowEndMillis, long canceledAtMillis) {
            this.routeId = routeId;
            this.departureIndex = departureIndex;
            this.sidingDepartureTime = sidingDepartureTime;
            this.windowEndMillis = windowEndMillis;
            this.canceledAtMillis = canceledAtMillis;
        }
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
