package org.justnoone.jme.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.IdentityHashMap;

@Mixin(RenderVehicles.class)
public class RenderVehiclesTiltMixin {

    @Unique
    private static final ThreadLocal<Long> JME_SMOOTHING_COUNTER = ThreadLocal.withInitial(() -> 0L);

    @Unique
    private static final ThreadLocal<IdentityHashMap<PositionAndRotation, PositionAndRotation>> JME_RENDER_TO_ABSOLUTE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @Inject(
            method = "render(JLorg/mtr/mapping/holder/Vector3d;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void jme$beginVehicleTiltFrame(long millisElapsed, org.mtr.mapping.holder.Vector3d cameraShakeOffset, CallbackInfo ci) {
        JME_SMOOTHING_COUNTER.set(0L);
        JME_RENDER_TO_ABSOLUTE.get().clear();
    }

    @Inject(
            method = "getRenderPositionAndRotation(Lorg/mtr/mapping/holder/Vector3d;Ljava/lang/Double;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mapping/holder/Vector3d;)Lorg/mtr/mod/render/PositionAndRotation;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void jme$captureAbsolutePositionForTilt(
            org.mtr.mapping.holder.Vector3d offsetVector,
            Double offsetRotation,
            PositionAndRotation ridingCarPositionAndRotation,
            PositionAndRotation absolutePositionAndRotation,
            org.mtr.mapping.holder.Vector3d cameraShakeOffset,
            CallbackInfoReturnable<PositionAndRotation> cir
    ) {
        final PositionAndRotation renderingPositionAndRotation = cir.getReturnValue();
        if (renderingPositionAndRotation == null || absolutePositionAndRotation == null) {
            return;
        }
        JME_RENDER_TO_ABSOLUTE.get().put(renderingPositionAndRotation, absolutePositionAndRotation);
    }

    @Inject(method = "getStoredMatrixTransformations", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void jme$applyRailTilt(
            boolean useOffset,
            PositionAndRotation renderingPositionAndRotation,
            double oscillationAmount,
            CallbackInfoReturnable<StoredMatrixTransformations> cir
    ) {
        final StoredMatrixTransformations storedMatrixTransformations = cir.getReturnValue();
        if (storedMatrixTransformations == null) {
            return;
        }

        final double tiltDegrees = jme$getSmoothedTiltDegreesAtRenderPosition(useOffset, renderingPositionAndRotation);
        if (Math.abs(tiltDegrees) < 0.001) {
            return;
        }

        final StoredMatrixTransformations adjusted = storedMatrixTransformations.copy();
        // Match the camera and rail cant direction.
        adjusted.add(graphicsHolder -> graphicsHolder.rotateZDegrees((float) -tiltDegrees));
        cir.setReturnValue(adjusted);
    }

    @ModifyArgs(
            method = "render(JLorg/mtr/mapping/holder/Vector3d;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/render/RenderVehicles;renderConnection(ZZZLorg/mtr/mod/render/RenderVehicles$PreviousConnectionPositions;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mod/render/PositionAndRotation;ZDDDDDDZ)V",
                    ordinal = 0
            ),
            remap = false,
            require = 0
    )
    private static void jme$applyRailTiltToGangwayConnection(Args args) {
        jme$applyRailTiltToConnection(args);
    }

    @ModifyArgs(
            method = "render(JLorg/mtr/mapping/holder/Vector3d;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/render/RenderVehicles;renderConnection(ZZZLorg/mtr/mod/render/RenderVehicles$PreviousConnectionPositions;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mod/render/PositionAndRotation;ZDDDDDDZ)V",
                    ordinal = 1
            ),
            remap = false,
            require = 0
    )
    private static void jme$applyRailTiltToBarrierConnection(Args args) {
        jme$applyRailTiltToConnection(args);
    }

    @Unique
    private static void jme$applyRailTiltToConnection(Args args) {
        final PositionAndRotation renderingPositionAndRotation = args.get(10);
        if (renderingPositionAndRotation == null) {
            return;
        }

        final boolean useOffset = args.get(11);
        final double tiltDegrees = jme$getSmoothedTiltDegreesAtRenderPosition(useOffset, renderingPositionAndRotation);
        if (Math.abs(tiltDegrees) < 0.001) {
            return;
        }

        final double oscillationAmount = (double) args.get(17);
        args.set(17, oscillationAmount - tiltDegrees);
    }

    @Unique
    private static long jme$nextSmoothingKey() {
        final long key = JME_SMOOTHING_COUNTER.get() + 1;
        JME_SMOOTHING_COUNTER.set(key);
        return key;
    }

    @Unique
    private static double jme$getSmoothedTiltDegreesAtRenderPosition(boolean useOffset, PositionAndRotation renderingPositionAndRotation) {
        final long smoothingKey = jme$nextSmoothingKey();
        final double fallbackForwardX = Math.sin(renderingPositionAndRotation.yaw);
        final double fallbackForwardZ = Math.cos(renderingPositionAndRotation.yaw);

        if (!useOffset) {
            final PositionAndRotation absolute = JME_RENDER_TO_ABSOLUTE.get().get(renderingPositionAndRotation);
            if (absolute != null) {
                final double forwardX = Math.sin(absolute.yaw);
                final double forwardZ = Math.cos(absolute.yaw);
                return MagicRailTiltClient.getSmoothedSignedTiltDegreesAt(
                        smoothingKey,
                        absolute.position.x,
                        absolute.position.y,
                        absolute.position.z,
                        forwardX,
                        forwardZ
                );
            }

            final Entity cameraEntity = MinecraftClient.getInstance().getCameraEntity();
            if (cameraEntity != null) {
                final Vec3d cameraPos = cameraEntity.getPos();
                return MagicRailTiltClient.getSmoothedSignedTiltDegreesAt(
                        smoothingKey,
                        cameraPos.x,
                        cameraPos.y,
                        cameraPos.z,
                        fallbackForwardX,
                        fallbackForwardZ
                );
            }
        }

        return MagicRailTiltClient.getSmoothedSignedTiltDegreesAt(
                smoothingKey,
                renderingPositionAndRotation.position.x,
                renderingPositionAndRotation.position.y,
                renderingPositionAndRotation.position.z,
                fallbackForwardX,
                fallbackForwardZ
        );
    }
}
