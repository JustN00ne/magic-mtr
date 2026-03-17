package org.justnoone.jme.mixin;

import org.justnoone.jme.client.DashboardRouteRenderState;
import org.justnoone.jme.client.DashboardMapOverlayCacheStore;
import org.justnoone.jme.client.DashboardRailViewMode;
import org.justnoone.jme.client.PositionAngleKey;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.tool.Vector;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.World;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.screen.WidgetMap;
import org.mtr.mod.Init;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Mixin(value = WidgetMap.class, remap = false)
public abstract class WidgetMapRouteOverlayMixin {

    @Shadow
    private double scale;

    @Shadow
    private double centerX;

    @Shadow
    private double centerY;

    @Shadow
    private boolean showStations;

    @Unique
    private static final Map<String, List<Object[]>> jme$pathCache = new HashMap<>();
    @Unique
    private static int jme$railsHash = Integer.MIN_VALUE;
    @Unique
    private static final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> jme$mergedGraph = new Object2ObjectOpenHashMap<>();
    @Unique
    private static int jme$mergedGraphHash = Integer.MIN_VALUE;

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderRouteOverlay(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        final Route editingRoute = showStations ? DashboardRouteRenderState.getEditingRoute() : null;
        final List<Platform> routePlatforms = new ArrayList<>();
        if (editingRoute != null) {
            for (final RoutePlatformData routePlatformData : editingRoute.getRoutePlatforms()) {
                if (routePlatformData.platform != null) {
                    routePlatforms.add(routePlatformData.platform);
                }
            }
        }

        final int routeColor = editingRoute == null ? 0xFFFFFF : editingRoute.getColor() & 0xFFFFFF;
        final int mainLineColor = jme$withAlpha(routeColor, 0xE6);
        final int alternativeLineColor = jme$withAlpha(jme$withSaturation(routeColor, 0.5F), 0xD0);
        final double lineThickness = Math.max(1.35D, Math.min(4.5D, 1.45D + scale * 0.035D));
        final int mapWidth = jme$getWidth2();
        final int mapHeight = jme$getHeight2();
        final MinecraftClientData railData = jme$getPreferredRailData();
        if (railData == null) {
            return;
        }
        final int currentRailsHash;
        if (editingRoute != null) {
            currentRailsHash = jme$getCombinedRailsHash(railData);
            if (currentRailsHash != jme$railsHash) {
                jme$railsHash = currentRailsHash;
                jme$pathCache.clear();
            }
        } else {
            currentRailsHash = 0;
        }

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        if (editingRoute != null) {
            // Grey out the dashboard map while a route is being edited.
            guiDrawing.drawRectangle(jme$getX2(), jme$getY2(), jme$getX2() + mapWidth, jme$getY2() + mapHeight, 0x66545454);
        }

        if (scale >= 0.1D) {
            final JmeConfig.DashboardRailOverlayMode overlayMode = JmeConfig.dashboardRailOverlayMode();
            if (overlayMode != JmeConfig.DashboardRailOverlayMode.OFF) {
                final boolean speedMode = DashboardRailViewMode.isSpeedMode();
                final int networkColor = editingRoute == null ? jme$withAlpha(0x3F8BFF, 0x95) : jme$withAlpha(0x8A8A8A, 0x6F);
                final List<DashboardMapOverlayCacheStore.RailSnapshot> railsToDraw = jme$selectAllKnownRailsForOverlay(overlayMode, mapWidth, mapHeight);
                jme$drawKnownRails(guiDrawing, railsToDraw, networkColor, Math.max(1.0D, lineThickness * 0.68D), mapWidth, mapHeight, speedMode, editingRoute != null);
                final int signalColor = speedMode ? (editingRoute == null ? jme$withAlpha(0xDCE7FF, 0x8C) : jme$withAlpha(0x9A9A9A, 0x6A)) : networkColor;
                jme$drawKnownSignalArrows(guiDrawing, railsToDraw, signalColor, Math.max(0.9D, lineThickness * 0.6D), mapWidth, mapHeight);
            }
        }

        if (editingRoute != null && !routePlatforms.isEmpty()) {
            for (int i = 0; i < routePlatforms.size() - 1; i++) {
                jme$drawRailPath(guiDrawing, routePlatforms.get(i), routePlatforms.get(i + 1), mainLineColor, lineThickness, mapWidth, mapHeight, currentRailsHash);
            }

            final int selectedIndex = DashboardRouteRenderState.getEditingRoutePlatformIndex();
            for (int i = 0; i < routePlatforms.size(); i++) {
                final Platform platform = routePlatforms.get(i);
                final double markerRadius = i == selectedIndex ? 3 : 2;
                jme$drawPoint(guiDrawing, platform.getMidPosition(), markerRadius, mainLineColor, mapWidth, mapHeight);

                final List<Long> alternatives = AlternativePlatformRegistry.getAlternatives(editingRoute.getId(), platform.getId());
                for (final long alternativeId : alternatives) {
                    Platform alternativePlatform = railData.platformIdMap.get(alternativeId);
                    if (alternativePlatform == null) {
                        final MinecraftClientData dashboardData = MinecraftClientData.getDashboardInstance();
                        if (dashboardData != null) {
                            alternativePlatform = dashboardData.platformIdMap.get(alternativeId);
                        }
                    }
                    if (alternativePlatform == null) {
                        final MinecraftClientData liveData = MinecraftClientData.getInstance();
                        if (liveData != null) {
                            alternativePlatform = liveData.platformIdMap.get(alternativeId);
                        }
                    }
                    if (alternativePlatform == null) {
                        continue;
                    }
                    jme$drawRailPath(guiDrawing, platform, alternativePlatform, alternativeLineColor, Math.max(1.25D, lineThickness * 0.8D), mapWidth, mapHeight, currentRailsHash);
                    jme$drawPoint(guiDrawing, alternativePlatform.getMidPosition(), 1.8D, alternativeLineColor, mapWidth, mapHeight);
                }
            }
        }

        if (scale >= 0.075D) {
            jme$drawVisibleVehicles(guiDrawing, editingRoute, mapWidth, mapHeight);
        }

        guiDrawing.finishDrawingRectangle();
    }

