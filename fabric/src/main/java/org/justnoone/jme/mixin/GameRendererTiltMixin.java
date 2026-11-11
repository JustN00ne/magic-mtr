package org.justnoone.jme.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.justnoone.jme.compat.McClientCompat;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererTiltMixin {

    // 1.17+ calls WorldRenderer#setupFrustum before WorldRenderer#render; 1.16 only has render (its Matrix4f is
    // net.minecraft's; org.joml's from 1.19.3). Only the descriptors of the version being built resolve, and this flag
    // makes sure the roll is applied once per frame, before the frustum is built where that exists.
    @Unique
    private boolean jme$cameraTiltApplied;

    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void jme$resetCameraTilt(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        jme$cameraTiltApplied = false;
    }

    @Inject(
            method = "renderWorld",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;)V", shift = At.Shift.BEFORE),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Matrix4f;)V", shift = At.Shift.BEFORE)
            },
            require = 0
    )
    private void jme$tiltCameraBeforeFrustum(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        jme$tiltCameraWhenRiding(matrices);
    }

    @Inject(
            method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/util/math/Matrix4f;)V", shift = At.Shift.BEFORE),
            require = 0
    )
    private void jme$tiltCameraBeforeWorld(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        jme$tiltCameraWhenRiding(matrices);
    }

    @Unique
    private void jme$tiltCameraWhenRiding(MatrixStack matrices) {
        if (jme$cameraTiltApplied) {
            return;
        }
        jme$cameraTiltApplied = true;
        if (!JmeConfig.cameraTiltEnabled()) {
            return;
        }
        if (!MagicRailTiltClient.isPlayerRidingMtrVehicle()) {
            return;
        }
        // The roll is signed relative to this yaw; without it the bank direction is unknown, so don't guess.
        final Double vehicleYaw = MagicRailTiltClient.getRidingVehicleYawRadians();
        if (vehicleYaw == null) {
            return;
        }

        final double tiltDegrees = MagicRailTiltClient.getCameraTiltDegrees() * JmeConfig.cameraTiltStrength();
        if (Math.abs(tiltDegrees) < 0.001) {
            return;
        }

        // The ridden car is rotated by -tilt around its forward axis (RenderVehiclesTiltMixin). The camera moves with
        // the car, so the view matrix (the inverse camera transform, already holding pitch and yaw here) gets the
        // inverse rotation, +tilt, around that same world-space axis. Rolling around the screen or world Z axis
        // instead made the bank flip with the track heading and vanish on east-west track.
        McClientCompat.rotateAroundAxis(matrices, (float) Math.sin(vehicleYaw), 0, (float) Math.cos(vehicleYaw), (float) tiltDegrees);
    }
}
