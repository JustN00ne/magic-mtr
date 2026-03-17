package org.justnoone.jme.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.justnoone.jme.Jme;
import org.justnoone.jme.block.ModBlocks;
import org.justnoone.jme.item.ModItemGroups;
import org.justnoone.jme.item.ModItems;
import org.justnoone.jme.network.MagicRailNetworking;
import org.mtr.mapping.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JmeFabric implements ModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("MAGIC");

    @Override
    public void onInitialize() {
        jme$logStartupBanner();
        final Registry registry = new Registry();
        ModItems.registerModItems(registry);
        ModItemGroups.registerModItemGroups(registry);
        ModBlocks.registerModBlocks(registry);
        ModItemGroups.fillTabs();
        Jme.init(registry);
        registry.init();
        MagicRailNetworking.registerServer();
    }

    private static void jme$logStartupBanner() {
        final String version = FabricLoader.getInstance()
                .getModContainer(Jme.MOD_ID)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        final String banner = String.join("\n",
                ":            .......           ",
                ":        ###........###       ",
                ":     #####.............#     ",
                ":   #######...............##  ",
                ":  #########       .......... ",
                ":  #######    #..    ........ ",
                ":  ######    #####    ....... ",
                ":  ######  #######  ......... ",
                ":  @@#####    #####    ....... ",
                ":  @@@#####    ###    ####.#.. ",
                ":  @@@@@#####       ########## ",
                ":   @@@@@@#################### ",
                ":     @@@@@@###############    ",
                ":        @@@@@##########       ",
                ":            @@@@###           ",
                String.format(": %s - JustNoone - 2026 MIT", version)
        );
        LOGGER.info(banner);
    }
}
