package org.justnoone.jme.mixin;

import org.justnoone.jme.client.MagicRailTiltClient;
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
public class RenderRailsTiltMixin {

    @Unique
    private static final ThreadLocal<Rail> JME_RENDERING_RAIL = new ThreadLocal<>();

    /**
     * XZ forward vector for {@link #JME_RENDERING_RAIL} in canonical rail direction (railMath 0 -> length).
     * Used to make "left" absolute (independent of vertex order / render pass).
     */
    @Unique
    private static final ThreadLocal<double[]> JME_RENDERING_RAIL_FORWARD_XZ = ThreadLocal.withInitial(() -> new double[]{0D, 0D});

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void jme$captureCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        JME_RENDERING_RAIL.set(rail);
        final double[] forward = JME_RENDERING_RAIL_FORWARD_XZ.get();
        forward[0] = 0;
        forward[1] = 0;
        if (rail == null) {
            return;
        }
        try {
            final double length = rail.railMath.getLength();
            if (length > 1.0E-4) {
                final Vector start = rail.railMath.getPosition(0, false);
                final Vector end = rail.railMath.getPosition(length, false);
                if (start != null && end != null) {
                    forward[0] = end.x() - start.x();
                    forward[1] = end.z() - start.z();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void jme$clearCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        JME_RENDERING_RAIL.remove();
        // Keep array instance to avoid churn; just reset values.
        final double[] forward = JME_RENDERING_RAIL_FORWARD_XZ.get();
        forward[0] = 0;
        forward[1] = 0;
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
    private static void jme$drawTiltedRailSurface(
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

        // Determine local forward for the quad (start edge is v1-v2, end edge is v3-v4).
        double forwardX = ((x3 + x4) - (x1 + x2)) * 0.5;
        double forwardZ = ((z3 + z4) - (z1 + z2)) * 0.5;
        if (Math.abs(forwardX) + Math.abs(forwardZ) < 1.0E-6) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        // One lookup per quad (projection is expensive). Sign correctness is handled below.
        final double centerX = (x1 + x2 + x3 + x4) * 0.25;
        final double centerY = (y1 + y2 + y3 + y4) * 0.25;
        final double centerZ = (z1 + z2 + z3 + z4) * 0.25;

        final double tiltDegrees = MagicRailTiltClient.getTiltDegreesOnRail(rail, centerX, centerY, centerZ);
        if (Math.abs(tiltDegrees) < 0.001) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        // Align forward with the rail's canonical direction so "left" is absolute (node placement direction doesn't matter).
        final double[] railForward = JME_RENDERING_RAIL_FORWARD_XZ.get();
        if ((railForward[0] != 0 || railForward[1] != 0) && railForward[0] * forwardX + railForward[1] * forwardZ < 0) {
            forwardX = -forwardX;
            forwardZ = -forwardZ;
        }

        // Normalized left vector in XZ.
        double leftX = forwardZ;
        double leftZ = -forwardX;
        final double leftLength = Math.sqrt(leftX * leftX + leftZ * leftZ);
        if (leftLength < 1.0E-6) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }
        leftX /= leftLength;
        leftZ /= leftLength;

        final double tanTilt = Math.tan(Math.toRadians(tiltDegrees));

        // Compute per-vertex offsets using the signed lateral distance along "left". This is robust to vertex ordering.
        final double startCenterX = (x1 + x2) * 0.5;
        final double startCenterZ = (z1 + z2) * 0.5;
        final double endCenterX = (x3 + x4) * 0.5;
        final double endCenterZ = (z3 + z4) * 0.5;

        final double lateral1 = (x1 - startCenterX) * leftX + (z1 - startCenterZ) * leftZ;
        final double lateral2 = (x2 - startCenterX) * leftX + (z2 - startCenterZ) * leftZ;
        final double lateral3 = (x3 - endCenterX) * leftX + (z3 - endCenterZ) * leftZ;
        final double lateral4 = (x4 - endCenterX) * leftX + (z4 - endCenterZ) * leftZ;

        final double yOffset1 = tanTilt * lateral1;
        final double yOffset2 = tanTilt * lateral2;
        final double yOffset3 = tanTilt * lateral3;
        final double yOffset4 = tanTilt * lateral4;

        IDrawing.drawTexture(
                graphicsHolder,
                x1, y1 + yOffset1, z1,
                x2, y2 + yOffset2, z2,
                x3, y3 + yOffset3, z3,
                x4, y4 + yOffset4, z4,
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
    private static void jme$renderTiltedRailModel(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            ClientWorld clientWorld,
            RailResource railResource,
            boolean flip,
            boolean[] renderType,
            org.mtr.mapping.holder.BlockPos blockPos,
            double x1,
            double z1,
            double x2,
            double z2,
            double x3,
            double z3,
            double x4,
            double z4,
            double y1,
            double y2
    ) {
        jme$renderTiltedRailModelInternal(invokedRailResource, storedMatrixTransformations, light, flip, x1, z1, x2, z2, x3, z3, x4, z4, y1, y2);
    }

    @Redirect(
            // Older versions use a different synthetic name for the same render lambda.
            method = "lambda$null$16(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/mod/resource/RailResource;Z[ZLorg/mtr/mapping/holder/BlockPos;DDDDDDDDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/resource/RailResource;render(Lorg/mtr/mod/render/StoredMatrixTransformations;I)V"
            ),
            remap = false,
            require = 0
    )
    private static void jme$renderTiltedRailModelLegacy(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            ClientWorld clientWorld,
            RailResource railResource,
            boolean flip,
            boolean[] renderType,
            org.mtr.mapping.holder.BlockPos blockPos,
            double x1,
            double z1,
            double x2,
            double z2,
            double x3,
            double z3,
            double x4,
            double z4,
            double y1,
            double y2
    ) {
        jme$renderTiltedRailModelInternal(invokedRailResource, storedMatrixTransformations, light, flip, x1, z1, x2, z2, x3, z3, x4, z4, y1, y2);
    }

    @Unique
    private static void jme$renderTiltedRailModelInternal(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            boolean flip,
            double x1,
            double z1,
            double x2,
            double z2,
            double x3,
            double z3,
            double x4,
            double z4,
            double y1,
            double y2
    ) {
        final double centerX = (x1 + x3) / 2;
        final double centerY = (y1 + y2) / 2;
        final double centerZ = (z1 + z3) / 2;
        final Rail currentRail = JME_RENDERING_RAIL.get();
        final double tiltDegrees = currentRail == null ? 0 : MagicRailTiltClient.getTiltDegreesOnRail(currentRail, centerX, centerY, centerZ);
        if (Math.abs(tiltDegrees) < 0.001) {
            invokedRailResource.render(storedMatrixTransformations, light);
            return;
        }

        final double signedTiltDegrees = jme$getSignedTiltDegrees(tiltDegrees, currentRail, x1, z1, x2, z2, x3, z3, x4, z4);
        // When the rail model is flipped, the local forward axis is reversed; invert roll so world cant stays consistent.
        final double modelTiltDegrees = flip ? -signedTiltDegrees : signedTiltDegrees;

        final StoredMatrixTransformations adjusted = storedMatrixTransformations.copy();
        adjusted.add(graphicsHolder -> graphicsHolder.rotateZDegrees((float) modelTiltDegrees));
        invokedRailResource.render(adjusted, light);
    }

    @Unique
    private static double jme$getSignedTiltDegrees(
            double tiltDegrees,
            Rail rail,
            double x1,
            double z1,
            double x2,
            double z2,
            double x3,
            double z3,
            double x4,
            double z4
    ) {
        if (rail == null || Math.abs(tiltDegrees) < 1.0E-6) {
            return tiltDegrees;
        }

        // Determine whether the quad's vertex order matches "left then right" in the rail's direction.
        // Some render paths swap the left/right vertices; if we don't compensate, the same stored tilt
        // value will sometimes bank the wrong way.
        double forwardX = ((x3 + x4) - (x1 + x2)) * 0.5;
        double forwardZ = ((z3 + z4) - (z1 + z2)) * 0.5;

        final double widthX = ((x2 + x4) - (x1 + x3)) * 0.5;
        final double widthZ = ((z2 + z4) - (z1 + z3)) * 0.5;

        if (Math.abs(forwardX) + Math.abs(forwardZ) < 1.0E-6 || Math.abs(widthX) + Math.abs(widthZ) < 1.0E-6) {
            return tiltDegrees;
        }

        // Align the inferred forward vector with the rail's canonical direction so start/end swapping in
        // the render call doesn't flip the bank.
        try {
            final double length = rail.railMath.getLength();
            if (length > 1.0E-4) {
                final Vector start = rail.railMath.getPosition(0, false);
                final Vector end = rail.railMath.getPosition(length, false);
                if (start != null && end != null) {
                    final double railForwardX = end.x() - start.x();
                    final double railForwardZ = end.z() - start.z();
                    if (railForwardX * forwardX + railForwardZ * forwardZ < 0) {
                        forwardX = -forwardX;
                        forwardZ = -forwardZ;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        final double cross = forwardX * widthZ - forwardZ * widthX;
        return cross >= 0 ? tiltDegrees : -tiltDegrees;
    }

    private static double getBankYOffset(double leftX, double leftZ, double rightX, double rightZ, double tiltDegrees) {
        final double halfWidth = Math.sqrt((rightX - leftX) * (rightX - leftX) + (rightZ - leftZ) * (rightZ - leftZ)) / 2;
        return Math.tan(Math.toRadians(tiltDegrees)) * halfWidth;
    }
}
