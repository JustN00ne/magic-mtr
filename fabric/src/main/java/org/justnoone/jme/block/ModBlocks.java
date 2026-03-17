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

    public static void registerModBlocks(Registry registry) {
        registerPlatform(registry, "platform_german", false);
        registerPlatform(registry, "platform_polish", false);
        registerPlatform(registry, "platform_portuguese", false);
        registerPlatform(registry, "platform_russian", false); // Backward-compatibility for existing worlds
        registerPlatform(registry, "platform_blue_american", false);
        registerPlatform(registry, "platform_swedish", false);
        registerPlatform(registry, "platform_german_slab", true);
        registerPlatform(registry, "platform_polish_slab", true);
        registerPlatform(registry, "platform_portuguese_slab", true);
        registerPlatform(registry, "platform_russian_slab", true); // Backward-compatibility for existing worlds
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
