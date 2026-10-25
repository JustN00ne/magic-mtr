package org.justnoone.jme.block;

import org.justnoone.jme.Jme;
import org.mtr.core.Main;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockHelper;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

public class BlockAxleCounter extends BlockExtension implements BlockWithEntity {

    public static final byte ROLE_NONE = 0;
    public static final byte ROLE_ACTIVATOR = 1;
    public static final byte ROLE_DEACTIVATOR = 2;

    private static final double MAX_RAIL_ATTACH_DISTANCE_BLOCKS = 6;
    private static final double MAX_RAIL_ATTACH_DISTANCE_SQUARED = MAX_RAIL_ATTACH_DISTANCE_BLOCKS * MAX_RAIL_ATTACH_DISTANCE_BLOCKS;
    private static final int LOCAL_NODE_PROBE_RADIUS_BLOCKS = 24;
    private static final int NODE_SEARCH_VERTICAL_RADIUS_BLOCKS = 6;
    private static final long OCCUPANCY_REFRESH_MILLIS = 1000;
    private static final int TICK_DIVIDER = 3;
    private static final int REFRESH_CACHE_TICKS = 400;

    private static volatile Field initMainField;
    private static volatile Field mainSimulatorsField;
    private static volatile Field sidingVehiclesField;
    private static volatile Field vehicleRailProgressField;
    private static volatile boolean loggedSimulatorFailure;
    private static volatile boolean loggedVehiclesFailure;
    private static volatile boolean loggedVehicleProgressFailure;

    private static final java.util.Map<String, AxleCounterCache> cacheByDimension = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> cacheBuiltAtMillisByDimension = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Object> cacheBuildLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public static final org.mtr.mapping.holder.DirectionProperty FACING = new org.mtr.mapping.holder.DirectionProperty(net.minecraft.state.property.DirectionProperty.of("facing", net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST));
    // Typed to the vanilla enum: BlockState#with passes the value straight to Minecraft, so it must be the unwrapped
    // Direction (passing the mapping holder crashed placement with "not an allowed value").
    private static final org.mtr.mapping.holder.Property<net.minecraft.util.math.Direction> FACING_PROPERTY = org.mtr.mapping.holder.Property.cast(FACING);
    /**
     * Set while a train occupies the detected rail node; the block then emits a redstone signal of 15 like a vanilla
     * detector rail.
     */
    public static final org.mtr.mapping.holder.BooleanProperty POWERED = org.mtr.mapping.holder.BooleanProperty.of("powered");
    private static final org.mtr.mapping.holder.Property<Boolean> POWERED_PROPERTY = org.mtr.mapping.holder.Property.cast(POWERED);

    public BlockAxleCounter() {
        super(BlockHelper.createBlockSettings(true, true).nonOpaque());
    }

    @Override
    public BlockState getPlacementState2(org.mtr.mapping.holder.ItemPlacementContext context) {
        return super.getPlacementState2(context).with(FACING_PROPERTY, context.getPlayerFacing().getOpposite().data).with(POWERED_PROPERTY, false);
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        properties.add(FACING);
        properties.add(POWERED);
    }

