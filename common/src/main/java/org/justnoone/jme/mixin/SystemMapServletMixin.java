package org.justnoone.jme.mixin;

import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Mixin(value = SystemMapServlet.class, remap = false)
public abstract class SystemMapServletMixin {

    @Unique
    private static final long JME_RAILS_REFRESH_MILLIS = 15000;
    @Unique
    private static final long JME_VEHICLES_REFRESH_MILLIS = 500;
    @Unique
    private static final Map<String, JsonObject> jme$railsSnapshotByDimension = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, JsonObject> jme$vehiclesSnapshotByDimension = new ConcurrentHashMap<>();
    @Unique
    private static final Set<String> jme$inFlightRailsDimensions = ConcurrentHashMap.newKeySet();
    @Unique
    private static final Set<String> jme$inFlightVehiclesDimensions = ConcurrentHashMap.newKeySet();
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

    @Inject(method = "getContent", at = @At("HEAD"), cancellable = true)
    private void jme$onGetContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<JsonObject> sendResponse, CallbackInfo ci) {
        if (endpoint.equals("rails")) {
            final String dimension = simulator.dimension;
            final String modeRaw = parameters == null ? "" : parameters.getOrDefault("mode", "");
            final String mode = modeRaw == null ? "" : modeRaw.trim().toLowerCase(Locale.ENGLISH);

            final boolean railOverlayEnabled = JmeConfig.dashboardRailOverlayMode() != JmeConfig.DashboardRailOverlayMode.OFF;
            final boolean includeRails = railOverlayEnabled && !"vehicles".equals(mode);
            final boolean includeVehicles = !"rails".equals(mode);

            if (includeRails) {
                jme$submitRailsBuildIfStale(dimension, simulator);
            }
            if (includeVehicles) {
                jme$submitVehiclesBuildIfStale(dimension, simulator);
            }

            if ("rails".equals(mode)) {
                if (railOverlayEnabled) {
                    final JsonObject railsSnapshot = jme$railsSnapshotByDimension.get(dimension);
                    sendResponse.accept(railsSnapshot == null ? jme$createEmptyRailsSnapshot() : railsSnapshot);
                } else {
                    sendResponse.accept(jme$createEmptyRailsSnapshot());
                }
            } else if ("vehicles".equals(mode)) {
                final JsonObject vehiclesSnapshot = jme$vehiclesSnapshotByDimension.get(dimension);
                sendResponse.accept(vehiclesSnapshot == null ? jme$createEmptyVehiclesSnapshot() : vehiclesSnapshot);
            } else {
                sendResponse.accept(jme$createFullRailsResponse(dimension));
            }
            ci.cancel();
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
        return response;
    }

    @Unique
    private static JsonObject jme$createEmptyVehiclesSnapshot() {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("jmeRailOverlayMode", JmeConfig.dashboardRailOverlayMode().name().toLowerCase(Locale.ENGLISH));
        response.addProperty("jmeRailCullMaxPerCell", JmeConfig.dashboardRailOverlayCullMaxPerCell());
        response.add("rails", new JsonArray());
        response.add("vehicles", new JsonArray());
        return response;
    }

    @Unique
    private static JsonObject jme$createFullRailsResponse(String dimension) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        response.addProperty("jmeRailOverlayMode", JmeConfig.dashboardRailOverlayMode().name().toLowerCase(Locale.ENGLISH));
        response.addProperty("jmeRailCullMaxPerCell", JmeConfig.dashboardRailOverlayCullMaxPerCell());

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
        if (railOverlayEnabled) {
            SystemMapOverlayCacheStore.mergeLiveRails(dimension, liveRails, railsSnapshotTime);
            response.add("rails", SystemMapOverlayCacheStore.getRailsForResponse(dimension));
        } else {
            response.add("rails", new JsonArray());
        }

        SystemMapOverlayCacheStore.mergeLiveVehicles(dimension, liveVehicles, vehiclesSnapshotTime);
        response.add("vehicles", SystemMapOverlayCacheStore.getVehiclesForResponse(dimension));
        return response;
    }

    @Unique
    private static void jme$patchRoutesArrayRouteTypes(JsonObject root) {
        final JsonObject container = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
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
                routeObject.addProperty("type", overrideRouteType);
            }
        }
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
        // Build the snapshot on the simulator thread to avoid concurrent modification crashes when update_data triggers sync().
        simulator.run(() -> {
            try {
                jme$railsSnapshotByDimension.put(dimension, jme$getRailsSnapshot(simulator));
                jme$lastRailsBuildMillis.put(dimension, System.currentTimeMillis());
            } catch (Exception e) {
                Main.LOGGER.warn("Rails snapshot build failed for dimension {}", dimension, e);
            } finally {
                jme$inFlightRailsDimensions.remove(dimension);
            }
        });
    }

    @Unique
    private static void jme$submitVehiclesBuild(String dimension, Simulator simulator) {
        if (!jme$inFlightVehiclesDimensions.add(dimension)) {
            return;
        }
        simulator.run(() -> {
            try {
                jme$vehiclesSnapshotByDimension.put(dimension, jme$getVehiclesSnapshot(simulator));
                jme$lastVehiclesBuildMillis.put(dimension, System.currentTimeMillis());
            } catch (Exception e) {
                Main.LOGGER.warn("Vehicles snapshot build failed for dimension {}", dimension, e);
            } finally {
                jme$inFlightVehiclesDimensions.remove(dimension);
            }
        });
    }

    @Unique
    private static JsonObject jme$getRailsSnapshot(Simulator simulator) {
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
        final Object2ObjectLinkedOpenHashMap<String, org.mtr.core.data.Rail> railsById = new Object2ObjectLinkedOpenHashMap<>();
        final Object2ObjectAVLTreeMap<Position, ObjectArrayList<JsonObject>> trainsByNode = new Object2ObjectAVLTreeMap<>();
        final Object2ObjectAVLTreeMap<Position, ObjectArrayList<String>> routesByNode = new Object2ObjectAVLTreeMap<>();

        try {
            jme$populateRailsById(simulator, railsById);

            simulator.routes.forEach(route -> route.getRoutePlatforms().forEach(routePlatformData -> {
                if (routePlatformData.platform != null) {
                    final Position[] platformPositions = jme$parsePositionsFromHexId(routePlatformData.platform.getHexId());
                    final Position position1 = platformPositions[0];
                    final Position position2 = platformPositions[1];
                    jme$addDistinct(routesByNode, position1, route.getHexId());
                    jme$addDistinct(routesByNode, position2, route.getHexId());
                }
            }));

            if (!jme$disableTrainExtraction) {
                simulator.sidings.forEach(siding -> jme$forEachSidingVehicle(siding, vehicle -> {
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

        response.add("rails", railsJson);
        response.add("vehicles", new JsonArray());
        return response;
    }

    @Unique
    private static JsonObject jme$getVehiclesSnapshot(Simulator simulator) {
        final JsonObject response = new JsonObject();
        response.addProperty("cachedResponseTime", System.currentTimeMillis());
        final JsonArray vehiclesJson = new JsonArray();

        try {
            if (!jme$disableTrainExtraction) {
                simulator.sidings.forEach(siding -> jme$forEachSidingVehicle(siding, vehicle -> {
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
                        vehicleJson.addProperty("routeId", routeId);
                        final JsonObject headPosition = jme$getVehicleHeadPosition(vehicle);
                        if (headPosition != null) {
                            vehicleJson.add("position", headPosition);
                        } else {
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
        } catch (Exception e) {
            Main.LOGGER.error("Error in getVehiclesSnapshot (JME Mixin)", e);
        }

        response.add("rails", new JsonArray());
        response.add("vehicles", vehiclesJson);
        return response;
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
