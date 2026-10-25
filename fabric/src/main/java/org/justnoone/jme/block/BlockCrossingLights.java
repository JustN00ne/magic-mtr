package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BlockView;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.DirectionProperty;
import org.mtr.mapping.holder.ItemPlacementContext;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockHelper;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;

import java.util.List;

/**
 * The level crossing. Any level crossing can be the main one of a crossing: axle counters are linked to it with the
 * Crossing Linker, and the other level crossings of the same crossing (eg the other side of the road) are linked to it
 * and switch with it. A level crossing also flashes and rings while it receives a redstone signal.
 */
public class BlockCrossingLights extends BlockExtension implements BlockWithEntity {

    /**
     * Set while a train is in the crossing (from its own axle counters or from the main level crossing it is linked
     * to); the block then emits a redstone signal of 15 for gates, lamps, etc. Which lamp is lit is
     * {@link CrossingSignal#LAMP}.
     */
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final DirectionProperty FACING = new DirectionProperty(net.minecraft.state.property.DirectionProperty.of("facing", net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST));
    private static final Property<Boolean> POWERED_PROPERTY = Property.cast(POWERED);
    // Vanilla enum: BlockState#with hands the value to Minecraft unwrapped (see BlockAxleCounter).
    private static final Property<net.minecraft.util.math.Direction> FACING_PROPERTY = Property.cast(FACING);
    /**
     * Set when the level crossing is turned a further 45° clockwise (seen from above) from {@link #FACING}, for
     * crossings on curves or diagonal tracks.
     */
    public static final BooleanProperty DIAGONAL = BooleanProperty.of("diagonal");
    private static final Property<Boolean> DIAGONAL_PROPERTY = Property.cast(DIAGONAL);
    private static final int REDSTONE_CHECK_TICKS = 4;
    /**
     * The model is 3.25 blocks tall and its lamp heads reach 0.91 blocks either side of the pole, in any rotation.
     */
    public static final double LINK_OUTLINE_HEIGHT = 3.3;
    public static final double LINK_OUTLINE_HALF_WIDTH = 0.95;

    public BlockCrossingLights() {
        super(BlockHelper.createBlockSettings(true, true).nonOpaque());
    }

    @Override
    public BlockState getPlacementState2(ItemPlacementContext context) {
        // The front (lamps) faces the player, snapped to 45° steps of the player's yaw (0 = south, clockwise).
        final int step = Math.floorMod(Math.round((context.getPlayerYaw() + 180) / 45F), 8);
        final net.minecraft.util.math.Direction front = net.minecraft.util.math.Direction.fromRotation(step / 2 * 90);
        // A boolean property defaults to its first value (true), which made freshly placed lights show as lit.
        return CrossingSignal.withLampOff(super.getPlacementState2(context).with(FACING_PROPERTY, front).with(DIAGONAL_PROPERTY, step % 2 == 1).with(POWERED_PROPERTY, false));
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        properties.add(POWERED);
        properties.add(FACING);
        properties.add(CrossingSignal.LAMP);
        properties.add(DIAGONAL);
    }

    /**
     * The direction the front (lamps) of a level crossing faces, as a Minecraft yaw (0 = south, 90 = west, clockwise
     * seen from above), in 45° steps.
     */
    public static float getFrontYaw(net.minecraft.block.BlockState state) {
        final net.minecraft.state.property.DirectionProperty facing = (net.minecraft.state.property.DirectionProperty) FACING.data;
        final net.minecraft.state.property.BooleanProperty diagonal = DIAGONAL.data;
        final float yaw = state.contains(facing) ? state.get(facing).asRotation() : 180;
        return state.contains(diagonal) && state.get(diagonal) ? yaw + 45 : yaw;
    }

    @Override
    public boolean emitsRedstonePower2(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower2(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED_PROPERTY) ? 15 : 0;
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    /**
     * A level crossing is both a crossing controller (axle counters and other crossing objects link to it) and a
     * crossing object (it can follow another controller).
     */
    public static class BlockEntity extends BlockEntityExtension implements CrossingControl.Holder, CrossingObject {

        /**
         * Set by the main level crossing (or legacy controller) this one is linked to.
         */
        private boolean followActive;
        /**
         * Set while the lights receive a vanilla redstone signal (lever, button, detector rail, an axle counter, ...):
         * they then flash and ring. This never switches the redstone output on: the output would power adjacent dust,
         * which powers the lights back and would latch them on.
         */
        private boolean redstoneActive;
        private int redstoneCheckCounter;
        /**
         * Which of the {@link CrossingSignal} bell sounds this level crossing rings, changed with MTR's brush.
         */
        private int bell;
        private final CrossingControl control = new CrossingControl(this::markDirty2);
        private final CrossingSignal signal = new CrossingSignal();

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CROSSING_LIGHTS_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            control.read(compoundTag);
            followActive = compoundTag.getBoolean("jme_crossing_follow_active");
            redstoneActive = compoundTag.getBoolean("jme_crossing_redstone_active");
            bell = CrossingSignal.clampBell(compoundTag.getInt("jme_crossing_bell"));
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            control.write(compoundTag);
            compoundTag.putBoolean("jme_crossing_follow_active", followActive);
            compoundTag.putBoolean("jme_crossing_redstone_active", redstoneActive);
            compoundTag.putInt("jme_crossing_bell", bell);
        }

        @Override
        public void blockEntityTick() {
            final World world = getWorld2();
            if (world == null || world.isClient()) {
                return;
            }

            final boolean trainActive = control.tick(world) || followActive;

            if (++redstoneCheckCounter >= REDSTONE_CHECK_TICKS) {
                redstoneCheckCounter = 0;
                final boolean powered = world.data.isReceivingRedstonePower(getPos2().data);
                if (powered != redstoneActive) {
                    redstoneActive = powered;
                    markDirty2();
                }
            }

            BlockState state = getCachedState2();
            if (state.contains(POWERED_PROPERTY) && state.get(POWERED_PROPERTY) != trainActive) {
                // Flag 3 notifies neighbours (redstone output) and syncs the state to clients.
                state = state.with(POWERED_PROPERTY, trainActive);
                world.setBlockState(getPos2(), state, 3);
            }
            signal.tick(world, getPos2(), state, trainActive || redstoneActive, bell);
        }

        /**
         * Switches to the next bell sound (rings it once) and returns its index.
         */
        public int cycleBell() {
            final int newBell = (bell + 1) % CrossingSignal.getBellCount();
            final World world = getWorld2();
            if (world != null) {
                signal.changeBell(world, getPos2(), bell, newBell);
            }
            bell = newBell;
            markDirty2();
            return bell;
        }

        public void setFollowActive(boolean followActive) {
            if (this.followActive != followActive) {
                this.followActive = followActive;
                markDirty2();
            }
        }

        @Override
        public CrossingControl getCrossingControl() {
            return control;
        }

        @Override
        public void setCrossingActive(boolean active) {
            setFollowActive(active);
        }

        @Override
        public double getLinkOutlineHeight() {
            return LINK_OUTLINE_HEIGHT;
        }

        @Override
        public double getLinkOutlineHalfWidth() {
            return LINK_OUTLINE_HALF_WIDTH;
        }
    }
}
