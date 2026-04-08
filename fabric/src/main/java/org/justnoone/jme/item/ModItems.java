package org.justnoone.jme.item;

import org.justnoone.jme.Jme;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Item;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.registry.Registry;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;

public class ModItems {

    public static final Item MAGIC_ICON = new Item(new ItemSettings().maxCount(1));
    public static final Item MAGIC_RAIL_CONNECTOR = new Item(new ItemRailModifier(true, true, true, false, RailType.DIAMOND, new ItemSettings().maxCount(1)));
    public static final Item MAGIC_RAIL_CONNECTOR_ONE_WAY = new Item(new ItemRailModifier(true, true, true, true, RailType.DIAMOND, new ItemSettings().maxCount(1)));

    public static void registerModItems(Registry registry) {
        registry.registerItem(new Identifier(Jme.MOD_ID, "magic_icon"), settings -> MAGIC_ICON);
        registry.registerItem(new Identifier(Jme.MOD_ID, "magic_rail_connector"), settings -> MAGIC_RAIL_CONNECTOR, ModItemGroups.JME_TAB);
        registry.registerItem(new Identifier(Jme.MOD_ID, "magic_rail_connector_one_way"), settings -> MAGIC_RAIL_CONNECTOR_ONE_WAY, ModItemGroups.JME_TAB);
    }
}
