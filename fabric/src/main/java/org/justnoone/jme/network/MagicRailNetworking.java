package org.justnoone.jme.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.justnoone.jme.block.BlockTrainDetector;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.BrushRailProfile;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mod.Items;
import org.mtr.mod.item.ItemNodeModifierBase;
import org.mtr.mod.packet.PacketUpdateData;

import java.lang.reflect.Method;

public final class MagicRailNetworking {

    private MagicRailNetworking() {
    }

    public static void registerServer() {
        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_SPEED_PACKET_ID, (server, player, buf) -> {
            final int speedKmh = MagicRailConstants.clampToStep(buf.readVarInt());
            final Hand hand = decodeHand(buf.readVarInt());
            final String style = buf.readString();
            final Rail.Shape shape;
            try {
                shape = Rail.Shape.valueOf(buf.readString());
            } catch (Exception ignored) {
                return;
            }
            final int tiltStart = MagicRailConstants.clampTiltDegrees(buf.readVarInt());
            final int tiltMiddle = MagicRailConstants.clampTiltDegrees(buf.readVarInt());
            final int tiltEnd = MagicRailConstants.clampTiltDegrees(buf.readVarInt());

            MagicNetworkingCompat.executeOnServer(server, () -> applySettingsToHeldConnector(player, hand, speedKmh, style, shape, tiltStart, tiltMiddle, tiltEnd));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_RAIL_TILT_PACKET_ID, (server, player, buf) -> {
            final String railId = buf.readString();
            final int tiltStart = MagicRailConstants.clampTiltDegrees(buf.readVarInt());
            final int tiltMiddle = MagicRailConstants.clampTiltDegrees(buf.readVarInt());
            final int tiltEnd = MagicRailConstants.clampTiltDegrees(buf.readVarInt());

            MagicNetworkingCompat.executeOnServer(server, () -> applyTiltToRail(railId, tiltStart, tiltMiddle, tiltEnd));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_BRUSH_PROFILE_PACKET_ID, (server, player, buf) -> {
            final Hand hand = decodeHand(buf.readVarInt());
            final BrushRailProfile profile = readBrushProfile(buf);
            if (profile == null) {
                return;
            }

            MagicNetworkingCompat.executeOnServer(server, () -> applyBrushProfileToHeldBrush(player, hand, profile));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.APPLY_BRUSH_PROFILE_PACKET_ID, (server, player, buf) -> {
            final Hand hand = decodeHand(buf.readVarInt());
            final long x1 = buf.readLong();
            final long y1 = buf.readLong();
            final long z1 = buf.readLong();
            final long x2 = buf.readLong();
            final long y2 = buf.readLong();
            final long z2 = buf.readLong();
            final BrushRailProfile profile = readBrushProfile(buf);
            if (profile == null) {
                return;
            }

            MagicNetworkingCompat.executeOnServer(server, () -> {
                applyBrushProfileToHeldBrush(player, hand, profile);
                applyBrushProfileToRail(player, x1, y1, z1, x2, y2, z2, profile);
            });
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, (server, player, buf) -> {
            final long routeId = buf.readLong();
            final long primaryPlatformId = buf.readLong();
            final long alternativePlatformId = buf.readLong();
            final boolean enabled = buf.readBoolean();
            if (!AlternativePlatformRegistry.isEnabled()) {
                return;
            }
            MagicNetworkingCompat.executeOnServer(server, () -> AlternativePlatformRegistry.setAlternative(routeId, primaryPlatformId, alternativePlatformId, enabled));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_DEPOT_CANCELLATION_PACKET_ID, (server, player, buf) -> {
            final long depotId = buf.readLong();
            final boolean enabled = buf.readBoolean();
            final int thresholdMinutes = buf.readVarInt();
            final DepotCancellationRegistry.Action action = DepotCancellationRegistry.Action.fromId(buf.readString());
            MagicNetworkingCompat.executeOnServer(server, () -> DepotCancellationRegistry.set(depotId, new DepotCancellationRegistry.Settings(enabled, thresholdMinutes, action)));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_TRAIN_DETECTOR_RANGE_PACKET_ID, (server, player, buf) -> {
            final BlockPos blockPos = buf.readBlockPos();
            final int nodeRange = buf.readVarInt();
            boolean useSecondsOffset = false;
            int secondsOffset = 0;
            if (buf.readableBytes() > 0) {
                useSecondsOffset = buf.readBoolean();
            }
            if (buf.readableBytes() > 0) {
                secondsOffset = buf.readVarInt();
            }
            final boolean finalUseSecondsOffset = useSecondsOffset;
            final int finalSecondsOffset = secondsOffset;
            MagicNetworkingCompat.executeOnServer(server, () -> applyTrainDetectorConfig(player, blockPos, nodeRange, finalUseSecondsOffset, finalSecondsOffset));
        });

        MagicNetworkingCompat.registerServerReceiver(MagicRailConstants.SET_ROUTE_TYPE_OVERRIDE_PACKET_ID, (server, player, buf) -> {
            final String routeId = buf.readString();
            final String routeType = buf.readString();
            MagicNetworkingCompat.executeOnServer(server, () -> RouteTypeOverrideConfig.setRouteType(routeId, routeType));
        });
    }

    private static void applySettingsToHeldConnector(ServerPlayerEntity player, Hand hand, int speedKmh, String style, Rail.Shape shape, int tiltStart, int tiltMiddle, int tiltEnd) {
        final ItemStack stack = new ItemStack(player.getStackInHand(hand));
        if (!MagicRailConstants.isUniversalConnector(stack)) {
            return;
        }

        MagicRailConstants.setSpeedOnStack(stack, speedKmh);
        MagicRailConstants.setStyleOnStack(stack, style);
        MagicRailConstants.setShapeOnStack(stack, shape);
        MagicRailConstants.setStartTiltOnStack(stack, tiltStart);
        MagicRailConstants.setMiddleTiltOnStack(stack, tiltMiddle);
        MagicRailConstants.setEndTiltOnStack(stack, tiltEnd);
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void applyBrushProfileToHeldBrush(ServerPlayerEntity player, Hand hand, BrushRailProfile profile) {
        if (player == null || profile == null) {
            return;
        }

        final net.minecraft.item.ItemStack stackInHand = player.getStackInHand(hand);
        if (stackInHand == null || stackInHand.isEmpty() || stackInHand.getItem() != Items.BRUSH.get().data) {
            return;
        }

        BrushRailProfile.writeToStack(new ItemStack(stackInHand), profile);
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void applyBrushProfileToRail(ServerPlayerEntity player, long x1, long y1, long z1, long x2, long y2, long z2, BrushRailProfile profile) {
        final net.minecraft.world.World playerWorld = jme$getPlayerWorld(player);
        if (player == null || playerWorld == null || profile == null) {
            return;
        }
        if (!(playerWorld instanceof net.minecraft.server.world.ServerWorld)) {
            return;
        }

        final org.mtr.mapping.holder.ServerPlayerEntity wrappedPlayer = new org.mtr.mapping.holder.ServerPlayerEntity(player);
        final org.mtr.mapping.holder.ServerWorld wrappedServerWorld = new org.mtr.mapping.holder.ServerWorld((net.minecraft.server.world.ServerWorld) playerWorld);
        final org.mtr.mapping.holder.World wrappedWorld = new org.mtr.mapping.holder.World(playerWorld);
        final org.mtr.mapping.holder.BlockPos startPos = new org.mtr.mapping.holder.BlockPos(clampToInt(x1), clampToInt(y1), clampToInt(z1));
        final org.mtr.mapping.holder.BlockPos endPos = new org.mtr.mapping.holder.BlockPos(clampToInt(x2), clampToInt(y2), clampToInt(z2));

        ItemNodeModifierBase.getRail(wrappedWorld, startPos, endPos, wrappedPlayer, rail -> {
            final Rail updatedRail = BrushRailProfile.applyToRail(rail, profile);
            if (updatedRail != null) {
                PacketUpdateData.sendDirectlyToServerRail(wrappedServerWorld, updatedRail);
            }
        });
    }

    private static void applyTiltToRail(String railId, int tiltStart, int tiltMiddle, int tiltEnd) {
        if (railId == null || railId.isEmpty()) {
            return;
        }
        MagicRailTiltRegistry.setTiltAbsolute(railId, tiltStart, tiltMiddle, tiltEnd);
    }

    private static void applyTrainDetectorConfig(ServerPlayerEntity player, BlockPos blockPos, int nodeRange, boolean useSecondsOffset, int secondsOffset) {
        final net.minecraft.world.World playerWorld = jme$getPlayerWorld(player);
        if (player == null || playerWorld == null || blockPos == null) {
            return;
        }

        final net.minecraft.block.entity.BlockEntity blockEntity = playerWorld.getBlockEntity(blockPos);
        if (blockEntity instanceof BlockTrainDetector.BlockEntity) {
            final BlockTrainDetector.BlockEntity detectorBlockEntity = (BlockTrainDetector.BlockEntity) blockEntity;
            detectorBlockEntity.setNodeRange(nodeRange);
            detectorBlockEntity.setUseSecondsOffset(useSecondsOffset);
            detectorBlockEntity.setSecondsOffset(secondsOffset);
        }
    }

    private static net.minecraft.world.World jme$getPlayerWorld(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }

        try {
            final Method getServerWorldMethod = player.getClass().getMethod("getServerWorld");
            final Object world = getServerWorldMethod.invoke(player);
            if (world instanceof net.minecraft.world.World) {
                return (net.minecraft.world.World) world;
            }
        } catch (Exception ignored) {
        }

        try {
            final Method getWorldMethod = player.getClass().getMethod("getWorld");
            final Object world = getWorldMethod.invoke(player);
            if (world instanceof net.minecraft.world.World) {
                return (net.minecraft.world.World) world;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static BrushRailProfile readBrushProfile(PacketByteBuf packetByteBuf) {
        final int speedKmh = MagicRailConstants.clampToStep(packetByteBuf.readVarInt());
        final String style = packetByteBuf.readString();
        final Rail.Shape shape;
        try {
            shape = Rail.Shape.valueOf(packetByteBuf.readString());
        } catch (Exception ignored) {
            return null;
        }
        final int tiltStart = MagicRailConstants.clampTiltDegrees(packetByteBuf.readVarInt());
        final int tiltMiddle = MagicRailConstants.clampTiltDegrees(packetByteBuf.readVarInt());
        final int tiltEnd = MagicRailConstants.clampTiltDegrees(packetByteBuf.readVarInt());
        return new BrushRailProfile(speedKmh, style, shape, tiltStart, tiltMiddle, tiltEnd);
    }

    private static Hand decodeHand(int handOrdinal) {
        final Hand[] values = Hand.values();
        if (handOrdinal < 0 || handOrdinal >= values.length) {
            return Hand.MAIN_HAND;
        }
        return values[handOrdinal];
    }

    private static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
