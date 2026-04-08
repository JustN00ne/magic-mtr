package org.justnoone.jme.block;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.mtr.mapping.registry.BlockRegistryObject;
import org.mtr.mapping.holder.BlockSettings;
import org.mtr.mod.block.BlockPlatform;
import org.mtr.mod.block.BlockPlatformSlab;

public class ModBlocks {

    public static final Block PLATFORM_GERMAN = registerBlock("platform_german",
            new BlockPlatform(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE)), false));
    public static final Block PLATFORM_POLISH = registerBlock("platform_polish",
            new BlockPlatform(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE)), false));
    public static final Block PLATFORM_BLUE_AMERICAN = registerBlock("platform_blue_american",
            new BlockPlatform(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE)), false));
    public static final Block PLATFORM_SWEDISH = registerBlock("platform_swedish",
            new BlockPlatform(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE)), false));
    public static final Block PLATFORM_GERMAN_SLAB = registerBlock("platform_german_slab",
            new BlockPlatformSlab(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE))));
    public static final Block PLATFORM_POLISH_SLAB = registerBlock("platform_polish_slab",
            new BlockPlatformSlab(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE))));
    public static final Block PLATFORM_BLUE_AMERICAN_SLAB = registerBlock("platform_blue_american_slab",
            new BlockPlatformSlab(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE))));
    public static final Block PLATFORM_SWEDISH_SLAB = registerBlock("platform_swedish_slab",
            new BlockPlatformSlab(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE))));
    public static final Block PLATFORM_CZECH = registerBlock("platform_czech",
            new BlockPlatform(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE)), false));
    public static final Block PLATFORM_CZECH_SLAB = registerBlock("platform_czech_slab",
            new BlockPlatformSlab(new BlockSettings(AbstractBlock.Settings.copy(Blocks.STONE))));

    // Use real MTR PIDS blocks so block entities and RenderPIDS arrival logic work.
    public static final Item PIDS_1 = registerExternalBlockItem("pids_1", getMtrBlock(org.mtr.mod.Blocks.PIDS_1));
    public static final Item PIDS_2 = registerExternalBlockItem("pids_2", getMtrBlock(org.mtr.mod.Blocks.PIDS_2));
    public static final Item PIDS_3 = registerExternalBlockItem("pids_3", getMtrBlock(org.mtr.mod.Blocks.PIDS_3));
    public static final Item PIDS_POLE = registerExternalBlockItem("pids_pole", getMtrBlock(org.mtr.mod.Blocks.PIDS_POLE));
    public static final Item PIDS_ODD = registerAliasedBlockItem("pids_odd", getMtrBlock(org.mtr.mod.Blocks.PIDS_1), "block.jme.pids_odd");

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier("jme", name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier("jme", name),
                new BlockItem(block, new FabricItemSettings()));
    }

    private static Item registerExternalBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier("jme", name), new BlockItem(block, new FabricItemSettings()));
    }

    private static Item registerAliasedBlockItem(String name, Block block, String translationKey) {
        return Registry.register(Registries.ITEM, new Identifier("jme", name), new BlockItem(block, new FabricItemSettings()) {
            @Override
            public String getTranslationKey() {
                return translationKey;
            }
        });
    }

    private static Block getMtrBlock(BlockRegistryObject blockRegistryObject) {
        return (Block) blockRegistryObject.get().data;
    }

    public static void registerModBlocks() {
        System.out.println("Registering Mod Blocks for jme");

        final RegistryKey<ItemGroup> mtrStationBlocksTab = RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("mtr", "station_building_blocks"));
        final RegistryKey<ItemGroup> mtrRailwayFacilitiesTab = RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("mtr", "railway_facilities"));

        ItemGroupEvents.modifyEntriesEvent(mtrStationBlocksTab).register(entries -> {
            entries.add(PLATFORM_GERMAN);
            entries.add(PLATFORM_POLISH);
            entries.add(PLATFORM_CZECH);
            entries.add(PLATFORM_BLUE_AMERICAN);
            entries.add(PLATFORM_SWEDISH);
            entries.add(PLATFORM_GERMAN_SLAB);
            entries.add(PLATFORM_POLISH_SLAB);
            entries.add(PLATFORM_CZECH_SLAB);
            entries.add(PLATFORM_BLUE_AMERICAN_SLAB);
            entries.add(PLATFORM_SWEDISH_SLAB);
        });

        ItemGroupEvents.modifyEntriesEvent(mtrRailwayFacilitiesTab).register(entries -> {
            entries.add(PIDS_1);
            entries.add(PIDS_2);
            entries.add(PIDS_3);
            entries.add(PIDS_POLE);
            entries.add(PIDS_ODD);
        });
    }
}
