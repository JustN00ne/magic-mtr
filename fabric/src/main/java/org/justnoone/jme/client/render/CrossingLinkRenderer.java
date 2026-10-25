package org.justnoone.jme.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.justnoone.jme.block.BlockCrossingLights;
import org.justnoone.jme.block.CrossingControl;
import org.justnoone.jme.block.CrossingObject;
import org.justnoone.jme.compat.McClientCompat;
import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.item.CrossingLinkMode;
import org.justnoone.jme.item.CrossingLinkerItem;

/**
 * Draws the crossing link feedback, which never shows during ordinary gameplay:
 * <ul>
 *     <li>while linking with the Crossing Linker: an outline around the selected controller in the mode's colour, plus a
 *     preview line to the aimed point in start node (green) and end node (red) mode, or a blue outline around the
 *     aimed crossing object in object mode (outlines only, no line);</li>
 *     <li>while holding an MTR brush: the saved connections of the controllers in range, green / red from the start /
 *     end axle counter to its controller and blue from a controller to each linked object, with arrows pointing the
 *     way the connection works (counter signals controller, controller switches object).</li>
 * </ul>
 */
public final class CrossingLinkRenderer {

    private static final double BRUSH_RANGE = 128;
    private static final double PREVIEW_REACH = 6;
    // Mid-pole, so lines between crossings clear platform edges and fences.
    private static final double CONTROLLER_ANCHOR_HEIGHT = 1.5;
    private static final double ARROW_SPACING = 1.5;
    private static final double ARROW_LENGTH = 0.3;
    private static final double ARROW_HALF_WIDTH = 0.18;
    private static final int OBJECT_COLOR = CrossingLinkMode.OBJECT.color;

    private CrossingLinkRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(CrossingLinkRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.player;
        final ClientWorld world = client.world;
        final MatrixStack matrices = context.matrixStack();
        if (player == null || world == null || matrices == null) {
            return;
        }

        final ItemStack mainHand = player.getMainHandStack();
        final CrossingLinkMode mode = mainHand.getItem() instanceof CrossingLinkerItem ? CrossingLinkMode.get(mainHand) : CrossingLinkMode.INACTIVE;
        final BlockPos selected = ClientCrossingLinks.getSelectedController();
        final boolean linking = mode != CrossingLinkMode.INACTIVE && selected != null;
        final boolean brush = isBrush(mainHand) || isBrush(player.getOffHandStack());
        if (!linking && !brush) {
            return;
        }

        final Vec3d camera = context.camera().getPos();
        final VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        final VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (linking) {
            renderLinking(client, world, matrices, lines, camera, selected, mode);
        }
        if (brush) {
            renderConnections(world, player, matrices, lines, camera);
        }

        matrices.pop();
        immediate.draw(RenderLayer.getLines());
    }

    private static void renderLinking(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumer lines, Vec3d camera, BlockPos selected, CrossingLinkMode mode) {
        outline(matrices, lines, selected, world.getBlockEntity(selected), mode.color);
        final HitResult target = client.crosshairTarget;
        if (mode.linksCounters()) {
            final Vec3d to;
            if (target instanceof EntityHitResult && ((EntityHitResult) target).getEntity() instanceof AxleCounterEntity) {
                to = ((EntityHitResult) target).getEntity().getPos().add(0, 0.1, 0);
            } else if (target != null && target.getType() != HitResult.Type.MISS) {
                to = target.getPos();
            } else {
                to = client.player.getCameraPosVec(1).add(client.player.getRotationVec(1).multiply(PREVIEW_REACH));
            }
            line(matrices, lines, controllerAnchor(selected), to, mode.color);
        } else if (mode == CrossingLinkMode.OBJECT && target instanceof BlockHitResult && target.getType() == HitResult.Type.BLOCK) {
            final BlockPos targetPos = ((BlockHitResult) target).getBlockPos();
            final BlockEntity targetEntity = world.getBlockEntity(targetPos);
            if (!targetPos.equals(selected) && targetEntity instanceof CrossingObject) {
                outline(matrices, lines, targetPos, targetEntity, OBJECT_COLOR);
            }
        }
    }

