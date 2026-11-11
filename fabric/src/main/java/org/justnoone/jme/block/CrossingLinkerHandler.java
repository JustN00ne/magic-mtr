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
 * Crossing Linker interactions (server authoritative). With the linker in Start Node or End Node mode, right-click a
 * crossing controller (a level crossing, or a legacy controller) and then an axle counter on a rail, or the counter
 * first and then the controller, to link the counter to the controller: counters can be any distance away and a
 * controller can have several of each. In Object Linking mode, right-click the controller and then a crossing object
 * (a level crossing or crossing gate). Linking a counter or object that is already linked the same way unlinks it.
 * Right-clicking the selected block again cancels; sneak + right-click cycles the modes and never changes a
 * connection. The selection ({@link Session}) lives on the server and is sent to the player's client, which draws the
 * outlines and preview line.
 * <p>
 * Also opens the crossing screens: right-click a level crossing or legacy controller with MTR's brush for the
 * controller's screen (sneak + right-click a level crossing for its own lights settings), or a crossing gate for its
 * settings.
 */
public final class CrossingLinkerHandler {

    public static final org.mtr.mapping.holder.Identifier SESSION_PACKET_ID = new org.mtr.mapping.holder.Identifier(Jme.MOD_ID, "crossing_link_session");
    private static final int SESSION_CHECK_TICKS = 10;

    /**
     * Which crossing screen to open, see {@link ScreenOpener}.
     */
    public static final int SCREEN_CONTROLLER = 0;
    public static final int SCREEN_LIGHTS = 1;
    public static final int SCREEN_GATE = 2;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int sessionCheckCounter;
    /**
     * Set by the client, which opens the crossing screens; never set on a dedicated server.
     */
    @Nullable
    private static ScreenOpener screenOpener;

    private CrossingLinkerHandler() {
    }

