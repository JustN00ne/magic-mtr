package org.justnoone.jme.mixin;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockTrainPoweredSensorBase;
import org.mtr.mod.block.BlockTrainScheduleSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = BlockTrainScheduleSensor.BlockEntity.class, remap = false)
public abstract class TrainScheduleSensorRailDetectMixin {

    @Unique
    private static volatile Field jme$initMainField;
    @Unique
    private static volatile Field jme$mainSimulatorsField;
    @Unique
    private static volatile Field jme$sidingVehiclesField;
    @Unique
    private static volatile Field jme$vehicleRailProgressField;
    @Unique
    private static volatile boolean jme$loggedSimulatorFailure;
    @Unique
    private static volatile boolean jme$loggedVehicleFailure;
    @Unique
    private static volatile boolean jme$loggedProgressFailure;

    @Unique
    private static final long JME_OCCUPANCY_REFRESH_MILLIS = 200;
    @Unique
    private static final int JME_SENSOR_TICK_DIVIDER = 5;
    @Unique
    private static final int JME_NODE_SEARCH_RADIUS_BLOCKS = 6;
    @Unique
    private static final int JME_NODE_SEARCH_VERTICAL_RADIUS_BLOCKS = 2;
    @Unique
    private static final long JME_NODE_CACHE_MILLIS = 10_000;
    @Unique
    private static final Map<String, Object2ObjectOpenHashMap<Position, LongArrayList>> jme$routeIdsByNodeByDimension = new ConcurrentHashMap<>();
    @Unique
    private static final Map<String, Long> jme$routeIdsByNodeBuiltAtMillisByDimension = new ConcurrentHashMap<>();

    @Unique
    private int jme$tickCounter;
    @Unique
    private Position jme$cachedClosestNode;
    @Unique
    private long jme$cachedClosestNodeAtMillis;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static <T extends BlockEntityExtension> void jme$detectTrainOnCurrentRail(@Nullable World world, BlockPos pos, T blockEntity, CallbackInfo ci) {
        if (world == null || world.isClient() || !(blockEntity instanceof BlockTrainScheduleSensor.BlockEntity)) {
            return;
        }
        final BlockTrainScheduleSensor.BlockEntity scheduleSensor = (BlockTrainScheduleSensor.BlockEntity) blockEntity;

        // This runs on the server tick thread for every schedule sensor. Keep it cheap.
        final TrainScheduleSensorRailDetectMixin self = (TrainScheduleSensorRailDetectMixin) (Object) scheduleSensor;
        self.jme$tickCounter++;
        if (self.jme$tickCounter % JME_SENSOR_TICK_DIVIDER != 0) {
            return;
        }

        final Simulator simulator = jme$getSimulator(world);
        if (simulator == null || simulator.positionsToRail.isEmpty()) {
            return;
        }

        final Position sensorNode = self.jme$getCachedOrFindClosestRailNode(simulator, pos);
        if (sensorNode == null) {
            return;
        }

        final Object2ObjectOpenHashMap<Position, LongArrayList> routeIdsByNode = jme$getOrBuildRouteIdsByNode(simulator);
        if (!jme$routeIdsByNodeHasMatchingTrain(routeIdsByNode, sensorNode, scheduleSensor)) {
            return;
        }

        final BlockState blockState = world.getBlockState(pos);
        if (blockState.getBlock().data instanceof BlockTrainPoweredSensorBase) {
            final BlockTrainPoweredSensorBase poweredSensor = (BlockTrainPoweredSensorBase) blockState.getBlock().data;
            poweredSensor.power(world, blockState, pos);
        }
    }

    @Unique
    private Position jme$getCachedOrFindClosestRailNode(Simulator simulator, BlockPos detectorPos) {
        if (simulator == null || detectorPos == null) {
            return null;
        }

        final long now = System.currentTimeMillis();
        final Position cached = jme$cachedClosestNode;
        if (cached != null && now - jme$cachedClosestNodeAtMillis < JME_NODE_CACHE_MILLIS) {
            // Re-validate quickly in case rails were removed.
            if (simulator.positionsToRail.containsKey(cached)) {
                return cached;
            }
        }

        final Position found = jme$findClosestRailNodeNearby(simulator, detectorPos, JME_NODE_SEARCH_RADIUS_BLOCKS);
        jme$cachedClosestNode = found;
        jme$cachedClosestNodeAtMillis = now;
        return found;
    }

