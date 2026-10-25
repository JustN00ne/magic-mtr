package org.justnoone.jme.block;

import java.util.List;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockSettings;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.block.BlockPlatform;

public class BlockWmtaOn extends BlockPlatform implements BlockWithEntity {

    public BlockWmtaOn(BlockSettings settings) {
        super(settings, false);
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        super.addBlockProperties(properties);
        properties.add(WmtaBlockEntity.BLINK);
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new WmtaBlockEntity(pos, state);
    }
}
