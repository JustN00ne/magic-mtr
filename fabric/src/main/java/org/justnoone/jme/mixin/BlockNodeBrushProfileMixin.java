package org.justnoone.jme.mixin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.BrushRailProfile;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.ActionResult;
import org.mtr.mapping.holder.BlockHitResult;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Hand;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockNode.class, remap = false)
public class BlockNodeBrushProfileMixin {

    @Inject(method = "onUse2", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$copyOrApplyBrushProfile(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!world.isClient() || player == null || !player.isHolding(Items.BRUSH.get())) {
            return;
        }

        final ItemStack heldStack = player.getStackInHand(hand);
        if (heldStack == null || heldStack.isEmpty()) {
            return;
        }

        final ObjectObjectImmutablePair<Rail, BlockPos> facingRailAndBlockPos = MinecraftClientData.getInstance().getFacingRailAndBlockPos(false);
        if (facingRailAndBlockPos == null) {
            return;
        }

        final Rail rail = facingRailAndBlockPos.left();
        if (rail == null) {
            return;
        }

        if (player.isSneaking()) {
            final BrushRailProfile profile = BrushRailProfile.fromStack(heldStack);
            if (profile == null) {
                return;
            }

            jme$sendBrushProfileToServer(hand, profile);
            jme$sendApplyBrushProfileToServer(hand, rail, profile);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        final BrushRailProfile copiedProfile = BrushRailProfile.fromRail(rail);
        if (copiedProfile != null) {
            BrushRailProfile.writeToStack(heldStack, copiedProfile);
            jme$sendBrushProfileToServer(hand, copiedProfile);
        }
    }

    @Unique
    private static void jme$sendBrushProfileToServer(Hand hand, BrushRailProfile profile) {
        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeVarInt(hand.ordinal());
        packet.writeVarInt(profile.speedKmh);
        packet.writeString(profile.style);
        packet.writeString(profile.shape.name());
        packet.writeVarInt(profile.tiltStart);
        packet.writeVarInt(profile.tiltMiddle);
        packet.writeVarInt(profile.tiltEnd);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_BRUSH_PROFILE_PACKET_ID, packet);
    }

    @Unique
    private static void jme$sendApplyBrushProfileToServer(Hand hand, Rail rail, BrushRailProfile profile) {
        final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
        final Position position1 = accessor.jme$getPosition1();
        final Position position2 = accessor.jme$getPosition2();

        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeVarInt(hand.ordinal());
        packet.writeLong(position1.getX());
        packet.writeLong(position1.getY());
        packet.writeLong(position1.getZ());
        packet.writeLong(position2.getX());
        packet.writeLong(position2.getY());
        packet.writeLong(position2.getZ());
        packet.writeVarInt(profile.speedKmh);
        packet.writeString(profile.style);
        packet.writeString(profile.shape.name());
        packet.writeVarInt(profile.tiltStart);
        packet.writeVarInt(profile.tiltMiddle);
        packet.writeVarInt(profile.tiltEnd);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.APPLY_BRUSH_PROFILE_PACKET_ID, packet);
    }
}
