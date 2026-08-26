package org.justnoone.jme.mixin;

import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.rail.CanceledTrainRegistry;
import org.justnoone.jme.rail.WaypointRegistry;
import org.justnoone.jme.systemmap.SystemMapOverlayCacheStore;
import org.mtr.core.Main;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.servlet.SystemMapServlet;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Mixin(value = SystemMapServlet.class, remap = false)
public abstract class SystemMapServletMixin {

    @Unique
    private static final long JME_RAILS_REFRESH_MILLIS = 10000;
    @Unique
    private static final long JME_VEHICLES_REFRESH_MILLIS = 500;
    @Unique
    private static final long JME_ROUTE_PATH_REFRESH_MILLIS = 60000;
    @Unique
    private static final Map<String, JsonObject> jme$railsSnapshotByDimension = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, JsonObject> jme$vehiclesSnapshotByDimension = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, JsonObject> jme$routePathCacheByKey = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, Long> jme$routePathBuildMillisByKey = new ConcurrentHashMap<>();
    @Unique
    private static final Set<String> jme$inFlightRailsDimensions = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Set<String> jme$inFlightVehiclesDimensions = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Set<String> jme$inFlightRoutePathKeys = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Map<String, Long> jme$lastRailsBuildMillis = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, Long> jme$lastVehiclesBuildMillis = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, JsonArray> jme$curvePointsCacheByRailId = new ConcurrentHashMap<>();
    @Unique
    private static volatile Field jme$vehiclesField;
    @Unique
    private static volatile Field jme$vehicleExtraDataField;
    @Unique
    private static volatile Field jme$vehicleSpeedField;
    @Unique
    private static volatile Field jme$vehicleRailProgressField;
    @Unique
    private static volatile Field jme$immutablePathField;
    @Unique
    private static volatile Method jme$getHexIdMethod;
    @Unique
    private static volatile Method jme$getThisRouteIdMethod;
    @Unique
    private static volatile Method jme$getVehicleCarsAndPositionsMethod;
    @Unique
    private static volatile Method jme$getHeadPositionAndTiltAngleMethod;
    @Unique
    private static volatile boolean jme$disableTrainExtraction;
    @Unique
    private static volatile String jme$lastWorldKey = "";

    /**
     * Integrated server sessions can switch worlds without restarting the JVM; clear static caches to avoid leaking
     * rails/vehicles between saves.
     */
    @Unique
    private static void jme$clearSystemMapCaches() {
        jme$railsSnapshotByDimension.clear();
        jme$vehiclesSnapshotByDimension.clear();
        jme$routePathCacheByKey.clear();
        jme$routePathBuildMillisByKey.clear();
        jme$inFlightRailsDimensions.clear();
        jme$inFlightVehiclesDimensions.clear();
        jme$inFlightRoutePathKeys.clear();
        jme$lastRailsBuildMillis.clear();
        jme$lastVehiclesBuildMillis.clear();
        jme$curvePointsCacheByRailId.clear();
        jme$disableTrainExtraction = false;
    }

    @Unique
    private static void jme$ensureWorldKeyUpToDate() {
        final String worldKey = SystemMapOverlayCacheStore.getWorldKey();
        final String normalized = worldKey == null ? "" : worldKey.trim();
        if (Objects.equals(jme$lastWorldKey, normalized)) {
            return;
        }
        jme$lastWorldKey = normalized;
        jme$clearSystemMapCaches();
    }

