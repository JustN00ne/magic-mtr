package org.justnoone.jme.client.render;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.StoredMatrixTransformations;

import javax.annotation.Nullable;

/**
 * Draws an {@link ObjBlockModel} for a block entity, centred on the block and turned with the block's facing like MTR's
 * eye candy objects.
 */
public class RenderObjBlock<T extends BlockEntityExtension> extends BlockEntityRenderer<T> {

    private final ObjBlockModel model;
    private final float scale;
    @Nullable
    private final net.minecraft.state.property.DirectionProperty facingProperty;
    private final boolean rendersOutsideBlock;

    public RenderObjBlock(Argument argument, ObjBlockModel model, float scale, @Nullable org.mtr.mapping.holder.DirectionProperty facingProperty, boolean rendersOutsideBlock) {
        super(argument);
        this.model = model;
        this.scale = scale;
        this.facingProperty = facingProperty == null ? null : (net.minecraft.state.property.DirectionProperty) facingProperty.data;
        this.rendersOutsideBlock = rendersOutsideBlock;
    }

    @Override
    public void render(T blockEntity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay) {
        final BlockPos pos = blockEntity.getPos2();
        final float yawDegrees = getYawDegrees(blockEntity);
        final StoredMatrixTransformations transformations = new StoredMatrixTransformations(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        transformations.add(holder -> {
            if (yawDegrees != 0) {
                holder.rotateYDegrees(yawDegrees);
            }
            if (scale != 1) {
                holder.scale(scale, scale, scale);
            }
            // Same as MTR's eye candy: its OBJ meshes are turned upside down (180 degrees about X) to match vehicle space.
            holder.rotateXDegrees(180);
        });
        model.render(transformations, light);
    }

    @Override
    public boolean rendersOutsideBoundingBox2(T blockEntity) {
        return rendersOutsideBlock;
    }

    private float getYawDegrees(T blockEntity) {
        if (facingProperty == null) {
            return 0;
        }
        final net.minecraft.block.BlockState state = blockEntity.getCachedState();
        if (state == null || !state.contains(facingProperty)) {
            return 0;
        }
        // North is the orientation the model was authored in (the JSON blockstates apply no rotation for it).
        return 180 - state.get(facingProperty).asRotation();
    }
}
