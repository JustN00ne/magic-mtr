package org.justnoone.jme.client.render;

import org.justnoone.jme.block.BlockCrossingLights;
import org.justnoone.jme.block.CrossingSignal;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.StoredMatrixTransformations;

/**
 * Draws the whole level crossing from its Blockbench model ({@link ObjBlockModel#getCrossing}): the JSON block model
 * format cannot describe its octagonal lamp heads, nor turn a block by 45°. The model is drawn with the texture of the
 * lamp that is lit and turned to the block's front yaw ({@link BlockCrossingLights#getFrontYaw}).
 */
public class RenderCrossing<T extends BlockEntityExtension> extends BlockEntityRenderer<T> {

    /**
     * Turns the model's front (lamps, facing -x in the Blockbench file) to Minecraft yaw 0 (south).
     */
    private static final float MODEL_FRONT_YAW = 90;

    private final net.minecraft.state.property.IntProperty lampProperty;

    public RenderCrossing(Argument argument) {
        super(argument);
        lampProperty = CrossingSignal.LAMP.data;
    }

    @Override
    public void render(T blockEntity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay) {
        final net.minecraft.block.BlockState state = blockEntity.getCachedState();
        if (state == null) {
            return;
        }
        // Every loaded controller is drawn each frame (it renders outside its bounding box), so this keeps the list of
        // controllers whose connections the MTR brush shows.
        ClientCrossingLinks.onControllerRendered(blockEntity);
        final BlockPos pos = blockEntity.getPos2();
        final float yawDegrees = MODEL_FRONT_YAW - BlockCrossingLights.getFrontYaw(state);
        final int lamp = state.contains(lampProperty) ? state.get(lampProperty) : 0;
        final StoredMatrixTransformations transformations = new StoredMatrixTransformations(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        transformations.add(holder -> {
            if (yawDegrees != 0) {
                holder.rotateYDegrees(yawDegrees);
            }
            // Same as MTR's eye candy: its models are turned upside down (180 degrees about X) to match vehicle space.
            holder.rotateXDegrees(180);
        });
        ObjBlockModel.getCrossing(lamp).render(transformations, light);
    }

    @Override
    public boolean rendersOutsideBoundingBox2(T blockEntity) {
        return true;
    }
}
