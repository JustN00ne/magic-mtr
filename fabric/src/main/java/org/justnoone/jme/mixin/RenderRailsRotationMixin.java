package org.justnoone.jme.mixin;

import org.justnoone.jme.client.MagicRailRotationClient;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.render.RenderRails;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.RailResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderRails.class)
public class RenderRailsRotationMixin {

    @Unique
    private static final ThreadLocal<Rail> JME_RENDERING_RAIL = new ThreadLocal<>();

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void jme$captureCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        JME_RENDERING_RAIL.set(rail);
    }

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void jme$clearCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        JME_RENDERING_RAIL.remove();
    }

    @Redirect(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;FLorg/mtr/mod/render/RenderRails$RenderState;FLorg/mtr/mapping/holder/Identifier;FFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/client/IDrawing;drawTexture(Lorg/mtr/mapping/mapper/GraphicsHolder;DDDDDDDDDDDDLorg/mtr/mapping/holder/Vector3d;FFFFLorg/mtr/mapping/holder/Direction;II)V"
            ),
            remap = false,
            require = 0
    )
    private static void jme$drawRotatedRailSurface(
            GraphicsHolder graphicsHolder,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            Vector3d playerOffset,
            float textureU1, float textureV1, float textureU2, float textureV2,
            Direction facing,
            int color,
            int light,
            ClientWorld clientWorld,
            Rail rail,
            float yOffset,
            @Coerce Object renderState,
            float railWidth,
            Identifier defaultTexture,
            float u1,
            float v1,
            float u2,
            float v2
    ) {
        if (rail == null) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        final double centerX = (x1 + x2 + x3 + x4) * 0.25;
        final double centerY = (y1 + y2 + y3 + y4) * 0.25;
        final double centerZ = (z1 + z2 + z3 + z4) * 0.25;
        final double rotationDegrees = MagicRailRotationClient.getRotationDegreesOnRail(rail, centerX, centerY, centerZ);
        if (Math.abs(rotationDegrees) < 0.001) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        final double[] xs = {x1, x2, x3, x4};
        final double[] ys = {y1, y2, y3, y4};
        final double[] zs = {z1, z2, z3, z4};
        jme$rotateVerticesY(centerX, centerZ, xs, zs, rotationDegrees);

        IDrawing.drawTexture(
                graphicsHolder,
                xs[0], ys[0], zs[0],
                xs[1], ys[1], zs[1],
                xs[2], ys[2], zs[2],
                xs[3], ys[3], zs[3],
                playerOffset,
                textureU1, textureV1, textureU2, textureV2,
                facing, color, light
        );
    }

    @Redirect(
            method = "lambda$renderRailStandard$16(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/mod/resource/RailResource;Z[ZLorg/mtr/mapping/holder/BlockPos;DDDDDDDDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/resource/RailResource;render(Lorg/mtr/mod/render/StoredMatrixTransformations;I)V"
            ),
            remap = false,
            require = 0
    )
    private static void jme$renderRotatedRailModel(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            ClientWorld clientWorld,
            RailResource railResource,
            boolean flip,
            boolean[] renderType,
            org.mtr.mapping.holder.BlockPos blockPos,
            double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2
    ) {
        jme$renderRotatedRailModelInternal(invokedRailResource, storedMatrixTransformations, light, x1, z1, x3, z3, y1, y2);
    }

    @Redirect(
            method = "lambda$null$16(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/mod/resource/RailResource;Z[ZLorg/mtr/mapping/holder/BlockPos;DDDDDDDDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/resource/RailResource;render(Lorg/mtr/mod/render/StoredMatrixTransformations;I)V"
            ),
            remap = false,
            require = 0
    )
    private static void jme$renderRotatedRailModelLegacy(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            ClientWorld clientWorld,
            RailResource railResource,
            boolean flip,
            boolean[] renderType,
            org.mtr.mapping.holder.BlockPos blockPos,
            double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2
    ) {
        jme$renderRotatedRailModelInternal(invokedRailResource, storedMatrixTransformations, light, x1, z1, x3, z3, y1, y2);
    }

    @Unique
    private static void jme$renderRotatedRailModelInternal(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            double x1,
            double z1,
            double x3,
            double z3,
            double y1,
            double y2
    ) {
        final Rail currentRail = JME_RENDERING_RAIL.get();
        final double centerX = (x1 + x3) / 2;
        final double centerY = (y1 + y2) / 2;
        final double centerZ = (z1 + z3) / 2;
        final double rotationDegrees = currentRail == null ? 0 : MagicRailRotationClient.getRotationDegreesOnRail(currentRail, centerX, centerY, centerZ);
        if (Math.abs(rotationDegrees) < 0.001) {
            invokedRailResource.render(storedMatrixTransformations, light);
            return;
        }

        final StoredMatrixTransformations adjusted = storedMatrixTransformations.copy();
        adjusted.add(graphicsHolder -> graphicsHolder.rotateYDegrees((float) rotationDegrees));
        invokedRailResource.render(adjusted, light);
    }

    @Unique
    private static void jme$rotateVerticesY(double centerX, double centerZ, double[] xs, double[] zs, double degrees) {
        final double radians = Math.toRadians(degrees);
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);
        for (int i = 0; i < xs.length; i++) {
            final double dx = xs[i] - centerX;
            final double dz = zs[i] - centerZ;
            xs[i] = centerX + dx * cos - dz * sin;
            zs[i] = centerZ + dx * sin + dz * cos;
        }
    }
}
