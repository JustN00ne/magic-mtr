package org.justnoone.jme.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;
import org.justnoone.jme.client.render.AxleCounterModel;
import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.entity.ModEntities;

import java.util.UUID;

/**
 * Client-side Minecraft API calls that changed between the supported versions (this copy: 1.18.x).
 */
public final class McClientCompat {

    private McClientCompat() {
    }

    public static void registerAxleCounter() {
        EntityRendererRegistry.register(ModEntities.AXLE_COUNTER, AxleCounterRenderer::new);
        // Spawns the counters the server announces with AxleCounterEntityBase#createSpawnPacket.
        ClientPlayNetworking.registerGlobalReceiver(AxleCounterEntityBase.SPAWN_PACKET_ID, (client, handler, packetByteBuf, responseSender) -> {
            final int id = packetByteBuf.readVarInt();
            final UUID uuid = packetByteBuf.readUuid();
            final double x = packetByteBuf.readDouble();
            final double y = packetByteBuf.readDouble();
            final double z = packetByteBuf.readDouble();
            final float yaw = packetByteBuf.readFloat();
            final float pitch = packetByteBuf.readFloat();
            client.execute(() -> {
                final ClientWorld world = client.world;
                if (world == null) {
                    return;
                }
                final AxleCounterEntity counter = new AxleCounterEntity(ModEntities.AXLE_COUNTER, world);
                counter.updateTrackedPosition(x, y, z);
                counter.refreshPositionAndAngles(x, y, z, yaw, pitch);
                counter.setId(id);
                counter.setUuid(uuid);
                world.addEntity(id, counter);
            });
        });
    }

    public static void rotateYawPitch(MatrixStack matrices, float yawDegrees, float pitchDegrees) {
        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(yawDegrees));
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(pitchDegrees));
    }

    public static void entityVertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light).normal(entry.getNormalMatrix(), nx, ny, nz).next();
    }

    public static void lineVertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float red, float green, float blue, float alpha, float nx, float ny, float nz) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(red, green, blue, alpha).normal(entry.getNormalMatrix(), nx, ny, nz).next();
    }

    /**
     * Draws the counter with {@link AxleCounterModel}.
     */
    private static final class AxleCounterRenderer extends EntityRenderer<AxleCounterEntity> {

        private AxleCounterRenderer(EntityRendererFactory.Context context) {
            super(context);
            shadowRadius = 0;
        }

        @Override
        public void render(AxleCounterEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
            AxleCounterModel.render(entity, tickDelta, matrices, vertexConsumers, light);
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }

        @Override
        public Identifier getTexture(AxleCounterEntity entity) {
            return AxleCounterModel.TEXTURE;
        }
    }
}