package org.justnoone.jme.block;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.justnoone.jme.Jme;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.mtr.mapping.mapper.TextHelper;

import java.util.List;
import java.util.UUID;

/**
 * The settings edited in the crossing screens (opened by right-clicking a level crossing, legacy controller or crossing
 * gate with MTR's brush), sent by the client and applied by the server. The screens read the current settings from the
 * block entities, which the server syncs to clients.
 */
public final class CrossingConfigPackets {

    public static final org.mtr.mapping.holder.Identifier PACKET_ID = new org.mtr.mapping.holder.Identifier(Jme.MOD_ID, "crossing_config");

    private static final int ACTION_CONTROLLER = 0;
    private static final int ACTION_LIGHTS = 1;
    private static final int ACTION_GATE = 2;
    private static final int ACTION_UNLINK = 3;
    private static final int ACTION_PREVIEW_BELL = 4;

    public static final int ROLE_START = 0;
    public static final int ROLE_END = 1;
    public static final int ROLE_OBJECT = 2;
    public static final int ROLE_DETECTOR = 3;

    /**
     * The player must be this close to the block to change it (they opened the screen by clicking it).
     */
    private static final double MAX_DISTANCE = 32;

    private CrossingConfigPackets() {
    }

    public static void register() {
        MagicNetworkingCompat.registerServerReceiver(PACKET_ID, (server, player, buf) -> {
            final int action = buf.readByte();
            final BlockPos pos = buf.readBlockPos();
            final Runnable apply;
            switch (action) {
                case ACTION_CONTROLLER: {
                    final boolean manualClosed = buf.readBoolean();
                    final int lightsOn = buf.readVarInt(), gatesLower = buf.readVarInt(), gatesRaise = buf.readVarInt(), lightsOff = buf.readVarInt();
                    apply = () -> {
                        final CrossingControl control = getControl(player, pos);
                        if (control != null) {
                            control.configure(manualClosed, lightsOn, gatesLower, gatesRaise, lightsOff);
                        }
                    };
                    break;
                }
                case ACTION_LIGHTS: {
                    final int bell = buf.readVarInt(), flashRate = buf.readVarInt();
                    apply = () -> {
                        final net.minecraft.block.entity.BlockEntity blockEntity = getBlockEntity(player, pos);
                        if (blockEntity instanceof BlockCrossingLights.BlockEntity) {
                            ((BlockCrossingLights.BlockEntity) blockEntity).configure(bell, flashRate);
                        }
                    };
                    break;
                }
                case ACTION_GATE: {
                    final int length = buf.readVarInt(), bell = buf.readVarInt();
                    apply = () -> {
                        final net.minecraft.block.entity.BlockEntity blockEntity = getBlockEntity(player, pos);
                        if (blockEntity instanceof BlockCrossingGate.BlockEntity) {
                            ((BlockCrossingGate.BlockEntity) blockEntity).configure(length, bell);
                        }
                    };
                    break;
                }
                case ACTION_UNLINK: {
                    final int role = buf.readByte();
                    final int index = buf.readVarInt();
                    final long identity1 = buf.readLong(), identity2 = buf.readLong();
                    apply = () -> unlink(player, pos, role, index, identity1, identity2);
                    break;
                }
                case ACTION_PREVIEW_BELL: {
                    final int bell = buf.readVarInt();
                    apply = () -> {
                        if (getBlockEntity(player, pos) != null) {
                            CrossingSignal.preview(new org.mtr.mapping.holder.World(player.getEntityWorld()), new org.mtr.mapping.holder.BlockPos(pos), bell);
                        }
                    };
                    break;
                }
                default:
                    return;
            }
            MagicNetworkingCompat.executeOnServer(server, apply);
        });
    }

    public static PacketByteBuf controller(BlockPos pos, boolean manualClosed, int lightsOnDelay, int gatesLowerDelay, int gatesRaiseDelay, int lightsOffDelay) {
        final PacketByteBuf buf = start(ACTION_CONTROLLER, pos);
        buf.writeBoolean(manualClosed);
        buf.writeVarInt(lightsOnDelay);
        buf.writeVarInt(gatesLowerDelay);
        buf.writeVarInt(gatesRaiseDelay);
        buf.writeVarInt(lightsOffDelay);
        return buf;
    }

