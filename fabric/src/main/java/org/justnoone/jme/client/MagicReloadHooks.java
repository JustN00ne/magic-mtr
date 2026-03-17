package org.justnoone.jme.client;

import net.minecraft.client.MinecraftClient;
import org.justnoone.jme.client.data.SidingSpeedSliderFileStore;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;

public final class MagicReloadHooks {

    private MagicReloadHooks() {
    }

    public static void reloadResourcesAndState() {
        reloadState();
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.reloadResources();
        }
    }

    public static void reloadTiltState() {
        JmeConfig.reload();
        MagicRailTiltClient.clearSmoothingCache();
    }

    public static void reloadState() {
        JmeConfig.reload();
        AlternativePlatformRegistry.reloadFromDisk();
        DepotCancellationRegistry.reloadFromDisk();
        DashboardRouteFolderStore.reloadFromDisk();
        SidingSpeedSliderFileStore.reloadFromDisk();
        MagicRailTiltClient.clearSmoothingCache();
    }
}
