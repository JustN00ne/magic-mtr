package org.justnoone.jme.client.render;

import org.justnoone.jme.block.BlockCrossingGate;
import org.justnoone.jme.block.BlockCrossingLights;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.StoredMatrixTransformations;

/**
 * Draws a crossing gate from its Blockbench model: the housing ({@link ObjBlockModel#GATE_BASE}), and the arm
 * ({@link ObjBlockModel#GATE_ARM} with the boom for the gate's length) turned about its hinge as far as the arm's
 * animation has got ({@link BlockCrossingGate.BlockEntity#updateArm}). The arm lowers towards -z in the model, which is
 * the left of the road in front of the gate; a right gate's arm lowers the other way.
 */
public class RenderCrossingGate<T extends BlockEntityExtension> extends BlockEntityRenderer<T> {

    /**
     * Turns the model's front (facing -x in the Blockbench file, like the level crossing) to Minecraft yaw 0 (south).
     */
    private static final float MODEL_FRONT_YAW = 90;
    private static final double HINGE_Y = 19 / 16D;
    private static final double HINGE_Z = -2 / 16D;

    public RenderCrossingGate(Argument argument) {
        super(argument);
    }

    @Override
    public void render(T blockEntity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay) {
        final net.minecraft.block.BlockState state = blockEntity.getCachedState();
        if (state == null || !(blockEntity instanceof BlockCrossingGate.BlockEntity)) {
            return;
        }
        final BlockCrossingGate.BlockEntity gate = (BlockCrossingGate.BlockEntity) blockEntity;
        final BlockPos pos = blockEntity.getPos2();
        final float yawDegrees = MODEL_FRONT_YAW - BlockCrossingLights.getFrontYaw(state);
        final float armDegrees = gate.updateArm(BlockCrossingGate.isLowered(state)) * (gate.isRight() ? 90 : -90);

        final StoredMatrixTransformations base = new StoredMatrixTransformations(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        base.add(holder -> {
            if (yawDegrees != 0) {
                holder.rotateYDegrees(yawDegrees);
            }
            // Same as MTR's eye candy: its models are turned upside down (180 degrees about X) to match vehicle space.
            holder.rotateXDegrees(180);
        });
        ObjBlockModel.GATE_BASE.render(base, light);

        final StoredMatrixTransformations arm = new StoredMatrixTransformations(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        arm.add(holder -> {
            if (yawDegrees != 0) {
                holder.rotateYDegrees(yawDegrees);
            }
            if (armDegrees != 0) {
                holder.translate(0, HINGE_Y, HINGE_Z);
                holder.rotateXDegrees(armDegrees);
                holder.translate(0, -HINGE_Y, -HINGE_Z);
            }
            holder.rotateXDegrees(180);
        });
        ObjBlockModel.GATE_ARM.render(arm, light);
        CrossingGateModels.getBoom(gate.getLength()).render(arm, light);
    }

    @Override
    public boolean rendersOutsideBoundingBox2(T blockEntity) {
        return true;
    }
}