    public static PacketByteBuf lights(BlockPos pos, int bell, int flashRate) {
        final PacketByteBuf buf = start(ACTION_LIGHTS, pos);
        buf.writeVarInt(bell);
        buf.writeVarInt(flashRate);
        return buf;
    }

    public static PacketByteBuf gate(BlockPos pos, int length, int bell) {
        final PacketByteBuf buf = start(ACTION_GATE, pos);
        buf.writeVarInt(length);
        buf.writeVarInt(bell);
        return buf;
    }

    /**
     * Unlinks a counter (by its index in the role and its UUID, both 0 for a legacy counter block) or an object (by its
     * position) from a controller.
     */
    public static PacketByteBuf unlink(BlockPos controllerPos, int role, int index, long identity1, long identity2) {
        final PacketByteBuf buf = start(ACTION_UNLINK, controllerPos);
        buf.writeByte(role);
        buf.writeVarInt(index);
        buf.writeLong(identity1);
        buf.writeLong(identity2);
        return buf;
    }

    public static PacketByteBuf previewBell(BlockPos pos, int bell) {
        final PacketByteBuf buf = start(ACTION_PREVIEW_BELL, pos);
        buf.writeVarInt(bell);
        return buf;
    }

    private static PacketByteBuf start(int action, BlockPos pos) {
        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeBlockPos(pos);
        return buf;
    }

    private static void unlink(ServerPlayerEntity player, BlockPos controllerPos, int role, int index, long identity1, long identity2) {
        final CrossingControl control = getControl(player, controllerPos);
        if (control == null) {
            return;
        }
        final org.mtr.mapping.holder.World world = new org.mtr.mapping.holder.World(player.getEntityWorld());
        if (role == ROLE_DETECTOR) {
            final List<org.mtr.mapping.holder.BlockPos> detectors = control.getDetectors();
            if (index >= 0 && index < detectors.size() && detectors.get(index).asLong() == identity1 && control.unlinkDetector(index)) {
                player.sendMessage(TextHelper.literal("§eUnlinked the train detector at " + BlockPos.fromLong(identity1).toShortString() + ".").data, true);
            }
            return;
        }
        if (role == ROLE_OBJECT) {
            final BlockPos objectPos = BlockPos.fromLong(identity1);
            for (final org.mtr.mapping.holder.BlockPos linked : control.getObjects()) {
                if (linked.data.equals(objectPos)) {
                    control.toggleObject(world, linked);
                    player.sendMessage(TextHelper.literal("§eUnlinked the crossing object at " + objectPos.toShortString() + ".").data, true);
                    return;
                }
            }
            return;
        }
        // The list may have changed since the screen was opened: only unlink the counter the player saw.
        final boolean startRole = role == ROLE_START;
        final List<CrossingControl.CounterLink> links = startRole ? control.getStarts() : control.getEnds();
        if (index >= 0 && index < links.size()) {
            final UUID id = links.get(index).getEntityId();
            final boolean matches = id == null ? identity1 == 0 && identity2 == 0 : id.getMostSignificantBits() == identity1 && id.getLeastSignificantBits() == identity2;
            if (matches && control.unlinkCounter(world, new org.mtr.mapping.holder.BlockPos(controllerPos), startRole, index)) {
                player.sendMessage(TextHelper.literal("§eUnlinked the " + (startRole ? "§2start node" : "§4end node") + "§e axle counter.").data, true);
            }
        }
    }

    private static CrossingControl getControl(ServerPlayerEntity player, BlockPos pos) {
        final net.minecraft.block.entity.BlockEntity blockEntity = getBlockEntity(player, pos);
        return blockEntity instanceof CrossingControl.Holder ? ((CrossingControl.Holder) blockEntity).getCrossingControl() : null;
    }

    private static net.minecraft.block.entity.BlockEntity getBlockEntity(ServerPlayerEntity player, BlockPos pos) {
        final World world = player.getEntityWorld();
        if (player.isSpectator() || player.getBlockPos().getSquaredDistance(pos) > MAX_DISTANCE * MAX_DISTANCE || !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        return world.getBlockEntity(pos);
    }
}
