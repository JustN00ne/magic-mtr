package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderRails.class)
public class RenderRailsMixin {

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void jme$renderSpeedTextOnRail(ClientWorld clientWorld, Rail rail, CallbackInfo ci) {
        if (rail.isPlatform() || rail.isSiding() || rail.canTurnBack()) {
            return;
        }

        final int speed = (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true));
        if (speed <= 0) {
            return;
        }

        final int color = MagicRailSpeedColor.colorForSpeed(speed);
        final String label = speed + "kmh";

        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerX = (x1 + x3) / 2;
            final double centerY = (y1 + y2) / 2 + 0.2;
            final double centerZ = (z1 + z3) / 2;
            final BlockPos blockPos = Init.newBlockPos(centerX, centerY, centerZ);

            // Draw labels periodically instead of every rendered rail slice.
            if ((Math.floorMod(blockPos.getX(), 4) + Math.floorMod(blockPos.getZ(), 4)) % 4 != 0) {
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
        }, 8, 0, 0);
    }
}
