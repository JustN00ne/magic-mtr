package org.justnoone.jme.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererTiltMixin {

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void jme$tiltCameraWhenRiding(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        if (!JmeConfig.cameraTiltEnabled()) {
            return;
        }
        if (!MagicRailTiltClient.isPlayerRidingMtrVehicle()) {
            return;
        }

        final double tiltDegrees = MagicRailTiltClient.getCameraTiltDegrees() * JmeConfig.cameraTiltStrength();
        if (Math.abs(tiltDegrees) < 0.001) {
            return;
        }

        // World render matrices are the inverse camera transform; invert roll to match physical bank direction.
        jme$applyZRotation(matrices, (float) -tiltDegrees);
    }

    private static void jme$applyZRotation(MatrixStack matrices, float degrees) {
        // 1.19+ path
        try {
            final Class<?> rotationAxisClass = Class.forName("net.minecraft.util.math.RotationAxis");
            final Object positiveZ = rotationAxisClass.getField("POSITIVE_Z").get(null);
            final Object rotation = rotationAxisClass.getMethod("rotationDegrees", float.class).invoke(positiveZ, degrees);
            matrices.getClass().getMethod("multiply", rotation.getClass()).invoke(matrices, rotation);
            return;
        } catch (Exception ignored) {
        }

        // 1.16.5 path
        try {
            final Class<?> vec3fClass = Class.forName("net.minecraft.util.math.Vec3f");
            final Object positiveZ = vec3fClass.getField("POSITIVE_Z").get(null);
            final Object quaternion = vec3fClass.getMethod("getDegreesQuaternion", float.class).invoke(positiveZ, degrees);
            matrices.getClass().getMethod("multiply", quaternion.getClass()).invoke(matrices, quaternion);
        } catch (Exception ignored) {
        }
    }
}
