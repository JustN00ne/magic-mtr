package org.justnoone.jme.block;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.justnoone.jme.Jme;
import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.item.CrossingLinkMode;
import org.justnoone.jme.item.CrossingLinkerItem;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.util.CrossingLinkRules;
import org.mtr.mapping.mapper.TextHelper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crossing Linker interactions (server authoritative). The crossing controller is always selected first: with the
 * linker in Start Node, End Node or Object Linking mode, right-click a crossing controller (a level crossing, or a legacy
 * controller) to select it, then right-click an axle counter on a rail (start / end node modes) or a crossing object
 * (object mode) to link it to the controller. Linking a counter or object that is already linked the same way unlinks
 * it. Right-clicking the selected controller again cancels; sneak + right-click cycles the modes and never changes a
 * connection. The selection ({@link Session}) lives on the server and is sent to the player's client, which draws the
 * outlines and preview line.
 * <p>
 * Also handles sneak + right-click on a level crossing with MTR's brush, which picks its bell sound.
 */
public final class CrossingLinkerHandler {

    public static final org.mtr.mapping.holder.Identifier SESSION_PACKET_ID = new org.mtr.mapping.holder.Identifier(Jme.MOD_ID, "crossing_link_session");
    private static final int SESSION_CHECK_TICKS = 10;
    /**
     * The selection is cancelled when the player moves further than this from the selected controller.
     */
    private static final double MAX_SESSION_DISTANCE = 96;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int sessionCheckCounter;

    private CrossingLinkerHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            final ItemStack heldItem = player.getStackInHand(hand);
            final BlockPos pos = hitResult.getBlockPos();

            // Sneak + right-click with MTR's brush picks the level crossing's next bell sound. Handled on the client
            // too, so the brush's own action never runs on the crossing.
            if (player.isSneaking() && heldItem.getItem() instanceof org.mtr.mod.item.ItemBrush && world.getBlockEntity(pos) instanceof BlockCrossingLights.BlockEntity) {
                if (!world.isClient()) {
                    final int bell = ((BlockCrossingLights.BlockEntity) world.getBlockEntity(pos)).cycleBell();
                    player.sendMessage(TextHelper.literal("§aBell sound " + (bell + 1) + " of " + CrossingSignal.getBellCount()).data, true);
                }
                return ActionResult.success(world.isClient());
            }