    public static void setScreenOpener(ScreenOpener opener) {
        screenOpener = opener;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            final ItemStack heldItem = player.getStackInHand(hand);
            final BlockPos pos = hitResult.getBlockPos();

            // MTR's brush opens the crossing screens. Handled on both sides, so the brush's own action never runs here.
            if (heldItem.getItem() instanceof org.mtr.mod.item.ItemBrush) {
                final net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
                final int screen;
                if (blockEntity instanceof BlockCrossingGate.BlockEntity) {
                    screen = SCREEN_GATE;
                } else if (blockEntity instanceof CrossingControl.Holder) {
                    screen = player.isSneaking() && blockEntity instanceof BlockCrossingLights.BlockEntity ? SCREEN_LIGHTS : SCREEN_CONTROLLER;
                } else {
                    return ActionResult.PASS;
                }
                if (world.isClient() && screenOpener != null) {
                    screenOpener.open(pos, screen);
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
            final boolean relevant = blockEntity instanceof CrossingControl.Holder || blockEntity instanceof CrossingObject || blockEntity instanceof BlockAxleCounter.BlockEntity || blockEntity instanceof org.mtr.mod.block.BlockTrainSensorBase.BlockEntityBase;
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
            // MTR's brush makes the counter directional: it then only detects trains running one way.
            if (heldItem.getItem() instanceof org.mtr.mod.item.ItemBrush) {
                if (!world.isClient()) {
                    final String setting = ((AxleCounterEntity) entity).cycleDetectionDirection();
                    player.sendMessage(TextHelper.literal("§aAxle counter now detects " + setting + ".").data, true);
                }
                return ActionResult.success(world.isClient());
            }
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
        player.sendMessage(TextHelper.literal("§7Crossing Linker: §f" + mode.displayName + (mode == CrossingLinkMode.INACTIVE ? "" : mode.linksCounters() ? "§7 - right-click a crossing controller or an axle counter first" : "§7 - right-click a crossing controller first")).data, true);
        final Session session = SESSIONS.get(player.getUuid());
        if (mode == CrossingLinkMode.INACTIVE || session != null && session.isCounter() && !mode.linksCounters()) {
            // A selected counter can only be linked in the node modes.
            clearSession(player);
        } else {
            // The outline colour follows the mode; resend so the client redraws it.
            sendSession(player, session);
        }
    }

    public static void onUseInAir(ServerPlayerEntity player, ItemStack stack) {
        final CrossingLinkMode mode = CrossingLinkMode.get(stack);
        final Session session = SESSIONS.get(player.getUuid());
        if (mode == CrossingLinkMode.INACTIVE) {
            player.sendMessage(TextHelper.literal("§7Crossing Linker is inactive. Sneak + right-click to choose a mode.").data, true);
        } else if (session == null) {
            player.sendMessage(TextHelper.literal(mode.linksCounters() ? "§eRight-click a crossing controller (level crossing) or an axle counter first." : "§eRight-click a crossing controller (level crossing) first.").data, true);
        } else if (session.isCounter()) {
            player.sendMessage(TextHelper.literal("§eRight-click the crossing controller (level crossing) to link the axle counter to.").data, true);
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

        if (session != null && pos.equals(session.controllerPos)) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§eSelection cancelled.").data, true);
            return;
        }

        // A counter selected first is linked to the controller clicked now.
        if (session != null && session.isCounter() && mode.linksCounters() && blockEntity instanceof CrossingControl.Holder) {
            final AxleCounterEntity counter = findCounter(world, session.counterId);
            linkCounter(player, pos, ((CrossingControl.Holder) blockEntity).getCrossingControl(), mode, session.counterId, session.counterX, session.counterY, session.counterZ, session.railId, session.railProgress, counter);
            return;
        }

        if (mode == CrossingLinkMode.OBJECT && session != null && !session.isCounter() && blockEntity instanceof CrossingObject) {
            linkObject(player, world, session, pos);
            return;
        }

        if (mode == CrossingLinkMode.OBJECT && session != null && !session.isCounter() && blockEntity instanceof org.mtr.mod.block.BlockTrainSensorBase.BlockEntityBase) {
            linkDetector(player, world, session, pos);
            return;
        }

        if (blockEntity instanceof CrossingControl.Holder) {
            setSession(player, Session.controller(pos.toImmutable(), world.getRegistryKey().getValue()));
            player.sendMessage(TextHelper.literal("§aSelected crossing controller at " + pos.toShortString() + ". " + (mode == CrossingLinkMode.OBJECT ? "Right-click a crossing object (level crossing or gate) or a train detector to link it." : "Right-click an axle counter on the rail (any distance) to link it as a " + mode.displayName.toLowerCase() + ".")).data, true);
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
        if (session == null || session.isCounter()) {
            // No controller selected yet: select the counter, then link it by clicking the controller.
            if (session != null && counter.getUuid().equals(session.counterId)) {
                clearSession(player);
                player.sendMessage(TextHelper.literal("§eSelection cancelled.").data, true);
                return;
            }
            setSession(player, Session.counter(counter.getUuid(), counter.getX(), counter.getY(), counter.getZ(), counter.getRailId(), counter.getRailProgress(), world.getRegistryKey().getValue()));
            player.sendMessage(TextHelper.literal("§aSelected the axle counter. Right-click a crossing controller (any distance) to link it as a " + mode.displayName.toLowerCase() + ".").data, true);
            return;
        }
        // The controller may be far away (counters link at any distance): load its chunk for this one action.
        final CrossingControl control = CrossingLinks.loadControl(world, session.controllerPos);
        if (control == null) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§cThe selected crossing controller is gone. Select it again.").data, true);
            return;
        }
        linkCounter(player, session.controllerPos, control, mode, counter.getUuid(), counter.getX(), counter.getY(), counter.getZ(), counter.getRailId(), counter.getRailProgress(), counter);
    }

    private static void linkCounter(ServerPlayerEntity player, BlockPos controllerPos, CrossingControl control, CrossingLinkMode mode, UUID counterId, double x, double y, double z, String railId, double railProgress, @Nullable AxleCounterEntity counter) {
        final boolean startRole = mode == CrossingLinkMode.START_NODE;
        final CrossingLinkRules.CounterLinkResult result = CrossingLinks.linkCounter(controllerPos, control, startRole, counterId, x, y, z, railId, railProgress, counter);
        final String role = startRole ? "§2start node" : "§4end node";
        final int count = (startRole ? control.getStarts() : control.getEnds()).size();
        switch (result) {
            case LINKED:
            case REPLACED:
                player.sendMessage(TextHelper.literal("§aLinked the axle counter as a " + role + "§a (" + count + " " + (startRole ? "start" : "end") + " node" + (count == 1 ? "" : "s") + ").").data, true);
                break;
            case UNLINKED:
                player.sendMessage(TextHelper.literal("§eUnlinked the " + role + "§e axle counter.").data, true);
                break;
            case REJECTED_OTHER_ROLE:
                player.sendMessage(TextHelper.literal("§cThis axle counter is already one of the controller's " + (startRole ? "end" : "start") + " nodes.").data, true);
                return;
            case REJECTED_FULL:
                player.sendMessage(TextHelper.literal("§cThis controller already has " + CrossingControl.MAX_COUNTERS_PER_ROLE + " " + (startRole ? "start" : "end") + " nodes.").data, true);
                return;
        }
        // The operation is complete: the outline and preview line are cleared.
        clearSession(player);
    }

    private static void linkObject(ServerPlayerEntity player, World world, Session session, BlockPos objectPos) {
        final CrossingControl control = CrossingLinks.loadControl(world, session.controllerPos);
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
     * Links a train detector (or one of MTR's powered train sensors) as an input: the crossing closes while it is powered.
     */
    private static void linkDetector(ServerPlayerEntity player, World world, Session session, BlockPos detectorPos) {
        final CrossingControl control = CrossingLinks.loadControl(world, session.controllerPos);
        if (control == null) {
            clearSession(player);
            player.sendMessage(TextHelper.literal("§cThe selected crossing controller is gone. Select it again.").data, true);
            return;
        }
        if (detectorPos.getSquaredDistance(session.controllerPos) > CrossingControl.MAX_LINK_DISTANCE * CrossingControl.MAX_LINK_DISTANCE) {
            player.sendMessage(TextHelper.literal("§cThis train detector is too far from the controller (max " + (int) CrossingControl.MAX_LINK_DISTANCE + " blocks).").data, true);
            return;
        }
        final int result = CrossingLinks.toggleDetector(control, detectorPos);
        final int count = control.getDetectors().size();
        if (result > 0) {
            player.sendMessage(TextHelper.literal("§9Linked the train detector at " + detectorPos.toShortString() + ": the crossing closes while it is powered (" + count + " linked).").data, true);
        } else if (result < 0) {
            player.sendMessage(TextHelper.literal("§eUnlinked the train detector at " + detectorPos.toShortString() + " (" + count + " linked).").data, true);
        } else {
            player.sendMessage(TextHelper.literal("§cThis controller already has " + CrossingControl.MAX_LINKED_DETECTORS + " linked train detectors.").data, true);
            return;
        }
        clearSession(player);
    }

    @Nullable
    private static AxleCounterEntity findCounter(World world, UUID id) {
        if (!(world instanceof net.minecraft.server.world.ServerWorld)) {
            return null;
        }
        final net.minecraft.entity.Entity entity = ((net.minecraft.server.world.ServerWorld) world).getEntity(id);
        return entity instanceof AxleCounterEntity && entity.isAlive() ? (AxleCounterEntity) entity : null;
    }

    /**
     * Cancels selections whose player no longer holds the linker, changed dimension, or whose controller is gone. There
     * is no distance limit, so a counter or controller can be selected and the player walk to the other end.
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
            final CrossingLinkMode mode = heldItem.getItem() instanceof CrossingLinkerItem ? CrossingLinkMode.get(heldItem) : CrossingLinkMode.INACTIVE;
            final boolean valid = mode != CrossingLinkMode.INACTIVE
                    && (!session.isCounter() || mode.linksCounters())
                    && world.getRegistryKey().getValue().equals(session.dimension)
                    && (session.isCounter() || !world.isChunkLoaded(session.controllerPos.getX() >> 4, session.controllerPos.getZ() >> 4) || CrossingLinks.getControl(world, session.controllerPos) != null);
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

    /**
     * Sends the selection: whether a controller is selected and its position, then whether a counter is selected and
     * its position.
     */
    private static void sendSession(PlayerEntity player, @Nullable Session session) {
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        final boolean controller = session != null && !session.isCounter();
        packetByteBuf.writeBoolean(controller);
        if (controller) {
            packetByteBuf.writeLong(session.controllerPos.asLong());
        }
        final boolean counter = session != null && session.isCounter();
        packetByteBuf.writeBoolean(counter);
        if (counter) {
            packetByteBuf.writeDouble(session.counterX);
            packetByteBuf.writeDouble(session.counterY);
            packetByteBuf.writeDouble(session.counterZ);
        }
        MagicNetworkingCompat.sendToPlayer((ServerPlayerEntity) player, SESSION_PACKET_ID, packetByteBuf);
    }

    /**
     * Opens a crossing screen on the client.
     */
    public interface ScreenOpener {
        /**
         * @param screen {@link #SCREEN_CONTROLLER}, {@link #SCREEN_LIGHTS} or {@link #SCREEN_GATE}
         */
        void open(BlockPos pos, int screen);
    }

    /**
     * What a player has selected with the linker: a crossing controller, or an axle counter to link to a controller
     * clicked next.
     */
    private static final class Session {

        @Nullable
        private final BlockPos controllerPos;
        @Nullable
        private final UUID counterId;
        private final double counterX, counterY, counterZ;
        private final String railId;
        private final double railProgress;
        /**
         * The dimension's id (a registry key's class moved between the supported versions; its id did not).
         */
        private final Identifier dimension;

        private Session(@Nullable BlockPos controllerPos, @Nullable UUID counterId, double counterX, double counterY, double counterZ, String railId, double railProgress, Identifier dimension) {
            this.controllerPos = controllerPos;
            this.counterId = counterId;
            this.counterX = counterX;
            this.counterY = counterY;
            this.counterZ = counterZ;
            this.railId = railId;
            this.railProgress = railProgress;
            this.dimension = dimension;
        }

        private static Session controller(BlockPos controllerPos, Identifier dimension) {
            return new Session(controllerPos, null, 0, 0, 0, "", 0, dimension);
        }

        private static Session counter(UUID counterId, double x, double y, double z, String railId, double railProgress, Identifier dimension) {
            return new Session(null, counterId, x, y, z, railId, railProgress, dimension);
        }

        private boolean isCounter() {
            return counterId != null;
        }
    }
}
