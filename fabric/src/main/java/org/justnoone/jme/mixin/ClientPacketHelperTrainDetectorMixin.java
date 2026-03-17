package org.justnoone.jme.mixin;

import org.justnoone.jme.block.BlockTrainDetector;
import org.justnoone.jme.client.screen.TrainDetectorSensorScreen;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mod.packet.ClientPacketHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketHelper.class, remap = false)
public abstract class ClientPacketHelperTrainDetectorMixin {

    @Inject(method = "openBlockEntityScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jme$openTrainDetectorSensorScreen(BlockPos blockPos, CallbackInfo ci) {
        final MinecraftClient minecraftClient = MinecraftClient.getInstance();
        final ClientWorld clientWorld = minecraftClient.getWorldMapped();
        if (clientWorld == null) {
            return;
        }

        final BlockEntity blockEntity = clientWorld.getBlockEntity(blockPos);
        if (blockEntity == null || !(blockEntity.data instanceof BlockTrainDetector.BlockEntity)) {
            return;
        }
        final BlockTrainDetector.BlockEntity detectorBlockEntity = (BlockTrainDetector.BlockEntity) blockEntity.data;

        final Screen currentScreen = minecraftClient.getCurrentScreenMapped();
        if (currentScreen == null || !(currentScreen.data instanceof TrainDetectorSensorScreen)) {
            minecraftClient.openScreen(new Screen(new TrainDetectorSensorScreen(blockPos, detectorBlockEntity)));
        }
        ci.cancel();
    }
}
