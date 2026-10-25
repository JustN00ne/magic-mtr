package org.justnoone.jme.client;

import net.minecraft.client.MinecraftClient;
import org.justnoone.jme.client.data.SidingSpeedSliderFileStore;
import org.justnoone.jme.config.MagicConfigReloader;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.justnoone.jme.rail.MagicRailRotationRegistry;

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
        MagicRailTiltRegistry.reloadFromDisk();
        MagicRailRotationRegistry.reloadFromDisk();
        MagicRailTiltClient.clearSmoothingCache();
    }

    public static void reloadState() {
        MagicConfigReloader.reloadAllFromDisk();
        DashboardRouteFolderStore.reloadFromDisk();
        SidingSpeedSliderFileStore.reloadFromDisk();
        MagicRailTiltClient.clearSmoothingCache();
    }
}
