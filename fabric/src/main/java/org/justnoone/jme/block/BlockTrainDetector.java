package org.justnoone.jme.block;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockTrainPoweredSensorBase;
import org.mtr.mod.block.BlockTrainSensorBase;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockTrainDetector extends BlockTrainPoweredSensorBase {

    private static volatile Field initMainField;
    private static volatile Field mainSimulatorsField;
    private static volatile Field sidingVehiclesField;
    private static volatile Field vehicleRailProgressField;
    private static volatile Field vehicleSpeedField;
    private static volatile boolean loggedSimulatorFailure;
    private static volatile boolean loggedVehiclesFailure;
    private static volatile boolean loggedVehicleProgressFailure;
    private static volatile boolean loggedVehicleSpeedFailure;
    private static final int DEFAULT_NODE_RANGE = 6;
    private static final int MIN_NODE_RANGE = 1;
    private static final int MAX_NODE_RANGE = 64;
    private static final int MIN_SECONDS_OFFSET = -86400;
    private static final int MAX_SECONDS_OFFSET = 86400;
    private static final int NODE_SEARCH_VERTICAL_RADIUS_BLOCKS = 6;
    private static final double MAX_RAIL_ATTACH_DISTANCE_BLOCKS = 6;
    private static final double MAX_RAIL_ATTACH_DISTANCE_SQUARED = MAX_RAIL_ATTACH_DISTANCE_BLOCKS * MAX_RAIL_ATTACH_DISTANCE_BLOCKS;
    private static final String KEY_NODE_RANGE = "jme_train_detector_nodes";
    private static final String KEY_SECONDS_OFFSET = "jme_train_detector_seconds_offset";
    private static final String KEY_USE_SECONDS_OFFSET = "jme_train_detector_use_seconds_offset";
    // Shared across all detectors for the same dimension. Keep a small value, but not every tick.
    // Refresh at ~1 tick; seconds mode relies on up-to-date rail progress.
    private static final long OCCUPANCY_REFRESH_MILLIS = 50;
    private static final Map<String, DetectorCache> cacheByDimension = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheBuiltAtMillisByDimension = new ConcurrentHashMap<>();

    private static final double SECONDS_DISTANCE_EPSILON = 1E-6;

    public BlockTrainDetector() {
        super();
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new BlockEntity(blockPos, blockState);
    }

    private static void tickServer(World world, BlockState state, BlockPos pos, BlockEntity blockEntity) {
        final Simulator simulator = getSimulator(world);
        if (simulator == null) {
            return;
        }

        final DetectorCache cache = getOrBuildCache(simulator);
        if (cache == null || cache.vehicleSnapshots.isEmpty()) {
            return;
        }

        final boolean shouldPower;
        if (blockEntity.getUseSecondsOffset()) {
            final DetectorRailLocation detectorRailLocation = blockEntity.getDetectorRailLocation(simulator, pos);
            if (detectorRailLocation == null) {
                return;
            }
            shouldPower = hasMatchingTrainAtSecondsOffset(detectorRailLocation, blockEntity.getSecondsOffset(), cache, blockEntity);
        } else {
            if (cache.occupancyByNode.isEmpty()) {
                return;
            }
            final Position detectorNode = blockEntity.getDetectorNode(simulator, pos);
            if (detectorNode == null) {
                return;
            }
            final int nodeRange = blockEntity.getNodeRange();
            final Object2IntAVLTreeMap<Position> reachableNodeDepths = collectReachableNodeDepths(simulator, detectorNode, nodeRange);
            shouldPower = !reachableNodeDepths.isEmpty() && hasMatchingTrainInNodes(reachableNodeDepths, cache.occupancyByNode, blockEntity);
        }

        if (shouldPower) {
            ((BlockTrainPoweredSensorBase) state.getBlock().data).power(world, state, pos);
        }
    }

    private static DetectorCache getOrBuildCache(Simulator simulator) {
        if (simulator == null) {
            return null;
        }

        final String dimension = simulator.dimension;
        final long now = System.currentTimeMillis();
        final Long cachedBuiltAt = cacheBuiltAtMillisByDimension.get(dimension);
        final DetectorCache cached = cacheByDimension.get(dimension);
        if (cached != null && cachedBuiltAt != null && now - cachedBuiltAt < OCCUPANCY_REFRESH_MILLIS) {
            return cached;
        }

        final DetectorCache built = buildCache(simulator);
        cacheByDimension.put(dimension, built);
        cacheBuiltAtMillisByDimension.put(dimension, now);
        return built;
    }

    private static DetectorCache buildCache(Simulator simulator) {
        final Object2ObjectOpenHashMap<Position, NodeOccupancy> occupancyByNode = new Object2ObjectOpenHashMap<>();
        final ArrayList<VehicleSnapshot> vehicleSnapshots = new ArrayList<>();
        if (simulator == null) {
            return new DetectorCache(occupancyByNode, vehicleSnapshots);
        }

        for (final Siding siding : simulator.sidings) {
            final Collection<?> vehicles = getSidingVehicles(siding);
            if (vehicles == null || vehicles.isEmpty()) {
                continue;
            }

            for (final Object vehicleObject : vehicles) {
                if (!(vehicleObject instanceof Vehicle)) {
                    continue;
                }
                final Vehicle vehicle = (Vehicle) vehicleObject;
                if (vehicle.vehicleExtraData == null || vehicle.vehicleExtraData.immutablePath == null || vehicle.vehicleExtraData.immutablePath.isEmpty()) {
                    continue;
                }

                final long routeId = vehicle.vehicleExtraData.getThisRouteId();
                final double railProgress = getVehicleRailProgress(vehicle);
                final double totalVehicleLength = vehicle.vehicleExtraData.getTotalVehicleLength();
                final double tailProgress = railProgress - totalVehicleLength;
                final double speed = getVehicleSpeed(vehicle);
                final java.util.List<PathData> path = vehicle.vehicleExtraData.immutablePath;

                int headIndex = Utilities.getIndexFromConditionalList(path, railProgress - 1);
                if (headIndex < 0) {
                    headIndex = Utilities.getIndexFromConditionalList(path, railProgress);
                }
                if (headIndex < 0 || headIndex >= path.size()) {
                    continue;
                }

                vehicleSnapshots.add(new VehicleSnapshot(vehicle.getId(), routeId, speed, railProgress, tailProgress, path, headIndex, totalVehicleLength));

                // Mark all path segments overlapped by the train's full length (tail->head).
                int index = headIndex;
                while (index >= 0) {
                    final PathData pathData = path.get(index);

                    // Stop once the segment is fully behind the tail.
                    if (pathData.getEndDistance() <= tailProgress) {
                        break;
                    }

                    addOccupancy(occupancyByNode, pathData.getOrderedPosition1(), routeId, speed);
                    addOccupancy(occupancyByNode, pathData.getOrderedPosition2(), routeId, speed);

                    index--;
                }
            }
        }

        return new DetectorCache(occupancyByNode, vehicleSnapshots);
    }

    private static void addOccupancy(Object2ObjectOpenHashMap<Position, NodeOccupancy> occupancyByNode, Position node, long routeId, double speed) {
        if (occupancyByNode == null || node == null) {
            return;
        }

        occupancyByNode.computeIfAbsent(node, ignored -> new NodeOccupancy()).add(routeId, speed);
    }

    private static boolean hasMatchingTrainInNodes(Object2IntAVLTreeMap<Position> reachableNodeDepths, Object2ObjectOpenHashMap<Position, NodeOccupancy> occupancyByNode, BlockEntity blockEntity) {
        if (reachableNodeDepths == null || reachableNodeDepths.isEmpty() || occupancyByNode == null || occupancyByNode.isEmpty() || blockEntity == null) {
            return false;
        }

        for (final Position node : reachableNodeDepths.keySet()) {
            final NodeOccupancy occupancy = occupancyByNode.get(node);
            if (occupancy != null && occupancy.matches(blockEntity)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasMatchingTrainAtSecondsOffset(DetectorRailLocation detectorRailLocation, int secondsOffset, DetectorCache cache, BlockEntity blockEntity) {
        if (detectorRailLocation == null || cache == null || cache.vehicleSnapshots.isEmpty() || blockEntity == null) {
            return false;
        }

        final long nowMillis = System.currentTimeMillis();
        final long keepMillis = secondsOffset < 0 ? Math.max(60_000L, (long) Math.abs((long) secondsOffset) * 1000L + 10_000L) : 60_000L;
        blockEntity.updateTailClearedTimes(nowMillis, detectorRailLocation, cache.vehicleSnapshots, keepMillis);

        // 0 seconds means "the train is currently over this point" (any part of the vehicle occupies it).
        if (secondsOffset == 0) {
            for (final VehicleSnapshot snapshot : cache.vehicleSnapshots) {
                if (!blockEntity.matchesFilter(snapshot.routeId, snapshot.speed)) {
                    continue;
                }
                final double passDistance = findLastPassDistance(snapshot, detectorRailLocation, snapshot.railProgress);
                if (Double.isNaN(passDistance)) {
                    continue;
                }
                if (passDistance >= snapshot.tailProgress - SECONDS_DISTANCE_EPSILON && passDistance <= snapshot.railProgress + SECONDS_DISTANCE_EPSILON) {
                    return true;
                }
            }
            return false;
        }

        if (secondsOffset < 0) {
            final long windowMillis = (long) Math.abs((long) secondsOffset) * 1000L;
            for (final VehicleSnapshot snapshot : cache.vehicleSnapshots) {
                if (!blockEntity.matchesFilter(snapshot.routeId, snapshot.speed)) {
                    continue;
                }

                final Long clearedAtMillis = blockEntity.getLastTailClearedMillis(snapshot.vehicleId);
                if (clearedAtMillis == null) {
                    continue;
                }

                final long deltaMillis = nowMillis - clearedAtMillis;
                if (deltaMillis >= 0 && deltaMillis <= windowMillis) {
                    return true;
                }
            }
            return false;
        }

        final int windowSeconds = secondsOffset;
        for (final VehicleSnapshot snapshot : cache.vehicleSnapshots) {
            if (!blockEntity.matchesFilter(snapshot.routeId, snapshot.speed)) {
                continue;
            }
            if (!(snapshot.speed > 0) || Double.isNaN(snapshot.speed)) {
                continue;
            }

            final double passDistance = findNextPassDistance(snapshot, detectorRailLocation, snapshot.railProgress);
            if (Double.isNaN(passDistance)) {
                continue;
            }

            final double deltaSeconds = (passDistance - snapshot.railProgress) / (snapshot.speed * 1000D);

            if (deltaSeconds >= 0 && deltaSeconds <= windowSeconds) {
                return true;
            }
        }

        return false;
    }

    private static double findNextPassDistance(VehicleSnapshot snapshot, DetectorRailLocation detectorRailLocation, double referenceProgress) {
        final java.util.List<PathData> path = snapshot.path;
        if (path == null || path.isEmpty() || detectorRailLocation == null) {
            return Double.NaN;
        }

        for (int i = Math.max(0, snapshot.headIndex); i < path.size(); i++) {
            final PathData pathData = path.get(i);

            if (!detectorRailLocation.matches(pathData)) {
                continue;
            }

            final double passDistance = detectorRailLocation.getPassDistance(pathData);
            if (passDistance >= referenceProgress - SECONDS_DISTANCE_EPSILON) {
                return passDistance;
            }
        }

        return Double.NaN;
    }

    private static double findLastPassDistance(VehicleSnapshot snapshot, DetectorRailLocation detectorRailLocation, double referenceProgress) {
        final java.util.List<PathData> path = snapshot.path;
        if (path == null || path.isEmpty() || detectorRailLocation == null) {
            return Double.NaN;
        }

        for (int i = Math.min(path.size() - 1, Math.max(0, snapshot.headIndex)); i >= 0; i--) {
            final PathData pathData = path.get(i);

            if (!detectorRailLocation.matches(pathData)) {
                continue;
            }

            final double passDistance = detectorRailLocation.getPassDistance(pathData);
            if (passDistance <= referenceProgress + SECONDS_DISTANCE_EPSILON) {
                return passDistance;
            }
        }

        return Double.NaN;
    }

    private static Object2IntAVLTreeMap<Position> collectReachableNodeDepths(Simulator simulator, Position startNode, int maxDepth) {
        final Object2IntAVLTreeMap<Position> depths = new Object2IntAVLTreeMap<>();
        if (simulator == null || startNode == null || maxDepth < 0) {
            return depths;
        }

        final ArrayDeque<Object[]> queue = new ArrayDeque<>();
        depths.put(startNode, 0);
        queue.add(new Object[]{startNode, 0});

        while (!queue.isEmpty()) {
            final Object[] entry = queue.poll();
            final Position currentNode = (Position) entry[0];
            final int depth = (int) entry[1];
            if (depth >= maxDepth) {
                continue;
            }

            final java.util.Map<Position, ?> connections = simulator.positionsToRail.get(currentNode);
            if (connections == null || connections.isEmpty()) {
                continue;
            }

            for (final Position nextNode : connections.keySet()) {
                if (depths.containsKey(nextNode)) {
                    continue;
                }
                depths.put(nextNode, depth + 1);
                queue.add(new Object[]{nextNode, depth + 1});
            }
        }

        return depths;
    }

    private static Set<Position> collectReachableNodesWithin(Simulator simulator, Position source1, Position source2, int maxDepth, Object2IntAVLTreeMap<Position> constraintNodes) {
        final Set<Position> visited = new HashSet<>();
        final ArrayDeque<Object[]> queue = new ArrayDeque<>();
        if (simulator == null || maxDepth < 0 || constraintNodes == null || constraintNodes.isEmpty()) {
            return visited;
        }

        if (source1 != null && constraintNodes.containsKey(source1)) {
            visited.add(source1);
            queue.add(new Object[]{source1, 0});
        }
        if (source2 != null && constraintNodes.containsKey(source2) && visited.add(source2)) {
            queue.add(new Object[]{source2, 0});
        }

        while (!queue.isEmpty()) {
            final Object[] entry = queue.poll();
            final Position currentNode = (Position) entry[0];
            final int depth = (int) entry[1];
            if (depth >= maxDepth) {
                continue;
            }

            final java.util.Map<Position, ?> connections = simulator.positionsToRail.get(currentNode);
            if (connections == null || connections.isEmpty()) {
                continue;
            }

            for (final Position nextNode : connections.keySet()) {
                if (!constraintNodes.containsKey(nextNode)) {
                    continue;
                }
                if (visited.add(nextNode)) {
                    queue.add(new Object[]{nextNode, depth + 1});
                }
            }
        }

        return visited;
    }

    private static Position findClosestRailNode(Simulator simulator, BlockPos detectorPos, double maxDistance) {
        if (simulator == null || detectorPos == null || simulator.positionsToRail.isEmpty()) {
            return null;
        }

        final double detectorX = detectorPos.getX() + 0.5D;
        final double detectorY = detectorPos.getY() + 0.5D;
        final double detectorZ = detectorPos.getZ() + 0.5D;
        final double maxDistanceSquared = maxDistance * maxDistance;

        Position bestPosition = null;
        double bestDistanceSquared = maxDistanceSquared;

        final long baseX = detectorPos.getX();
        final long baseY = detectorPos.getY();
        final long baseZ = detectorPos.getZ();

        final long maxDistanceCeil = (long) Math.ceil(maxDistance);

        // Iterate existing rail nodes instead of probing block positions. This avoids allocating
        // millions of temporary Position objects when the search radius is large.
        for (final Position candidate : simulator.positionsToRail.keySet()) {
            if (candidate == null) {
                continue;
            }

            final long dx = candidate.getX() - baseX;
            final long dz = candidate.getZ() - baseZ;
            if (Math.abs(dx) > maxDistanceCeil || Math.abs(dz) > maxDistanceCeil) {
                continue;
            }

            final long dy = candidate.getY() - baseY;
            if (Math.abs(dy) > NODE_SEARCH_VERTICAL_RADIUS_BLOCKS) {
                continue;
            }

            final double distX = candidate.getX() + 0.5D - detectorX;
            final double distY = candidate.getY() + 0.5D - detectorY;
            final double distZ = candidate.getZ() + 0.5D - detectorZ;
            final double distanceSquared = distX * distX + distY * distY + distZ * distZ;
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestPosition = candidate;
            }
        }

        return bestPosition;
    }

    private static Simulator getSimulator(World world) {
        final String worldId = Init.getWorldId(world);
        if (worldId == null || worldId.isEmpty()) {
            return null;
        }

        final String normalizedWorldId = normalizeDimensionId(worldId);

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
                Main.LOGGER.warn("[{}] Failed to resolve simulator for train detector", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    private static String normalizeDimensionId(String id) {
        if (id == null) {
            return "";
        }

        // MTR commonly uses "namespace/path" but some environments may hand us "namespace:path".
        final String normalized = id.trim().replace(':', '/');
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static int clampSecondsOffset(int secondsOffset) {
        return Math.max(MIN_SECONDS_OFFSET, Math.min(MAX_SECONDS_OFFSET, secondsOffset));
    }

    private static Collection<?> getSidingVehicles(Siding siding) {
        if (siding == null) {
            return null;
        }

        try {
            Field localSidingVehiclesField = sidingVehiclesField;
            if (localSidingVehiclesField == null) {
                try {
                    localSidingVehiclesField = findField(siding.getClass(), "vehicles");
                } catch (NoSuchFieldException ignored) {
                    localSidingVehiclesField = findVehiclesCollectionField(siding);
                }
                if (localSidingVehiclesField == null) {
                    return null;
                }
                localSidingVehiclesField.setAccessible(true);
                sidingVehiclesField = localSidingVehiclesField;
            }

            final Object vehiclesObject = localSidingVehiclesField.get(siding);
            return vehiclesObject instanceof Collection<?> ? (Collection<?>) vehiclesObject : null;
        } catch (Exception exception) {
            if (!loggedVehiclesFailure) {
                loggedVehiclesFailure = true;
                Main.LOGGER.warn("[{}] Failed to read siding vehicles for train detector", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    private static Field findVehiclesCollectionField(Siding siding) {
        if (siding == null) {
            return null;
        }

        // Try to find a field that looks like "vehicles" without depending on the exact name.
        Class<?> check = siding.getClass();
        while (check != null) {
            final Field[] fields = check.getDeclaredFields();

            // Prefer fields with "vehicle" in the name.
            for (final Field field : fields) {
                if (!Collection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                final String name = field.getName();
                if (name == null || !name.toLowerCase().contains("vehicle")) {
                    continue;
                }
                return field;
            }

            for (final Field field : fields) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    return field;
                }
            }

            check = check.getSuperclass();
        }

        return null;
    }

    private static double getVehicleRailProgress(Vehicle vehicle) {
        if (vehicle == null) {
            return 0;
        }

        try {
            Field localVehicleRailProgressField = vehicleRailProgressField;
            if (localVehicleRailProgressField == null) {
                localVehicleRailProgressField = findField(vehicle.getClass(), "railProgress");
                localVehicleRailProgressField.setAccessible(true);
                vehicleRailProgressField = localVehicleRailProgressField;
            }
            return localVehicleRailProgressField.getDouble(vehicle);
        } catch (Exception exception) {
            if (!loggedVehicleProgressFailure) {
                loggedVehicleProgressFailure = true;
                Main.LOGGER.warn("[{}] Failed reading vehicle rail progress for train detector", Jme.MOD_ID, exception);
            }
            return 0;
        }
    }

    private static double getVehicleSpeed(Vehicle vehicle) {
        if (vehicle == null) {
            return Double.NaN;
        }

        try {
            Field localVehicleSpeedField = vehicleSpeedField;
            if (localVehicleSpeedField == null) {
                localVehicleSpeedField = findField(vehicle.getClass(), "speed");
                localVehicleSpeedField.setAccessible(true);
                vehicleSpeedField = localVehicleSpeedField;
            }
            return localVehicleSpeedField.getDouble(vehicle);
        } catch (Exception exception) {
            if (!loggedVehicleSpeedFailure) {
                loggedVehicleSpeedFailure = true;
                Main.LOGGER.warn("[{}] Failed reading vehicle speed for train detector", Jme.MOD_ID, exception);
            }
            return Double.NaN;
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
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

    public static class BlockEntity extends BlockTrainSensorBase.BlockEntityBase {

        private int nodeRange = DEFAULT_NODE_RANGE;
        private int secondsOffset;
        private boolean useSecondsOffset;
        private DetectorRailLocation cachedDetectorRailLocation;
        private int tickCounter;
        private int detectorNodeRefreshCounter;
        private Position cachedDetectorNode;
        private int detectorRailRefreshCounter;
        private final Set<Long> occupiedVehicleIds = new HashSet<>();
        private final Map<Long, Long> tailClearedAtMillisByVehicle = new HashMap<>();

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.TRAIN_DETECTOR_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            final int rawNodeRange = compoundTag.getInt(KEY_NODE_RANGE);
            nodeRange = rawNodeRange <= 0 ? DEFAULT_NODE_RANGE : Math.max(MIN_NODE_RANGE, Math.min(MAX_NODE_RANGE, rawNodeRange));
            secondsOffset = clampSecondsOffset(compoundTag.getInt(KEY_SECONDS_OFFSET));
            useSecondsOffset = compoundTag.getBoolean(KEY_USE_SECONDS_OFFSET);
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            compoundTag.putInt(KEY_NODE_RANGE, nodeRange);
            compoundTag.putInt(KEY_SECONDS_OFFSET, secondsOffset);
            compoundTag.putBoolean(KEY_USE_SECONDS_OFFSET, useSecondsOffset);
        }

        @Override
        public void blockEntityTick() {
            final World world = getWorld2();
            if (world == null || world.isClient()) {
                return;
            }

            // Tick every tick; this improves reliability for fast trains and short trigger windows.
            tickCounter++;

            BlockTrainDetector.tickServer(world, getCachedState2(), getPos2(), this);
        }

        private Position getDetectorNode(Simulator simulator, BlockPos detectorPos) {
            detectorNodeRefreshCounter++;
            // Refresh infrequently; rail graphs are large and scanning for the nearest node is expensive.
            if (cachedDetectorNode != null && detectorNodeRefreshCounter < 200 && simulator.positionsToRail.containsKey(cachedDetectorNode)) {
                return cachedDetectorNode;
            }

            detectorNodeRefreshCounter = 0;
            // Node range is in rail nodes, not blocks. In practice rail nodes can be far apart
            // (especially for long platforms), so use a generous block radius for node discovery.
            final double searchRadiusBlocks = Math.max(128D, Math.min(2048D, nodeRange * 64D));
            cachedDetectorNode = findClosestRailNode(simulator, detectorPos, searchRadiusBlocks);
            return cachedDetectorNode;
        }

        private DetectorRailLocation getDetectorRailLocation(Simulator simulator, BlockPos detectorPos) {
            detectorRailRefreshCounter++;
            if (cachedDetectorRailLocation != null && detectorRailRefreshCounter < 200 && simulator != null && simulator.railIdMap.containsKey(cachedDetectorRailLocation.railHexId)) {
                return cachedDetectorRailLocation;
            }

            detectorRailRefreshCounter = 0;
            cachedDetectorRailLocation = findClosestRailLocation(simulator, detectorPos, 64);
            return cachedDetectorRailLocation;
        }

        private void updateTailClearedTimes(long nowMillis, DetectorRailLocation detectorRailLocation, java.util.List<VehicleSnapshot> snapshots, long keepMillis) {
            if (detectorRailLocation == null || snapshots == null || snapshots.isEmpty()) {
                occupiedVehicleIds.clear();
                return;
            }

            final Set<Long> newOccupied = new HashSet<>();

            for (final VehicleSnapshot snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }

                final double passDistance = findLastPassDistance(snapshot, detectorRailLocation, snapshot.railProgress);
                if (Double.isNaN(passDistance)) {
                    continue;
                }

                if (passDistance >= snapshot.tailProgress - SECONDS_DISTANCE_EPSILON && passDistance <= snapshot.railProgress + SECONDS_DISTANCE_EPSILON) {
                    newOccupied.add(snapshot.vehicleId);
                }
            }

            for (final Long previousOccupiedVehicleId : occupiedVehicleIds) {
                if (!newOccupied.contains(previousOccupiedVehicleId)) {
                    tailClearedAtMillisByVehicle.put(previousOccupiedVehicleId, nowMillis);
                }
            }

            occupiedVehicleIds.clear();
            occupiedVehicleIds.addAll(newOccupied);

            // Keep the map small; only store recent clears.
            final long cutoffMillis = nowMillis - Math.max(60_000L, keepMillis);
            tailClearedAtMillisByVehicle.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < cutoffMillis);
        }

        private Long getLastTailClearedMillis(long vehicleId) {
            return tailClearedAtMillisByVehicle.get(vehicleId);
        }

        public int getNodeRange() {
            return nodeRange;
        }

        public void setNodeRange(int nodeRange) {
            final int clamped = Math.max(MIN_NODE_RANGE, Math.min(MAX_NODE_RANGE, nodeRange));
            if (this.nodeRange != clamped) {
                this.nodeRange = clamped;
                markDirty2();
            }
        }

        public int getSecondsOffset() {
            return secondsOffset;
        }

        public void setSecondsOffset(int secondsOffset) {
            final int clamped = clampSecondsOffset(secondsOffset);
            if (this.secondsOffset != clamped) {
                this.secondsOffset = clamped;
                markDirty2();
            }
        }

        public boolean getUseSecondsOffset() {
            return useSecondsOffset;
        }

        public void setUseSecondsOffset(boolean useSecondsOffset) {
            if (this.useSecondsOffset != useSecondsOffset) {
                this.useSecondsOffset = useSecondsOffset;
                markDirty2();
            }
        }
    }

    private static final class NodeOccupancy {

        private final LongArrayList movingRouteIds = new LongArrayList(2);
        private final LongArrayList stoppedRouteIds = new LongArrayList(2);

        void add(long routeId, double speed) {
            if (Double.isNaN(speed)) {
                if (!stoppedRouteIds.contains(routeId)) {
                    stoppedRouteIds.add(routeId);
                }
                if (!movingRouteIds.contains(routeId)) {
                    movingRouteIds.add(routeId);
                }
                return;
            }

            final boolean moving = speed > 0;
            final LongArrayList target = moving ? movingRouteIds : stoppedRouteIds;
            if (!target.contains(routeId)) {
                target.add(routeId);
            }
        }

        boolean matches(BlockEntity blockEntity) {
            if (blockEntity == null) {
                return false;
            }

            for (int i = 0; i < stoppedRouteIds.size(); i++) {
                if (blockEntity.matchesFilter(stoppedRouteIds.getLong(i), 0)) {
                    return true;
                }
            }

            for (int i = 0; i < movingRouteIds.size(); i++) {
                if (blockEntity.matchesFilter(movingRouteIds.getLong(i), 1)) {
                    return true;
                }
            }

            return false;
        }
    }

    private record VehicleSnapshot(long vehicleId, long routeId, double speed, double railProgress, double tailProgress, java.util.List<PathData> path, int headIndex, double totalVehicleLength) {
    }

    private record DetectorCache(Object2ObjectOpenHashMap<Position, NodeOccupancy> occupancyByNode, java.util.List<VehicleSnapshot> vehicleSnapshots) {
    }

    private record DetectorRailLocation(String railHexId, Position orderedPosition1, Position orderedPosition2, double offsetFromOrderedPosition1, double railLength, double distanceSquared) {

        boolean matches(PathData pathData) {
            if (pathData == null || orderedPosition1 == null || orderedPosition2 == null) {
                return false;
            }
            return orderedPosition1.equals(pathData.getOrderedPosition1()) && orderedPosition2.equals(pathData.getOrderedPosition2());
        }

        double getPassDistance(PathData pathData) {
            if (pathData == null) {
                return Double.NaN;
            }

            final double segmentLength = pathData.getEndDistance() - pathData.getStartDistance();
            if (!(segmentLength > 0) || Double.isInfinite(segmentLength) || Double.isNaN(segmentLength)) {
                return Double.NaN;
            }

            final double clampedOffsetOrdered = clamp(offsetFromOrderedPosition1, 0, segmentLength);
            final double offsetFromStart = pathData.reversePositions ? segmentLength - clampedOffsetOrdered : clampedOffsetOrdered;
            return pathData.getStartDistance() + offsetFromStart;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static DetectorRailLocation findClosestRailLocation(Simulator simulator, BlockPos detectorPos, double searchRadiusBlocks) {
        if (simulator == null || detectorPos == null || simulator.rails == null || simulator.rails.isEmpty()) {
            return null;
        }

        final double maxDistanceSquared = Math.max(1, searchRadiusBlocks) * Math.max(1, searchRadiusBlocks);
        final Vector detectorPoint = new Vector(detectorPos.getX() + 0.5, detectorPos.getY() + 0.5, detectorPos.getZ() + 0.5);

        Rail bestRail = null;
        double bestOffset = 0;
        double bestRailLength = 0;
        double bestDistanceSquared = maxDistanceSquared;

        for (final Rail rail : simulator.rails) {
            if (rail == null || rail.railMath == null) {
                continue;
            }

            final double minDistanceSquaredToAabb = getMinDistanceSquaredToAabb(detectorPoint, rail.railMath.minX, rail.railMath.minY, rail.railMath.minZ, rail.railMath.maxX, rail.railMath.maxY, rail.railMath.maxZ);
            if (minDistanceSquaredToAabb > bestDistanceSquared) {
                continue;
            }

            final double railLength = rail.railMath.getLength();
            if (!(railLength > 0) || Double.isNaN(railLength) || Double.isInfinite(railLength)) {
                continue;
            }

            final ClosestPoint closestPoint = findClosestPointOnRail(rail, detectorPoint, bestDistanceSquared);
            if (closestPoint == null) {
                continue;
            }

            if (closestPoint.distanceSquared <= bestDistanceSquared) {
                bestRail = rail;
                bestOffset = closestPoint.offsetFromOrderedStart;
                bestRailLength = railLength;
                bestDistanceSquared = closestPoint.distanceSquared;
            }
        }

        if (bestRail == null || bestDistanceSquared > MAX_RAIL_ATTACH_DISTANCE_SQUARED) {
            return null;
        }

        final String railHexId = bestRail.getHexId();
        final Position[] endpoints = parseRailHexId(railHexId);
        if (endpoints == null) {
            return null;
        }

        return new DetectorRailLocation(railHexId, endpoints[0], endpoints[1], bestOffset, bestRailLength, bestDistanceSquared);
    }

    private static ClosestPoint findClosestPointOnRail(Rail rail, Vector detectorPoint, double bestDistanceSquared) {
        if (rail == null || rail.railMath == null || detectorPoint == null) {
            return null;
        }

        final double railLength = rail.railMath.getLength();
        if (!(railLength > 0) || Double.isNaN(railLength) || Double.isInfinite(railLength)) {
            return null;
        }

        // Coarse scan then refine near the best point.
        final double coarseStep = Math.max(0.5, Math.min(4, railLength / 64D));
        double bestOffset = 0;
        double bestLocalDistanceSquared = bestDistanceSquared;

        for (double offset = 0; offset <= railLength; offset += coarseStep) {
            final Vector point = rail.railMath.getPosition(offset, false);
            final double distanceSquared = getDistanceSquared(detectorPoint, point);
            if (distanceSquared <= bestLocalDistanceSquared) {
                bestLocalDistanceSquared = distanceSquared;
                bestOffset = offset;
            }
        }

        final double fineStep = 0.25;
        final double refineRadius = coarseStep;
        for (double offset = Math.max(0, bestOffset - refineRadius); offset <= Math.min(railLength, bestOffset + refineRadius); offset += fineStep) {
            final Vector point = rail.railMath.getPosition(offset, false);
            final double distanceSquared = getDistanceSquared(detectorPoint, point);
            if (distanceSquared <= bestLocalDistanceSquared) {
                bestLocalDistanceSquared = distanceSquared;
                bestOffset = offset;
            }
        }

        return new ClosestPoint(bestOffset, bestLocalDistanceSquared);
    }

    private static double getMinDistanceSquaredToAabb(Vector point, long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {
        final double x = point.x();
        final double y = point.y();
        final double z = point.z();

        final double dx;
        if (x < minX) {
            dx = minX - x;
        } else if (x > maxX) {
            dx = x - maxX;
        } else {
            dx = 0;
        }

        final double dy;
        if (y < minY) {
            dy = minY - y;
        } else if (y > maxY) {
            dy = y - maxY;
        } else {
            dy = 0;
        }

        final double dz;
        if (z < minZ) {
            dz = minZ - z;
        } else if (z > maxZ) {
            dz = z - maxZ;
        } else {
            dz = 0;
        }

        return dx * dx + dy * dy + dz * dz;
    }

    private static double getDistanceSquared(Vector a, Vector b) {
        final double dx = a.x() - b.x();
        final double dy = a.y() - b.y();
        final double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Position[] parseRailHexId(String railHexId) {
        if (railHexId == null || railHexId.isEmpty()) {
            return null;
        }

        final String[] parts = railHexId.split("-");
        if (parts.length != 6) {
            return null;
        }

        try {
            final long x1 = parseHexLong(parts[0]);
            final long y1 = parseHexLong(parts[1]);
            final long z1 = parseHexLong(parts[2]);
            final long x2 = parseHexLong(parts[3]);
            final long y2 = parseHexLong(parts[4]);
            final long z2 = parseHexLong(parts[5]);
            return new Position[]{new Position(x1, y1, z1), new Position(x2, y2, z2)};
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long parseHexLong(String hex) {
        // Rail IDs are padded 64-bit hex strings (two's complement for negatives).
        return Long.parseUnsignedLong(hex, 16);
    }

    private record ClosestPoint(double offsetFromOrderedStart, double distanceSquared) {
    }
}
