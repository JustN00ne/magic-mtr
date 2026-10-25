package org.justnoone.jme.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import org.justnoone.jme.Jme;
import org.justnoone.jme.compat.McCompat;

public final class ModEntities {

    public static EntityType<AxleCounterEntity> AXLE_COUNTER;

    private ModEntities() {
    }

    public static void register() {
        AXLE_COUNTER = McCompat.registerEntityType(new Identifier(Jme.MOD_ID, "axle_counter"), FabricEntityTypeBuilder.<AxleCounterEntity>create(SpawnGroup.MISC, AxleCounterEntity::new)
                .dimensions(EntityDimensions.fixed(0.45F, 0.25F))
                .trackRangeBlocks(96)
                .trackedUpdateRate(40)
                .disableSummon()
                .build());
    }
}
