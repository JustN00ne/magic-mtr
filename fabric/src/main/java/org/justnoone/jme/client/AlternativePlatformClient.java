package org.justnoone.jme.client;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.MagicRailConstants;

/**
 * Client-side edits of alternative platforms: applied locally for immediate feedback, then sent to the server, which
 * stores them and answers with its authoritative snapshot.
 */
public final class AlternativePlatformClient {

    private AlternativePlatformClient() {
    }

    public static boolean setAlternative(long routeId, long primaryPlatformId, long alternativePlatformId, boolean enabled) {
        if (!AlternativePlatformRegistry.isEnabled() || routeId == 0 || primaryPlatformId == 0 || alternativePlatformId == 0 || primaryPlatformId == alternativePlatformId) {
            return false;
        }

        final boolean changed = AlternativePlatformRegistry.setAlternative(routeId, primaryPlatformId, alternativePlatformId, enabled);
        if (changed) {
            final PacketByteBuf packet = PacketByteBufs.create();
            packet.writeLong(routeId);
            packet.writeLong(primaryPlatformId);
            packet.writeLong(alternativePlatformId);
            packet.writeBoolean(enabled);
            MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, packet);
        }
        return changed;
    }
}