    @Unique
    private void jme$drawRailPath(GuiDrawing guiDrawing, Platform startPlatform, Platform endPlatform, int color, double thickness, int mapWidth, int mapHeight, int railsHash) {
        final Position startMid = startPlatform.getMidPosition();
        final Position endMid = endPlatform.getMidPosition();
        final List<Object[]> path = jme$getRailPath(startPlatform, endPlatform, railsHash);

        if (path.isEmpty()) {
            jme$drawWorldSegment(guiDrawing, startMid.getX() + 0.5D, startMid.getZ() + 0.5D, endMid.getX() + 0.5D, endMid.getZ() + 0.5D, color, thickness, mapWidth, mapHeight);
            return;
        }

        for (final Object[] pathEdge : path) {
            final Position from = (Position) pathEdge[0];
            final Position to = (Position) pathEdge[1];
            final Rail rail = (Rail) pathEdge[2];
            jme$drawRailEdge(guiDrawing, from, to, rail, color, thickness, mapWidth, mapHeight);
            jme$drawRailSignalArrows(guiDrawing, from, to, rail, color, Math.max(0.9D, thickness * 0.8D), mapWidth, mapHeight);
        }
    }

    @Unique
    private List<Object[]> jme$getRailPath(Platform startPlatform, Platform endPlatform, int railsHash) {
        final String cacheKey = startPlatform.getId() + ">" + endPlatform.getId() + ":" + railsHash;
        final List<Object[]> cached = jme$pathCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        final SavedRailBasePositionAccessor startAccessor = (SavedRailBasePositionAccessor) (Object) startPlatform;
        final SavedRailBasePositionAccessor endAccessor = (SavedRailBasePositionAccessor) (Object) endPlatform;
        final Position[] startPositions = new Position[]{startAccessor.jme$getPosition1(), startAccessor.jme$getPosition2()};
        final Position[] endPositions = new Position[]{endAccessor.jme$getPosition1(), endAccessor.jme$getPosition2()};

        List<Object[]> bestPath = Collections.emptyList();
        double bestDistance = Double.MAX_VALUE;

        for (final Position startPosition : startPositions) {
            for (final Position endPosition : endPositions) {
                final AbstractMap.SimpleImmutableEntry<List<Object[]>, Double> pathResult = jme$findPath(startPosition, endPosition);
                if (pathResult != null && pathResult.getValue() < bestDistance) {
                    bestDistance = pathResult.getValue();
                    bestPath = pathResult.getKey();
                }
            }
        }

        jme$pathCache.put(cacheKey, bestPath);
        return bestPath;
    }

