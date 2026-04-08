package org.justnoone.jme.bluemap;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.Jme;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.mtr.core.Main;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional BlueMap integration.
 *
 * <p>Publishes 2 layers (marker-sets) on BlueMap:
 * <ul>
 *   <li>Rails (orange, visible by default)</li>
 *   <li>Rails (speed) (speed-colored, hidden by default)</li>
 * </ul>
 *
 * <p>This class must only be loaded/called when the BlueMap mod is present.
 */
public final class BlueMapRailIntegration {

    private static final String DEFAULT_MARKER_SET_RAILS_ID = "jme_rails";
    private static final String DEFAULT_MARKER_SET_SPEEDS_ID = "jme_rails_speeds";

    private static final ExecutorService BUILD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-BlueMap-Rails");
        thread.setDaemon(true);
        return thread;
    });

    private static final ScheduledExecutorService REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "MAGIC-BlueMap-Rails-Refresh");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile MinecraftServer server;
    private static volatile BlueMapAPI api;
    private static volatile ScheduledFuture<?> refreshJob;
    private static volatile long scheduledRefreshIntervalSeconds = -1;
    private static volatile long scheduledRefreshInitialDelaySeconds = -1;
    private static final AtomicLong generation = new AtomicLong(0);
    private static volatile boolean listenersRegistered;

    private static volatile Field initMainField;
    private static volatile Field mainSimulatorsField;
    private static volatile boolean loggedSimulatorFailure;

    private static final Map<String, Long> lastSignatureByDimension = new ConcurrentHashMap<>();
    private static volatile String lastAppliedBaseMarkerSetId = DEFAULT_MARKER_SET_RAILS_ID;
    private static volatile String lastAppliedSpeedMarkerSetId = DEFAULT_MARKER_SET_SPEEDS_ID;

    private BlueMapRailIntegration() {
    }

    public static void onServerStarted(MinecraftServer serverInstance) {
        if (!FabricLoader.getInstance().isModLoaded("bluemap")) {
            return;
        }

        generation.incrementAndGet();
        server = serverInstance;
        if (!listenersRegistered) {
            listenersRegistered = true;
            BlueMapAPI.onEnable(enabledApi -> {
                api = enabledApi;
                Main.LOGGER.info("[{}] BlueMap detected, enabling rail layers", Jme.MOD_ID);
                scheduleRefresh(true);
                startRefreshJob();
            });
            BlueMapAPI.onDisable(disabledApi -> {
                if (api == disabledApi) {
                    api = null;
                }
            });
        }

        // If BlueMap is already enabled, onEnable might not fire again; pick up the instance.
        try {
            api = BlueMapAPI.getInstance().orElse(api);
        } catch (Throwable ignored) {
        }
        if (api != null) {
            scheduleRefresh(true);
            startRefreshJob();
        }
    }

    public static void onServerStopping() {
        generation.incrementAndGet();
        server = null;
        api = null;

        final ScheduledFuture<?> job = refreshJob;
        refreshJob = null;
        if (job != null) {
            job.cancel(false);
        }

        lastSignatureByDimension.clear();
        scheduledRefreshIntervalSeconds = -1;
        scheduledRefreshInitialDelaySeconds = -1;
        lastAppliedBaseMarkerSetId = DEFAULT_MARKER_SET_RAILS_ID;
        lastAppliedSpeedMarkerSetId = DEFAULT_MARKER_SET_SPEEDS_ID;
    }

    private static void startRefreshJob() {
        final long intervalSeconds = Math.max(1L, JmeConfig.blueMapRefreshIntervalSeconds());
        final long initialDelaySeconds = Math.max(0L, JmeConfig.blueMapRefreshInitialDelaySeconds());

        final ScheduledFuture<?> job = refreshJob;
        if (job != null
                && !job.isDone()
                && scheduledRefreshIntervalSeconds == intervalSeconds
                && scheduledRefreshInitialDelaySeconds == initialDelaySeconds) {
            return;
        }

        if (job != null) {
            job.cancel(false);
        }

        scheduledRefreshIntervalSeconds = intervalSeconds;
        scheduledRefreshInitialDelaySeconds = initialDelaySeconds;
        refreshJob = REFRESH_SCHEDULER.scheduleWithFixedDelay(
                () -> scheduleRefresh(false),
                initialDelaySeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private static void scheduleRefresh(boolean force) {
        final MinecraftServer serverInstance = server;
        final BlueMapAPI apiInstance = api;
        final long generationSnapshot = generation.get();
        if (serverInstance == null || apiInstance == null) {
            return;
        }

        if (!JmeConfig.blueMapEnabled()
                || (!JmeConfig.blueMapBaseLayerEnabled() && !JmeConfig.blueMapSpeedLayerEnabled())) {
            serverInstance.execute(() -> removeFromBlueMap(apiInstance, serverInstance));
            return;
        }

        BUILD_EXECUTOR.submit(() -> {
            if (generationSnapshot != generation.get()) {
                return;
            }
            try {
                final long startMillis = System.currentTimeMillis();
                final Map<String, DimensionRenderData> renderDataByDimension = new HashMap<>();

                for (final ServerWorld world : serverInstance.getWorlds()) {
                    if (world == null) {
                        continue;
                    }
                    final String dimensionId = normalizeDimensionId(Init.getWorldId(new World(world)));
                    if (dimensionId.isEmpty()) {
                        continue;
                    }

                    final Simulator simulator = getSimulatorForDimensionId(dimensionId);
                    if (simulator == null) {
                        continue;
                    }

                    final DimensionRenderData built = buildDimensionData(simulator, dimensionId, force);
                    if (built != null) {
                        renderDataByDimension.put(dimensionId, built);
                    }
                }

                if (renderDataByDimension.isEmpty()) {
                    return;
                }

                serverInstance.execute(() -> {
                    if (generationSnapshot != generation.get()) {
                        return;
                    }
                    try {
                        applyToBlueMap(apiInstance, serverInstance, renderDataByDimension);
                        Main.LOGGER.info("[{}] BlueMap rail layers updated ({} dims) in {} ms",
                                Jme.MOD_ID, renderDataByDimension.size(), System.currentTimeMillis() - startMillis);
                    } catch (Exception e) {
                        Main.LOGGER.warn("[{}] Failed to apply BlueMap rail layers", Jme.MOD_ID, e);
                    }
                });
            } catch (Exception e) {
                Main.LOGGER.warn("[{}] BlueMap rail refresh failed", Jme.MOD_ID, e);
            }
        });
    }

    private static DimensionRenderData buildDimensionData(Simulator simulator, String dimensionId, boolean force) {
        if (simulator == null) {
            return null;
        }

        final CompletableFuture<DimensionRenderData> future = new CompletableFuture<>();
        simulator.run(() -> {
            try {
                final long signature = computeRailsSignature(simulator) * 31 + computeBlueMapConfigSignature();
                final long previousSignature = lastSignatureByDimension.getOrDefault(dimensionId, Long.MIN_VALUE);
                if (!force && previousSignature == signature) {
                    future.complete(null);
                    return;
                }

                lastSignatureByDimension.put(dimensionId, signature);

                final List<RailLineData> rails = new ArrayList<>();
                simulator.rails.forEach(rail -> {
                    final RailLineData line = buildRailLineData(rail);
                    if (line != null) {
                        rails.add(line);
                    }
                });

                future.complete(new DimensionRenderData(dimensionId, rails));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Main.LOGGER.warn("[{}] Timed out building BlueMap rail data for {}", Jme.MOD_ID, dimensionId, e);
            return null;
        }
    }

    private static long computeRailsSignature(Simulator simulator) {
        // Size-based signature is O(1) and stable enough for periodic refreshes.
        long sig = 1;
        try {
            sig = sig * 31 + (simulator.rails == null ? 0 : simulator.rails.size());
        } catch (Exception ignored) {
        }
        try {
            sig = sig * 31 + (simulator.railIdMap == null ? 0 : simulator.railIdMap.size());
        } catch (Exception ignored) {
        }
        try {
            sig = sig * 31 + (simulator.positionsToRail == null ? 0 : simulator.positionsToRail.size());
        } catch (Exception ignored) {
        }
        return sig;
    }

    private static long computeBlueMapConfigSignature() {
        long sig = 1L;
        try {
            sig = sig * 31 + (JmeConfig.blueMapEnabled() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapBaseLayerEnabled() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapSpeedLayerEnabled() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapMarkerSetsToggleable() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapBaseLayerDefaultHidden() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapSpeedLayerDefaultHidden() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapMarkersListed() ? 1 : 0);
            sig = sig * 31 + (JmeConfig.blueMapDepthTestEnabled() ? 1 : 0);
            sig = sig * 31 + JmeConfig.blueMapBaseLineWidth();
            sig = sig * 31 + JmeConfig.blueMapSpeedLineWidth();
            sig = sig * 31 + JmeConfig.blueMapBaseColorRgb();
            sig = sig * 31 + JmeConfig.blueMapBasePlatformColorRgb();
            sig = sig * 31 + JmeConfig.blueMapBaseSidingColorRgb();
            sig = sig * 31 + JmeConfig.blueMapBaseTurnBackColorRgb();
            sig = sig * 31 + JmeConfig.blueMapPlatformColorRgb();
            sig = sig * 31 + (JmeConfig.blueMapPlatformRailsForceRedEnabled() ? 1 : 0);
            sig = sig * 31 + JmeConfig.blueMapHighSpeedThresholdKmh();
            sig = sig * 31 + JmeConfig.blueMapHighSpeedColorRgb();
            sig = sig * 31 + (JmeConfig.blueMapHighSpeedRailsForceRedEnabled() ? 1 : 0);
            sig = sig * 31 + safeHash(JmeConfig.blueMapBaseMarkerSetId());
            sig = sig * 31 + safeHash(JmeConfig.blueMapSpeedMarkerSetId());
            sig = sig * 31 + safeHash(JmeConfig.blueMapBaseMarkerSetLabel());
            sig = sig * 31 + safeHash(JmeConfig.blueMapSpeedMarkerSetLabel());
            sig = sig * 31 + JmeConfig.blueMapBaseMarkerSetSorting();
            sig = sig * 31 + JmeConfig.blueMapSpeedMarkerSetSorting();
            sig = sig * 31 + Double.doubleToLongBits(JmeConfig.blueMapLineYBias());
            sig = sig * 31 + JmeConfig.blueMapCurveSampleTargetPoints();
            sig = sig * 31 + Double.doubleToLongBits(JmeConfig.blueMapCurveSampleIntervalMin());
            sig = sig * 31 + Double.doubleToLongBits(JmeConfig.blueMapCurveSampleIntervalMax());

            final JmeConfig.TrackColorMode trackColorMode = JmeConfig.trackColorMode();
            sig = sig * 31 + (trackColorMode == null ? 0 : trackColorMode.ordinal());
            if (trackColorMode == JmeConfig.TrackColorMode.CUSTOM_GRADIENT) {
                final JmeConfig.TrackColorStop[] stops = JmeConfig.trackColorCustomGradientStops();
                if (stops != null) {
                    sig = sig * 31 + stops.length;
                    for (final JmeConfig.TrackColorStop stop : stops) {
                        if (stop == null) {
                            continue;
                        }
                        sig = sig * 31 + stop.speedKmh;
                        sig = sig * 31 + stop.colorArgb;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return sig;
    }

    private static int safeHash(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private static RailLineData buildRailLineData(Rail rail) {
        if (rail == null) {
            return null;
        }

        final String id = rail.getHexId();
        if (id == null || id.isEmpty()) {
            return null;
        }

        final Position[] positions = parsePositionsFromHexId(id);
        final Position position1 = positions[0];
        final Position position2 = positions[1];
        if (position1 == null || position2 == null) {
            return null;
        }

        final double speed1 = rail.getSpeedLimitMetersPerMillisecond(position1);
        final double speed2 = rail.getSpeedLimitMetersPerMillisecond(position2);
        final int speedKmh = (int) Math.round(Math.max(0, Math.max(speed1, speed2) * 3600D));
        final boolean isPlatform = rail.isPlatform();
        final boolean isSiding = rail.isSiding();
        final boolean canTurnBack = rail.canTurnBack();

        final double[] points = sampleCurvePointsFlattened(rail, position1, position2);
        if (points == null || points.length < 6) {
            return null;
        }

        return new RailLineData(id, speedKmh, isPlatform, isSiding, canTurnBack, points);
    }

    private static double[] sampleCurvePointsFlattened(Rail rail, Position position1, Position position2) {
        final ArrayList<double[]> curvePoints = new ArrayList<>();

        final double railLength = Math.max(0.001, rail.railMath.getLength());
        final int targetPoints = Math.max(4, JmeConfig.blueMapCurveSampleTargetPoints());
        final double minInterval = Math.max(1.0E-6D, JmeConfig.blueMapCurveSampleIntervalMin());
        final double maxInterval = Math.max(minInterval, JmeConfig.blueMapCurveSampleIntervalMax());
        final double interval = Math.max(minInterval, Math.min(maxInterval, railLength / targetPoints));

        addCurvePointIfDistinct(curvePoints, position1.getX(), position1.getY(), position1.getZ());
        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerX = (x1 + x3) / 2;
            final double centerY = (y1 + y2) / 2;
            final double centerZ = (z1 + z3) / 2;
            addCurvePointIfDistinct(curvePoints, centerX, centerY, centerZ);
        }, interval, 0, 0);
        addCurvePointIfDistinct(curvePoints, position2.getX(), position2.getY(), position2.getZ());

        if (curvePoints.size() == 1) {
            addCurvePointIfDistinct(curvePoints, position2.getX(), position2.getY(), position2.getZ());
        }

        if (curvePoints.size() < 2) {
            return null;
        }

        final double[] flattened = new double[curvePoints.size() * 3];
        int out = 0;
        for (final double[] point : curvePoints) {
            flattened[out++] = point[0];
            flattened[out++] = point[1];
            flattened[out++] = point[2];
        }
        return flattened;
    }

    private static void addCurvePointIfDistinct(List<double[]> points, double x, double y, double z) {
        final double[] point = new double[]{x, y, z};
        if (points.isEmpty()) {
            points.add(point);
            return;
        }

        final double[] previous = points.get(points.size() - 1);
        final double dx = previous[0] - x;
        final double dy = previous[1] - y;
        final double dz = previous[2] - z;
        if (dx * dx + dy * dy + dz * dz > 1.0E-4) {
            points.add(point);
        }
    }

    private static void applyToBlueMap(BlueMapAPI apiInstance, MinecraftServer serverInstance, Map<String, DimensionRenderData> dataByDimension) {
        if (apiInstance == null || serverInstance == null || dataByDimension == null) {
            return;
        }

        final boolean enabled = JmeConfig.blueMapEnabled();
        final boolean baseEnabled = enabled && JmeConfig.blueMapBaseLayerEnabled();
        final boolean speedEnabled = enabled && JmeConfig.blueMapSpeedLayerEnabled();

        final String baseMarkerSetId = JmeConfig.blueMapBaseMarkerSetId();
        final String speedMarkerSetId = JmeConfig.blueMapSpeedMarkerSetId();
        final String previousBaseId = lastAppliedBaseMarkerSetId;
        final String previousSpeedId = lastAppliedSpeedMarkerSetId;
        lastAppliedBaseMarkerSetId = baseMarkerSetId;
        lastAppliedSpeedMarkerSetId = speedMarkerSetId;

        for (final ServerWorld world : serverInstance.getWorlds()) {
            if (world == null) {
                continue;
            }

            final String dimensionId = normalizeDimensionId(Init.getWorldId(new World(world)));
            final DimensionRenderData renderData = dataByDimension.get(dimensionId);
            if (renderData == null) {
                continue;
            }

            final Optional<BlueMapWorld> blueMapWorldOpt = apiInstance.getWorld(world);
            if (!blueMapWorldOpt.isPresent()) {
                continue;
            }

            final MarkerSet baseRailsMarkerSet = baseEnabled ? buildBaseRailsMarkerSet(renderData.rails) : null;
            final MarkerSet speedRailsMarkerSet = speedEnabled ? buildSpeedRailsMarkerSet(renderData.rails) : null;

            for (final BlueMapMap map : blueMapWorldOpt.get().getMaps()) {
                if (map == null) {
                    continue;
                }

                if (!previousBaseId.equals(baseMarkerSetId)) {
                    map.getMarkerSets().remove(previousBaseId);
                }
                if (!previousSpeedId.equals(speedMarkerSetId)) {
                    map.getMarkerSets().remove(previousSpeedId);
                }

                if (baseEnabled && baseRailsMarkerSet != null) {
                    map.getMarkerSets().put(baseMarkerSetId, baseRailsMarkerSet);
                } else {
                    map.getMarkerSets().remove(baseMarkerSetId);
                }

                if (speedEnabled && speedRailsMarkerSet != null) {
                    map.getMarkerSets().put(speedMarkerSetId, speedRailsMarkerSet);
                } else {
                    map.getMarkerSets().remove(speedMarkerSetId);
                }
            }
        }
    }

    private static void removeFromBlueMap(BlueMapAPI apiInstance, MinecraftServer serverInstance) {
        if (apiInstance == null || serverInstance == null) {
            return;
        }

        final String baseMarkerSetId = JmeConfig.blueMapBaseMarkerSetId();
        final String speedMarkerSetId = JmeConfig.blueMapSpeedMarkerSetId();
        final String previousBaseId = lastAppliedBaseMarkerSetId;
        final String previousSpeedId = lastAppliedSpeedMarkerSetId;
        lastAppliedBaseMarkerSetId = baseMarkerSetId;
        lastAppliedSpeedMarkerSetId = speedMarkerSetId;

        for (final ServerWorld world : serverInstance.getWorlds()) {
            if (world == null) {
                continue;
            }

            final Optional<BlueMapWorld> blueMapWorldOpt = apiInstance.getWorld(world);
            if (!blueMapWorldOpt.isPresent()) {
                continue;
            }

            for (final BlueMapMap map : blueMapWorldOpt.get().getMaps()) {
                if (map == null) {
                    continue;
                }

                map.getMarkerSets().remove(baseMarkerSetId);
                map.getMarkerSets().remove(speedMarkerSetId);

                if (!previousBaseId.equals(baseMarkerSetId)) {
                    map.getMarkerSets().remove(previousBaseId);
                }
                if (!previousSpeedId.equals(speedMarkerSetId)) {
                    map.getMarkerSets().remove(previousSpeedId);
                }
            }
        }
    }

    private static MarkerSet buildBaseRailsMarkerSet(List<RailLineData> rails) {
        final boolean toggleable = JmeConfig.blueMapMarkerSetsToggleable();
        final boolean defaultHidden = JmeConfig.blueMapBaseLayerDefaultHidden();
        final int sorting = JmeConfig.blueMapBaseMarkerSetSorting();
        final String markerSetLabel = JmeConfig.blueMapBaseMarkerSetLabel();
        final boolean listed = JmeConfig.blueMapMarkersListed();
        final boolean depthTestEnabled = JmeConfig.blueMapDepthTestEnabled();
        final int lineWidth = JmeConfig.blueMapBaseLineWidth();
        final Color normalColor = colorFromArgb(0xFF000000 | (JmeConfig.blueMapBaseColorRgb() & 0xFFFFFF));
        final Color platformColor = colorFromArgb(0xFF000000 | (JmeConfig.blueMapBasePlatformColorRgb() & 0xFFFFFF));
        final Color sidingColor = colorFromArgb(0xFF000000 | (JmeConfig.blueMapBaseSidingColorRgb() & 0xFFFFFF));
        final Color turnBackColor = colorFromArgb(0xFF000000 | (JmeConfig.blueMapBaseTurnBackColorRgb() & 0xFFFFFF));

        final boolean highSpeedHighlightEnabled = JmeConfig.blueMapHighSpeedRailsForceRedEnabled();
        final int highSpeedThresholdKmh = JmeConfig.blueMapHighSpeedThresholdKmh();
        final Color highSpeedColor = colorFromArgb(0xFF000000 | (JmeConfig.blueMapHighSpeedColorRgb() & 0xFFFFFF));

        final MarkerSet markerSet = MarkerSet.builder()
                .label(markerSetLabel)
                .toggleable(toggleable)
                .defaultHidden(defaultHidden)
                .sorting(sorting)
                .build();

        if (rails == null || rails.isEmpty()) {
            return markerSet;
        }

        for (final RailLineData rail : rails) {
            if (rail == null) {
                continue;
            }
            final Line line = buildBlueMapLine(rail.points);
            if (line == null) {
                continue;
            }
            final Color lineColor;
            if (highSpeedHighlightEnabled && rail.speedKmh > highSpeedThresholdKmh) {
                lineColor = highSpeedColor;
            } else if (rail.isPlatform) {
                lineColor = platformColor;
            } else if (rail.isSiding) {
                lineColor = sidingColor;
            } else if (rail.canTurnBack) {
                lineColor = turnBackColor;
            } else {
                lineColor = normalColor;
            }
            final LineMarker marker = LineMarker.builder()
                    .label("Rail")
                    .line(line)
                    .depthTestEnabled(depthTestEnabled)
                    .lineWidth(lineWidth)
                    .lineColor(lineColor)
                    .build();
            marker.setListed(listed);
            markerSet.put("rail_" + rail.id, marker);
        }

        return markerSet;
    }

    private static MarkerSet buildSpeedRailsMarkerSet(List<RailLineData> rails) {
        final boolean toggleable = JmeConfig.blueMapMarkerSetsToggleable();
        final boolean defaultHidden = JmeConfig.blueMapSpeedLayerDefaultHidden();
        final int sorting = JmeConfig.blueMapSpeedMarkerSetSorting();
        final String markerSetLabel = JmeConfig.blueMapSpeedMarkerSetLabel();
        final boolean listed = JmeConfig.blueMapMarkersListed();
        final boolean depthTestEnabled = JmeConfig.blueMapDepthTestEnabled();
        final int lineWidth = JmeConfig.blueMapSpeedLineWidth();

        final boolean platformOverrideEnabled = JmeConfig.blueMapPlatformRailsForceRedEnabled();
        final int platformColorArgb = 0xFF000000 | (JmeConfig.blueMapPlatformColorRgb() & 0xFFFFFF);

        final MarkerSet markerSet = MarkerSet.builder()
                .label(markerSetLabel)
                .toggleable(toggleable)
                .defaultHidden(defaultHidden)
                .sorting(sorting)
                .build();

        if (rails == null || rails.isEmpty()) {
            return markerSet;
        }

        for (final RailLineData rail : rails) {
            if (rail == null) {
                continue;
            }
            final Line line = buildBlueMapLine(rail.points);
            if (line == null) {
                continue;
            }

            final int argb;
            if (platformOverrideEnabled && rail.isPlatform) {
                argb = platformColorArgb;
            } else {
                argb = MagicRailSpeedColor.colorForSpeed(rail.speedKmh);
            }
            final Color color = colorFromArgb(argb);

            final LineMarker marker = LineMarker.builder()
                    .label("Rail speed")
                    .line(line)
                    .depthTestEnabled(depthTestEnabled)
                    .lineWidth(lineWidth)
                    .lineColor(color)
                    .build();
            marker.setListed(listed);
            markerSet.put("rail_speed_" + rail.id, marker);
        }

        return markerSet;
    }

    private static Line buildBlueMapLine(double[] flattenedPoints) {
        if (flattenedPoints == null || flattenedPoints.length < 6) {
            return null;
        }

        final double yBias = JmeConfig.blueMapLineYBias();
        final Line.Builder builder = Line.builder();
        for (int i = 0; i + 2 < flattenedPoints.length; i += 3) {
            // Slight Y-offset avoids z-fighting with terrain/blocks when depth-test is enabled elsewhere.
            builder.addPoint(new Vector3d(flattenedPoints[i], flattenedPoints[i + 1] + yBias, flattenedPoints[i + 2]));
        }
        return builder.build();
    }

    private static Color colorFromArgb(int argb) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >>> 16) & 0xFF;
        final int g = (argb >>> 8) & 0xFF;
        final int b = argb & 0xFF;
        return new Color(r, g, b, a / 255F);
    }

    private static Simulator getSimulatorForDimensionId(String normalizedWorldId) {
        if (normalizedWorldId == null || normalizedWorldId.isEmpty()) {
            return null;
        }

        try {
            Field localInitMainField = initMainField;
            if (localInitMainField == null) {
                localInitMainField = Init.class.getDeclaredField("main");
                localInitMainField.setAccessible(true);
                initMainField = localInitMainField;
            }

            final Object main = localInitMainField.get(null);
            if (main == null) {
                return null;
            }

            Field localMainSimulatorsField = mainSimulatorsField;
            if (localMainSimulatorsField == null) {
                localMainSimulatorsField = main.getClass().getDeclaredField("simulators");
                localMainSimulatorsField.setAccessible(true);
                mainSimulatorsField = localMainSimulatorsField;
            }

            final Object simulators = localMainSimulatorsField.get(main);
            if (!(simulators instanceof Iterable<?>)) {
                return null;
            }

            Simulator fallback = null;
            int count = 0;
            for (final Object simulatorObject : (Iterable<?>) simulators) {
                if (!(simulatorObject instanceof Simulator)) {
                    continue;
                }
                final Simulator simulator = (Simulator) simulatorObject;

                count++;
                if (fallback == null) {
                    fallback = simulator;
                }

                if (normalizedWorldId.equals(normalizeDimensionId(simulator.dimension))) {
                    return simulator;
                }
            }

            return count == 1 ? fallback : null;
        } catch (Exception exception) {
            if (!loggedSimulatorFailure) {
                loggedSimulatorFailure = true;
                Main.LOGGER.warn("[{}] Failed to resolve simulator for BlueMap rails", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    private static String normalizeDimensionId(String id) {
        if (id == null) {
            return "";
        }

        // MTR commonly uses "namespace/path" but some environments may hand us "namespace:path".
        final String normalized = id.trim().replace(':', '/').toLowerCase(Locale.ENGLISH);
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static Position[] parsePositionsFromHexId(String hexId) {
        try {
            if (hexId == null) {
                return new Position[]{null, null};
            }
            final String[] split = hexId.split("-");
            if (split.length != 6) {
                return new Position[]{null, null};
            }
            return new Position[]{
                    new Position(Long.parseUnsignedLong(split[0], 16), Long.parseUnsignedLong(split[1], 16), Long.parseUnsignedLong(split[2], 16)),
                    new Position(Long.parseUnsignedLong(split[3], 16), Long.parseUnsignedLong(split[4], 16), Long.parseUnsignedLong(split[5], 16))
            };
        } catch (Exception ignored) {
            return new Position[]{null, null};
        }
    }

    private static final class DimensionRenderData {
        private final String dimensionId;
        private final List<RailLineData> rails;

        private DimensionRenderData(String dimensionId, List<RailLineData> rails) {
            this.dimensionId = dimensionId;
            this.rails = rails == null ? new ArrayList<>() : rails;
        }
    }

    private static final class RailLineData {
        private final String id;
        private final int speedKmh;
        private final boolean isPlatform;
        private final boolean isSiding;
        private final boolean canTurnBack;
        private final double[] points;

        private RailLineData(String id, int speedKmh, boolean isPlatform, boolean isSiding, boolean canTurnBack, double[] points) {
            this.id = id;
            this.speedKmh = speedKmh;
            this.isPlatform = isPlatform;
            this.isSiding = isSiding;
            this.canTurnBack = canTurnBack;
            this.points = points;
        }
    }
}