    @Override
    public boolean emitsRedstonePower2(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower2(BlockState state, org.mtr.mapping.holder.BlockView world, BlockPos pos, org.mtr.mapping.holder.Direction direction) {
        return state.get(POWERED_PROPERTY) ? 15 : 0;
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    public static void tickServer(World world, BlockPos pos, BlockEntity blockEntity) {
        final Simulator simulator = getSimulator(world);
        if (simulator == null) {
            return;
        }

        final AxleCounterCache cache = getOrBuildCache(simulator);
        if (cache == null || cache.vehicleSnapshots.isEmpty()) {
            blockEntity.setDetecting(false);
            return;
        }

        final Position detectorNode = blockEntity.getDetectorNode(simulator, pos);
        if (detectorNode == null) {
            blockEntity.setDetecting(false);
            return;
        }

        final boolean detected = cache.occupiedNodes.contains(detectorNode);
        blockEntity.setDetecting(detected);
    }

    /**
     * Whether a train covers the given point of a rail, measured along the rail's {@code RailMath} (not reversed).
     * Used by the axle counters attached to rails: unlike the legacy block's nearest-node check, this only detects
     * trains actually passing over the counter.
     */
    public static boolean isRailPointOccupied(Simulator simulator, Rail rail, double railProgress) {
        final AxleCounterCache cache = getOrBuildCache(simulator);
        if (cache == null) {
            return false;
        }
        for (final VehicleSnapshot vehicle : cache.vehicleSnapshots) {
            // The head can already be on the segment after the one found for railProgress - 1.
            for (int index = Math.min(vehicle.headIndex + 1, vehicle.path.size() - 1); index >= 0; index--) {
                final org.mtr.core.data.PathData pathData = vehicle.path.get(index);
                if (pathData.getEndDistance() <= vehicle.tailProgress) {
                    break;
                }
                final Rail pathRail = pathData.getRail();
                if (pathRail != rail && (pathRail == null || !pathRail.getHexId().equals(rail.getHexId()))) {
                    continue;
                }
                final double length = pathData.getEndDistance() - pathData.getStartDistance();
                // PathData#getPosition reads the rail reversed when the train runs from its second position to its first.
                final double alongPath = pathData.getStartDistance() + (pathData.reversePositions ? length - railProgress : railProgress);
                if (alongPath >= vehicle.tailProgress && alongPath <= vehicle.railProgress) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AxleCounterCache getOrBuildCache(Simulator simulator) {
        if (simulator == null) {
            return null;
        }

        final String dimension = simulator.dimension;
        final long now = System.currentTimeMillis();
        final Long cachedBuiltAt = cacheBuiltAtMillisByDimension.get(dimension);
        final AxleCounterCache cached = cacheByDimension.get(dimension);
        if (cached != null && cachedBuiltAt != null && now - cachedBuiltAt < OCCUPANCY_REFRESH_MILLIS) {
            return cached;
        }

        final Object lock = cacheBuildLocks.computeIfAbsent(dimension, ignored -> new Object());
        synchronized (lock) {
            final long lockedNow = System.currentTimeMillis();
            final Long rebuiltAt = cacheBuiltAtMillisByDimension.get(dimension);
            final AxleCounterCache existing = cacheByDimension.get(dimension);
            if (existing != null && rebuiltAt != null && lockedNow - rebuiltAt < OCCUPANCY_REFRESH_MILLIS) {
                return existing;
            }

            final AxleCounterCache built;
            try {
                built = buildCache(simulator);
            } catch (java.util.ConcurrentModificationException | IndexOutOfBoundsException | NullPointerException exception) {
                // The simulator runs on its own thread and may change its vehicle lists while they are read here;
                // keep the previous snapshot and retry on the next refresh instead of failing the server tick.
                return existing;
            }
            cacheByDimension.put(dimension, built);
            cacheBuiltAtMillisByDimension.put(dimension, lockedNow);
            return built;
        }
    }

    private static AxleCounterCache buildCache(Simulator simulator) {
        final java.util.Set<Position> occupiedNodes = new java.util.HashSet<>();
        final java.util.List<VehicleSnapshot> vehicleSnapshots = new java.util.ArrayList<>();
        if (simulator == null) {
            return new AxleCounterCache(occupiedNodes, vehicleSnapshots);
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

                final double railProgress = getVehicleRailProgress(vehicle);
                final double totalVehicleLength = vehicle.vehicleExtraData.getTotalVehicleLength();
                final double tailProgress = railProgress - totalVehicleLength;
                final java.util.List<org.mtr.core.data.PathData> path = vehicle.vehicleExtraData.immutablePath;

                int headIndex = Utilities.getIndexFromConditionalList(path, railProgress - 1);
                if (headIndex < 0) {
                    headIndex = Utilities.getIndexFromConditionalList(path, railProgress);
                }
                if (headIndex < 0 || headIndex >= path.size()) {
                    continue;
                }

                vehicleSnapshots.add(new VehicleSnapshot(vehicle.getId(), railProgress, tailProgress, path, headIndex));

                int index = headIndex;
                while (index >= 0) {
                    final org.mtr.core.data.PathData pathData = path.get(index);
                    if (pathData.getEndDistance() <= tailProgress) {
                        break;
                    }
                    occupiedNodes.add(pathData.getOrderedPosition1());
                    occupiedNodes.add(pathData.getOrderedPosition2());
                    index--;
                }
            }
        }

        return new AxleCounterCache(occupiedNodes, vehicleSnapshots);
    }

    private static Position findClosestRailNode(Simulator simulator, BlockPos detectorPos, double maxDistance) {
        if (simulator == null || detectorPos == null || simulator.positionsToRail.isEmpty()) {
            return null;
        }

        final double detectorX = detectorPos.getX() + 0.5D;
        final double detectorY = detectorPos.getY() + 0.5D;
        final double detectorZ = detectorPos.getZ() + 0.5D;
        final double maxDistanceSquared = maxDistance * maxDistance;

        final long baseX = detectorPos.getX();
        final long baseY = detectorPos.getY();
        final long baseZ = detectorPos.getZ();
        final long maxDistanceCeil = (long) Math.ceil(maxDistance);

        Position bestPosition = null;
        double bestDistanceSquared = maxDistanceSquared;

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

    /**
     * The MTR simulator (rails, sidings, vehicles) of a server world's dimension, or {@code null}.
     */
    @javax.annotation.Nullable
    public static Simulator getSimulator(World world) {
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
                Main.LOGGER.warn("[{}] Failed to resolve simulator for axle counter", Jme.MOD_ID, exception);
            }
            return null;
        }
    }

    private static String normalizeDimensionId(String id) {
        if (id == null) {
            return "";
        }
        final String normalized = id.trim().replace(':', '/');
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static Collection<?> getSidingVehicles(Siding siding) {
        if (siding == null) {
            return null;
        }

        try {
            Field localSidingVehiclesField = sidingVehiclesField;
            if (localSidingVehiclesField == null) {
                localSidingVehiclesField = findField(siding.getClass(), "vehicles");
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
                Main.LOGGER.warn("[{}] Failed to read siding vehicles for axle counter", Jme.MOD_ID, exception);
            }
            return null;
        }
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
                Main.LOGGER.warn("[{}] Failed reading vehicle rail progress for axle counter", Jme.MOD_ID, exception);
            }
            return 0;
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

    public static class BlockEntity extends BlockEntityExtension {

        private byte role = ROLE_NONE;
        private BlockPos controllerPos;
        private boolean detecting;
        private int tickCounter;
        private int nodeRefreshCounter;
        private Position cachedNode;

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.AXLE_COUNTER_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            role = compoundTag.getByte("jme_crossing_role");
            if (compoundTag.contains("jme_crossing_controller_x")) {
                controllerPos = new BlockPos(
                        compoundTag.getInt("jme_crossing_controller_x"),
                        compoundTag.getInt("jme_crossing_controller_y"),
                        compoundTag.getInt("jme_crossing_controller_z")
                );
            }
            detecting = compoundTag.getBoolean("jme_crossing_detecting");
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            compoundTag.putByte("jme_crossing_role", role);
            if (controllerPos != null) {
                compoundTag.putInt("jme_crossing_controller_x", controllerPos.getX());
                compoundTag.putInt("jme_crossing_controller_y", controllerPos.getY());
                compoundTag.putInt("jme_crossing_controller_z", controllerPos.getZ());
            }
            compoundTag.putBoolean("jme_crossing_detecting", detecting);
        }

        @Override
        public void blockEntityTick() {
            final World world = getWorld2();
            if (world == null || world.isClient()) {
                return;
            }

            tickCounter++;
            final int stagger = Math.floorMod(getPos2().getX() * 31 + getPos2().getZ(), TICK_DIVIDER);
            if ((tickCounter + stagger) % TICK_DIVIDER != 0) {
                return;
            }

            BlockAxleCounter.tickServer(world, getPos2(), this);
        }

        public byte getRole() {
            return role;
        }

        public void setRole(byte role) {
            this.role = role;
            markDirty2();
        }

        public BlockPos getControllerPos() {
            return controllerPos;
        }

        public void setControllerPos(BlockPos controllerPos) {
            this.controllerPos = controllerPos;
            markDirty2();
        }

        public boolean isDetecting() {
            return detecting;
        }

        /**
         * The rail node this counter watches. Searching {@code positionsToRail} walks every rail node of the
         * dimension, so the result is kept and only searched again every {@link #REFRESH_CACHE_TICKS} ticks (to
         * pick up rails built or removed nearby).
         */
        private Position getDetectorNode(Simulator simulator, BlockPos pos) {
            if (cachedNode == null || nodeRefreshCounter <= 0 || !simulator.positionsToRail.containsKey(cachedNode)) {
                cachedNode = findClosestRailNode(simulator, pos, LOCAL_NODE_PROBE_RADIUS_BLOCKS);
                nodeRefreshCounter = REFRESH_CACHE_TICKS / TICK_DIVIDER;
            } else {
                nodeRefreshCounter--;
            }
            return cachedNode;
        }

        public void setDetecting(boolean detecting) {
            if (this.detecting != detecting) {
                this.detecting = detecting;
                markDirty2();
            }
            // Also checked when unchanged: counters saved before the "powered" property existed load with the
            // property's default value (true) and must be corrected.
            final World world = getWorld2();
            final BlockState state = getCachedState2();
            if (world != null && !world.isClient() && state.contains(POWERED_PROPERTY) && state.get(POWERED_PROPERTY) != detecting) {
                // Flag 3 notifies neighbours (redstone) and syncs the state to clients.
                world.setBlockState(getPos2(), state.with(POWERED_PROPERTY, detecting), 3);
            }
        }
    }

    private static final class AxleCounterCache {
        private final java.util.Set<Position> occupiedNodes;
        private final java.util.List<VehicleSnapshot> vehicleSnapshots;

        private AxleCounterCache(java.util.Set<Position> occupiedNodes, java.util.List<VehicleSnapshot> vehicleSnapshots) {
            this.occupiedNodes = occupiedNodes;
            this.vehicleSnapshots = vehicleSnapshots;
        }
    }

    private static final class VehicleSnapshot {
        private final long vehicleId;
        private final double railProgress;
        private final double tailProgress;
        private final java.util.List<org.mtr.core.data.PathData> path;
        private final int headIndex;

        private VehicleSnapshot(long vehicleId, double railProgress, double tailProgress, java.util.List<org.mtr.core.data.PathData> path, int headIndex) {
            this.vehicleId = vehicleId;
            this.railProgress = railProgress;
            this.tailProgress = tailProgress;
            this.path = path;
            this.headIndex = headIndex;
        }
    }
}