    private static void renderConnections(ClientWorld world, ClientPlayerEntity player, MatrixStack matrices, VertexConsumer lines, Vec3d camera) {
        for (final BlockEntity blockEntity : ClientCrossingLinks.getControllers(world)) {
            if (!(blockEntity instanceof CrossingControl.Holder) || !blockEntity.getPos().isWithinDistance(player.getPos(), BRUSH_RANGE)) {
                continue;
            }
            final CrossingControl control = ((CrossingControl.Holder) blockEntity).getCrossingControl();
            final Vec3d anchor = controllerAnchor(blockEntity.getPos());
            final CrossingControl.CounterLink start = control.getStart();
            final CrossingControl.CounterLink end = control.getEnd();
            if (start != null) {
                arrowLine(matrices, lines, camera, counterAnchor(start), anchor, CrossingLinkMode.START_NODE.color);
            }
            if (end != null) {
                arrowLine(matrices, lines, camera, counterAnchor(end), anchor, CrossingLinkMode.END_NODE.color);
            }
            for (final org.mtr.mapping.holder.BlockPos objectPos : control.getObjects()) {
                arrowLine(matrices, lines, camera, anchor, controllerAnchor(objectPos.data), OBJECT_COLOR);
            }
        }
    }

    private static boolean isBrush(ItemStack stack) {
        return stack.getItem() instanceof org.mtr.mod.item.ItemBrush;
    }

    private static Vec3d controllerAnchor(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + CONTROLLER_ANCHOR_HEIGHT, pos.getZ() + 0.5);
    }

    private static Vec3d counterAnchor(CrossingControl.CounterLink link) {
        return new Vec3d(link.getX(), link.getY() + 0.1, link.getZ());
    }

    private static void outline(MatrixStack matrices, VertexConsumer lines, BlockPos pos, BlockEntity blockEntity, int color) {
        // Level crossings and controllers are drawn as tall crossings; other objects say how big they are.
        final double height = blockEntity instanceof CrossingObject ? ((CrossingObject) blockEntity).getLinkOutlineHeight() : BlockCrossingLights.LINK_OUTLINE_HEIGHT;
        final double halfWidth = blockEntity instanceof CrossingObject ? ((CrossingObject) blockEntity).getLinkOutlineHalfWidth() : BlockCrossingLights.LINK_OUTLINE_HALF_WIDTH;
        final double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        final Box box = new Box(x - halfWidth, pos.getY(), z - halfWidth, x + halfWidth, pos.getY() + height, z + halfWidth).expand(0.02);
        WorldRenderer.drawBox(matrices, lines, box, red(color), green(color), blue(color), 1);
    }

    private static void arrowLine(MatrixStack matrices, VertexConsumer lines, Vec3d camera, Vec3d from, Vec3d to, int color) {
        line(matrices, lines, from, to, color);
        final Vec3d delta = to.subtract(from);
        final double length = delta.length();
        if (length < 1E-3) {
            return;
        }
        final Vec3d direction = delta.multiply(1 / length);
        final int arrows = Math.max(1, (int) (length / ARROW_SPACING));
        final double spacing = length / (arrows + 1);
        for (int i = 1; i <= arrows; i++) {
            final Vec3d tip = from.add(direction.multiply(spacing * i + ARROW_LENGTH / 2));
            // Spread the arrow head across the view, so it reads from any angle.
            Vec3d side = direction.crossProduct(tip.subtract(camera));
            if (side.lengthSquared() < 1E-6) {
                side = direction.crossProduct(new Vec3d(0, 1, 0));
            }
            side = side.normalize().multiply(ARROW_HALF_WIDTH);
            final Vec3d back = tip.subtract(direction.multiply(ARROW_LENGTH));
            line(matrices, lines, back.add(side), tip, color);
            line(matrices, lines, back.subtract(side), tip, color);
        }
    }

    private static void line(MatrixStack matrices, VertexConsumer lines, Vec3d from, Vec3d to, int color) {
        final Vec3d delta = to.subtract(from);
        final double length = delta.length();
        if (length < 1E-4) {
            return;
        }
        final float nx = (float) (delta.x / length), ny = (float) (delta.y / length), nz = (float) (delta.z / length);
        final MatrixStack.Entry entry = matrices.peek();
        McClientCompat.lineVertex(lines, entry, (float) from.x, (float) from.y, (float) from.z, red(color), green(color), blue(color), 1, nx, ny, nz);
        McClientCompat.lineVertex(lines, entry, (float) to.x, (float) to.y, (float) to.z, red(color), green(color), blue(color), 1, nx, ny, nz);
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255F;
    }
}
