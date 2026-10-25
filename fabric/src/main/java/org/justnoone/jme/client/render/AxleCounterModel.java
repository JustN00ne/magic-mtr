package org.justnoone.jme.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.justnoone.jme.Jme;
import org.justnoone.jme.compat.McClientCompat;
import org.justnoone.jme.entity.AxleCounterEntity;

/**
 * Draws an axle counter attached to a rail: the axle counter model (the body and its slanted inner side, as in
 * {@code axle_counter.obj}) along the rail, at the entity's yaw and pitch (the rail's direction and slope). The model's
 * long side runs along the rail (+z is the counter's facing).
 */
public final class AxleCounterModel {

    public static final Identifier TEXTURE = new Identifier(Jme.MOD_ID, "textures/block/axle_counter.png");
    private static final float TEXTURE_SIZE = 32;

    private AxleCounterModel() {
    }

    /**
     * Called by the entity renderer (registered per Minecraft version by {@link McClientCompat#registerAxleCounter()}).
     */
    public static void render(AxleCounterEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        McClientCompat.rotateYawPitch(matrices, -entity.getYaw(tickDelta), entity.getPitch(tickDelta));
        final VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        final MatrixStack.Entry entry = matrices.peek();
        // Body: 2 x 3 x 7.5 px, texture regions in pixels of the 32 x 32 texture (from axle_counter.obj).
        box(consumer, entry, light, -0.0625F, 0, -0.234375F, 0.0625F, 0.1875F, 0.234375F,
                new float[]{0, 27, 8, 32}, new float[]{0.25F, 24.66F, 7.75F, 26}, new float[]{9, 15, 11, 23}, new float[]{9.75F, 24, 11.75F, 31.5F}, new float[]{12.75F, 18, 14.75F, 23}, new float[]{0.25F, 14, 2.25F, 19});
        // Slanted inner side, as a low step.
        box(consumer, entry, light, -0.09375F, 0, -0.234375F, -0.0625F, 0.140625F, 0.234375F,
                null, new float[]{0.25F, 20.9F, 7.75F, 24.66F}, new float[]{0.25F, 24.66F, 7.75F, 26}, new float[]{9.25F, 24, 9.75F, 31.5F}, new float[]{12.25F, 18, 12.75F, 21.75F}, new float[]{2.25F, 14, 2.75F, 17.75F});
        matrices.pop();
    }

    /**
     * A box with a texture region (u0, v0, u1, v1 in pixels) for each face: +x, -x, +y, -y, +z, -z ({@code null} skips
     * the face).
     */
    private static void box(VertexConsumer consumer, MatrixStack.Entry entry, int light, float x0, float y0, float z0, float x1, float y1, float z1, float[] east, float[] west, float[] up, float[] down, float[] south, float[] north) {
        if (east != null) {
            quad(consumer, entry, light, east, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
        }
        if (west != null) {
            quad(consumer, entry, light, west, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
        }
        if (up != null) {
            quad(consumer, entry, light, up, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }
        if (down != null) {
            quad(consumer, entry, light, down, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        }
        if (south != null) {
            quad(consumer, entry, light, south, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
        }
        if (north != null) {
            quad(consumer, entry, light, north, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
        }
    }

    /**
     * A quad from its top-left, top-right, bottom-right and bottom-left corners.
     */
    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry, int light, float[] uv, float nx, float ny, float nz, float... corners) {
        final float u0 = uv[0] / TEXTURE_SIZE, v0 = uv[1] / TEXTURE_SIZE, u1 = uv[2] / TEXTURE_SIZE, v1 = uv[3] / TEXTURE_SIZE;
        final float[] us = {u0, u1, u1, u0};
        final float[] vs = {v0, v0, v1, v1};
        for (int i = 0; i < 4; i++) {
            McClientCompat.entityVertex(consumer, entry, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], us[i], vs[i], OverlayTexture.DEFAULT_UV, light, nx, ny, nz);
        }
    }
}