            if (!(heldItem.getItem() instanceof CrossingLinkerItem)) {
                return ActionResult.PASS;
            }
            if (player.isSneaking()) {
                CrossingLinkerItem.cycleMode(world, player, heldItem);
                return ActionResult.success(world.isClient());
            }
            final net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
            final boolean relevant = blockEntity instanceof CrossingControl.Holder || blockEntity instanceof CrossingObject || blockEntity instanceof BlockAxleCounter.BlockEntity;
            if (!relevant) {
                return ActionResult.PASS;
            }
            if (!world.isClient() && player instanceof ServerPlayerEntity) {
                onBlockClicked((ServerPlayerEntity) player, world, pos, blockEntity, CrossingLinkMode.get(heldItem));
            }
            return ActionResult.success(world.isClient());
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND || !(entity instanceof AxleCounterEntity)) {
                return ActionResult.PASS;
            }
            final ItemStack heldItem = player.getStackInHand(hand);
            if (!(heldItem.getItem() instanceof CrossingLinkerItem)) {
                return ActionResult.PASS;
            }
            if (player.isSneaking()) {
                CrossingLinkerItem.cycleMode(world, player, heldItem);
            } else if (!world.isClient() && player instanceof ServerPlayerEntity) {
                onCounterClicked((ServerPlayerEntity) player, world, (AxleCounterEntity) entity, CrossingLinkMode.get(heldItem));
            }
            return ActionResult.success(world.isClient());
        });

        ServerTickEvents.END_SERVER_TICK.register(CrossingLinkerHandler::checkSessions);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
    }

    public static void onModeChanged(ServerPlayerEntity player, CrossingLinkMode mode) {
        player.sendMessage(TextHelper.literal("§7Crossing Linker: §f" + mode.displayName + (mode == CrossingLinkMode.INACTIVE ? "" : "§7 - right-click a crossing controller first")).data, true);
        if (mode == CrossingLinkMode.INACTIVE) {
            clearSession(player);
        } else {
            // The outline colour follows the mode; resend so the client redraws it.
            final Session session = SESSIONS.get(player.getUuid());
            sendSession(player, session);
        }
    }

    public static void onUseInAir(ServerPlayerEntity player, ItemStack stack) {
        final CrossingLinkMode mode = CrossingLinkMode.get(stack);
        if (mode == CrossingLinkMode.INACTIVE) {
            player.sendMessage(TextHelper.literal("§7Crossing Linker is inactive. Sneak + right-click to choose a mode.").data, true);
        } else if (!SESSIONS.containsKey(player.getUuid())) {
            player.sendMessage(TextHelper.literal("§eRight-click a crossing controller (level crossing) first.").data, true);
        } else if (mode.linksCounters()) {
            player.sendMessage(TextHelper.literal("§eAim at an axle counter on the rail.").data, true);
        }
    }

    private static void onBlockClicked(ServerPlayerEntity player, World world, BlockPos pos, net.minecraft.block.entity.BlockEntity blockEntity, CrossingLinkMode mode) {
        if (mode == CrossingLinkMode.INACTIVE) {
            player.sendMessage(TextHelper.literal("§7Crossing Linker is inactive. Sneak + right-click to choose a mode.").data, true);
            return;
        }
        final Session session = getValidSession(player, world);

        if (session != null && session.controllerPos.equals(pos)) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§eSelection cancelled.").data, true);
            return;
        }

        if (mode == CrossingLinkMode.OBJECT && session != null && blockEntity instanceof CrossingObject) {
            linkObject(player, world, session, pos);
            return;
        }

        if (blockEntity instanceof CrossingControl.Holder) {
            setSession(player, new Session(pos.toImmutable(), world.getRegistryKey().getValue()));
            player.sendMessage(TextHelper.literal("§aSelected crossing controller at " + pos.toShortString() + ". " + (mode == CrossingLinkMode.OBJECT ? "Right-click a crossing object to link it." : "Right-click an axle counter on the rail to link it as the " + mode.displayName.toLowerCase() + ".")).data, true);
            return;
        }

        if (session == null) {
            player.sendMessage(TextHelper.literal("§eRight-click a crossing controller (level crossing) first.").data, true);
        } else if (blockEntity instanceof BlockAxleCounter.BlockEntity) {
            player.sendMessage(TextHelper.literal("§cThis is an old axle counter block. Place axle counters on the rail and link those.").data, true);
        } else {
            player.sendMessage(TextHelper.literal("§cThis cannot be linked in " + mode.displayName + " mode.").data, true);
        }
    }

    private static void onCounterClicked(ServerPlayerEntity player, World world, AxleCounterEntity counter, CrossingLinkMode mode) {
        if (!mode.linksCounters()) {
            player.sendMessage(TextHelper.literal(mode == CrossingLinkMode.INACTIVE ? "§7Crossing Linker is inactive. Sneak + right-click to choose a mode." : "§cAxle counters are linked in Start Node or End Node mode.").data, true);
            return;
        }
        final Session session = getValidSession(player, world);
        if (session == null) {
            player.sendMessage(TextHelper.literal("§eRight-click a crossing controller (level crossing) first.").data, true);
            return;
        }
        final CrossingControl control = CrossingLinks.getControl(world, session.controllerPos);
        if (control == null) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§cThe selected crossing controller is gone. Select it again.").data, true);
            return;
        }
        if (counter.getBlockPos().getSquaredDistance(session.controllerPos) > CrossingControl.MAX_LINK_DISTANCE * CrossingControl.MAX_LINK_DISTANCE) {
            player.sendMessage(TextHelper.literal("§cThis axle counter is too far from the controller (max " + (int) CrossingControl.MAX_LINK_DISTANCE + " blocks).").data, true);
            return;
        }

        final boolean startRole = mode == CrossingLinkMode.START_NODE;
        final CrossingLinkRules.CounterLinkResult result = CrossingLinks.linkCounter(world, session.controllerPos, control, startRole, counter);
        final String role = startRole ? "§2start node" : "§4end node";
        switch (result) {
            case LINKED:
                player.sendMessage(TextHelper.literal("§aLinked the axle counter as the " + role + "§a.").data, true);
                break;
            case REPLACED:
                player.sendMessage(TextHelper.literal("§aLinked the axle counter as the " + role + "§a, replacing the previous one.").data, true);
                break;
            case UNLINKED:
                player.sendMessage(TextHelper.literal("§eUnlinked the " + role + "§e axle counter.").data, true);
                break;
            case REJECTED_OTHER_ROLE:
                player.sendMessage(TextHelper.literal("§cThis axle counter is already the controller's " + (startRole ? "end" : "start") + " node.").data, true);
                return;
        }
        // The operation is complete: the outline and preview line are cleared.
        clearSession(player);
    }

    private static void linkObject(ServerPlayerEntity player, World world, Session session, BlockPos objectPos) {
        final CrossingControl control = CrossingLinks.getControl(world, session.controllerPos);
        if (control == null) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§cThe selected crossing controller is gone. Select it again.").data, true);
            return;
        }
        if (objectPos.getSquaredDistance(session.controllerPos) > CrossingControl.MAX_LINK_DISTANCE * CrossingControl.MAX_LINK_DISTANCE) {
            player.sendMessage(TextHelper.literal("§cThis object is too far from the controller (max " + (int) CrossingControl.MAX_LINK_DISTANCE + " blocks).").data, true);
            return;
        }
        final int result = CrossingLinks.toggleObject(world, control, objectPos);
        if (result > 0) {
            player.sendMessage(TextHelper.literal("§9Linked the crossing object at " + objectPos.toShortString() + " (" + CrossingLinks.getLinkedObjectsCount(control) + " linked).").data, true);
        } else if (result < 0) {
            player.sendMessage(TextHelper.literal("§eUnlinked the crossing object at " + objectPos.toShortString() + " (" + CrossingLinks.getLinkedObjectsCount(control) + " linked).").data, true);
        } else {
            player.sendMessage(TextHelper.literal("§cThis controller already has the maximum number of linked objects.").data, true);
            return;
        }
        clearSession(player);
    }

    /**
     * Cancels selections whose player no longer holds the linker, changed dimension, moved away, or whose controller
     * is gone.
     */
    private static void checkSessions(MinecraftServer server) {
        if (SESSIONS.isEmpty() || ++sessionCheckCounter < SESSION_CHECK_TICKS) {
            return;
        }
        sessionCheckCounter = 0;
        SESSIONS.entrySet().removeIf(entry -> {
            final ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            final Session session = entry.getValue();
            final ItemStack heldItem = player.getMainHandStack();
            final World world = player.getEntityWorld();
            final boolean valid = heldItem.getItem() instanceof CrossingLinkerItem
                    && CrossingLinkMode.get(heldItem) != CrossingLinkMode.INACTIVE
                    && world.getRegistryKey().getValue().equals(session.dimension)
                    && player.getBlockPos().getSquaredDistance(session.controllerPos) <= MAX_SESSION_DISTANCE * MAX_SESSION_DISTANCE
                    && (!world.isChunkLoaded(session.controllerPos.getX() >> 4, session.controllerPos.getZ() >> 4) || CrossingLinks.getControl(world, session.controllerPos) != null);
            if (!valid) {
                sendSession(player, null);
            }
            return !valid;
        });
    }

    @Nullable
    private static Session getValidSession(ServerPlayerEntity player, World world) {
        final Session session = SESSIONS.get(player.getUuid());
        if (session == null || !world.getRegistryKey().getValue().equals(session.dimension)) {
            return null;
        }
        return session;
    }

    private static void setSession(ServerPlayerEntity player, Session session) {
        SESSIONS.put(player.getUuid(), session);
        sendSession(player, session);
    }

    private static void clearSession(ServerPlayerEntity player) {
        if (SESSIONS.remove(player.getUuid()) != null) {
            sendSession(player, null);
        }
    }

    private static void sendSession(PlayerEntity player, @Nullable Session session) {
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeBoolean(session != null);
        if (session != null) {
            packetByteBuf.writeLong(session.controllerPos.asLong());
        }
        MagicNetworkingCompat.sendToPlayer((ServerPlayerEntity) player, SESSION_PACKET_ID, packetByteBuf);
    }

    /**
     * The crossing controller a player has selected with the linker.
     */
    private static final class Session {

        private final BlockPos controllerPos;
        /**
         * The dimension's id (a registry key's class moved between the supported versions; its id did not).
         */
        private final Identifier dimension;

        private Session(BlockPos controllerPos, Identifier dimension) {
            this.controllerPos = controllerPos;
            this.dimension = dimension;
        }
    }
}
