package org.justnoone.jme.fabric.bluemap;

import com.flowpowered.math.vector.Vector3d;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.MagicRailSpeedColor;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Optional BlueMap integration (Fabric).
 * <p>
 * When enabled in {@code config/jme.json} and BlueMap is installed, MAGIC will render rails and trains as BlueMap marker-sets.
 */
public final class BlueMapIntegration {

    private static final Logger LOGGER = LogManager.getLogger("MAGIC-BlueMap");

    private static final String MTR_API_BASE = "http://127.0.0.1:8888/mtr/api/map/";

    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-BlueMap-Fetch");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean initialized;
    private static volatile BlueMapAPI api;

    private static final Map<Integer, DimensionState> STATE_BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<MarkerSetCacheKey, List<MarkerSet>> MARKER_SETS_BY_WORLD_AND_ID = new ConcurrentHashMap<>();

    private BlueMapIntegration() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        BlueMapAPI.onEnable(enabledApi -> {
            api = enabledApi;
            MARKER_SETS_BY_WORLD_AND_ID.clear();
            STATE_BY_DIMENSION.values().forEach(DimensionState::forceRefresh);
            if (JmeConfig.bluemapEnabled()) {
                LOGGER.info("BlueMap API detected, enabling overlays");
            } else {
                LOGGER.info("BlueMap API detected, overlays available (config disabled)");
            }
        });
        BlueMapAPI.onDisable(disabledApi -> {
            if (api == disabledApi) {
                api = null;
                MARKER_SETS_BY_WORLD_AND_ID.clear();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(BlueMapIntegration::tick);
    }

    private static void tick(MinecraftServer currentServer) {
        if (!JmeConfig.bluemapEnabled()) {
            return;
        }

        final BlueMapAPI currentApi = api;
        if (currentApi == null) {
            return;
        }

        final int[] dimensions = JmeConfig.bluemapDimensions();
        if (dimensions == null || dimensions.length == 0) {
            return;
        }

        for (final int dimension : dimensions) {
            final DimensionState state = STATE_BY_DIMENSION.computeIfAbsent(dimension, ignored -> new DimensionState());
            state.maybeUpdate(currentServer, currentApi, dimension);
        }
    }

    private static final class DimensionState {
        private volatile boolean railsInFlight;
        private volatile boolean trainsInFlight;
        private volatile boolean routesInFlight;

        private volatile long lastRailsRequestMillis;
        private volatile long lastTrainsRequestMillis;
        private volatile long lastRoutesRequestMillis;

        private volatile long lastRailsSnapshotMillis;
        private volatile long lastTrainsSnapshotMillis;

        private final Map<String, RouteInfo> routesById = new ConcurrentHashMap<>();

        void forceRefresh() {
            lastRailsRequestMillis = 0;
            lastTrainsRequestMillis = 0;
            lastRoutesRequestMillis = 0;
            lastRailsSnapshotMillis = 0;
            lastTrainsSnapshotMillis = 0;
        }

        void maybeUpdate(MinecraftServer server, BlueMapAPI api, int dimension) {
            final long now = System.currentTimeMillis();
            final ServerWorld world = getServerWorld(server, dimension);
            if (world == null) {
                return;
            }

            if (JmeConfig.bluemapShowTrains()) {
                final int routesRefreshMs = JmeConfig.bluemapRoutesRefreshMs();
                if (!routesInFlight && now - lastRoutesRequestMillis >= routesRefreshMs) {
                    routesInFlight = true;
                    lastRoutesRequestMillis = now;
                    FETCH_EXECUTOR.execute(() -> {
                        try {
                            final JsonObject data = fetchEndpointData("stations-and-routes?dimension=" + dimension);
                            if (data != null) {
                                final Map<String, RouteInfo> routes = parseRoutes(data);
                                if (!routes.isEmpty()) {
                                    routesById.clear();
                                    routesById.putAll(routes);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to refresh routes for dimension {}", dimension, e);
                        } finally {
                            routesInFlight = false;
                        }
                    });
                }
            }

            if (JmeConfig.bluemapShowRails()) {
                final int railsRefreshMs = JmeConfig.bluemapRailsRefreshMs();
                if (!railsInFlight && now - lastRailsRequestMillis >= railsRefreshMs) {
                    railsInFlight = true;
                    lastRailsRequestMillis = now;
                    FETCH_EXECUTOR.execute(() -> {
                        try {
                            final JsonObject data = fetchEndpointData("rails?dimension=" + dimension + "&mode=rails&format=bluemap");
                            if (data != null) {
                                final long snapshotTime = getLong(data, "cachedResponseTime", 0);
                                if (snapshotTime != 0 && snapshotTime == lastRailsSnapshotMillis) {
                                    return;
                                }

                                final List<RailEntry> rails = parseRails(data);
                                lastRailsSnapshotMillis = snapshotTime;
                                applyRails(api, world, dimension, rails);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to refresh rails for dimension {}", dimension, e);
                        } finally {
                            railsInFlight = false;
                        }
                    });
                }
            }

            if (JmeConfig.bluemapShowTrains()) {
                final int trainsRefreshMs = JmeConfig.bluemapTrainsRefreshMs();
                if (!trainsInFlight && now - lastTrainsRequestMillis >= trainsRefreshMs) {
                    trainsInFlight = true;
                    lastTrainsRequestMillis = now;
                    FETCH_EXECUTOR.execute(() -> {
                        try {
                            final JsonObject data = fetchEndpointData("rails?dimension=" + dimension + "&mode=vehicles&format=bluemap");
                            if (data != null) {
                                final long snapshotTime = getLong(data, "cachedResponseTime", 0);
                                if (snapshotTime != 0 && snapshotTime == lastTrainsSnapshotMillis) {
                                    return;
                                }

                                final List<VehicleEntry> vehicles = parseVehicles(data);
                                lastTrainsSnapshotMillis = snapshotTime;
                                applyVehicles(api, world, dimension, vehicles, routesById);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to refresh vehicles for dimension {}", dimension, e);
                        } finally {
                            trainsInFlight = false;
                        }
                    });
                }
            }
        }
    }

    private static Optional<BlueMapWorld> getBlueMapWorld(BlueMapAPI api, ServerWorld world) {
        if (api == null || world == null) {
            return Optional.empty();
        }
        try {
            return api.getWorld(world);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static ServerWorld getServerWorld(MinecraftServer server, int dimension) {
        if (server == null) {
            return null;
        }
        try {
            switch (dimension) {
                case 0:
                    return server.getWorld(World.OVERWORLD);
                case 1:
                    return server.getWorld(World.NETHER);
                case 2:
                    return server.getWorld(World.END);
                default:
                    return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void applyRails(BlueMapAPI api, ServerWorld world, int dimension, List<RailEntry> rails) {
        final Optional<BlueMapWorld> blueWorld = getBlueMapWorld(api, world);
        if (!blueWorld.isPresent()) {
            return;
        }

        if (JmeConfig.bluemapRailLayerSpeedThreshold()) {
            getMarkerSets(blueWorld.get(), "jme-rails-orm-d" + dimension, "MAGIC Rails (ORM)", false, 10)
                    .forEach(set -> upsertRailsSpeedThreshold(set, rails));
        }

        if (JmeConfig.bluemapRailLayerSpeedGradient()) {
            getMarkerSets(blueWorld.get(), "jme-rails-gradient-d" + dimension, "MAGIC Rails (Gradient)", true, 11)
                    .forEach(set -> upsertRailsSpeedGradient(set, rails));
        }

        if (JmeConfig.bluemapRailLayerSignals()) {
            getMarkerSets(blueWorld.get(), "jme-rails-signals-d" + dimension, "MAGIC Rails (Signals)", false, 12)
                    .forEach(set -> rebuildRailsSignals(set, rails));
        }

        if (JmeConfig.bluemapRailLayerSpeedLabels()) {
            getMarkerSets(blueWorld.get(), "jme-rails-speeds-d" + dimension, "MAGIC Rails (Speed Labels)", false, 13)
                    .forEach(set -> upsertRailsSpeedLabels(set, rails));
        }
    }

    private static void applyVehicles(BlueMapAPI api, ServerWorld world, int dimension, List<VehicleEntry> vehicles, Map<String, RouteInfo> routesById) {
        final Optional<BlueMapWorld> blueWorld = getBlueMapWorld(api, world);
        if (!blueWorld.isPresent()) {
            return;
        }

        getMarkerSets(blueWorld.get(), "jme-trains-d" + dimension, "MAGIC Trains", false, 5)
                .forEach(set -> upsertVehicles(set, vehicles, routesById));
    }

    private static List<MarkerSet> getMarkerSets(BlueMapWorld world, String id, String label, boolean defaultHidden, int sorting) {
        if (world == null || id == null || id.isEmpty()) {
            return new ArrayList<>();
        }

        final MarkerSetCacheKey key = new MarkerSetCacheKey(world.getId(), id);
        final List<MarkerSet> cached = MARKER_SETS_BY_WORLD_AND_ID.get(key);
        if (cached != null) {
            cached.forEach(set -> {
                if (set != null) {
                    set.setLabel(label);
                    set.setDefaultHidden(defaultHidden);
                    set.setSorting(sorting);
                }
            });
            return cached;
        }

        final List<MarkerSet> built = new ArrayList<>();
        for (final BlueMapMap map : world.getMaps()) {
            if (map == null) {
                continue;
            }

            final Map<String, MarkerSet> markerSets = map.getMarkerSets();
            MarkerSet set = markerSets.get(id);
            if (set == null) {
                set = MarkerSet.builder()
                        .label(label)
                        .toggleable(true)
                        .defaultHidden(defaultHidden)
                        .sorting(sorting)
                        .build();
                markerSets.put(id, set);
            } else {
                set.setLabel(label);
                set.setDefaultHidden(defaultHidden);
                set.setSorting(sorting);
            }

            built.add(set);
        }

        MARKER_SETS_BY_WORLD_AND_ID.put(key, built);
        return built;
    }

    private static void upsertRailsSpeedThreshold(MarkerSet set, List<RailEntry> rails) {
        final Map<String, Marker> markers = set.getMarkers();
        final Set<String> seen = new HashSet<>();

        for (final RailEntry rail : rails) {
            if (rail == null || rail.points.size() < 2) {
                continue;
            }

            final int speed = rail.speedKmh;
            final Color color = speed > 200 ? new Color(0xFFFF0000) : new Color(0xFFFF8800);

            final String key = "r_" + rail.id;
            seen.add(key);

            final Marker existing = markers.get(key);
            if (existing instanceof LineMarker) {
                final LineMarker marker = (LineMarker) existing;
                marker.setLabel("Rail " + rail.id);
                marker.setLine(new Line(rail.points));
                marker.setLineWidth(2);
                marker.setLineColor(color);
                marker.setDepthTestEnabled(false);
            } else {
                final LineMarker marker = new LineMarker("Rail " + rail.id, new Line(rail.points));
                marker.setLineWidth(2);
                marker.setLineColor(color);
                marker.setDepthTestEnabled(false);
                markers.put(key, marker);
            }
        }

        markers.keySet().removeIf(key -> key != null && key.startsWith("r_") && !seen.contains(key));
    }

    private static void upsertRailsSpeedGradient(MarkerSet set, List<RailEntry> rails) {
        final Map<String, Marker> markers = set.getMarkers();
        final Set<String> seen = new HashSet<>();

        for (final RailEntry rail : rails) {
            if (rail == null || rail.points.size() < 2) {
                continue;
            }

            final int argb = MagicRailSpeedColor.colorForSpeed(rail.speedKmh);
            final String key = "r_" + rail.id;
            seen.add(key);

            final Marker existing = markers.get(key);
            if (existing instanceof LineMarker) {
                final LineMarker marker = (LineMarker) existing;
                marker.setLabel("Rail " + rail.id);
                marker.setLine(new Line(rail.points));
                marker.setLineWidth(2);
                marker.setLineColor(new Color(argb));
                marker.setDepthTestEnabled(false);
            } else {
                final LineMarker marker = new LineMarker("Rail " + rail.id, new Line(rail.points));
                marker.setLineWidth(2);
                marker.setLineColor(new Color(argb));
                marker.setDepthTestEnabled(false);
                markers.put(key, marker);
            }
        }

        markers.keySet().removeIf(key -> key != null && key.startsWith("r_") && !seen.contains(key));
    }

    private static void rebuildRailsSignals(MarkerSet set, List<RailEntry> rails) {
        final Map<String, Marker> markers = set.getMarkers();
        markers.clear();

        final boolean dashedMulticolor = JmeConfig.bluemapSignalsDashedMulticolor();

        for (final RailEntry rail : rails) {
            if (rail == null || rail.points.size() < 2) {
                continue;
            }

            final List<Integer> colors = rail.signalColorsArgb;
            if (colors.isEmpty()) {
                continue;
            }

            if (colors.size() == 1 || !dashedMulticolor) {
                final LineMarker marker = new LineMarker("Signal " + rail.id, new Line(rail.points));
                marker.setLineWidth(3);
                marker.setLineColor(new Color(colors.get(0)));
                marker.setDepthTestEnabled(false);
                markers.put("s_" + rail.id, marker);
                continue;
            }

            // Fake a dashed multi-color stroke by splitting into short line segments and alternating colors.
            final int dashOnSegments = 2;
            final int dashOffSegments = 1;
            final int cycle = dashOnSegments + dashOffSegments;

            int dashIndex = 0;
            int segmentIndex = 0;
            while (segmentIndex < rail.points.size() - 1) {
                // Skip off-segments to create gaps.
                if ((segmentIndex % cycle) >= dashOnSegments) {
                    segmentIndex++;
                    continue;
                }

                final int colorIndex = dashIndex % colors.size();
                final int argb = colors.get(colorIndex);

                final List<Vector3d> dashPoints = new ArrayList<>();
                dashPoints.add(rail.points.get(segmentIndex));

                while (segmentIndex < rail.points.size() - 1 && (segmentIndex % cycle) < dashOnSegments) {
                    dashPoints.add(rail.points.get(segmentIndex + 1));
                    segmentIndex++;
                }

                if (dashPoints.size() >= 2) {
                    final LineMarker marker = new LineMarker("Signal " + rail.id, new Line(dashPoints));
                    marker.setLineWidth(3);
                    marker.setLineColor(new Color(argb));
                    marker.setDepthTestEnabled(false);
                    markers.put("s_" + rail.id + "_" + dashIndex, marker);
                }

                dashIndex++;
            }
        }
    }

    private static void upsertRailsSpeedLabels(MarkerSet set, List<RailEntry> rails) {
        final Map<String, Marker> markers = set.getMarkers();
        final Set<String> seen = new HashSet<>();

        for (final RailEntry rail : rails) {
            if (rail == null || rail.points.isEmpty()) {
                continue;
            }

            final int idx = rail.points.size() / 2;
            final Vector3d pos = rail.points.get(Math.max(0, Math.min(rail.points.size() - 1, idx)));
            final String label = JmeConfig.formatSpeedLabel(rail.speedKmh);
            final String html = "<div style=\""
                    + "font: 600 12px/1.1 sans-serif;"
                    + "color: #ffffff;"
                    + "background: rgba(0,0,0,0.68);"
                    + "border: 1px solid rgba(255,255,255,0.18);"
                    + "border-radius: 4px;"
                    + "padding: 1px 4px;"
                    + "white-space: nowrap;"
                    + "\">" + escapeHtml(label) + "</div>";

            final String key = "sp_" + rail.id;
            seen.add(key);

            final Marker existing = markers.get(key);
            if (existing instanceof HtmlMarker) {
                final HtmlMarker marker = (HtmlMarker) existing;
                marker.setLabel(label);
                marker.setPosition(pos);
                marker.setHtml(html);
            } else {
                final HtmlMarker marker = new HtmlMarker(label, pos, html);
                markers.put(key, marker);
            }
        }

        markers.keySet().removeIf(key -> key != null && key.startsWith("sp_") && !seen.contains(key));
    }

    private static void upsertVehicles(MarkerSet set, List<VehicleEntry> vehicles, Map<String, RouteInfo> routesById) {
        final Map<String, Marker> markers = set.getMarkers();
        final Set<String> seen = new HashSet<>();

        for (final VehicleEntry vehicle : vehicles) {
            if (vehicle == null) {
                continue;
            }
            final String key = "v_" + vehicle.id;
            seen.add(key);

            final RouteInfo route = routesById.getOrDefault(normalizeId(vehicle.routeId), RouteInfo.UNKNOWN);
            final int colorRgb = route.colorRgb;
            final String cssColor = String.format(Locale.ROOT, "#%06X", colorRgb & 0xFFFFFF);

            final String label = route.name.isEmpty() ? ("Train " + vehicle.id) : route.name;
            final String html = "<div style=\""
                    + "width: 16px;"
                    + "height: 16px;"
                    + "border-radius: 50%;"
                    + "background: " + cssColor + ";"
                    + "border: 2px solid rgba(20,20,20,0.95);"
                    + "box-sizing: border-box;"
                    + "opacity: 0.92;"
                    + "\"></div>";

            final Marker existing = markers.get(key);
            if (existing instanceof HtmlMarker) {
                final HtmlMarker marker = (HtmlMarker) existing;
                marker.setLabel(label);
                marker.setPosition(vehicle.position);
                marker.setHtml(html);
            } else {
                final HtmlMarker marker = new HtmlMarker(label, vehicle.position, html);
                markers.put(key, marker);
            }
        }

        // Remove trains that disappeared.
        markers.keySet().removeIf(key -> key != null && key.startsWith("v_") && !seen.contains(key));
    }

    private static JsonObject fetchEndpointData(String endpointWithQuery) {
        final JsonObject payload = postJson(MTR_API_BASE + endpointWithQuery, "{}");
        if (payload == null) {
            return null;
        }
        return unwrapData(payload);
    }

    private static JsonObject postJson(String url, String jsonBody) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setDoOutput(true);
            connection.getOutputStream().write((jsonBody == null ? "{}" : jsonBody).getBytes(StandardCharsets.UTF_8));

            final int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            final String encoding = connection.getContentEncoding();
            InputStream inputStream = new BufferedInputStream(connection.getInputStream());
            if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }

            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                // JsonParser.parseReader(...) was introduced in newer Gson versions; use the legacy instance method for 1.17 compatibility.
                final JsonElement element = new JsonParser().parse(reader);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static JsonObject unwrapData(JsonObject payload) {
        if (payload == null) {
            return null;
        }
        final JsonElement data = payload.get("data");
        return data != null && data.isJsonObject() ? data.getAsJsonObject() : payload;
    }

    private static Map<String, RouteInfo> parseRoutes(JsonObject data) {
        final Map<String, RouteInfo> result = new HashMap<>();
        if (data == null) {
            return result;
        }

        final JsonElement routesElement = data.get("routes");
        if (routesElement == null || !routesElement.isJsonArray()) {
            return result;
        }

        final JsonArray routes = routesElement.getAsJsonArray();
        for (int i = 0; i < routes.size(); i++) {
            final JsonElement routeElement = routes.get(i);
            if (routeElement == null || !routeElement.isJsonObject()) {
                continue;
            }
            final JsonObject route = routeElement.getAsJsonObject();
            final String id = normalizeId(getString(route, "id", ""));
            if (id.isEmpty()) {
                continue;
            }

            final int color = (int) (parseNumeric(route.get("color"), 0x7EA6FF) & 0xFFFFFF);
            final String name = getString(route, "name", "");
            result.put(id, new RouteInfo(color & 0xFFFFFF, name));
        }

        return result;
    }

    private static List<RailEntry> parseRails(JsonObject data) {
        final List<RailEntry> result = new ArrayList<>();
        if (data == null) {
            return result;
        }

        final JsonElement railsElement = data.get("rails");
        if (railsElement == null || !railsElement.isJsonArray()) {
            return result;
        }

        final JsonArray rails = railsElement.getAsJsonArray();
        for (int i = 0; i < rails.size(); i++) {
            final JsonElement railElement = rails.get(i);
            if (railElement == null || !railElement.isJsonObject()) {
                continue;
            }
            final JsonObject rail = railElement.getAsJsonObject();

            final String id = getString(rail, "id", "");
            if (id.isEmpty()) {
                continue;
            }

            final long speed1 = getLong(rail, "speedLimit1", 0);
            final long speed2 = getLong(rail, "speedLimit2", 0);
            final int speedKmh = (int) Math.max(0, Math.min(400, Math.max(speed1, speed2)));

            final List<Vector3d> points = parsePoints(rail);
            if (points.size() < 2) {
                continue;
            }

            final List<Integer> signalColors = parseSignalColors(rail);
            result.add(new RailEntry(id, speedKmh, points, signalColors));
        }

        return result;
    }

    private static List<VehicleEntry> parseVehicles(JsonObject data) {
        final List<VehicleEntry> result = new ArrayList<>();
        if (data == null) {
            return result;
        }

        final JsonElement vehiclesElement = data.get("vehicles");
        if (vehiclesElement == null || !vehiclesElement.isJsonArray()) {
            return result;
        }

        final JsonArray vehicles = vehiclesElement.getAsJsonArray();
        for (int i = 0; i < vehicles.size(); i++) {
            final JsonElement vehicleElement = vehicles.get(i);
            if (vehicleElement == null || !vehicleElement.isJsonObject()) {
                continue;
            }
            final JsonObject vehicle = vehicleElement.getAsJsonObject();

            final String id = getString(vehicle, "id", "");
            if (id.isEmpty()) {
                continue;
            }

            final String routeId = getString(vehicle, "routeId", "");
            final Vector3d position = parsePosition(vehicle.get("position"));
            if (position == null) {
                continue;
            }

            result.add(new VehicleEntry(id, routeId, position));
        }

        return result;
    }

    private static List<Vector3d> parsePoints(JsonObject rail) {
        final List<Vector3d> points = new ArrayList<>();

        final JsonElement curvePointsElement = rail.get("curvePoints");
        if (curvePointsElement != null && curvePointsElement.isJsonArray()) {
            final JsonArray curvePoints = curvePointsElement.getAsJsonArray();
            for (int i = 0; i < curvePoints.size(); i++) {
                final Vector3d pos = parsePosition(curvePoints.get(i));
                if (pos != null) {
                    points.add(pos);
                }
            }
        }

        if (points.size() >= 2) {
            return points;
        }

        final Vector3d pos1 = parsePosition(rail.get("position1"));
        final Vector3d pos2 = parsePosition(rail.get("position2"));
        if (pos1 != null && pos2 != null) {
            points.add(pos1);
            points.add(pos2);
        }

        return points;
    }

    private static List<Integer> parseSignalColors(JsonObject rail) {
        final List<Integer> colors = new ArrayList<>();
        final JsonElement element = rail.get("signalColors");
        if (element == null || !element.isJsonArray()) {
            return colors;
        }

        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final JsonElement value = array.get(i);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                final long parsed = parseNumeric(value, Long.MIN_VALUE);
                if (parsed == Long.MIN_VALUE) {
                    continue;
                }
                colors.add(resolveSignalColorArgb((int) parsed));
            } catch (Exception ignored) {
            }
        }

        // Deduplicate while preserving order.
        final Set<Integer> seen = new HashSet<>();
        final List<Integer> deduped = new ArrayList<>();
        for (final Integer color : colors) {
            if (color == null) {
                continue;
            }
            if (seen.add(color)) {
                deduped.add(color);
            }
        }
        return deduped;
    }

    private static int resolveSignalColorArgb(int value) {
        if (value >= 0 && value <= 3) {
            switch (value) {
                case 0:
                    return 0xFFFF3B30;
                case 1:
                    return 0xFFFFD60A;
                case 2:
                    return 0xFF34C759;
                case 3:
                    return 0xFF0A84FF;
                default:
                    break;
            }
        }
        return 0xFF000000 | (value & 0xFFFFFF);
    }

    private static long parseNumeric(JsonElement element, long fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }

        final JsonPrimitive primitive = element.getAsJsonPrimitive();
        try {
            if (primitive.isNumber()) {
                return primitive.getAsLong();
            }
        } catch (Exception ignored) {
        }

        try {
            final String raw = primitive.getAsString();
            if (raw == null) {
                return fallback;
            }

            final String value = raw.trim();
            if (value.isEmpty()) {
                return fallback;
            }

            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseLong(value.substring(2), 16);
            }

            boolean maybeHex = true;
            boolean hasHexLetters = false;
            for (int i = 0; i < value.length(); i++) {
                final char c = value.charAt(i);
                if ((c >= '0' && c <= '9')) {
                    continue;
                }
                if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                    hasHexLetters = true;
                    continue;
                }
                maybeHex = false;
                break;
            }

            if (maybeHex && hasHexLetters) {
                return Long.parseLong(value, 16);
            }

            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Vector3d parsePosition(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        final JsonObject obj = element.getAsJsonObject();
        try {
            final double x = getDouble(obj, "x", Double.NaN);
            final double y = getDouble(obj, "y", Double.NaN);
            final double z = getDouble(obj, "z", Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                return null;
            }
            return new Vector3d(x, Double.isFinite(y) ? y : 0, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        try {
            final JsonElement element = obj.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        try {
            final JsonElement element = obj.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        try {
            final JsonElement element = obj.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double getDouble(JsonObject obj, String key, double fallback) {
        try {
            final JsonElement element = obj.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class RouteInfo {
        static final RouteInfo UNKNOWN = new RouteInfo(0x7EA6FF, "");

        final int colorRgb;
        final String name;

        RouteInfo(int colorRgb, String name) {
            this.colorRgb = colorRgb;
            this.name = name == null ? "" : name;
        }
    }

    private static final class RailEntry {
        final String id;
        final int speedKmh;
        final List<Vector3d> points;
        final List<Integer> signalColorsArgb;

        RailEntry(String id, int speedKmh, List<Vector3d> points, List<Integer> signalColorsArgb) {
            this.id = id;
            this.speedKmh = speedKmh;
            this.points = points;
            this.signalColorsArgb = signalColorsArgb;
        }
    }

    private static final class VehicleEntry {
        final String id;
        final String routeId;
        final Vector3d position;

        VehicleEntry(String id, String routeId, Vector3d position) {
            this.id = id;
            this.routeId = routeId;
            this.position = position;
        }
    }

    private static final class MarkerSetCacheKey {
        private final String worldId;
        private final String markerSetId;

        MarkerSetCacheKey(String worldId, String markerSetId) {
            this.worldId = worldId == null ? "" : worldId;
            this.markerSetId = markerSetId == null ? "" : markerSetId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MarkerSetCacheKey that = (MarkerSetCacheKey) o;
            return worldId.equals(that.worldId) && markerSetId.equals(that.markerSetId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + markerSetId.hashCode();
            return result;
        }
    }
}
