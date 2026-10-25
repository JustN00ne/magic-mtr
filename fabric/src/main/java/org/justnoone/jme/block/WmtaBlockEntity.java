package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;

public class WmtaBlockEntity extends BlockEntityExtension {

    public static final BooleanProperty BLINK = BooleanProperty.of("blink");
    private static final Property<Boolean> BLINK_PROPERTY = Property.cast(BLINK);

    public WmtaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WMTA_ON_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void readCompoundTag(CompoundTag compoundTag) {
        super.readCompoundTag(compoundTag);
    }

    @Override
    public void writeCompoundTag(CompoundTag compoundTag) {
        super.writeCompoundTag(compoundTag);
    }

    @Override
    public void blockEntityTick() {
        final World world = getWorld2();
        if (world == null || world.isClient()) {
            return;
        }

        final String worldId = Init.getWorldId(world);
        if (worldId == null || worldId.isEmpty()) {
            return;
        }

        final WmtaStateManager state = WmtaStateManager.getOrCreate(worldId);
        final org.mtr.core.simulation.Simulator simulator = WmtaStateManager.getSimulator(world);
        if (simulator != null) {
            state.tick(simulator);
        }

        final boolean shouldBlink = state.isBlinking();
        final BlockState currentState = getCachedState2();
        final boolean isBlinking = currentState.get(BLINK_PROPERTY);

        if (isBlinking != shouldBlink) {
            world.setBlockState(getPos2(), currentState.with(BLINK_PROPERTY, shouldBlink), 3);
        }
    }
}
