package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BlockView;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Direction;
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
 * The railroad crossing controller: a lineside cabinet that controls a whole crossing on its own, so the controls do
 * not have to live in one of the level crossings. Axle counters (start and end nodes, several of each, at any distance)
 * and the crossing's level crossings and gates are linked to it with the Crossing Linker; right-click it with MTR's
 * brush for its components and timeline. It has no lamps or bell, and emits a redstone signal of 15 while the crossing
 * is active. Level crossings that already control a crossing keep working.
 */
public class BlockRailroadCrossingController extends BlockExtension implements BlockWithEntity {

    // The same facing property as the legacy controller: the cabinet's door faces the player who places it.
    private static final Property<net.minecraft.util.math.Direction> FACING_PROPERTY = Property.cast(BlockCrossingController.FACING);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final Property<Boolean> POWERED_PROPERTY = Property.cast(POWERED);
    /**
     * The cabinet is 1.25 blocks tall and fits in its block.
     */
    private static final double LINK_OUTLINE_HEIGHT = 1.3;

    public BlockRailroadCrossingController() {
        super(BlockHelper.createBlockSettings(true, true).nonOpaque());
    }

    @Override
    public BlockState getPlacementState2(ItemPlacementContext context) {
        return super.getPlacementState2(context).with(FACING_PROPERTY, context.getPlayerFacing().getOpposite().data).with(POWERED_PROPERTY, false);
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        properties.add(BlockCrossingController.FACING);
        properties.add(POWERED);
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

    public static class BlockEntity extends BlockEntityExtension implements CrossingTicker.Ticking, CrossingControl.Holder {

        private final CrossingControl control = new CrossingControl(this::markDirty2);

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.RAILROAD_CROSSING_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            CrossingTicker.track(this);
            control.read(compoundTag);
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            control.write(compoundTag);
        }

        @Override
        public void blockEntityTick() {
            CrossingTicker.onTick(this);
        }

        @Override
        public void crossingTick(World world) {
            final boolean active = control.tick(world);
            final BlockState state = getCachedState2();
            if (state.contains(POWERED_PROPERTY) && state.get(POWERED_PROPERTY) != active) {
                // Flag 3 notifies neighbours (redstone output) and syncs the state to clients.
                world.setBlockState(getPos2(), state.with(POWERED_PROPERTY, active), 3);
            }
        }

        @Override
        public CrossingControl getCrossingControl() {
            return control;
        }

        @Override
        public double getLinkOutlineHeight() {
            return LINK_OUTLINE_HEIGHT;
        }

        @Override
        public double getLinkOutlineHalfWidth() {
            return 0.5;
        }
    }
}
