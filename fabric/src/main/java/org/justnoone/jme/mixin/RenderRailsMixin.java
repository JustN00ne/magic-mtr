package org.justnoone.jme.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.InitClient;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderRails.class)
public class RenderRailsMixin {

    private static final double JME_SPEED_TEXT_MAX_DISTANCE_SQUARED = 64D * 64D;
    private static final String[] JME_KMH_LABEL_CACHE = new String[401];
    private static final String[] JME_MPH_LABEL_CACHE = new String[401];

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void jme$renderSpeedTextOnRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        if (!JmeConfig.inWorldSpeedTextEnabled()) {
            return;
        }
        if (rail.isPlatform() || rail.isSiding() || rail.canTurnBack()) {
            return;
        }

        final int speed = (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true));
        if (speed <= 0) {
            return;
        }

        final int color = MagicRailSpeedColor.colorForSpeed(speed);
        final boolean useMph = JmeConfig.useMph();
        final String label = jme$getCachedSpeedLabel(speed, useMph);
        if (label == null || label.isEmpty()) {
            return;
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        final Vec3d cameraPos = client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null
                ? null
                : client.gameRenderer.getCamera().getPos();
        final double cameraX = cameraPos == null ? 0 : cameraPos.x;
        final double cameraY = cameraPos == null ? 0 : cameraPos.y;
        final double cameraZ = cameraPos == null ? 0 : cameraPos.z;

        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerX = (x1 + x3) / 2;
            final double centerY = (y1 + y2) / 2 + 0.2;
            final double centerZ = (z1 + z3) / 2;

            final double dx = centerX - cameraX;
            final double dy = centerY - cameraY;
            final double dz = centerZ - cameraZ;
            if (dx * dx + dy * dy + dz * dz > JME_SPEED_TEXT_MAX_DISTANCE_SQUARED) {
                return;
            }

            // Draw labels periodically instead of every rendered rail slice.
            final int blockX = (int) Math.floor(centerX);
            final int blockZ = (int) Math.floor(centerZ);
            if ((Math.floorMod(blockX, 4) + Math.floorMod(blockZ, 4)) % 4 != 0) {
                return;
            }

            MainRenderer.scheduleRender(QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
                graphicsHolder.push();
                graphicsHolder.translate(centerX - offset.getXMapped(), centerY - offset.getYMapped(), centerZ - offset.getZMapped());
                InitClient.transformToFacePlayer(graphicsHolder, centerX, centerY, centerZ);
                graphicsHolder.rotateZDegrees(180);
                graphicsHolder.scale(0.02F, 0.02F, -0.02F);
                graphicsHolder.drawText(label, -GraphicsHolder.getTextWidth(label) / 2, 0, color, true, GraphicsHolder.getDefaultLight());
                graphicsHolder.pop();
            });
        }, 16, 0, 0);
    }

    private static String jme$getCachedSpeedLabel(int speedKmh, boolean useMph) {
        final int clamped = Math.max(1, Math.min(400, speedKmh));
        if (useMph) {
            String cached = JME_MPH_LABEL_CACHE[clamped];
            if (cached == null) {
                cached = JmeConfig.toMph(clamped) + "mph";
                JME_MPH_LABEL_CACHE[clamped] = cached;
            }
            return cached;
        } else {
            String cached = JME_KMH_LABEL_CACHE[clamped];
            if (cached == null) {
                cached = clamped + "kmh";
                JME_KMH_LABEL_CACHE[clamped] = cached;
            }
            return cached;
        }
    }
}
