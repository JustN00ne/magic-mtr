package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BlockView;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockHelper;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;

import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.DirectionProperty;
import org.mtr.mapping.holder.ItemPlacementContext;
import org.mtr.mapping.holder.Property;

import java.util.List;

/**
 * Legacy level crossing controller. Every level crossing ({@link BlockCrossingLights}) can now take the axle counters
 * itself, so this block is no longer in the creative tab; controllers already placed in worlds keep working.
 */
public class BlockCrossingController extends BlockExtension implements BlockWithEntity {

    public static final DirectionProperty FACING = new DirectionProperty(net.minecraft.state.property.DirectionProperty.of("facing", net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST));
    // Vanilla enum: BlockState#with hands the value to Minecraft unwrapped (see BlockAxleCounter).
    private static final Property<net.minecraft.util.math.Direction> FACING_PROPERTY = Property.cast(FACING);
    /**
     * Set while the crossing is active; the controller then emits a redstone signal of 15, so vanilla redstone (lamps,
     * bells, doors, pistons for gates, etc) can follow the crossing.
     */
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final Property<Boolean> POWERED_PROPERTY = Property.cast(POWERED);
    private static final int REDSTONE_CHECK_TICKS = 4;

    public BlockCrossingController() {
        super(BlockHelper.createBlockSettings(true, true).nonOpaque());
    }

    @Override
    public BlockState getPlacementState2(ItemPlacementContext context) {
        return CrossingSignal.withLampOff(super.getPlacementState2(context).with(FACING_PROPERTY, context.getPlayerFacing().getOpposite().data).with(POWERED_PROPERTY, false));
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        properties.add(FACING);
        properties.add(POWERED);
        properties.add(CrossingSignal.LAMP);
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

    public static class BlockEntity extends BlockEntityExtension implements CrossingControl.Holder {

        private final CrossingControl control = new CrossingControl(this::markDirty2);
        // The controller is drawn as a crossing too, so its own lamps and bell follow the crossing like linked lights.
        private final CrossingSignal signal = new CrossingSignal();
        private boolean redstoneInput;
        private int redstoneCheckCounter;

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CROSSING_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            control.read(compoundTag);
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            control.write(compoundTag);
        }

        @Override
        public void blockEntityTick() {
            final World world = getWorld2();
            if (world == null || world.isClient()) {
                return;
            }

            final boolean active = control.tick(world);
            BlockState state = getCachedState2();
            // Also checked when unchanged: controllers saved before the "powered" property existed load with the
            // property's default value (true) and must be corrected.
            if (state.contains(POWERED_PROPERTY) && state.get(POWERED_PROPERTY) != active) {
                // Flag 3 notifies neighbours (redstone output) and syncs the state to clients.
                state = state.with(POWERED_PROPERTY, active);
                world.setBlockState(getPos2(), state, 3);
            }

            // A redstone signal into the controller (lever, axle counter, detector rail, ...) only flashes and rings its
            // own lamps, like it does for crossing lights. It must not switch the crossing or the redstone output on:
            // the output would power adjacent dust, which powers the controller back and would latch it on.
            if (++redstoneCheckCounter >= REDSTONE_CHECK_TICKS) {
                redstoneCheckCounter = 0;
                redstoneInput = world.data.isReceivingRedstonePower(getPos2().data);
            }
            signal.tick(world, getPos2(), state, active || redstoneInput, 0);
        }

        @Override
        public CrossingControl getCrossingControl() {
            return control;
        }
    }
}
