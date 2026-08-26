package org.justnoone.jme.item;

import org.justnoone.jme.Jme;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.registry.CreativeModeTabHolder;
import org.mtr.mapping.registry.Registry;

public final class ModItemGroups {

    public static CreativeModeTabHolder JME_TAB;

    public static void registerModItemGroups(Registry registry) {
        if (JME_TAB == null) {
            JME_TAB = registry.createCreativeModeTabHolder(
                    new Identifier(Jme.MOD_ID, "jme_tab"),
                    () -> ModItems.MAGIC_ICON.getDefaultStack()
            );
        }
    }

    public static void fillTabs() {
        // Items are added via registry.registerItem/registerBlockWithBlockItem.
    }
}