    @Unique
    private static Object2ObjectOpenHashMap<Position, LongArrayList> jme$getOrBuildRouteIdsByNode(Simulator simulator) {
        if (simulator == null) {
            return new Object2ObjectOpenHashMap<>();
        }

        final String dimension = simulator.dimension;
        final long now = System.currentTimeMillis();
        final Long cachedBuiltAt = jme$routeIdsByNodeBuiltAtMillisByDimension.get(dimension);
        final Object2ObjectOpenHashMap<Position, LongArrayList> cached = jme$routeIdsByNodeByDimension.get(dimension);
        if (cached != null && cachedBuiltAt != null && now - cachedBuiltAt < JME_OCCUPANCY_REFRESH_MILLIS) {
            return cached;
        }

        final Object2ObjectOpenHashMap<Position, LongArrayList> built = jme$buildRouteIdsByNode(simulator);
        jme$routeIdsByNodeByDimension.put(dimension, built);
        jme$routeIdsByNodeBuiltAtMillisByDimension.put(dimension, now);
        return built;
    }

    @Unique
    private static boolean jme$routeIdsByNodeHasMatchingTrain(Object2ObjectOpenHashMap<Position, LongArrayList> routeIdsByNode, Position node, BlockTrainScheduleSensor.BlockEntity scheduleSensor) {
        if (routeIdsByNode == null || node == null || scheduleSensor == null) {
            return false;
        }

        final LongArrayList routeIds = routeIdsByNode.get(node);
        if (routeIds == null || routeIds.isEmpty()) {
            return false;
        }

        for (int i = 0; i < routeIds.size(); i++) {
            if (scheduleSensor.matchesFilter(routeIds.getLong(i), 1)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static Object2ObjectOpenHashMap<Position, LongArrayList> jme$buildRouteIdsByNode(Simulator simulator) {
        final Object2ObjectOpenHashMap<Position, LongArrayList> routeIdsByNode = new Object2ObjectOpenHashMap<>();
        if (simulator == null) {
            return routeIdsByNode;
        }

        for (final Siding siding : simulator.sidings) {
            final Collection<?> vehicles = jme$getSidingVehicles(siding);
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

                final PathData pathData = jme$getCurrentPathData(vehicle);
                if (pathData == null) {
                    continue;
                }

                final long routeId = vehicle.vehicleExtraData.getThisRouteId();
                jme$addRouteId(routeIdsByNode, pathData.getOrderedPosition1(), routeId);
                jme$addRouteId(routeIdsByNode, pathData.getOrderedPosition2(), routeId);
            }
        }

        return routeIdsByNode;
    }

    @Unique
    private static void jme$addRouteId(Object2ObjectOpenHashMap<Position, LongArrayList> routeIdsByNode, Position node, long routeId) {
        if (routeIdsByNode == null || node == null || routeId == 0) {
            return;
        }

        final LongArrayList routeIds = routeIdsByNode.computeIfAbsent(node, ignored -> new LongArrayList(2));
        if (!routeIds.contains(routeId)) {
            routeIds.add(routeId);
        }
    }

    @Unique
    private static PathData jme$getCurrentPathData(Vehicle vehicle) {
        try {
            final double railProgress = jme$getVehicleRailProgress(vehicle);
            final java.util.List<PathData> path = vehicle.vehicleExtraData.immutablePath;
            if (path == null || path.isEmpty()) {
                return null;
            }

            final int pathIndex = Utilities.getIndexFromConditionalList(path, railProgress);
            return Utilities.getElement(path, pathIndex);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static double jme$getVehicleRailProgress(Vehicle vehicle) {
        try {
            Field localVehicleRailProgressField = jme$vehicleRailProgressField;
            if (localVehicleRailProgressField == null) {
                localVehicleRailProgressField = jme$findField(vehicle.getClass(), "railProgress");
                localVehicleRailProgressField.setAccessible(true);
                jme$vehicleRailProgressField = localVehicleRailProgressField;
            }
            return localVehicleRailProgressField.getDouble(vehicle);
        } catch (Exception exception) {
            if (!jme$loggedProgressFailure) {
                jme$loggedProgressFailure = true;
                Main.LOGGER.warn("[{}] Failed reading vehicle rail progress for schedule rail detection", Jme.MOD_ID, exception);
            }
            return 0;
        }
    }

    @Unique
    private static Position jme$findClosestRailNodeNearby(Simulator simulator, BlockPos detectorPos, int searchRadiusBlocks) {
        if (simulator == null || detectorPos == null) {
            return null;
        }

        final double detectorX = detectorPos.getX() + 0.5D;
        final double detectorY = detectorPos.getY() + 0.5D;
        final double detectorZ = detectorPos.getZ() + 0.5D;
        final int radius = Math.max(1, searchRadiusBlocks);
        final double maxDistanceSquared = radius * (double) radius;

        Position bestPosition = null;
        double bestDistanceSquared = maxDistanceSquared;

        final long baseX = detectorPos.getX();
        final long baseY = detectorPos.getY();
        final long baseZ = detectorPos.getZ();

        // Avoid scanning the full rail graph (can be tens of thousands of nodes). Instead, probe nearby blocks.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Skip points outside the horizontal circle early.
                final double dxCenter = dx + 0.5D;
                final double dzCenter = dz + 0.5D;
                if (dxCenter * dxCenter + dzCenter * dzCenter > maxDistanceSquared) {
                    continue;
                }

                for (int dy = -JME_NODE_SEARCH_VERTICAL_RADIUS_BLOCKS; dy <= JME_NODE_SEARCH_VERTICAL_RADIUS_BLOCKS; dy++) {
                    final long x = baseX + dx;
                    final long y = baseY + dy;
                    final long z = baseZ + dz;
                    final Position candidate = new Position(x, y, z);
                    if (!simulator.positionsToRail.containsKey(candidate)) {
                        continue;
                    }

                    final double distX = x + 0.5D - detectorX;
                    final double distY = y + 0.5D - detectorY;
                    final double distZ = z + 0.5D - detectorZ;
                    final double distanceSquared = distX * distX + distY * distY + distZ * distZ;
                    if (distanceSquared <= bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        bestPosition = candidate;
                    }
                }
            }
        }

        return bestPosition;
    }

    @Unique
    private static Simulator jme$getSimulator(World world) {
        final String worldId = Init.getWorldId(world);
        if (worldId == null || worldId.isEmpty()) {
            return null;
        }

        try {
            Field localInitMainField = jme$initMainField;
            if (localInitMainField == null) {
                localInitMainField = Init.class.getDeclaredField("main");
                localInitMainField.setAccessible(true);
                jme$initMainField = localInitMainField;
            }

            final Object main = localInitMainField.get(null);
            if (main == null) {
                return null;
            }

            Field localMainSimulatorsField = jme$mainSimulatorsField;
            if (localMainSimulatorsField == null) {
                localMainSimulatorsField = main.getClass().getDeclaredField("simulators");
                localMainSimulatorsField.setAccessible(true);
                jme$mainSimulatorsField = localMainSimulatorsField;
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
                if (worldId.equals(simulator.dimension)) {
                    return simulator;
                }
            }

            return count == 1 ? fallback : null;
        } catch (Exception exception) {
            if (!jme$loggedSimulatorFailure) {
                jme$loggedSimulatorFailure = true;
                Main.LOGGER.warn("[{}] Failed resolving simulator for schedule rail detection", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    @Unique
    private static Collection<?> jme$getSidingVehicles(Siding siding) {
        try {
            Field localSidingVehiclesField = jme$sidingVehiclesField;
            if (localSidingVehiclesField == null) {
                localSidingVehiclesField = siding.getClass().getDeclaredField("vehicles");
                localSidingVehiclesField.setAccessible(true);
                jme$sidingVehiclesField = localSidingVehiclesField;
            }

            final Object vehiclesObject = localSidingVehiclesField.get(siding);
            return vehiclesObject instanceof Collection<?> ? (Collection<?>) vehiclesObject : null;
        } catch (Exception exception) {
            if (!jme$loggedVehicleFailure) {
                jme$loggedVehicleFailure = true;
                Main.LOGGER.warn("[{}] Failed reading siding vehicles for schedule rail detection", Jme.MOD_ID, exception);
            }
            return null;
        }
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
