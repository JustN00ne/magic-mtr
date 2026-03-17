package org.justnoone.jme.item;

import org.justnoone.jme.Jme;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Item;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.registry.Registry;

public class ModItems {

    public static final Item MAGIC_ICON = new Item(new ItemSettings().maxCount(1));

    public static void registerModItems(Registry registry) {
        registry.registerItem(new Identifier(Jme.MOD_ID, "magic_icon"), settings -> MAGIC_ICON);
    }
}