    @Unique
    private AbstractMap.SimpleImmutableEntry<List<Object[]>, Double> jme$findPath(Position start, Position end) {
        final MinecraftClientData railData = jme$getPreferredRailData();
        if (railData == null) {
            return null;
        }
        final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> graph = jme$getRailGraph(railData);
        if (start == null || end == null || graph == null || graph.isEmpty()) {
            return null;
        }
        if (start.equals(end)) {
            return new AbstractMap.SimpleImmutableEntry<>(Collections.emptyList(), 0D);
        }

        // Mirror the core path-finder logic (one-way tracks + angle continuity).
        final PositionAngleKey startKey = new PositionAngleKey(start, null);
        final Map<PositionAngleKey, Double> distances = new HashMap<>();
        final Map<PositionAngleKey, PositionAngleKey> previousNode = new HashMap<>();
        final Map<PositionAngleKey, Rail> previousRail = new HashMap<>();
        final PriorityQueue<Map.Entry<PositionAngleKey, Double>> queue = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));

        distances.put(startKey, 0D);
        queue.add(new AbstractMap.SimpleImmutableEntry<>(startKey, 0D));

        PositionAngleKey bestEnd = null;
        while (!queue.isEmpty()) {
            final Map.Entry<PositionAngleKey, Double> current = queue.poll();
            final PositionAngleKey currentKey = current.getKey();
            final double currentDistance = current.getValue();
            final double recordedDistance = distances.getOrDefault(currentKey, Double.MAX_VALUE);
            if (currentDistance > recordedDistance) {
                continue;
            }

            if (currentKey.position.equals(end)) {
                bestEnd = currentKey;
                break;
            }

            final Object2ObjectOpenHashMap<Position, Rail> edges = graph.get(currentKey.position);
            if (edges == null || edges.isEmpty()) {
                continue;
            }

            edges.forEach((neighbor, rail) -> {
                if (neighbor == null || rail == null) {
                    return;
                }

                final double speedLimit = rail.getSpeedLimitMetersPerMillisecond(currentKey.position);
                if (speedLimit <= 0) {
                    return;
                }

                final Angle requiredAngle = rail.getStartAngle(currentKey.position);
                if (currentKey.angle != null && currentKey.angle != requiredAngle && !rail.canTurnBack()) {
                    return;
                }

                final Angle nextAngle = rail.getStartAngle(neighbor).getOpposite();
                final PositionAngleKey neighborKey = new PositionAngleKey(neighbor, nextAngle);
                final double nextDistance = currentDistance + rail.railMath.getLength() / speedLimit;
                if (nextDistance < distances.getOrDefault(neighborKey, Double.MAX_VALUE)) {
                    distances.put(neighborKey, nextDistance);
                    previousNode.put(neighborKey, currentKey);
                    previousRail.put(neighborKey, rail);
                    queue.add(new AbstractMap.SimpleImmutableEntry<>(neighborKey, nextDistance));
                }
            });
        }

        if (bestEnd == null) {
            return null;
        }

        final List<Object[]> path = new ArrayList<>();
        PositionAngleKey currentKey = bestEnd;
        while (!currentKey.equals(startKey)) {
            final PositionAngleKey previous = previousNode.get(currentKey);
            final Rail rail = previousRail.get(currentKey);
            if (previous == null || rail == null) {
                return null;
            }
            path.add(new Object[]{previous.position, currentKey.position, rail});
            currentKey = previous;
        }
        Collections.reverse(path);
        return new AbstractMap.SimpleImmutableEntry<>(path, distances.get(bestEnd));
    }

    @Unique
    private void jme$drawRailEdge(GuiDrawing guiDrawing, Position from, Position to, Rail rail, int color, double thickness, int mapWidth, int mapHeight) {
        final List<double[]> points = new ArrayList<>();
        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerXPrevious = (x1 + x2) * 0.5D;
            final double centerZPrevious = (z1 + z2) * 0.5D;
            final double centerXCurrent = (x3 + x4) * 0.5D;
            final double centerZCurrent = (z3 + z4) * 0.5D;
            jme$appendPolylinePoint(points, centerXPrevious, centerZPrevious);
            jme$appendPolylinePoint(points, centerXCurrent, centerZCurrent);
        }, 1, 0, 0);

        if (points.size() < 2) {
            jme$drawWorldSegment(guiDrawing, from.getX() + 0.5D, from.getZ() + 0.5D, to.getX() + 0.5D, to.getZ() + 0.5D, color, thickness, mapWidth, mapHeight);
            return;
        }

        final double fromX = from.getX() + 0.5D;
        final double fromZ = from.getZ() + 0.5D;
        final double[] firstPoint = points.get(0);
        final double[] lastPoint = points.get(points.size() - 1);
        if (Math.hypot(firstPoint[0] - fromX, firstPoint[1] - fromZ) > Math.hypot(lastPoint[0] - fromX, lastPoint[1] - fromZ)) {
            Collections.reverse(points);
        }

        for (int i = 1; i < points.size(); i++) {
            final double[] previousPoint = points.get(i - 1);
            final double[] currentPoint = points.get(i);
            jme$drawWorldSegment(guiDrawing, previousPoint[0], previousPoint[1], currentPoint[0], currentPoint[1], color, thickness, mapWidth, mapHeight);
        }
    }

    @Unique
    private List<DashboardMapOverlayCacheStore.RailSnapshot> jme$selectAllKnownRailsForOverlay(JmeConfig.DashboardRailOverlayMode overlayMode, int mapWidth, int mapHeight) {
        final String dimensionId = jme$getDashboardOverlayDimensionId();

        // Merge currently-known rails into the persistent cache.
        final LinkedHashMap<String, Rail> liveRailsById = new LinkedHashMap<>();
        jme$appendKnownRails(liveRailsById, jme$getPreferredRailData());
        jme$appendKnownRails(liveRailsById, MinecraftClientData.getDashboardInstance());
        jme$appendKnownRails(liveRailsById, MinecraftClientData.getInstance());
        DashboardMapOverlayCacheStore.mergeLiveRails(dimensionId, liveRailsById.values());

        final List<DashboardMapOverlayCacheStore.RailSnapshot> allRails = DashboardMapOverlayCacheStore.getRailsForRender(dimensionId);
        if (allRails == null || allRails.isEmpty()) {
            return Collections.emptyList();
        }

        final int cellSize = 26;
        final int maxPerCell = Math.max(1, Math.min(64, JmeConfig.dashboardRailOverlayCullMaxPerCell()));
        final Map<Long, Integer> countsByCell = new HashMap<>();
        final List<DashboardMapOverlayCacheStore.RailSnapshot> result = new ArrayList<>();

        for (final DashboardMapOverlayCacheStore.RailSnapshot rail : allRails) {
            if (rail == null || rail.points == null || rail.points.size() < 2) {
                continue;
            }

            final double[] mid = rail.points.get(rail.points.size() / 2);
            if (mid == null || mid.length < 2) {
                continue;
            }

            final double mapX = jme$toMapX(mid[0]);
            final double mapY = jme$toMapY(mid[1]);
            if (mapX < -cellSize || mapY < -cellSize || mapX > mapWidth + cellSize || mapY > mapHeight + cellSize) {
                continue;
            }

            if (overlayMode == JmeConfig.DashboardRailOverlayMode.CULL) {
                final int cellX = (int) Math.floor(mapX / cellSize);
                final int cellY = (int) Math.floor(mapY / cellSize);
                final long cellKey = (((long) cellX) << 32) ^ (cellY & 0xFFFFFFFFL);
                final int count = countsByCell.getOrDefault(cellKey, 0);
                if (count >= maxPerCell) {
                    continue;
                }
                countsByCell.put(cellKey, count + 1);
            }

            result.add(rail);
        }

        return result;
    }

    @Unique
    private void jme$drawKnownRails(
            GuiDrawing guiDrawing,
            List<DashboardMapOverlayCacheStore.RailSnapshot> rails,
            int color,
            double thickness,
            int mapWidth,
            int mapHeight,
            boolean speedMode,
            boolean dimmed
    ) {
        if (rails == null || rails.isEmpty()) {
            return;
        }

        final int alpha = dimmed ? 0x70 : 0x95;
        for (final DashboardMapOverlayCacheStore.RailSnapshot rail : rails) {
            if (rail == null || rail.points == null || rail.points.size() < 2) {
                continue;
            }

            final int railColor = speedMode ? jme$getRailSpeedColor(rail.speedKmh, alpha) : color;
            jme$drawRailPolyline(guiDrawing, rail.points, railColor, thickness, mapWidth, mapHeight);
        }
    }

    @Unique
    private void jme$drawKnownSignalArrows(GuiDrawing guiDrawing, List<DashboardMapOverlayCacheStore.RailSnapshot> rails, int color, double thickness, int mapWidth, int mapHeight) {
        if (rails == null || rails.isEmpty()) {
            return;
        }

        for (final DashboardMapOverlayCacheStore.RailSnapshot rail : rails) {
            if (rail == null || !rail.hasSignals || rail.points == null || rail.points.size() < 2) {
                continue;
            }

            final List<double[]> points = rail.points;
            if (points.size() < 2) {
                continue;
            }

            if (rail.allowForward) {
                final double[] start = points.get(0);
                final double[] startNext = points.get(1);
                jme$drawArrowWorld(guiDrawing, start[0], start[1], startNext[0], startNext[1], color, thickness, mapWidth, mapHeight);
            }

            if (rail.allowReverse) {
                final double[] end = points.get(points.size() - 1);
                final double[] endPrevious = points.get(points.size() - 2);
                jme$drawArrowWorld(guiDrawing, end[0], end[1], endPrevious[0], endPrevious[1], color, thickness, mapWidth, mapHeight);
            }
        }
    }

    @Unique
    private void jme$drawRailPolyline(GuiDrawing guiDrawing, List<double[]> points, int color, double thickness, int mapWidth, int mapHeight) {
        if (points == null || points.size() < 2) {
            return;
        }

        for (int i = 1; i < points.size(); i++) {
            final double[] previous = points.get(i - 1);
            final double[] current = points.get(i);
            if (previous == null || current == null || previous.length < 2 || current.length < 2) {
                continue;
            }
            jme$drawWorldSegment(guiDrawing, previous[0], previous[1], current[0], current[1], color, thickness, mapWidth, mapHeight);
        }
    }

    @Unique
    private void jme$drawRailSignalArrows(GuiDrawing guiDrawing, Position from, Position to, Rail rail, int color, double thickness, int mapWidth, int mapHeight) {
        if (rail.getSignalColors().isEmpty()) {
            return;
        }

        final boolean allowForward = rail.getSpeedLimitMetersPerMillisecond(from) > 0;
        final boolean allowReverse = rail.getSpeedLimitMetersPerMillisecond(to) > 0;

        final List<double[]> points = new ArrayList<>();
        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerXPrevious = (x1 + x2) * 0.5D;
            final double centerZPrevious = (z1 + z2) * 0.5D;
            final double centerXCurrent = (x3 + x4) * 0.5D;
            final double centerZCurrent = (z3 + z4) * 0.5D;
            jme$appendPolylinePoint(points, centerXPrevious, centerZPrevious);
            jme$appendPolylinePoint(points, centerXCurrent, centerZCurrent);
        }, 1, 0, 0);

        if (points.size() < 2) {
            if (allowForward) {
                jme$drawArrowWorld(guiDrawing, from.getX() + 0.5D, from.getZ() + 0.5D, to.getX() + 0.5D, to.getZ() + 0.5D, color, thickness, mapWidth, mapHeight);
            }
            if (allowReverse) {
                jme$drawArrowWorld(guiDrawing, to.getX() + 0.5D, to.getZ() + 0.5D, from.getX() + 0.5D, from.getZ() + 0.5D, color, thickness, mapWidth, mapHeight);
            }
            return;
        }

        final double fromX = from.getX() + 0.5D;
        final double fromZ = from.getZ() + 0.5D;
        final double[] firstPoint = points.get(0);
        final double[] lastPoint = points.get(points.size() - 1);
        if (Math.hypot(firstPoint[0] - fromX, firstPoint[1] - fromZ) > Math.hypot(lastPoint[0] - fromX, lastPoint[1] - fromZ)) {
            Collections.reverse(points);
        }

        final double[] start = points.get(0);
        final double[] startNext = points.get(1);
        final double[] end = points.get(points.size() - 1);
        final double[] endPrevious = points.get(points.size() - 2);
        if (allowForward) {
            jme$drawArrowWorld(guiDrawing, start[0], start[1], startNext[0], startNext[1], color, thickness, mapWidth, mapHeight);
        }
        if (allowReverse) {
            jme$drawArrowWorld(guiDrawing, end[0], end[1], endPrevious[0], endPrevious[1], color, thickness, mapWidth, mapHeight);
        }
    }

    @Unique
    private void jme$drawArrowWorld(
            GuiDrawing guiDrawing,
            double fromX,
            double fromZ,
            double toX,
            double toZ,
            int color,
            double thickness,
            int mapWidth,
            int mapHeight
    ) {
        final double dx = toX - fromX;
        final double dz = toZ - fromZ;
        final double distance = Math.hypot(dx, dz);
        if (distance < 0.001D) {
            return;
        }

        final double ux = dx / distance;
        final double uz = dz / distance;
        final double px = -uz;
        final double pz = ux;

        final double baseX = fromX + ux * 0.35D;
        final double baseZ = fromZ + uz * 0.35D;
        final double tipX = fromX + ux * 1.05D;
        final double tipZ = fromZ + uz * 1.05D;
        final double wingLength = 0.42D;
        final double wingWidth = 0.22D;
        final double wingLeftX = tipX - ux * wingLength + px * wingWidth;
        final double wingLeftZ = tipZ - uz * wingLength + pz * wingWidth;
        final double wingRightX = tipX - ux * wingLength - px * wingWidth;
        final double wingRightZ = tipZ - uz * wingLength - pz * wingWidth;

        jme$drawWorldSegment(guiDrawing, baseX, baseZ, tipX, tipZ, color, thickness, mapWidth, mapHeight);
        jme$drawWorldSegment(guiDrawing, tipX, tipZ, wingLeftX, wingLeftZ, color, Math.max(0.8D, thickness * 0.9D), mapWidth, mapHeight);
        jme$drawWorldSegment(guiDrawing, tipX, tipZ, wingRightX, wingRightZ, color, Math.max(0.8D, thickness * 0.9D), mapWidth, mapHeight);
    }

    @Unique
    private static void jme$appendKnownRails(LinkedHashMap<String, Rail> railsById, MinecraftClientData railData) {
        if (railData == null) {
            return;
        }
        railData.rails.forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
        railData.railIdMap.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
        railData.positionsToRail.values().forEach(edges -> edges.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail)));
    }

    @Unique
    private void jme$drawVisibleVehicles(GuiDrawing guiDrawing, Route editingRoute, int mapWidth, int mapHeight) {
        final String dimensionId = jme$getDashboardOverlayDimensionId();

        // Merge live vehicles into the persistent cache.
        final LinkedHashMap<Long, VehicleExtension> liveVehiclesById = new LinkedHashMap<>();
        jme$appendVehicles(liveVehiclesById, jme$getPreferredRailData());
        jme$appendVehicles(liveVehiclesById, MinecraftClientData.getDashboardInstance());
        jme$appendVehicles(liveVehiclesById, MinecraftClientData.getInstance());
        DashboardMapOverlayCacheStore.mergeLiveVehicles(dimensionId, liveVehiclesById.values());

        final List<DashboardMapOverlayCacheStore.VehicleSnapshot> vehicles = DashboardMapOverlayCacheStore.getVehiclesForRender(dimensionId);
        if (vehicles == null || vehicles.isEmpty()) {
            return;
        }

        final int margin = 20;
        for (final DashboardMapOverlayCacheStore.VehicleSnapshot vehicle : vehicles) {
            if (vehicle == null) {
                continue;
            }

            final double mapX = jme$toMapX(vehicle.x);
            final double mapY = jme$toMapY(vehicle.z);
            if (mapX < -margin || mapY < -margin || mapX > mapWidth + margin || mapY > mapHeight + margin) {
                continue;
            }

            final long vehicleRouteId = vehicle.routeId;
            final boolean isFocusedRoute = editingRoute != null && vehicleRouteId == editingRoute.getId();
            final int routeColor = jme$getRouteColor(vehicleRouteId);
            final int markerColor = editingRoute == null ? jme$withAlpha(routeColor, 0xE6) : (isFocusedRoute ? jme$withAlpha(routeColor, 0xEE) : jme$withAlpha(0xB4B4B4, 0x99));
            final int outlineColor = editingRoute == null ? 0xA0000000 : (isFocusedRoute ? 0xCC000000 : 0x88000000);

            jme$drawPoint(guiDrawing, vehicle.x, vehicle.z, 2.4D, outlineColor, mapWidth, mapHeight);
            jme$drawPoint(guiDrawing, vehicle.x, vehicle.z, 1.6D, markerColor, mapWidth, mapHeight);
        }
    }

    @Unique
    private static void jme$appendVehicles(LinkedHashMap<Long, VehicleExtension> vehiclesById, MinecraftClientData railData) {
        if (railData == null) {
            return;
        }
        railData.vehicles.forEach(vehicle -> vehiclesById.putIfAbsent(vehicle.getId(), vehicle));
    }

    @Unique
    private static String jme$getDashboardOverlayDimensionId() {
        try {
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return "";
            }
            final ClientWorld clientWorld = client.getWorldMapped();
            if (clientWorld == null) {
                return "";
            }
            final String worldId = Init.getWorldId(new World(clientWorld.data));
            return worldId == null ? "" : worldId;
        } catch (Exception ignored) {
            return "";
        }
    }

    @Unique
    private static int jme$getRouteColor(long routeId) {
        if (routeId == 0) {
            return 0xFFFFFF;
        }
        final MinecraftClientData preferred = jme$getPreferredRailData();
        if (preferred != null && preferred.routeIdMap.containsKey(routeId)) {
            return preferred.routeIdMap.get(routeId).getColor() & 0xFFFFFF;
        }
        final MinecraftClientData dashboard = MinecraftClientData.getDashboardInstance();
        if (dashboard != null && dashboard.routeIdMap.containsKey(routeId)) {
            return dashboard.routeIdMap.get(routeId).getColor() & 0xFFFFFF;
        }
        final MinecraftClientData live = MinecraftClientData.getInstance();
        if (live != null && live.routeIdMap.containsKey(routeId)) {
            return live.routeIdMap.get(routeId).getColor() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    @Unique
    private void jme$drawWorldSegment(GuiDrawing guiDrawing, double worldX1, double worldZ1, double worldX2, double worldZ2, int color, double thickness, int mapWidth, int mapHeight) {
        final double x1 = jme$toMapX(worldX1);
        final double y1 = jme$toMapY(worldZ1);
        final double x2 = jme$toMapX(worldX2);
        final double y2 = jme$toMapY(worldZ2);

        final double distance = Math.hypot(x2 - x1, y2 - y1);
        final int steps = Math.max(1, (int) Math.ceil(distance / Math.max(0.7D, thickness * 0.65D)));
        final double radius = thickness / 2D;

        for (int i = 0; i <= steps; i++) {
            final double t = i / (double) steps;
            final double x = x1 + (x2 - x1) * t;
            final double y = y1 + (y2 - y1) * t;
            if (x + radius < 0 || y + radius < 0 || x - radius > mapWidth || y - radius > mapHeight) {
                continue;
            }
            guiDrawing.drawRectangle(jme$getX2() + x - radius, jme$getY2() + y - radius, jme$getX2() + x + radius, jme$getY2() + y + radius, color);
        }
    }

    private void jme$drawPoint(GuiDrawing guiDrawing, Position position, double radius, int color, int mapWidth, int mapHeight) {
        jme$drawPoint(guiDrawing, position.getX() + 0.5D, position.getZ() + 0.5D, radius, color, mapWidth, mapHeight);
    }

    @Unique
    private void jme$drawPoint(GuiDrawing guiDrawing, double worldX, double worldZ, double radius, int color, int mapWidth, int mapHeight) {
        final double x = jme$toMapX(worldX);
        final double y = jme$toMapY(worldZ);
        if (x + radius < 0 || y + radius < 0 || x - radius > mapWidth || y - radius > mapHeight) {
            return;
        }
        guiDrawing.drawRectangle(jme$getX2() + x - radius, jme$getY2() + y - radius, jme$getX2() + x + radius, jme$getY2() + y + radius, color);
    }

    private double jme$toMapX(double worldX) {
        return (worldX - centerX) * scale + jme$getWidth2() / 2D;
    }

    private double jme$toMapY(double worldZ) {
        return (worldZ - centerY) * scale + jme$getHeight2() / 2D;
    }

    private static int jme$withSaturation(int rgb, float saturationMultiplier) {
        final int red = rgb >> 16 & 0xFF;
        final int green = rgb >> 8 & 0xFF;
        final int blue = rgb & 0xFF;
        final float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        final int saturated = Color.HSBtoRGB(hsb[0], Math.max(0, Math.min(1, hsb[1] * saturationMultiplier)), hsb[2]);
        return saturated & 0xFFFFFF;
    }

    private static int jme$withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    @Unique
    private static int jme$getRailSpeedColor(int speedKmh, int alpha) {
        final int clampedSpeed = (int) Math.max(1L, Math.min(400L, speedKmh <= 0 ? 1 : speedKmh));
        final int rgb = MagicRailSpeedColor.colorForSpeed(clampedSpeed) & 0xFFFFFF;
        return jme$withAlpha(rgb, alpha);
    }

    @Unique
    private static int jme$getRailSpeedColor(Rail rail, int alpha) {
        if (rail == null) {
            return jme$withAlpha(0x3F8BFF, alpha);
        }

        final long speedForward = rail.getSpeedLimitKilometersPerHour(false);
        final long speedReverse = rail.getSpeedLimitKilometersPerHour(true);
        final long resolvedSpeed = Math.max(speedForward, speedReverse);
        return jme$getRailSpeedColor((int) resolvedSpeed, alpha);
    }

    private int jme$getX2() {
        return ((WidgetMap) (Object) this).getX2();
    }

    private int jme$getY2() {
        return ((WidgetMap) (Object) this).getY2();
    }

    private int jme$getWidth2() {
        return ((WidgetMap) (Object) this).getWidth2();
    }

    private int jme$getHeight2() {
        return ((WidgetMap) (Object) this).getHeight2();
    }

    @Unique
    private static void jme$appendPolylinePoint(List<double[]> points, double x, double z) {
        if (points.isEmpty()) {
            points.add(new double[]{x, z});
            return;
        }
        final double[] previousPoint = points.get(points.size() - 1);
        if (Math.hypot(previousPoint[0] - x, previousPoint[1] - z) > 0.08D) {
            points.add(new double[]{x, z});
        }
    }

    @Unique
    private static MinecraftClientData jme$getPreferredRailData() {
        final MinecraftClientData dashboardData = MinecraftClientData.getDashboardInstance();
        if (dashboardData != null && !dashboardData.rails.isEmpty() && !dashboardData.positionsToRail.isEmpty()) {
            return dashboardData;
        }
        final MinecraftClientData liveData = MinecraftClientData.getInstance();
        if (liveData != null && !liveData.rails.isEmpty() && !liveData.positionsToRail.isEmpty()) {
            return liveData;
        }
        return dashboardData;
    }

    @Unique
    private static int jme$getCombinedRailsHash(MinecraftClientData preferredData) {
        int hash = 1;
        if (preferredData != null) {
            hash = 31 * hash + preferredData.rails.hashCode();
            hash = 31 * hash + preferredData.railIdMap.hashCode();
        }
        final MinecraftClientData dashboardData = MinecraftClientData.getDashboardInstance();
        if (dashboardData != null) {
            hash = 31 * hash + dashboardData.rails.hashCode();
            hash = 31 * hash + dashboardData.railIdMap.hashCode();
        }
        final MinecraftClientData liveData = MinecraftClientData.getInstance();
        if (liveData != null) {
            hash = 31 * hash + liveData.rails.hashCode();
            hash = 31 * hash + liveData.railIdMap.hashCode();
        }
        return hash;
    }

    @Unique
    private static Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> jme$getRailGraph(MinecraftClientData preferredData) {
        final int railsHash = jme$getCombinedRailsHash(preferredData);
        if (jme$mergedGraphHash == railsHash && !jme$mergedGraph.isEmpty()) {
            return jme$mergedGraph;
        }

        jme$mergedGraph.clear();
        jme$appendGraphData(jme$mergedGraph, preferredData);
        jme$appendGraphData(jme$mergedGraph, MinecraftClientData.getDashboardInstance());
        jme$appendGraphData(jme$mergedGraph, MinecraftClientData.getInstance());
        jme$mergedGraphHash = railsHash;
        return jme$mergedGraph;
    }

    @Unique
    private static void jme$appendGraphData(
            Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> targetGraph,
            MinecraftClientData sourceData
    ) {
        if (sourceData == null) {
            return;
        }

        sourceData.positionsToRail.forEach((position1, connections) -> {
            if (position1 == null || connections == null || connections.isEmpty()) {
                return;
            }
            final Object2ObjectOpenHashMap<Position, Rail> edges = targetGraph.computeIfAbsent(position1, ignored -> new Object2ObjectOpenHashMap<>());
            connections.forEach((position2, rail) -> {
                if (position2 == null || rail == null) {
                    return;
                }
                // Respect one-way tracks.
                if (rail.getSpeedLimitMetersPerMillisecond(position1) <= 0) {
                    return;
                }
                edges.put(position2, rail);
            });
        });

        sourceData.rails.forEach(rail -> jme$addRailFromHexId(targetGraph, rail));
        sourceData.railIdMap.values().forEach(rail -> jme$addRailFromHexId(targetGraph, rail));
    }

    @Unique
    private static void jme$addRailFromHexId(
            Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> targetGraph,
            Rail rail
    ) {
        if (rail == null) {
            return;
        }
        final Position[] positions = jme$parseRailPositions(rail.getHexId());
        final Position position1 = positions[0];
        final Position position2 = positions[1];
        if (position1 == null || position2 == null) {
            return;
        }

        // Respect one-way tracks: only add a directed edge if the rail allows travel from that node.
        if (rail.getSpeedLimitMetersPerMillisecond(position1) > 0) {
            targetGraph.computeIfAbsent(position1, ignored -> new Object2ObjectOpenHashMap<>()).put(position2, rail);
        }
        if (rail.getSpeedLimitMetersPerMillisecond(position2) > 0) {
            targetGraph.computeIfAbsent(position2, ignored -> new Object2ObjectOpenHashMap<>()).put(position1, rail);
        }
    }

    @Unique
    private static Position[] jme$parseRailPositions(String railId) {
        try {
            if (railId == null) {
                return new Position[]{null, null};
            }
            final String[] split = railId.split("-");
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

}
