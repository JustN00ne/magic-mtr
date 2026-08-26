package org.justnoone.jme.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import org.justnoone.jme.block.ModBlocks;

public class JmeClient {
    public static void initClient() {
        MagicRailClientHooks.register();
        MagicClientCommands.register();
    }
}