    @Inject(method = "getContent", at = @At("HEAD"), cancellable = true)
    private void jme$onGetContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<JsonObject> sendResponse, CallbackInfo ci) {
        jme$ensureWorldKeyUpToDate();

        if (endpoint.equals("jme-config")) {
            sendResponse.accept(jme$handleConfigRequest(jsonReader));
            ci.cancel();
            return;
        }

        if (endpoint.equals("clients") && JmeConfig.systemMapHidePlayer()) {
            final JsonObject response = new JsonObject();
            response.addProperty("cachedResponseTime", System.currentTimeMillis());
            response.add("clients", new JsonArray());
            sendResponse.accept(response);
            ci.cancel();
            return;
        }

    }

    private static JsonObject jme$handleMagicRailsApiRequest(Simulator simulator, String modeRaw, String routeIdRaw) {
        jme$ensureWorldKeyUpToDate();

        final String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ENGLISH);
        if (simulator == null) {
            if ("vehicles".equals(mode)) {
                return jme$createEmptyVehiclesSnapshot();
            }
            if ("routepath".equals(mode)) {
                return jme$createEmptyRoutePathResponse(routeIdRaw);
            }
            return jme$createEmptyRailsSnapshot();
        }

        if ("routepath".equals(mode)) {
            return jme$handleRoutePathRequest(simulator, routeIdRaw);
        }

        final String dimension = simulator.dimension;
        final boolean railOverlayEnabled = JmeConfig.dashboardRailOverlayMode() != JmeConfig.DashboardRailOverlayMode.OFF;
        final boolean overlayWantsRails = JmeConfig.systemMapOverlayShowBaseRails() || JmeConfig.systemMapOverlayShowDetails();
        final boolean overlayWantsVehicles = JmeConfig.systemMapOverlayShowVehicles();
        final boolean includeRails = railOverlayEnabled && overlayWantsRails && !"vehicles".equals(mode);
        final boolean includeVehicles = overlayWantsVehicles && !"rails".equals(mode);

        if (includeRails) {
            jme$submitRailsBuildIfStale(dimension, simulator);
        }
        if (includeVehicles) {
            jme$submitVehiclesBuildIfStale(dimension, simulator);
        }

        if ("rails".equals(mode)) {
            if (railOverlayEnabled && overlayWantsRails) {
                final JsonObject railsSnapshot = jme$railsSnapshotByDimension.get(dimension);
                return railsSnapshot == null ? jme$createEmptyRailsSnapshot() : railsSnapshot;
            }
            return jme$createEmptyRailsSnapshot();
        } else if ("vehicles".equals(mode)) {
            if (overlayWantsVehicles) {
                final JsonObject vehiclesSnapshot = jme$vehiclesSnapshotByDimension.get(dimension);
                return vehiclesSnapshot == null ? jme$createEmptyVehiclesSnapshot() : vehiclesSnapshot;
            }
            return jme$createEmptyVehiclesSnapshot();
        } else {
            return jme$createFullRailsResponse(dimension);
        }
    }

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "getContent",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V")
    )
    private void jme$patchRouteTypesBeforeSend(Consumer<Object> consumer, Object payload) {
        if (payload instanceof JsonObject) {
            try {
                jme$patchRoutesArrayRouteTypes((JsonObject) payload);
            } catch (Exception ignored) {
            }
        }
        consumer.accept(payload);
    }

    @Unique
    private static JsonObject jme$createEmptyRailsSnapshot() {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("jmeRailOverlayMode", JmeConfig.dashboardRailOverlayMode().name().toLowerCase(Locale.ENGLISH));
        response.addProperty("jmeRailCullMaxPerCell", JmeConfig.dashboardRailOverlayCullMaxPerCell());
        response.addProperty("jmeShowBaseRails", JmeConfig.systemMapOverlayShowBaseRails());
        response.addProperty("jmeShowDetails", JmeConfig.systemMapOverlayShowDetails());
        response.addProperty("jmeShowSignals", JmeConfig.systemMapOverlayShowSignals());
        response.addProperty("jmeShowVehicles", JmeConfig.systemMapOverlayShowVehicles());
        response.addProperty("jmeRespectRouteFilters", JmeConfig.systemMapOverlayRespectRouteFilters());

        final String customCss = JmeConfig.getSystemMapCustomCss();
        final String customJs = JmeConfig.getSystemMapCustomJs();
        if (!customCss.isEmpty()) {
            response.addProperty("customCss", customCss);
        }
        if (!customJs.isEmpty()) {
            response.addProperty("customJs", customJs);
        }

        response.add("rails", new JsonArray());
        response.add("vehicles", new JsonArray());

        // Include waypoints even in empty responses so the web dashboard always has access.
        final JsonArray waypointArray = new JsonArray();
        for (final WaypointRegistry.Waypoint wp : WaypointRegistry.getAll()) {
            if (wp == null) {
                continue;
            }
            final JsonObject wpJson = new JsonObject();
            wpJson.addProperty("id", String.valueOf(wp.id));
            wpJson.addProperty("name", wp.name);
            wpJson.addProperty("color", wp.color);
            wpJson.addProperty("x", wp.position.getX());
            wpJson.addProperty("z", wp.position.getZ());
            waypointArray.add(wpJson);
        }
        response.add("waypoints", waypointArray);

        return response;
    }

    @Unique
    private static JsonObject jme$createEmptyVehiclesSnapshot() {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("jmeRailOverlayMode", JmeConfig.dashboardRailOverlayMode().name().toLowerCase(Locale.ENGLISH));
        response.addProperty("jmeRailCullMaxPerCell", JmeConfig.dashboardRailOverlayCullMaxPerCell());
        response.addProperty("jmeShowBaseRails", JmeConfig.systemMapOverlayShowBaseRails());
        response.addProperty("jmeShowDetails", JmeConfig.systemMapOverlayShowDetails());
        response.addProperty("jmeShowSignals", JmeConfig.systemMapOverlayShowSignals());
        response.addProperty("jmeShowVehicles", JmeConfig.systemMapOverlayShowVehicles());
        response.addProperty("jmeRespectRouteFilters", JmeConfig.systemMapOverlayRespectRouteFilters());
        response.add("rails", new JsonArray());
        response.add("vehicles", new JsonArray());
        response.add("waypoints", new JsonArray());
        return response;
    }

    @Unique
    private static JsonObject jme$createEmptyRoutePathResponse(String routeId) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("routeId", routeId == null ? "" : routeId);
        response.add("rails", new JsonArray());
        return response;
    }

    @Unique
    private static JsonObject jme$handleRoutePathRequest(Simulator simulator, String routeIdRaw) {
        final String routeId = jme$normalizeHexId(routeIdRaw);
        if (routeId.isEmpty() || simulator == null) {
            return jme$createEmptyRoutePathResponse(routeId);
        }

        final String cacheKey = simulator.dimension + "|" + routeId;
        final long now = System.currentTimeMillis();
        final JsonObject cached = jme$routePathCacheByKey.get(cacheKey);
        final long lastBuild = jme$routePathBuildMillisByKey.getOrDefault(cacheKey, 0L);
        if (cached != null && now - lastBuild < JME_ROUTE_PATH_REFRESH_MILLIS) {
            return cached;
        }
        if (!jme$inFlightRoutePathKeys.add(cacheKey)) {
            return cached != null ? cached : jme$createEmptyRoutePathResponse(routeId);
        }

        // Only gather references on the simulator thread; the BFS path search runs on a background thread.
        simulator.run(() -> {
            try {
                final Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById = new Object2ObjectLinkedOpenHashMap<>();
                jme$populateRailsById(simulator, railsById);
                final ObjectArrayList<org.mtr.core.data.RoutePlatformData> platformsCopy = new ObjectArrayList<>();
                final org.mtr.core.data.Route[] foundRoute = new org.mtr.core.data.Route[1];
                simulator.routes.forEach(route -> {
                    if (route != null && route.getHexId().equalsIgnoreCase(routeId)) {
                        foundRoute[0] = route;
                    }
                });
                if (foundRoute[0] != null) {
                    foundRoute[0].getRoutePlatforms().forEach(platformData -> {
                        if (platformData != null && platformData.platform != null) {
                            platformsCopy.add(platformData);
                        }
                    });
                }

                new Thread(() -> {
                    try {
                        final JsonObject pathJson = jme$buildRoutePathJson(routeId, railsById, platformsCopy, foundRoute[0]);
                        jme$routePathCacheByKey.put(cacheKey, pathJson);
                        jme$routePathBuildMillisByKey.put(cacheKey, System.currentTimeMillis());
                    } catch (Exception e) {
                        Main.LOGGER.warn("Route path build failed for route {}", routeId, e);
                    } finally {
                        jme$inFlightRoutePathKeys.remove(cacheKey);
                    }
                }, "MAGIC-RoutePath").start();
            } catch (Exception e) {
                jme$inFlightRoutePathKeys.remove(cacheKey);
            }
        });

        return cached != null ? cached : jme$createEmptyRoutePathResponse(routeId);
    }

    @Unique
    private static JsonObject jme$buildRoutePathJson(String routeId, Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById, ObjectArrayList<org.mtr.core.data.RoutePlatformData> platforms, org.mtr.core.data.Route route) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("routeId", routeId);
        if (route != null) {
            response.addProperty("name", route.getName());
            response.addProperty("color", route.getColor());
        }

        final JsonArray pathRails = new JsonArray();
        if (railsById != null && !railsById.isEmpty() && platforms != null && platforms.size() >= 2) {
            final java.util.Map<Position, ObjectArrayList<String>> railsByEndpoint = new java.util.HashMap<>();
            railsById.values().forEach(rail -> {
                try {
                    final Position[] railPositions = jme$parsePositionsFromHexId(rail.getHexId());
                    railsByEndpoint.computeIfAbsent(railPositions[0], key -> new ObjectArrayList<>()).add(rail.getHexId());
                    railsByEndpoint.computeIfAbsent(railPositions[1], key -> new ObjectArrayList<>()).add(rail.getHexId());
                } catch (Exception ignored) {
                }
            });

            final boolean circular = route != null && jme$isCircularRoute(route);
            final int pairCount = platforms.size() - (circular ? 0 : 1);
            final java.util.LinkedHashSet<String> includedRailIds = new java.util.LinkedHashSet<>();

            for (int i = 0; i < pairCount; i++) {
                final org.mtr.core.data.RoutePlatformData platformA = platforms.get(i % platforms.size());
                final org.mtr.core.data.RoutePlatformData platformB = platforms.get((i + 1) % platforms.size());
                if (platformA == null || platformB == null || platformA.platform == null || platformB.platform == null) {
                    continue;
                }

                final Position[] positionsA = jme$parsePositionsFromHexId(platformA.platform.getHexId());
                final Position[] positionsB = jme$parsePositionsFromHexId(platformB.platform.getHexId());

                final ObjectArrayList<String> startRails = new ObjectArrayList<>();
                jme$appendRailsAtEndpoint(railsByEndpoint, startRails, positionsA[0]);
                jme$appendRailsAtEndpoint(railsByEndpoint, startRails, positionsA[1]);
                final ObjectArrayList<String> endRails = new ObjectArrayList<>();
                jme$appendRailsAtEndpoint(railsByEndpoint, endRails, positionsB[0]);
                jme$appendRailsAtEndpoint(railsByEndpoint, endRails, positionsB[1]);

                if (startRails.isEmpty() || endRails.isEmpty()) {
                    continue;
                }

                final ObjectArrayList<String> pairPath = jme$findRailPath(railsById, railsByEndpoint, startRails, endRails);
                includedRailIds.addAll(pairPath);
            }

            for (final String railId : includedRailIds) {
                final org.mtr.core.data.Rail rail = railsById.get(railId);
                if (rail == null) {
                    continue;
                }
                try {
                    final Position[] railPositions = jme$parsePositionsFromHexId(rail.getHexId());
                    final JsonObject railJson = new JsonObject();
                    railJson.addProperty("id", railId);
                    railJson.add("curvePoints", jme$getCachedCurvePoints(rail, railPositions[0], railPositions[1]));
                    pathRails.add(railJson);
                } catch (Exception ignored) {
                }
            }
        }

        response.add("rails", pathRails);
        return response;
    }

    @Unique
    private static void jme$appendRailsAtEndpoint(java.util.Map<Position, ObjectArrayList<String>> railsByEndpoint, ObjectArrayList<String> target, Position position) {
        final ObjectArrayList<String> rails = railsByEndpoint.get(position);
        if (rails == null) {
            return;
        }
        rails.forEach(railId -> {
            if (!target.contains(railId)) {
                target.add(railId);
            }
        });
    }

    @Unique
    private static ObjectArrayList<String> jme$findRailPath(Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById, java.util.Map<Position, ObjectArrayList<String>> railsByEndpoint, ObjectArrayList<String> startRails, ObjectArrayList<String> endRails) {
        final java.util.HashSet<String> visited = new java.util.HashSet<>();
        final java.util.HashMap<String, String> parent = new java.util.HashMap<>();
        final java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();

        final java.util.HashSet<String> endSet = new java.util.HashSet<>(endRails);
        for (final String startRail : startRails) {
            if (!visited.add(startRail)) {
                continue;
            }
            queue.addLast(startRail);
            parent.put(startRail, null);
        }

        String reached = null;
        while (!queue.isEmpty() && reached == null) {
            final String current = queue.removeFirst();
            if (endSet.contains(current)) {
                reached = current;
                break;
            }

            final org.mtr.core.data.Rail rail = railsById.get(current);
            if (rail == null) {
                continue;
            }
            final Position[] railPositions = jme$parsePositionsFromHexId(rail.getHexId());
            jme$enqueueRailNeighbors(railsByEndpoint, visited, parent, queue, current, railPositions[0]);
            jme$enqueueRailNeighbors(railsByEndpoint, visited, parent, queue, current, railPositions[1]);
        }

        final ObjectArrayList<String> path = new ObjectArrayList<>();
        if (reached != null) {
            String walk = reached;
            while (walk != null) {
                path.add(0, walk);
                walk = parent.get(walk);
            }
        }
        return path;
    }

    @Unique
    private static void jme$enqueueRailNeighbors(java.util.Map<Position, ObjectArrayList<String>> railsByEndpoint, java.util.HashSet<String> visited, java.util.HashMap<String, String> parent, java.util.ArrayDeque<String> queue, String selfRailId, Position endpoint) {
        final ObjectArrayList<String> neighbors = railsByEndpoint.get(endpoint);
        if (neighbors == null) {
            return;
        }
        for (final String neighbor : neighbors) {
            if (neighbor.equals(selfRailId) || !visited.add(neighbor)) {
                continue;
            }
            parent.put(neighbor, selfRailId);
            queue.addLast(neighbor);
        }
    }

    @Unique
    private static boolean jme$isCircularRoute(org.mtr.core.data.Route route) {
        try {
            return route.getCircularState() != org.mtr.core.data.Route.CircularState.NONE;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Unique
    private static String jme$normalizeHexId(String raw) {
        if (raw == null) {
            return "";
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        final String lower = trimmed.toLowerCase(Locale.ENGLISH);
        return lower.startsWith("0x") ? lower.substring(2) : lower;
    }

    @Unique
    private static JsonObject jme$createFullRailsResponse(String dimension) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("jmeRailOverlayMode", JmeConfig.dashboardRailOverlayMode().name().toLowerCase(Locale.ENGLISH));
        response.addProperty("jmeRailCullMaxPerCell", JmeConfig.dashboardRailOverlayCullMaxPerCell());
        response.addProperty("jmeShowBaseRails", JmeConfig.systemMapOverlayShowBaseRails());
        response.addProperty("jmeShowDetails", JmeConfig.systemMapOverlayShowDetails());
        response.addProperty("jmeShowSignals", JmeConfig.systemMapOverlayShowSignals());
        response.addProperty("jmeShowVehicles", JmeConfig.systemMapOverlayShowVehicles());
        response.addProperty("jmeRespectRouteFilters", JmeConfig.systemMapOverlayRespectRouteFilters());

        final String customCss = JmeConfig.getSystemMapCustomCss();
        final String customJs = JmeConfig.getSystemMapCustomJs();
        if (!customCss.isEmpty()) {
            response.addProperty("customCss", customCss);
        }
        if (!customJs.isEmpty()) {
            response.addProperty("customJs", customJs);
        }

        final JsonObject railsSnapshot = jme$railsSnapshotByDimension.get(dimension);
        final JsonObject vehiclesSnapshot = jme$vehiclesSnapshotByDimension.get(dimension);

        final JsonArray liveRails = railsSnapshot != null && railsSnapshot.has("rails") ? railsSnapshot.getAsJsonArray("rails") : new JsonArray();
        final JsonArray liveVehicles = vehiclesSnapshot != null && vehiclesSnapshot.has("vehicles") ? vehiclesSnapshot.getAsJsonArray("vehicles") : new JsonArray();

        final long railsSnapshotTime = railsSnapshot != null && railsSnapshot.has("cachedResponseTime") && railsSnapshot.get("cachedResponseTime").isJsonPrimitive()
                ? railsSnapshot.get("cachedResponseTime").getAsLong()
                : 0;
        final long vehiclesSnapshotTime = vehiclesSnapshot != null && vehiclesSnapshot.has("cachedResponseTime") && vehiclesSnapshot.get("cachedResponseTime").isJsonPrimitive()
                ? vehiclesSnapshot.get("cachedResponseTime").getAsLong()
                : 0;

        // Merge live snapshots into a persisted on-disk cache so rails/vehicles remain visible even if chunks unload.
        // When the overlay is disabled, don't serve/build rails to keep the endpoint cheap.
        final boolean railOverlayEnabled = JmeConfig.dashboardRailOverlayMode() != JmeConfig.DashboardRailOverlayMode.OFF;
        final boolean overlayWantsRails = JmeConfig.systemMapOverlayShowBaseRails() || JmeConfig.systemMapOverlayShowDetails();
        final boolean overlayWantsVehicles = JmeConfig.systemMapOverlayShowVehicles();
        final boolean overlayCacheEnabled = JmeConfig.systemMapOverlayCacheEnabled();
        if (railOverlayEnabled && overlayWantsRails) {
            if (overlayCacheEnabled) {
                SystemMapOverlayCacheStore.mergeLiveRails(dimension, liveRails, railsSnapshotTime);
                response.add("rails", SystemMapOverlayCacheStore.getRailsForResponse(dimension));
            } else {
                response.add("rails", liveRails);
            }
        } else {
            response.add("rails", new JsonArray());
        }

        if (overlayWantsVehicles) {
            if (overlayCacheEnabled) {
                SystemMapOverlayCacheStore.mergeLiveVehicles(dimension, liveVehicles, vehiclesSnapshotTime);
                response.add("vehicles", SystemMapOverlayCacheStore.getVehiclesForResponse(dimension));
            } else {
                response.add("vehicles", liveVehicles);
            }
        } else {
            response.add("vehicles", new JsonArray());
        }

        // Waypoints: merge from WaypointRegistry into the overlay cache.
        final JsonArray waypointArray = new JsonArray();
        for (final WaypointRegistry.Waypoint wp : WaypointRegistry.getAll()) {
            if (wp == null) {
                continue;
            }
            final JsonObject wpJson = new JsonObject();
            wpJson.addProperty("id", String.valueOf(wp.id));
            wpJson.addProperty("name", wp.name);
            wpJson.addProperty("color", wp.color);
            wpJson.addProperty("x", wp.position.getX());
            wpJson.addProperty("z", wp.position.getZ());
            waypointArray.add(wpJson);
        }
        if (overlayCacheEnabled) {
            SystemMapOverlayCacheStore.mergeWaypoints(waypointArray);
            response.add("waypoints", SystemMapOverlayCacheStore.getWaypointsForResponse());
        } else {
            response.add("waypoints", waypointArray);
        }

        return response;
    }

    @Unique
    private static void jme$patchRoutesArrayRouteTypes(JsonObject root) {
        final JsonObject container = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
        jme$applySystemMapLanguageDisplay(container);

        if (!container.has("routes") || !container.get("routes").isJsonArray()) {
            return;
        }

        final JsonArray routes = container.getAsJsonArray("routes");
        for (int i = 0; i < routes.size(); i++) {
            final JsonElement routeElement = routes.get(i);
            if (routeElement == null || !routeElement.isJsonObject()) {
                continue;
            }

            final JsonObject routeObject = routeElement.getAsJsonObject();
            final String normalizedRouteId = jme$getNormalizedRouteId(routeObject.get("id"));
            if (normalizedRouteId.isEmpty()) {
                continue;
            }

            // Only apply config-based overrides when the route is still using a base MTR type.
            // If the route already has an extended type stored in route data (eg train_tram), do not override it.
            final String currentType = routeObject.has("type") && routeObject.get("type").isJsonPrimitive()
                    ? routeObject.get("type").getAsString().trim().toLowerCase(Locale.ENGLISH)
                    : "";
            final boolean isBaseTrainType = currentType.isEmpty()
                    || RouteTypeOverrideConfig.TRAIN_NORMAL.equals(currentType)
                    || RouteTypeOverrideConfig.TRAIN_LIGHT_RAIL.equals(currentType)
                    || RouteTypeOverrideConfig.TRAIN_HIGH_SPEED.equals(currentType);
            if (!isBaseTrainType) {
                continue;
            }

            final String overrideRouteType = RouteTypeOverrideConfig.getRouteType(normalizedRouteId);
            if (!overrideRouteType.isEmpty()) {
                // The upstream web UI only looks at `type` when grouping/rendering routes.
                // Preserve the original value (for any downstream consumers) and also expose the override.
                routeObject.addProperty("jmeBaseType", currentType);
                routeObject.addProperty("jmeType", overrideRouteType);
                routeObject.addProperty("type", overrideRouteType);
            }
        }
    }

    @Unique
    private static JsonObject jme$handleConfigRequest(JsonReader jsonReader) {
        final boolean requestReload = jsonReader != null && jsonReader.getBoolean("reload", false);
        if (requestReload) {
            JmeConfig.reload();
            RouteTypeOverrideConfig.reload();
        }

        final boolean[] changed = {false};
        if (!requestReload && jsonReader != null) {
            jsonReader.unpackBoolean("use_mph", value -> {
                JmeConfig.setUseMph(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("in_world_speed_text_enabled", value -> {
                JmeConfig.setInWorldSpeedTextEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("camera_tilt_enabled", value -> {
                JmeConfig.setCameraTiltEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackDouble("camera_tilt_strength", value -> {
                JmeConfig.setCameraTiltStrength(value);
                changed[0] = true;
            });
            jsonReader.unpackString("dashboard_route_list_mode", value -> {
                JmeConfig.setDashboardRouteListMode(JmeConfig.DashboardRouteListMode.fromString(value));
                changed[0] = true;
            });
            jsonReader.unpackBoolean("dashboard_map_auto_save_enabled", value -> {
                JmeConfig.setDashboardMapAutoSaveEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackString("dashboard_rail_overlay_mode", value -> {
                JmeConfig.setDashboardRailOverlayMode(JmeConfig.DashboardRailOverlayMode.fromString(value));
                changed[0] = true;
            });
            jsonReader.unpackInt("dashboard_rail_overlay_cull_max_per_cell", value -> {
                JmeConfig.setDashboardRailOverlayCullMaxPerCell(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("alternative_platforms_enabled", value -> {
                JmeConfig.setAlternativePlatformsEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_cache_enabled", value -> {
                JmeConfig.setSystemMapOverlayCacheEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_cache_persist_enabled", value -> {
                JmeConfig.setSystemMapOverlayCachePersistEnabled(value);
                changed[0] = true;
            });
            jsonReader.unpackString("system_map_language_display", value -> {
                JmeConfig.setSystemMapLanguageDisplay(JmeConfig.SystemMapLanguageDisplay.fromString(value));
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_hide_player", value -> {
                JmeConfig.setSystemMapHidePlayer(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_show_base_rails", value -> {
                JmeConfig.setSystemMapOverlayShowBaseRails(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_show_details", value -> {
                JmeConfig.setSystemMapOverlayShowDetails(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_show_signals", value -> {
                JmeConfig.setSystemMapOverlayShowSignals(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_show_vehicles", value -> {
                JmeConfig.setSystemMapOverlayShowVehicles(value);
                changed[0] = true;
            });
            jsonReader.unpackBoolean("system_map_overlay_respect_route_filters", value -> {
                JmeConfig.setSystemMapOverlayRespectRouteFilters(value);
                changed[0] = true;
            });
            jsonReader.unpackString("track_color_mode", value -> {
                JmeConfig.setTrackColorMode(JmeConfig.TrackColorMode.fromString(value));
                changed[0] = true;
            });
            jsonReader.unpackString("system_map_custom_css", value -> {
                JmeConfig.setSystemMapCustomCss(value);
                changed[0] = true;
            });
            jsonReader.unpackString("system_map_custom_js", value -> {
                JmeConfig.setSystemMapCustomJs(value);
                changed[0] = true;
            });
        }

        if (changed[0]) {
            JmeConfig.save();
        }

        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("saved", changed[0]);

        final JsonObject config = new JsonObject();
        config.addProperty("use_mph", JmeConfig.useMph());
        config.addProperty("in_world_speed_text_enabled", JmeConfig.inWorldSpeedTextEnabled());
        config.addProperty("camera_tilt_enabled", JmeConfig.cameraTiltEnabled());
        config.addProperty("camera_tilt_strength", JmeConfig.cameraTiltStrength());
        config.addProperty("dashboard_route_list_mode", JmeConfig.dashboardRouteListMode().name());
        config.addProperty("dashboard_map_auto_save_enabled", JmeConfig.dashboardMapAutoSaveEnabled());
        config.addProperty("dashboard_rail_overlay_mode", JmeConfig.dashboardRailOverlayMode().name());
        config.addProperty("dashboard_rail_overlay_cull_max_per_cell", JmeConfig.dashboardRailOverlayCullMaxPerCell());
        config.addProperty("alternative_platforms_enabled", JmeConfig.alternativePlatformsEnabled());
        config.addProperty("system_map_overlay_cache_enabled", JmeConfig.systemMapOverlayCacheEnabled());
        config.addProperty("system_map_overlay_cache_persist_enabled", JmeConfig.systemMapOverlayCachePersistEnabled());
        config.addProperty("system_map_language_display", JmeConfig.systemMapLanguageDisplay().name());
        config.addProperty("system_map_hide_player", JmeConfig.systemMapHidePlayer());
        config.addProperty("system_map_overlay_show_base_rails", JmeConfig.systemMapOverlayShowBaseRails());
        config.addProperty("system_map_overlay_show_details", JmeConfig.systemMapOverlayShowDetails());
        config.addProperty("system_map_overlay_show_signals", JmeConfig.systemMapOverlayShowSignals());
        config.addProperty("system_map_overlay_show_vehicles", JmeConfig.systemMapOverlayShowVehicles());
        config.addProperty("system_map_overlay_respect_route_filters", JmeConfig.systemMapOverlayRespectRouteFilters());
        config.addProperty("track_color_mode", JmeConfig.trackColorMode().name());

        final JsonArray trackColorStops = new JsonArray();
        final JmeConfig.TrackColorStop[] stops = JmeConfig.trackColorCustomGradientStops();
        if (stops != null) {
            for (final JmeConfig.TrackColorStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                final JsonObject stopObject = new JsonObject();
                stopObject.addProperty("speed_kmh", stop.speedKmh);
                stopObject.addProperty("color", String.format(Locale.ROOT, "#%06X", stop.colorArgb & 0xFFFFFF));
                trackColorStops.add(stopObject);
            }
        }
        config.add("track_color_custom_gradient", trackColorStops);
        config.add("track_color_resolved_gradient", jme$buildResolvedTrackColorGradient());
        config.addProperty("system_map_custom_css", JmeConfig.getSystemMapCustomCss());
        config.addProperty("system_map_custom_js", JmeConfig.getSystemMapCustomJs());
        response.add("config", config);

        final JsonObject enums = new JsonObject();
        final JsonArray routeListModes = new JsonArray();
        for (final JmeConfig.DashboardRouteListMode mode : JmeConfig.DashboardRouteListMode.values()) {
            routeListModes.add(mode.name());
        }
        enums.add("dashboard_route_list_mode", routeListModes);

        final JsonArray railOverlayModes = new JsonArray();
        for (final JmeConfig.DashboardRailOverlayMode mode : JmeConfig.DashboardRailOverlayMode.values()) {
            railOverlayModes.add(mode.name());
        }
        enums.add("dashboard_rail_overlay_mode", railOverlayModes);

        final JsonArray languageDisplays = new JsonArray();
        for (final JmeConfig.SystemMapLanguageDisplay display : JmeConfig.SystemMapLanguageDisplay.values()) {
            languageDisplays.add(display.name());
        }
        enums.add("system_map_language_display", languageDisplays);

        final JsonArray trackColorModes = new JsonArray();
        for (final JmeConfig.TrackColorMode mode : JmeConfig.TrackColorMode.values()) {
            trackColorModes.add(mode.name());
        }
        enums.add("track_color_mode", trackColorModes);

        response.add("enums", enums);
        return response;
    }

    @Unique
    private static JsonArray jme$buildResolvedTrackColorGradient() {
        final JmeConfig.TrackColorMode mode = JmeConfig.trackColorMode();

        final JsonArray gradient = new JsonArray();

        if (mode == JmeConfig.TrackColorMode.MTR_DEFAULT) {
            // Mirror MagicRailSpeedColor's MTR palette (sorted, with copper at 250 and magenta at 400).
            final java.util.TreeMap<Integer, Integer> bySpeed = new java.util.TreeMap<>();
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.WOODEN.speedLimit, org.mtr.mod.data.RailType.WOODEN.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.STONE.speedLimit, org.mtr.mod.data.RailType.STONE.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.EMERALD.speedLimit, org.mtr.mod.data.RailType.EMERALD.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.IRON.speedLimit, org.mtr.mod.data.RailType.IRON.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.BRICKS.speedLimit, org.mtr.mod.data.RailType.BRICKS.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.OBSIDIAN.speedLimit, org.mtr.mod.data.RailType.OBSIDIAN.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.PRISMARINE.speedLimit, org.mtr.mod.data.RailType.PRISMARINE.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.BLAZE.speedLimit, org.mtr.mod.data.RailType.BLAZE.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.QUARTZ.speedLimit, org.mtr.mod.data.RailType.QUARTZ.color);
            jme$putMtrGradientStop(bySpeed, org.mtr.mod.data.RailType.DIAMOND.speedLimit, org.mtr.mod.data.RailType.DIAMOND.color);

            // Requested: 250 km/h should be copper to stand out.
            bySpeed.put(250, 0xFFB87333);
            // Requested: 400 km/h is the purpur connector (magenta).
            bySpeed.put(400, 0xFFB42AE6);

            for (final Map.Entry<Integer, Integer> entry : bySpeed.entrySet()) {
                if (entry == null) {
                    continue;
                }
                final JsonObject stopObject = new JsonObject();
                stopObject.addProperty("speed_kmh", entry.getKey());
                stopObject.addProperty("color", String.format(Locale.ROOT, "#%06X", entry.getValue() & 0xFFFFFF));
                gradient.add(stopObject);
            }

            return gradient;
        }

        if (mode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT) {
            final JmeConfig.TrackColorStop[] stops = JmeConfig.trackColorCustomGradientStops();
            if (stops != null) {
                for (final JmeConfig.TrackColorStop stop : stops) {
                    if (stop == null) {
                        continue;
                    }
                    final JsonObject stopObject = new JsonObject();
                    stopObject.addProperty("speed_kmh", stop.speedKmh);
                    stopObject.addProperty("color", String.format(Locale.ROOT, "#%06X", stop.colorArgb & 0xFFFFFF));
                    gradient.add(stopObject);
                }
            }
            return gradient;
        }

        // OpenRailwayMap-like default.
        final int[][] stops = new int[][]{
                {5, 0xFF102A8A},
                {100, 0xFF25C977},
                {180, 0xFFD9E344},
                {220, 0xFFFFE028},
                {300, 0xFFEF3A26},
                {400, 0xFFB42AE6}
        };

        for (final int[] stop : stops) {
            if (stop == null || stop.length < 2) {
                continue;
            }
            final JsonObject stopObject = new JsonObject();
            stopObject.addProperty("speed_kmh", stop[0]);
            stopObject.addProperty("color", String.format(Locale.ROOT, "#%06X", stop[1] & 0xFFFFFF));
            gradient.add(stopObject);
        }

        return gradient;
    }

    @Unique
    private static void jme$putMtrGradientStop(java.util.TreeMap<Integer, Integer> bySpeed, int speedKmh, int color) {
        if (bySpeed == null) {
            return;
        }
        final int speed = Math.max(1, Math.min(400, speedKmh));
        final int argb = 0xFF000000 | (color & 0xFFFFFF);
        bySpeed.put(speed, argb);
    }

    @Unique
    private static void jme$applySystemMapLanguageDisplay(JsonObject container) {
        if (container == null) {
            return;
        }

        final JmeConfig.SystemMapLanguageDisplay display = JmeConfig.systemMapLanguageDisplay();

        if (container.has("stations") && container.get("stations").isJsonArray()) {
            final JsonArray stations = container.getAsJsonArray("stations");
            for (int i = 0; i < stations.size(); i++) {
                final JsonElement stationElement = stations.get(i);
                if (stationElement == null || !stationElement.isJsonObject()) {
                    continue;
                }
                final JsonObject stationObject = stationElement.getAsJsonObject();
                jme$patchNameField(stationObject, "name", display);
            }
        }

        if (container.has("routes") && container.get("routes").isJsonArray()) {
            final JsonArray routes = container.getAsJsonArray("routes");
            for (int i = 0; i < routes.size(); i++) {
                final JsonElement routeElement = routes.get(i);
                if (routeElement == null || !routeElement.isJsonObject()) {
                    continue;
                }

                final JsonObject routeObject = routeElement.getAsJsonObject();
                // Preserve the upstream System Map route variation delimiter `||`.
                // The web UI splits route names on `||` to build a route with variations;
                // stripping pipes here breaks that grouping and shows each variation as a separate route.
                jme$patchRouteNameField(routeObject, "name", display);
                jme$patchRouteNameField(routeObject, "routeName", display);

                if (routeObject.has("stations") && routeObject.get("stations").isJsonArray()) {
                    final JsonArray routeStations = routeObject.getAsJsonArray("stations");
                    for (int j = 0; j < routeStations.size(); j++) {
                        final JsonElement routeStationElement = routeStations.get(j);
                        if (routeStationElement == null || !routeStationElement.isJsonObject()) {
                            continue;
                        }
                        final JsonObject routeStationObject = routeStationElement.getAsJsonObject();
                        jme$patchNameField(routeStationObject, "name", display);
                    }
                }
            }
        }
    }

    @Unique
    private static void jme$patchNameField(JsonObject obj, String fieldName, JmeConfig.SystemMapLanguageDisplay display) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return;
        }
        if (!obj.has(fieldName) || !obj.get(fieldName).isJsonPrimitive()) {
            return;
        }

        final String raw = obj.get(fieldName).getAsString();
        final String patched = jme$formatMultilingualText(raw, display);
        if (!patched.equals(raw)) {
            obj.addProperty(fieldName, patched);
        }
    }

    @Unique
    private static void jme$patchRouteNameField(JsonObject obj, String fieldName, JmeConfig.SystemMapLanguageDisplay display) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return;
        }
        if (!obj.has(fieldName) || !obj.get(fieldName).isJsonPrimitive()) {
            return;
        }

        final String raw = obj.get(fieldName).getAsString();
        final String patched = jme$formatRouteName(raw, display);
        if (!patched.equals(raw)) {
            obj.addProperty(fieldName, patched);
        }
    }

    @Unique
    private static String jme$formatMultilingualText(String raw, JmeConfig.SystemMapLanguageDisplay display) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return value;
        }

        final JmeConfig.SystemMapLanguageDisplay mode = display == null ? JmeConfig.SystemMapLanguageDisplay.NORMAL : display;

        final String[] parts = value.split("\\|");
        if (parts.length > 1) {
            String bestCjk = "";
            int bestCjkCount = -1;
            String bestNonCjk = "";
            int bestNonCjkCount = -1;

            final StringBuilder joined = new StringBuilder();
            for (final String partRaw : parts) {
                final String part = partRaw == null ? "" : partRaw.trim();
                if (part.isEmpty()) {
                    continue;
                }

                if (joined.length() > 0) {
                    joined.append(" / ");
                }
                joined.append(part);

                final int cjkCount = jme$countCjk(part);
                if (cjkCount > bestCjkCount) {
                    bestCjkCount = cjkCount;
                    bestCjk = part;
                }
                final int nonCjkCount = part.codePointCount(0, part.length()) - cjkCount;
                if (nonCjkCount > bestNonCjkCount) {
                    bestNonCjkCount = nonCjkCount;
                    bestNonCjk = part;
                }
            }

            if (mode == JmeConfig.SystemMapLanguageDisplay.CJK_ONLY) {
                final String candidate = bestCjk.isEmpty() ? value : bestCjk;
                return jme$filterByCjk(candidate, true);
            } else if (mode == JmeConfig.SystemMapLanguageDisplay.NON_CJK_ONLY) {
                final String candidate = bestNonCjk.isEmpty() ? value : bestNonCjk;
                return jme$filterByCjk(candidate, false);
            } else {
                return jme$joinMultilingualParts(parts, value);
            }
        }

        if (mode == JmeConfig.SystemMapLanguageDisplay.CJK_ONLY) {
            return jme$filterByCjk(value, true);
        } else if (mode == JmeConfig.SystemMapLanguageDisplay.NON_CJK_ONLY) {
            return jme$filterByCjk(value, false);
        } else {
            return value;
        }
    }

    @Unique
    private static String jme$joinMultilingualParts(String[] parts, String fallback) {
        if (parts == null || parts.length == 0) {
            return fallback == null ? "" : fallback;
        }

        final StringBuilder joined = new StringBuilder();
        for (final String partRaw : parts) {
            final String part = partRaw == null ? "" : partRaw.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(part);
        }

        return joined.length() == 0 ? (fallback == null ? "" : fallback) : joined.toString();
    }

    /**
     * The upstream web System Map uses {@code name.split("||")} to determine the route name and its variations.
     * Preserve a single {@code ||} delimiter so route variations remain grouped, and collapse any extra {@code ||}
     * segments into the variation part.
     *
     * <p>This method still applies {@link #jme$formatMultilingualText} to each segment so the
     * {@code system_map_language_display} setting works as expected.</p>
     */
    @Unique
    private static String jme$formatRouteName(String raw, JmeConfig.SystemMapLanguageDisplay display) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return value;
        }

        if (!value.contains("||")) {
            return jme$formatMultilingualText(value, display);
        }

        final String[] parts = value.split("\\|\\|");
        if (parts.length <= 1) {
            return jme$formatMultilingualText(value, display);
        }

        final String base = jme$formatMultilingualText(parts[0], display);

        final StringBuilder variations = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i] == null ? "" : parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }

            final String formatted = jme$formatMultilingualText(part, display).trim();
            if (formatted.isEmpty()) {
                continue;
            }

            if (variations.length() > 0) {
                variations.append(" / ");
            }
            variations.append(formatted);
        }

        if (variations.length() == 0) {
            return base;
        }

        return base + "||" + variations;
    }

    @Unique
    private static String jme$filterByCjk(String text, boolean keepCjk) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '|') {
                continue;
            }

            final boolean isCjk = jme$isCjk(codePoint);
            if (keepCjk && !isCjk) {
                // Keep ASCII digits/spaces/punctuation for readability alongside CJK names.
                if (codePoint >= '0' && codePoint <= '9') {
                    out.appendCodePoint(codePoint);
                } else if (Character.isWhitespace(codePoint)) {
                    out.append(' ');
                } else if (codePoint == '-' || codePoint == '_' || codePoint == '/' || codePoint == '(' || codePoint == ')' || codePoint == '.' || codePoint == ',') {
                    out.appendCodePoint(codePoint);
                }
                continue;
            }
            if (!keepCjk && isCjk) {
                continue;
            }
            out.appendCodePoint(codePoint);
        }

        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }

    @Unique
    private static int jme$countCjk(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (jme$isCjk(codePoint)) {
                count++;
            }
        }
        return count;
    }

    @Unique
    private static boolean jme$isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // CJK Unified Ideographs
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // CJK Unified Ideographs Extension A
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF) // CJK Compatibility Ideographs
                || (codePoint >= 0x2E80 && codePoint <= 0x2FFF) // CJK Radicals Supplement, Kangxi Radicals, etc.
                || (codePoint >= 0x3000 && codePoint <= 0x303F) // CJK Symbols and Punctuation
                || (codePoint >= 0x3040 && codePoint <= 0x309F) // Hiragana
                || (codePoint >= 0x30A0 && codePoint <= 0x30FF) // Katakana
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF) // Hangul Syllables
                || (codePoint >= 0x1100 && codePoint <= 0x11FF) // Hangul Jamo
                || (codePoint >= 0x3130 && codePoint <= 0x318F) // Hangul Compatibility Jamo
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF); // Halfwidth and Fullwidth Forms
    }

    @Unique
    private static String jme$getNormalizedRouteId(JsonElement routeIdElement) {
        if (routeIdElement == null || routeIdElement.isJsonNull()) {
            return "";
        }

        try {
            if (routeIdElement.isJsonPrimitive() && routeIdElement.getAsJsonPrimitive().isNumber()) {
                return RouteTypeOverrideConfig.normalizeRouteId(Utilities.numberToPaddedHexString(routeIdElement.getAsLong()));
            }

            final String raw = routeIdElement.getAsString().trim();
            if (raw.isEmpty()) {
                return "";
            }

            if (raw.chars().allMatch(character -> character == '-' || (character >= '0' && character <= '9'))) {
                try {
                    return RouteTypeOverrideConfig.normalizeRouteId(Utilities.numberToPaddedHexString(Long.parseLong(raw)));
                } catch (Exception ignored) {
                }
            }

            return RouteTypeOverrideConfig.normalizeRouteId(raw.toUpperCase(Locale.ENGLISH));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void jme$populateRailsById(Simulator simulator, Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById) {
        simulator.positionsToRail.values().forEach(connectionMap -> connectionMap.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail)));
        simulator.railIdMap.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
        simulator.rails.forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
    }

    @Unique
    private static void jme$submitRailsBuildIfStale(String dimension, Simulator simulator) {
        final long now = System.currentTimeMillis();
        final long lastBuild = jme$lastRailsBuildMillis.getOrDefault(dimension, 0L);
        if (jme$railsSnapshotByDimension.containsKey(dimension) && now - lastBuild < JME_RAILS_REFRESH_MILLIS) {
            return;
        }
        jme$submitRailsBuild(dimension, simulator);
    }

    @Unique
    private static void jme$submitVehiclesBuildIfStale(String dimension, Simulator simulator) {
        final long now = System.currentTimeMillis();
        final long lastBuild = jme$lastVehiclesBuildMillis.getOrDefault(dimension, 0L);
        if (jme$vehiclesSnapshotByDimension.containsKey(dimension) && now - lastBuild < JME_VEHICLES_REFRESH_MILLIS) {
            return;
        }
        jme$submitVehiclesBuild(dimension, simulator);
    }

    @Unique
    private static void jme$submitRailsBuild(String dimension, Simulator simulator) {
        if (!jme$inFlightRailsDimensions.add(dimension)) {
            return;
        }
        
        final Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById = new Object2ObjectLinkedOpenHashMap<>();
        final ObjectArrayList<org.mtr.core.data.Siding> sidingsCopy = new ObjectArrayList<>();
        final ObjectArrayList<org.mtr.core.data.Route> routesCopy = new ObjectArrayList<>();
        
        // Only gather references on the simulator thread to minimize tick lag.
        simulator.run(() -> {
            try {
                jme$populateRailsById(simulator, railsById);
                sidingsCopy.addAll(simulator.sidings);
                routesCopy.addAll(simulator.routes);
            } catch (Exception e) {
                Main.LOGGER.warn("Rails extraction failed for dimension {}", dimension, e);
            } finally {
                // Offload JSON generation to a background thread.
                new Thread(() -> {
                    try {
                        jme$railsSnapshotByDimension.put(dimension, jme$getRailsSnapshot(simulator, railsById, sidingsCopy, routesCopy));
                        jme$lastRailsBuildMillis.put(dimension, System.currentTimeMillis());
                    } catch (Exception e) {
                        Main.LOGGER.warn("Rails snapshot build failed for dimension {}", dimension, e);
                    } finally {
                        jme$inFlightRailsDimensions.remove(dimension);
                    }
                }, "MAGIC-RailsBuilder").start();
            }
        });
    }

    @Unique
    private static void jme$submitVehiclesBuild(String dimension, Simulator simulator) {
        if (!jme$inFlightVehiclesDimensions.add(dimension)) {
            return;
        }
        
        final ObjectArrayList<org.mtr.core.data.Siding> sidingsCopy = new ObjectArrayList<>();
        
        simulator.run(() -> {
            try {
                sidingsCopy.addAll(simulator.sidings);
            } catch (Exception e) {
                Main.LOGGER.warn("Vehicles extraction failed for dimension {}", dimension, e);
            } finally {
                new Thread(() -> {
                    try {
                        jme$vehiclesSnapshotByDimension.put(dimension, jme$getVehiclesSnapshot(simulator, sidingsCopy));
                        jme$lastVehiclesBuildMillis.put(dimension, System.currentTimeMillis());
                    } catch (Exception e) {
                        Main.LOGGER.warn("Vehicles snapshot build failed for dimension {}", dimension, e);
                    } finally {
                        jme$inFlightVehiclesDimensions.remove(dimension);
                    }
                }, "MAGIC-VehiclesBuilder").start();
            }
        });
    }

    @Unique
    private static JsonObject jme$getRailsSnapshot(Simulator simulator, Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById, ObjectArrayList<org.mtr.core.data.Siding> sidings, ObjectArrayList<org.mtr.core.data.Route> routes) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());

        final String customCss = JmeConfig.getSystemMapCustomCss();
        final String customJs = JmeConfig.getSystemMapCustomJs();
        if (!customCss.isEmpty()) {
            response.addProperty("customCss", customCss);
        }
        if (!customJs.isEmpty()) {
            response.addProperty("customJs", customJs);
        }

        final JsonArray railsJson = new JsonArray();
        final Object2ObjectAVLTreeMap<Position, ObjectArrayList<JsonObject>> trainsByNode = new Object2ObjectAVLTreeMap<>();
        final Object2ObjectAVLTreeMap<Position, ObjectArrayList<String>> routesByNode = new Object2ObjectAVLTreeMap<>();

        try {
            routes.forEach(route -> route.getRoutePlatforms().forEach(routePlatformData -> {
                if (routePlatformData.platform != null) {
                    final Position[] platformPositions = jme$parsePositionsFromHexId(routePlatformData.platform.getHexId());
                    final Position position1 = platformPositions[0];
                    final Position position2 = platformPositions[1];
                    jme$addDistinct(routesByNode, position1, route.getHexId());
                    jme$addDistinct(routesByNode, position2, route.getHexId());
                }
            }));

            if (!jme$disableTrainExtraction) {
                sidings.forEach(siding -> jme$forEachSidingVehicle(siding, vehicle -> {
                    if (jme$disableTrainExtraction) {
                        return;
                    }
                    try {
                        final double railProgress = jme$getVehicleRailProgress(vehicle);
                        final double speed = jme$getVehicleSpeed(vehicle);

                        final Object vehicleExtraData = jme$getVehicleExtraData(vehicle);
                        final Object pathObject = jme$getImmutablePath(vehicleExtraData);
                        if (!(pathObject instanceof java.util.List<?>)) {
                            return;
                        }
                        final java.util.List<?> path = (java.util.List<?>) pathObject;

                        final int pathIndex = Utilities.getIndexFromConditionalList((java.util.List<PathData>) path, railProgress);
                        final PathData pathData = Utilities.getElement((java.util.List<PathData>) path, pathIndex);
                        if (pathData == null) {
                            return;
                        }

                        final JsonObject trainJson = new JsonObject();
                        final String vehicleId = (String) jme$getVehicleHexId(vehicle);
                        trainJson.addProperty("id", vehicleId);
                        trainJson.addProperty("speed", speed);
                        trainJson.addProperty("railProgress", railProgress);
                        trainJson.addProperty("deviationMillis", ((VehicleDelayAccessor) vehicle).jme$getDeviation());
                        final long thisRouteId = jme$getThisRouteId(vehicleExtraData);
                        final String routeId = Utilities.numberToPaddedHexString(thisRouteId);
                        trainJson.addProperty("routeId", routeId);
                        jme$add(trainsByNode, pathData.getOrderedPosition1(), trainJson);
                        jme$add(trainsByNode, pathData.getOrderedPosition2(), trainJson);
                    } catch (Exception e) {
                        jme$disableTrainExtraction = true;
                        Main.LOGGER.warn("Disabled train extraction for rails API due to reflection incompatibility: {}", e.getMessage());
                    }
                }));
            }

            railsById.values().forEach(rail -> {
                try {
                    final JsonObject railJson = Utilities.getJsonObjectFromData(rail);
                    final Position[] railPositions = jme$parsePositionsFromHexId(rail.getHexId());
                    final Position position1 = railPositions[0];
                    final Position position2 = railPositions[1];

                    final JsonObject railData = new JsonObject();
                    railData.addProperty("id", rail.getHexId());
                    railData.add("position1", railJson.get("position1"));
                    railData.add("position2", railJson.get("position2"));
                    railData.addProperty("rotation1", railJson.has("angle1") ? railJson.get("angle1").getAsString() : "0");
                    railData.addProperty("rotation2", railJson.has("angle2") ? railJson.get("angle2").getAsString() : "0");
                    railData.add("styles", railJson.getAsJsonArray("styles"));
                    railData.add("signalColors", railJson.has("signalColors") ? railJson.getAsJsonArray("signalColors") : new JsonArray());
                    railData.addProperty("canHaveSignal", railJson.has("canHaveSignal") && railJson.get("canHaveSignal").getAsBoolean());
                    railData.addProperty("length", rail.railMath.getLength());

                    final long speedLimit1 = railJson.has("speedLimit1") ? railJson.get("speedLimit1").getAsLong() : 0;
                    final long speedLimit2 = railJson.has("speedLimit2") ? railJson.get("speedLimit2").getAsLong() : 0;
                    railData.addProperty("speedLimit1", speedLimit1);
                    railData.addProperty("speedLimit2", speedLimit2);
                    railData.addProperty("canTravel1To2", speedLimit1 > 0);
                    railData.addProperty("canTravel2To1", speedLimit2 > 0);

                    // Keep the original rails endpoint shape (two nodes) but also provide a sampled polyline.
                    final JsonArray nodes = new JsonArray();
                    nodes.add(jme$createNodeJson(position1, speedLimit1, trainsByNode, routesByNode));
                    nodes.add(jme$createNodeJson(position2, speedLimit2, trainsByNode, routesByNode));
                    railData.add("connectedNodes", nodes);
                    railData.add("curvePoints", jme$getCachedCurvePoints(rail, position1, position2));

                    final JsonArray connectedRails = new JsonArray();
                    jme$appendNextRails(connectedRails, simulator, position1, rail.getHexId());
                    jme$appendNextRails(connectedRails, simulator, position2, rail.getHexId());
                    railData.add("connectedRails", connectedRails);

                    final JsonArray connectedRails1 = new JsonArray();
                    jme$appendNextRails(connectedRails1, simulator, position1, rail.getHexId());
                    railData.add("connectedRails1", connectedRails1);
                    final JsonArray connectedRails2 = new JsonArray();
                    jme$appendNextRails(connectedRails2, simulator, position2, rail.getHexId());
                    railData.add("connectedRails2", connectedRails2);

                    railsJson.add(railData);
                } catch (Exception e) {
                    Main.LOGGER.error("Error processing rail " + rail.getHexId(), e);
                }
            });
        } catch (Exception e) {
            Main.LOGGER.error("Error in getRails (JME Mixin)", e);
        }

        final JsonArray stationsJson = new JsonArray();
        simulator.stations.forEach(station -> {
            try {
                stationsJson.add(Utilities.getJsonObjectFromData(station));
            } catch (Exception e) {}
        });
        response.add("stations", stationsJson);

        final JsonArray platformsJson = new JsonArray();
        simulator.platforms.forEach(platform -> {
            try {
                platformsJson.add(Utilities.getJsonObjectFromData(platform));
            } catch (Exception e) {}
        });
        response.add("platforms", platformsJson);

        final JsonArray sidingsJson = new JsonArray();
        sidings.forEach(siding -> {
            try {
                sidingsJson.add(Utilities.getJsonObjectFromData(siding));
            } catch (Exception e) {}
        });
        response.add("sidings", sidingsJson);

        final JsonArray routesJson = new JsonArray();
        routes.forEach(route -> {
            try {
                routesJson.add(Utilities.getJsonObjectFromData(route));
            } catch (Exception e) {}
        });
        response.add("routes", routesJson);

        response.add("rails", railsJson);
        response.add("vehicles", new JsonArray());
        return response;
    }

    @Unique
    private static JsonObject jme$getVehiclesSnapshot(Simulator simulator, ObjectArrayList<org.mtr.core.data.Siding> sidings) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray vehiclesJson = new JsonArray();

        try {
            if (!jme$disableTrainExtraction) {
                sidings.forEach(siding -> jme$forEachSidingVehicle(siding, vehicle -> {
                    if (jme$disableTrainExtraction) {
                        return;
                    }
                    try {
                        final double railProgress = jme$getVehicleRailProgress(vehicle);
                        final double speed = jme$getVehicleSpeed(vehicle);

                        final Object vehicleExtraData = jme$getVehicleExtraData(vehicle);
                        final Object pathObject = jme$getImmutablePath(vehicleExtraData);
                        if (!(pathObject instanceof java.util.List<?>)) {
                            return;
                        }
                        final java.util.List<?> path = (java.util.List<?>) pathObject;

                        final int pathIndex = Utilities.getIndexFromConditionalList((java.util.List<PathData>) path, railProgress);
                        final PathData pathData = Utilities.getElement((java.util.List<PathData>) path, pathIndex);
                        if (pathData == null) {
                            return;
                        }

final String vehicleId = (String) jme$getVehicleHexId(vehicle);
                        final long thisRouteId = jme$getThisRouteId(vehicleExtraData);
                        final String routeId = Utilities.numberToPaddedHexString(thisRouteId);

                        final JsonObject vehicleJson = new JsonObject();
                        vehicleJson.addProperty("id", vehicleId);
                        vehicleJson.addProperty("speed", speed);
                        vehicleJson.addProperty("railProgress", railProgress);
                        vehicleJson.addProperty("deviationMillis", ((VehicleDelayAccessor) vehicle).jme$getDeviation());
                        vehicleJson.addProperty("routeId", routeId);
                        jme$addVehicleLineInfo(vehicleJson, vehicleExtraData);
                        vehicleJson.addProperty("bearing", jme$computePathBearing(pathData));
                        final JsonObject headPosition = jme$getVehicleHeadPosition(vehicle);
                        if (headPosition != null) {
                            vehicleJson.add("position", headPosition);
                        }
                        JsonArray cars = jme$getVehicleCarsJson(vehicle);
                        if (cars != null && cars.size() > 0) {
                            vehicleJson.add("cars", cars);
                        }
                        if (headPosition == null) {
                            final Position position1 = pathData.getOrderedPosition1();
                            final Position position2 = pathData.getOrderedPosition2();
                            vehicleJson.add("position", jme$createPositionJson(
                                    (position1.getX() + position2.getX()) / 2D,
                                    (position1.getY() + position2.getY()) / 2D,
                                    (position1.getZ() + position2.getZ()) / 2D
                            ));
                        }
                        vehicleJson.add("cars", jme$getVehicleCars(vehicle));
                        vehiclesJson.add(vehicleJson);
                    } catch (Exception e) {
                        jme$disableTrainExtraction = true;
                        Main.LOGGER.warn("Disabled train extraction for rails API due to reflection incompatibility: {}", e.getMessage());
                    }
                }));
            }

            jme$addCanceledVehicles(simulator, vehiclesJson);
        } catch (Exception e) {
            Main.LOGGER.error("Error in getVehiclesSnapshot (JME Mixin)", e);
        }

        response.add("rails", new JsonArray());
        response.add("vehicles", vehiclesJson);
        return response;
    }


    @Unique
    private static void jme$addCanceledVehicles(Simulator simulator, JsonArray vehiclesJson) {
        if (simulator == null || vehiclesJson == null) {
            return;
        }

        for (final CanceledTrainRegistry.CanceledTrain canceledTrain : CanceledTrainRegistry.activeForMap()) {
            try {
                final org.mtr.core.data.Platform platform = simulator.platformIdMap.get(canceledTrain.platformId);
                if (platform == null) {
                    continue;
                }

                final Position[] platformPositions = jme$parsePositionsFromHexId(platform.getHexId());
                final Position position1 = platformPositions[0];
                final Position position2 = platformPositions[1];
                if (position1 == null || position2 == null) {
                    continue;
                }

                final JsonObject vehicleJson = new JsonObject();
                vehicleJson.addProperty("id", "canceled-" + Long.toUnsignedString(canceledTrain.vehicleId, 16));
                vehicleJson.addProperty("cancelled", true);
                vehicleJson.addProperty("canceled", true);
                vehicleJson.addProperty("lineName", "Canceled");
                vehicleJson.addProperty("lineColor", 0x808080);
                vehicleJson.addProperty("destination", canceledTrain.destination);
                vehicleJson.addProperty("struckDestination", canceledTrain.struckDestination);
                vehicleJson.addProperty("canceledAtMillis", canceledTrain.canceledAtMillis);
                vehicleJson.addProperty("speed", 0D);
                vehicleJson.addProperty("railProgress", 0D);
                vehicleJson.addProperty("deviationMillis", 0L);
                vehicleJson.addProperty("routeId", Utilities.numberToPaddedHexString(canceledTrain.routeId));
                vehicleJson.addProperty("platformId", Utilities.numberToPaddedHexString(canceledTrain.platformId));
                vehicleJson.addProperty("bearing", -1D);
                vehicleJson.add("position", jme$createPositionJson(
                        (position1.getX() + position2.getX()) / 2D,
                        (position1.getY() + position2.getY()) / 2D,
                        (position1.getZ() + position2.getZ()) / 2D
                ));
                vehicleJson.add("cars", new JsonArray());
                vehiclesJson.add(vehicleJson);
            } catch (Exception ignored) {
            }
        }
    }

    @Unique
    private static JsonArray jme$getCachedCurvePoints(org.mtr.core.data.Rail rail, Position position1, Position position2) {
        final String railId = rail.getHexId();
        final JsonArray cached = jme$curvePointsCacheByRailId.get(railId);
        if (cached != null) {
            return cached;
        }

        final ObjectArrayList<double[]> curvePoints = jme$sampleCurvePoints(rail, position1, position2);
        final JsonArray created = jme$createCurvePointsJson(curvePoints);
        jme$curvePointsCacheByRailId.put(railId, created);
        return created;
    }

    private static JsonObject jme$createNodeJson(Position position, long speedLimit, Object2ObjectAVLTreeMap<Position, ObjectArrayList<JsonObject>> trainsByNode, Object2ObjectAVLTreeMap<Position, ObjectArrayList<String>> routesByNode) {
        final JsonObject nodeJson = new JsonObject();
        nodeJson.add("position", Utilities.getJsonObjectFromData(position));
        nodeJson.addProperty("speedLimit", speedLimit);

        final JsonArray trains = new JsonArray();
        trainsByNode.getOrDefault(position, new ObjectArrayList<>()).forEach(trains::add);
        nodeJson.add("trains", trains);

        final JsonArray routes = new JsonArray();
        routesByNode.getOrDefault(position, new ObjectArrayList<>()).forEach(routes::add);
        nodeJson.add("routes", routes);
        return nodeJson;
    }

    private static JsonArray jme$createConnectedNodesJson(
            ObjectArrayList<double[]> curvePoints,
            Position position1,
            Position position2,
            long speedLimit1,
            long speedLimit2,
            Object2ObjectAVLTreeMap<Position, ObjectArrayList<JsonObject>> trainsByNode,
            Object2ObjectAVLTreeMap<Position, ObjectArrayList<String>> routesByNode
    ) {
        final JsonArray nodes = new JsonArray();
        final int lastIndex = Math.max(0, curvePoints.size() - 1);
        for (int i = 0; i <= lastIndex; i++) {
            if (i == 0) {
                nodes.add(jme$createNodeJson(position1, speedLimit1, trainsByNode, routesByNode));
            } else if (i == lastIndex) {
                nodes.add(jme$createNodeJson(position2, speedLimit2, trainsByNode, routesByNode));
            } else {
                final double progress = i / (double) lastIndex;
                nodes.add(jme$createIntermediateNodeJson(curvePoints.get(i), jme$interpolateSpeedLimit(speedLimit1, speedLimit2, progress)));
            }
        }
        return nodes;
    }

    private static JsonObject jme$createIntermediateNodeJson(double[] point, long speedLimit) {
        final JsonObject nodeJson = new JsonObject();
        nodeJson.add("position", jme$createPositionJson(point[0], point[1], point[2]));
        nodeJson.addProperty("speedLimit", speedLimit);
        nodeJson.add("trains", new JsonArray());
        nodeJson.add("routes", new JsonArray());
        return nodeJson;
    }

    private static JsonArray jme$createCurvePointsJson(ObjectArrayList<double[]> curvePoints) {
        final JsonArray points = new JsonArray();
        final int lastIndex = Math.max(0, curvePoints.size() - 1);
        for (int i = 0; i <= lastIndex; i++) {
            final double[] point = curvePoints.get(i);
            final JsonObject pointJson = jme$createPositionJson(point[0], point[1], point[2]);
            pointJson.addProperty("progress", lastIndex <= 0 ? 0 : i / (double) lastIndex);
            points.add(pointJson);
        }
        return points;
    }

    private static ObjectArrayList<double[]> jme$sampleCurvePoints(org.mtr.core.data.Rail rail, Position position1, Position position2) {
        final ObjectArrayList<double[]> curvePoints = new ObjectArrayList<>();
        final double railLength = Math.max(0.001, rail.railMath.getLength());
        final double interval = Math.max(0.4, Math.min(1.25, railLength / 24));

        jme$addCurvePointIfDistinct(curvePoints, position1.getX(), position1.getY(), position1.getZ());
        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerX = (x1 + x3) / 2;
            final double centerY = (y1 + y2) / 2;
            final double centerZ = (z1 + z3) / 2;
            jme$addCurvePointIfDistinct(curvePoints, centerX, centerY, centerZ);
        }, interval, 0, 0);
        jme$addCurvePointIfDistinct(curvePoints, position2.getX(), position2.getY(), position2.getZ());

        if (curvePoints.size() == 1) {
            jme$addCurvePointIfDistinct(curvePoints, position2.getX(), position2.getY(), position2.getZ());
        }
        return curvePoints;
    }

    private static void jme$addCurvePointIfDistinct(ObjectArrayList<double[]> points, double x, double y, double z) {
        final double[] point = new double[]{x, y, z};
        if (points.isEmpty()) {
            points.add(point);
            return;
        }

        final double[] previous = points.get(points.size() - 1);
        final double differenceX = previous[0] - x;
        final double differenceY = previous[1] - y;
        final double differenceZ = previous[2] - z;
        if (differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ > 1.0E-4) {
            points.add(point);
        }
    }

    private static long jme$interpolateSpeedLimit(long speedLimit1, long speedLimit2, double progress) {
        return Math.round(speedLimit1 + (speedLimit2 - speedLimit1) * Math.max(0, Math.min(1, progress)));
    }

    private static JsonObject jme$createPositionJson(double x, double y, double z) {
        final JsonObject positionJson = new JsonObject();
        positionJson.addProperty("x", x);
        positionJson.addProperty("y", y);
        positionJson.addProperty("z", z);
        return positionJson;
    }

    private static void jme$appendNextRails(JsonArray nextRails, Simulator simulator, Position nodePosition, String selfRailId) {
        final Object2ObjectOpenHashMap<Position, org.mtr.core.data.Rail> connectionMap = simulator.positionsToRail.get(nodePosition);
        if (connectionMap == null) {
            return;
        }
        connectionMap.values().forEach(nextRail -> {
            final boolean canLeaveThisNode;
            try {
                canLeaveThisNode = nextRail.getSpeedLimitMetersPerMillisecond(nodePosition) > 0;
            } catch (Exception ignored) {
                // Fall back to the legacy behavior when the API differs.
                return;
            }

            if (canLeaveThisNode && !selfRailId.equals(nextRail.getHexId()) && !jme$contains(nextRails, nextRail.getHexId())) {
                nextRails.add(nextRail.getHexId());
            }
        });
    }

    private static boolean jme$contains(JsonArray array, String value) {
        for (int i = 0; i < array.size(); i++) {
            if (value.equals(array.get(i).getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static <T> void jme$add(Object2ObjectAVLTreeMap<Position, ObjectArrayList<T>> map, Position position, T value) {
        map.computeIfAbsent(position, key -> new ObjectArrayList<>()).add(value);
    }

    private static void jme$addDistinct(Object2ObjectAVLTreeMap<Position, ObjectArrayList<String>> map, Position position, String value) {
        final ObjectArrayList<String> values = map.computeIfAbsent(position, key -> new ObjectArrayList<>());
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static Position[] jme$parsePositionsFromHexId(String hexId) {
        final String[] split = hexId.split("-");
        if (split.length != 6) {
            return new Position[]{new Position(0, 0, 0), new Position(0, 0, 0)};
        }

        return new Position[]{
                new Position(jme$parseHexLong(split[0]), jme$parseHexLong(split[1]), jme$parseHexLong(split[2])),
                new Position(jme$parseHexLong(split[3]), jme$parseHexLong(split[4]), jme$parseHexLong(split[5]))
        };
    }

    private static long jme$parseHexLong(String value) {
        return Long.parseUnsignedLong(value, 16);
    }

    private static void jme$forEachSidingVehicle(Object siding, Consumer<Object> consumer) {
        try {
            Field vehiclesField = jme$vehiclesField;
            if (vehiclesField == null) {
                vehiclesField = siding.getClass().getDeclaredField("vehicles");
                vehiclesField.setAccessible(true);
                jme$vehiclesField = vehiclesField;
            }
            vehiclesField.setAccessible(true);
            final Object vehiclesObject = vehiclesField.get(siding);
            if (vehiclesObject instanceof Collection<?>) {
                final Collection<?> vehicles = (Collection<?>) vehiclesObject;
                vehicles.forEach(consumer);
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Unable to read siding vehicles for rails API", e);
        }
    }

    @Unique
    private static JsonArray jme$getVehicleCarsJson(Object vehicle) {
        try {
            java.lang.reflect.Method method = vehicle.getClass().getMethod("getVehicleCarsAndPositions");
            method.setAccessible(true);
            Object carsAndPositions = method.invoke(vehicle);
            if (carsAndPositions instanceof Iterable<?>) {
                JsonArray carsJson = new JsonArray();
                for (Object pair : (Iterable<?>) carsAndPositions) {
                    java.lang.reflect.Method rightMethod = pair.getClass().getMethod("right");
                    Object bogies = rightMethod.invoke(pair);
                    if (bogies instanceof Iterable<?>) {
                        double sumX = 0, sumY = 0, sumZ = 0;
                        int count = 0;
                        for (Object bogiePair : (Iterable<?>) bogies) {
                            java.lang.reflect.Method rightBogieMethod = bogiePair.getClass().getMethod("right");
                            Object vector = rightBogieMethod.invoke(bogiePair);
                            if (vector != null) {
                                try {
                                    java.lang.reflect.Field xField = vector.getClass().getField("x");
                                    java.lang.reflect.Field yField = vector.getClass().getField("y");
                                    java.lang.reflect.Field zField = vector.getClass().getField("z");
                                    sumX += xField.getDouble(vector);
                                    sumY += yField.getDouble(vector);
                                    sumZ += zField.getDouble(vector);
                                    count++;
                                } catch (Exception e) {
                                    // Maybe the fields are private or methods
                                    java.lang.reflect.Method xMethod = vector.getClass().getMethod("x");
                                    java.lang.reflect.Method yMethod = vector.getClass().getMethod("y");
                                    java.lang.reflect.Method zMethod = vector.getClass().getMethod("z");
                                    sumX += (double) xMethod.invoke(vector);
                                    sumY += (double) yMethod.invoke(vector);
                                    sumZ += (double) zMethod.invoke(vector);
                                    count++;
                                }
                            }
                        }
                        if (count > 0) {
                            JsonObject carJson = new JsonObject();
                            carJson.add("position", jme$createPositionJson(sumX / count, sumY / count, sumZ / count));
                            carsJson.add(carJson);
                        }
                    }
                }
                return carsJson;
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Unique
    private static void jme$addVehicleLineInfo(JsonObject vehicleJson, Object vehicleExtraData) {
        try {
            final String lineName = jme$invokeStringNoArg(vehicleExtraData, "getThisRouteName");
            if (!lineName.isEmpty()) {
                vehicleJson.addProperty("lineName", lineName);
            }
            final String lineNumber = jme$invokeStringNoArg(vehicleExtraData, "getThisRouteNumber");
            if (!lineNumber.isEmpty()) {
                vehicleJson.addProperty("lineNumber", lineNumber);
            }
            final String destination = jme$invokeStringNoArg(vehicleExtraData, "getThisRouteDestination");
            if (!destination.isEmpty()) {
                vehicleJson.addProperty("destination", destination);
            }
            final String nextStation = jme$invokeStringNoArg(vehicleExtraData, "getNextStationName");
            if (!nextStation.isEmpty()) {
                vehicleJson.addProperty("nextStation", nextStation);
            }
            final Object colorValue = jme$invokeNoArg(vehicleExtraData, "getThisRouteColor");
            if (colorValue instanceof Number) {
                vehicleJson.addProperty("lineColor", ((Number) colorValue).intValue() & 0xFFFFFF);
            }
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static String jme$invokeStringNoArg(Object object, String... methodNames) {
        final Object value = jme$invokeNoArg(object, methodNames);
        return value == null ? "" : String.valueOf(value);
    }

    /** Compass bearing in degrees (0 = north toward -Z, 90 = east toward +X), -1 if it cannot be determined. */
    @Unique
    private static double jme$computePathBearing(PathData pathData) {
        if (pathData == null) {
            return -1;
        }
        try {
            final Position position1 = pathData.getOrderedPosition1();
            final Position position2 = pathData.getOrderedPosition2();
            if (position1 == null || position2 == null) {
                return -1;
            }
            final double deltaX = position2.getX() - position1.getX();
            final double deltaZ = position2.getZ() - position1.getZ();
            final double bearing = Math.toDegrees(Math.atan2(deltaX, -deltaZ));
            return bearing < 0 ? bearing + 360 : bearing;
        } catch (Exception ignored) {
            return -1;
        }
    }

    @Unique
    private static JsonObject jme$getVehicleHeadPosition(Object vehicle) {
        try {
            Method method = jme$getHeadPositionAndTiltAngleMethod;
            if (method == null) {
                method = vehicle.getClass().getMethod("getHeadPositionAndTiltAngle");
                method.setAccessible(true);
                jme$getHeadPositionAndTiltAngleMethod = method;
            }
            final Object positionAndTilt = method.invoke(vehicle);
            return jme$getPositionFromPositionAndTilt(positionAndTilt);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static JsonArray jme$getVehicleCars(Object vehicle) {
        final JsonArray carsJson = new JsonArray();
        try {
            Method method = jme$getVehicleCarsAndPositionsMethod;
            if (method == null) {
                method = vehicle.getClass().getMethod("getVehicleCarsAndPositions");
                method.setAccessible(true);
                jme$getVehicleCarsAndPositionsMethod = method;
            }
            final Object carsAndPositionsObject = method.invoke(vehicle);
            if (!(carsAndPositionsObject instanceof java.util.List<?>)) {
                return carsJson;
            }

            int index = 0;
            for (final Object carAndPosition : (java.util.List<?>) carsAndPositionsObject) {
                final JsonObject carJson = new JsonObject();
                carJson.addProperty("index", index++);

                final Object carObject = jme$invokeNoArg(carAndPosition, "left", "first", "key");
                final Object bogiesObject = jme$invokeNoArg(carAndPosition, "right", "second", "value");
                if (!(bogiesObject instanceof java.util.List<?>)) {
                    continue;
                }

                final JsonObject carPosition = jme$getAverageBogiePosition((java.util.List<?>) bogiesObject);
                if (carPosition == null) {
                    continue;
                }
                carJson.add("position", carPosition);

                final Double length = jme$invokeDoubleNoArg(carObject, "getLength");
                if (length != null) {
                    carJson.addProperty("length", length);
                }
                final Double width = jme$invokeDoubleNoArg(carObject, "getWidth");
                if (width != null) {
                    carJson.addProperty("width", width);
                }

                carsJson.add(carJson);
            }
        } catch (Exception ignored) {
            // Ignore malformed vehicle car reflection; the rail endpoint remains functional.
        }
        return carsJson;
    }

    @Unique
    private static JsonObject jme$getAverageBogiePosition(java.util.List<?> bogies) {
        double x = 0;
        double y = 0;
        double z = 0;
        int count = 0;

        for (final Object bogie : bogies) {
            final Object positionAndTilt1 = jme$invokeNoArg(bogie, "positionAndTiltAngle1", "first", "left");
            final Object positionAndTilt2 = jme$invokeNoArg(bogie, "positionAndTiltAngle2", "second", "right");

            final JsonObject position1 = jme$getPositionFromPositionAndTilt(positionAndTilt1);
            final JsonObject position2 = jme$getPositionFromPositionAndTilt(positionAndTilt2);
            if (position1 != null) {
                x += position1.get("x").getAsDouble();
                y += position1.get("y").getAsDouble();
                z += position1.get("z").getAsDouble();
                count++;
            }
            if (position2 != null) {
                x += position2.get("x").getAsDouble();
                y += position2.get("y").getAsDouble();
                z += position2.get("z").getAsDouble();
                count++;
            }
        }

        return count <= 0 ? null : jme$createPositionJson(x / count, y / count, z / count);
    }

    @Unique
    private static JsonObject jme$getPositionFromPositionAndTilt(Object positionAndTilt) {
        if (positionAndTilt == null) {
            return null;
        }

        final Object vector = jme$invokeNoArg(positionAndTilt, "position", "left", "first");
        if (vector == null) {
            return null;
        }

        final Double x = jme$invokeDoubleNoArg(vector, "x");
        final Double y = jme$invokeDoubleNoArg(vector, "y");
        final Double z = jme$invokeDoubleNoArg(vector, "z");
        if (x == null || y == null || z == null) {
            return null;
        }

        return jme$createPositionJson(x, y, z);
    }

    @Unique
    private static Object jme$invokeNoArg(Object object, String... methodNames) {
        if (object == null) {
            return null;
        }

        for (final String methodName : methodNames) {
            try {
                final Method method = object.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(object);
            } catch (Exception ignored) {
                // Try next method name.
            }
        }
        return null;
    }

    @Unique
    private static Double jme$invokeDoubleNoArg(Object object, String methodName) {
        final Object value = jme$invokeNoArg(object, methodName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    @Unique
    private static Object jme$getVehicleExtraData(Object vehicle) throws Exception {
        Field field = jme$vehicleExtraDataField;
        if (field == null) {
            field = jme$findField(vehicle.getClass(), "vehicleExtraData");
            field.setAccessible(true);
            jme$vehicleExtraDataField = field;
        }
        return field.get(vehicle);
    }

    @Unique
    private static Object jme$getImmutablePath(Object vehicleExtraData) throws Exception {
        Field field = jme$immutablePathField;
        if (field == null) {
            field = jme$findField(vehicleExtraData.getClass(), "immutablePath");
            field.setAccessible(true);
            jme$immutablePathField = field;
        }
        return field.get(vehicleExtraData);
    }

    @Unique
    private static Object jme$getVehicleHexId(Object vehicle) throws Exception {
        Method method = jme$getHexIdMethod;
        if (method == null) {
            method = vehicle.getClass().getMethod("getHexId");
            method.setAccessible(true);
            jme$getHexIdMethod = method;
        }
        return method.invoke(vehicle);
    }

    @Unique
    private static long jme$getThisRouteId(Object vehicleExtraData) throws Exception {
        Method method = jme$getThisRouteIdMethod;
        if (method == null) {
            method = vehicleExtraData.getClass().getMethod("getThisRouteId");
            method.setAccessible(true);
            jme$getThisRouteIdMethod = method;
        }
        return (long) method.invoke(vehicleExtraData);
    }

    @Unique
    private static double jme$getVehicleSpeed(Object vehicle) throws Exception {
        Field field = jme$vehicleSpeedField;
        if (field == null) {
            field = jme$findField(vehicle.getClass(), "speed");
            field.setAccessible(true);
            jme$vehicleSpeedField = field;
        }
        return field.getDouble(vehicle);
    }

    @Unique
    private static double jme$getVehicleRailProgress(Object vehicle) throws Exception {
        Field field = jme$vehicleRailProgressField;
        if (field == null) {
            field = jme$findField(vehicle.getClass(), "railProgress");
            field.setAccessible(true);
            jme$vehicleRailProgressField = field;
        }
        return field.getDouble(vehicle);
    }

    @Unique
    private static Field jme$findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> check = clazz;
        while (check != null) {
            try {
                return check.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                check = check.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

}
