package org.justnoone.jme.block;

import org.justnoone.jme.Jme;
import org.justnoone.jme.item.ModItemGroups;

import org.mtr.mapping.holder.Block;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.BlockHelper;
import org.mtr.mapping.registry.BlockEntityTypeRegistryObject;
import org.mtr.mapping.registry.BlockRegistryObject;
import org.mtr.mapping.registry.Registry;
import org.mtr.mod.block.BlockPlatform;
import org.mtr.mod.block.BlockPlatformSlab;

public class ModBlocks {

    public static BlockRegistryObject TRAIN_DETECTOR;
    public static BlockEntityTypeRegistryObject<BlockTrainDetector.BlockEntity> TRAIN_DETECTOR_BLOCK_ENTITY;

    public static BlockRegistryObject WMTA_ON_BLOCK;
    public static BlockRegistryObject WMTA_ON_SLAB_BLOCK;
    public static BlockEntityTypeRegistryObject<WmtaBlockEntity> WMTA_ON_BLOCK_ENTITY;

    public static BlockRegistryObject AXLE_COUNTER_BLOCK;
    public static BlockEntityTypeRegistryObject<BlockAxleCounter.BlockEntity> AXLE_COUNTER_BLOCK_ENTITY;

    public static BlockRegistryObject CROSSING_CONTROLLER_BLOCK;
    public static BlockEntityTypeRegistryObject<BlockCrossingController.BlockEntity> CROSSING_CONTROLLER_BLOCK_ENTITY;

    public static BlockRegistryObject CROSSING_LIGHTS_BLOCK;
    public static BlockEntityTypeRegistryObject<BlockCrossingLights.BlockEntity> CROSSING_LIGHTS_BLOCK_ENTITY;

    public static void registerModBlocks(Registry registry) {
        registerPlatform(registry, "platform_german", false);
        registerPlatform(registry, "platform_polish", false);
        registerPlatform(registry, "platform_portuguese", false);
        registerPlatform(registry, "platform_blue_american", false);
        registerPlatform(registry, "platform_swedish", false);
        registerPlatform(registry, "platform_german_slab", true);
        registerPlatform(registry, "platform_polish_slab", true);
        registerPlatform(registry, "platform_portuguese_slab", true);
        registerPlatform(registry, "platform_blue_american_slab", true);
        registerPlatform(registry, "platform_swedish_slab", true);
        registerPlatform(registry, "platform_czech", false);
        registerPlatform(registry, "platform_czech_slab", true);
        registerPlatform(registry, "platform_dutch", false);
        registerPlatform(registry, "platform_dutch_slab", true);
        registerPlatform(registry, "platform_tactile_b1", false);
        registerPlatform(registry, "platform_tactile_b1_slab", true);
        registerPlatform(registry, "platform_tactile_b2", false);
        registerPlatform(registry, "platform_tactile_b2_slab", true);
        registerPlatform(registry, "platform_tactile_w1", false);
        registerPlatform(registry, "platform_tactile_w1_slab", true);
        registerPlatform(registry, "platform_tactile_w2", false);
        registerPlatform(registry, "platform_tactile_w2_slab", true);
        registerPlatform(registry, "platform_tactile_y1", false);
        registerPlatform(registry, "platform_tactile_y1_slab", true);
        registerPlatform(registry, "platform_tactile_y2", false);
        registerPlatform(registry, "platform_tactile_y2_slab", true);

        registerPlatform(registry, "platform_japanese_metro", false);
        registerPlatform(registry, "platform_japanese_metro_slab", true);
        registerPlatform(registry, "platform_metro_generic", false);
        registerPlatform(registry, "platform_metro_generic_slab", true);

        registerPlatform(registry, "platform_wmta", false);
        registerPlatform(registry, "platform_wmta_slab", true);
        registerPlatform(registry, "platform_wmta_off", false);
        registerPlatform(registry, "platform_wmta_off_slab", true);

        WMTA_ON_BLOCK = registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, "platform_wmta_on"),
                () -> new Block(new BlockWmtaOn(BlockHelper.createBlockSettings(true, true).nonOpaque())),
                ModItemGroups.JME_TAB
        );
        WMTA_ON_SLAB_BLOCK = registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, "platform_wmta_on_slab"),
                () -> new Block(new BlockWmtaSlabOn(BlockHelper.createBlockSettings(true, true).nonOpaque())),
                ModItemGroups.JME_TAB
        );
        WMTA_ON_BLOCK_ENTITY = registry.registerBlockEntityType(
                new Identifier(Jme.MOD_ID, "wmta_on"),
                WmtaBlockEntity::new,
                WMTA_ON_BLOCK::get,
                WMTA_ON_SLAB_BLOCK::get
        );

        TRAIN_DETECTOR = registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, "train_detector"),
                () -> new Block(new BlockTrainDetector()),
                ModItemGroups.JME_TAB
        );
        TRAIN_DETECTOR_BLOCK_ENTITY = registry.registerBlockEntityType(
                new Identifier(Jme.MOD_ID, "train_detector"),
                BlockTrainDetector.BlockEntity::new,
                TRAIN_DETECTOR::get
        );

        // Legacy: axle counters are attached to MTR rails now (AxleCounterEntity, placed with the "jme:axle_counter"
        // item). The block stays registered, without an item, so counters in existing worlds keep working.
        AXLE_COUNTER_BLOCK = registry.registerBlock(
                new Identifier(Jme.MOD_ID, "axle_counter"),
                () -> new Block(new BlockAxleCounter())
        );
        AXLE_COUNTER_BLOCK_ENTITY = registry.registerBlockEntityType(
                new Identifier(Jme.MOD_ID, "axle_counter"),
                BlockAxleCounter.BlockEntity::new,
                AXLE_COUNTER_BLOCK::get
        );

        // Legacy: every level crossing can take the axle counters itself now, so the controller is no longer in the
        // creative tab (it looked like a second level crossing). It stays registered, with its item, so controllers in
        // existing worlds and inventories keep working.
        CROSSING_CONTROLLER_BLOCK = registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, "crossing_controller"),
                () -> new Block(new BlockCrossingController())
        );
        CROSSING_CONTROLLER_BLOCK_ENTITY = registry.registerBlockEntityType(
                new Identifier(Jme.MOD_ID, "crossing_controller"),
                BlockCrossingController.BlockEntity::new,
                CROSSING_CONTROLLER_BLOCK::get
        );

        CROSSING_LIGHTS_BLOCK = registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, "crossing_lights"),
                () -> new Block(new BlockCrossingLights()),
                ModItemGroups.JME_TAB
        );
        CROSSING_LIGHTS_BLOCK_ENTITY = registry.registerBlockEntityType(
                new Identifier(Jme.MOD_ID, "crossing_lights"),
                BlockCrossingLights.BlockEntity::new,
                CROSSING_LIGHTS_BLOCK::get
        );
    }

    private static void registerPlatform(Registry registry, String id, boolean slab) {
        registry.registerBlockWithBlockItem(
                new Identifier(Jme.MOD_ID, id),
                slab
                        ? () -> new Block(new BlockPlatformSlab(BlockHelper.createBlockSettings(true, true).nonOpaque()))
                        : () -> new Block(new BlockPlatform(BlockHelper.createBlockSettings(true, true).nonOpaque(), false)),
                ModItemGroups.JME_TAB
        );
    }
}
