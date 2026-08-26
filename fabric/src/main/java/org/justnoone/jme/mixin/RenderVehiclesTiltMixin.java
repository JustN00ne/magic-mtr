package org.justnoone.jme.mixin;

import org.justnoone.jme.client.MagicRailTiltClient;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RenderVehicles.class)
public class RenderVehiclesTiltMixin {

    @Unique
    private static final ThreadLocal<Long> JME_SMOOTHING_COUNTER = ThreadLocal.withInitial(() -> 0L);

    @Inject(
            method = "render(JLorg/mtr/mapping/holder/Vector3d;)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void jme$beginVehicleTiltFrame(long millisElapsed, org.mtr.mapping.holder.Vector3d cameraShakeOffset, CallbackInfo ci) {
        JME_SMOOTHING_COUNTER.set(0L);
        MagicRailTiltClient.beginRenderFrame();
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
            // In current MTR versions (eg 4.0.3+1.20.1) the gangway/barrier connections are rendered
            // inside a synthetic lambda method, not directly in RenderVehicles#render.
            // Keep the original target for backwards compatibility.
            method = {"lambda$render$12", "render(JLorg/mtr/mapping/holder/Vector3d;)V"},
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
            // See note above.
            method = {"lambda$render$12", "render(JLorg/mtr/mapping/holder/Vector3d;)V"},
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
        // The riding car and the local riding player must roll exactly like the camera: share one
        // smoothed "riding car roll" value instead of a per-position lookup that can drift apart.
        if (!useOffset) {
            return MagicRailTiltClient.getRidingCarRollDegrees();
        }

        final long smoothingKey = jme$nextSmoothingKey();
        final double fallbackForwardX = Math.sin(renderingPositionAndRotation.yaw);
        final double fallbackForwardZ = Math.cos(renderingPositionAndRotation.yaw);

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
