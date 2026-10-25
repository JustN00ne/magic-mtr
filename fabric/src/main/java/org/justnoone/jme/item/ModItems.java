package org.justnoone.jme.item;

import org.justnoone.jme.Jme;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Item;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.registry.ItemRegistryObject;
import org.mtr.mapping.registry.Registry;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;

public class ModItems {

    public static final Item MAGIC_ICON = new Item(new ItemSettings().maxCount(1));
    public static final Item MAGIC_RAIL_CONNECTOR = new Item(new ItemRailModifier(true, true, true, false, RailType.DIAMOND, new ItemSettings().maxCount(1)));
    public static final Item MAGIC_RAIL_CONNECTOR_ONE_WAY = new Item(new ItemRailModifier(true, true, true, true, RailType.DIAMOND, new ItemSettings().maxCount(1)));
    public static final Item CROSSING_LINKER = new Item(new CrossingLinkerItem());
    /**
     * Places an axle counter on an MTR rail. Uses the id of the old axle counter block's item, so counters in
     * inventories become this item.
     */
    public static final Item AXLE_COUNTER = new Item(new AxleCounterItem());

    // Kept so the client can register the "mtr:selected" model predicate that switches the icons to their selected models.
    public static ItemRegistryObject MAGIC_RAIL_CONNECTOR_ENTRY;
    public static ItemRegistryObject MAGIC_RAIL_CONNECTOR_ONE_WAY_ENTRY;
    // For the "jme:link_mode" model predicate that shows the linker's mode icon.
    public static ItemRegistryObject CROSSING_LINKER_ENTRY;

    public static void registerModItems(Registry registry) {
        registry.registerItem(new Identifier(Jme.MOD_ID, "magic_icon"), settings -> MAGIC_ICON);
        MAGIC_RAIL_CONNECTOR_ENTRY = registry.registerItem(new Identifier(Jme.MOD_ID, "magic_rail_connector"), settings -> MAGIC_RAIL_CONNECTOR, ModItemGroups.JME_TAB);
        MAGIC_RAIL_CONNECTOR_ONE_WAY_ENTRY = registry.registerItem(new Identifier(Jme.MOD_ID, "magic_rail_connector_one_way"), settings -> MAGIC_RAIL_CONNECTOR_ONE_WAY, ModItemGroups.JME_TAB);
        CROSSING_LINKER_ENTRY = registry.registerItem(new Identifier(Jme.MOD_ID, "crossing_linker"), settings -> CROSSING_LINKER, ModItemGroups.JME_TAB);
        registry.registerItem(new Identifier(Jme.MOD_ID, "axle_counter"), settings -> AXLE_COUNTER, ModItemGroups.JME_TAB);
    }
}
