package org.justnoone.jme.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.CronTimetableRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.rail.MagicRailConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps clients in sync with the server's route types, alternative platforms, depot cancellation settings and cron
 * timetables.
 * <p>
 * Both are stored in server-side config files. Previously each client only read its own local copy, so on a
 * dedicated server a selection was shown only on the machine that made it (and was lost when reopening the GUI
 * from another client). The server now pushes a snapshot on join and after every change.
 */
public final class MagicRouteStateSync {

    private static final int MAX_ENTRIES = 1 << 16;

    private MagicRouteStateSync() {
    }

    public static void sendTo(ServerPlayerEntity player) {
        if (player != null) {
            MagicNetworkingCompat.sendToPlayer(player, MagicRailConstants.SYNC_ROUTE_STATE_PACKET_ID, writeSnapshot());
        }
    }

    public static void broadcast(Object server) {
        if (!(server instanceof MinecraftServer)) {
            return;
        }
        for (final ServerPlayerEntity player : ((MinecraftServer) server).getPlayerManager().getPlayerList()) {
            sendTo(player);
        }
    }

    /**
     * Reads the packet on the network thread and returns the work that applies it, to be run on the client thread.
     */
    public static Runnable readSnapshot(PacketByteBuf packetByteBuf) {
        final Map<String, String> routeTypes = new HashMap<>();
        final Map<String, Boolean> alternativePlatformsEnabled = new HashMap<>();
        final Map<String, List<Long>> alternatives = new HashMap<>();

        final int routeTypeCount = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
        for (int i = 0; i < routeTypeCount; i++) {
            routeTypes.put(packetByteBuf.readString(), packetByteBuf.readString());
        }

        final int enabledCount = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
        for (int i = 0; i < enabledCount; i++) {
            alternativePlatformsEnabled.put(packetByteBuf.readString(), packetByteBuf.readBoolean());
        }

        final int alternativeCount = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
        for (int i = 0; i < alternativeCount; i++) {
            final String key = packetByteBuf.readString();
            final int size = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
            final List<Long> platformIds = new ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                platformIds.add(packetByteBuf.readLong());
            }
            alternatives.put(key, platformIds);
        }

        final Map<Long, DepotCancellationRegistry.Settings> cancellations = new HashMap<>();
        final Map<Long, CronTimetableRegistry.Entry> cronTimetables = new HashMap<>();
        Boolean alternativePlatformsServerEnabled = null;
        if (packetByteBuf.readableBytes() > 0) {
            final int cancellationCount = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
            for (int i = 0; i < cancellationCount; i++) {
                final long depotId = packetByteBuf.readLong();
                final boolean enabled = packetByteBuf.readBoolean();
                final int thresholdMinutes = packetByteBuf.readVarInt();
                cancellations.put(depotId, new DepotCancellationRegistry.Settings(enabled, thresholdMinutes, DepotCancellationRegistry.Action.DESPAWN));
            }

            final int cronCount = Math.min(MAX_ENTRIES, packetByteBuf.readVarInt());
            for (int i = 0; i < cronCount; i++) {
                final long depotId = packetByteBuf.readLong();
                final String expression = packetByteBuf.readString();
                final String zoneId = packetByteBuf.readString();
                final boolean enabled = packetByteBuf.readBoolean();
                cronTimetables.put(depotId, new CronTimetableRegistry.Entry(expression, zoneId, enabled));
            }

            // Appended in 1.3.6; older servers do not send it.
            if (packetByteBuf.readableBytes() > 0) {
                alternativePlatformsServerEnabled = packetByteBuf.readBoolean();
            }
        }

        final Boolean finalAlternativePlatformsServerEnabled = alternativePlatformsServerEnabled;
        return () -> {
            RouteTypeOverrideConfig.applySyncedSnapshot(routeTypes, alternativePlatformsEnabled);
            AlternativePlatformRegistry.applySyncedSnapshot(alternatives);
            DepotCancellationRegistry.applySyncedSnapshot(cancellations);
            CronTimetableRegistry.applySyncedSnapshot(cronTimetables);
        };
    }

    private static PacketByteBuf writeSnapshot() {
        final PacketByteBuf packetByteBuf = PacketByteBufs.create();

        final Map<String, String> routeTypes = RouteTypeOverrideConfig.getRouteTypesSnapshot();
        packetByteBuf.writeVarInt(routeTypes.size());
        routeTypes.forEach((routeId, routeType) -> {
            packetByteBuf.writeString(routeId);
            packetByteBuf.writeString(routeType);
        });

        final Map<String, Boolean> alternativePlatformsEnabled = RouteTypeOverrideConfig.getAlternativePlatformsEnabledSnapshot();
        packetByteBuf.writeVarInt(alternativePlatformsEnabled.size());
        alternativePlatformsEnabled.forEach((routeId, enabled) -> {
            packetByteBuf.writeString(routeId);
            packetByteBuf.writeBoolean(enabled);
        });

        final Map<String, List<Long>> alternatives = AlternativePlatformRegistry.getSnapshot();
        packetByteBuf.writeVarInt(alternatives.size());
        alternatives.forEach((key, platformIds) -> {
            packetByteBuf.writeString(key);
            packetByteBuf.writeVarInt(platformIds.size());
            platformIds.forEach(packetByteBuf::writeLong);
        });

        final Map<Long, DepotCancellationRegistry.Settings> cancellations = DepotCancellationRegistry.getSnapshot();
        packetByteBuf.writeVarInt(cancellations.size());
        cancellations.forEach((depotId, settings) -> {
            packetByteBuf.writeLong(depotId);
            packetByteBuf.writeBoolean(settings.enabled);
            packetByteBuf.writeVarInt(settings.thresholdMinutes);
        });

        final Map<Long, CronTimetableRegistry.Entry> cronTimetables = CronTimetableRegistry.getSnapshot();
        packetByteBuf.writeVarInt(cronTimetables.size());
        cronTimetables.forEach((depotId, entry) -> {
            packetByteBuf.writeLong(depotId);
            packetByteBuf.writeString(entry.expression);
            packetByteBuf.writeString(entry.zoneId);
            packetByteBuf.writeBoolean(entry.enabled);
        });

        packetByteBuf.writeBoolean(AlternativePlatformRegistry.isEnabled());

        return packetByteBuf;
    }
}
