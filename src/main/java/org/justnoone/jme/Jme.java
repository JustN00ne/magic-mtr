package org.justnoone.jme;

import net.fabricmc.api.ModInitializer;
import org.justnoone.jme.block.ModBlocks;
import org.justnoone.jme.item.ModItems;

public class Jme implements ModInitializer {

    @Override
    public void onInitialize() {
        ModBlocks.registerModBlocks();
        ModItems.registerModItems();
    }
}
