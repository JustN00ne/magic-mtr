package org.justnoone.jme.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Minecraft API calls that changed between the supported versions (this copy: 1.19 to 1.19.2).
 */
public final class McCompat {

    private McCompat() {
    }

    public static SoundEvent createSoundEvent(Identifier id) {
        return new SoundEvent(id);
    }

    public static void registerSoundEvent(Identifier id, SoundEvent sound) {
        Registry.register(Registry.SOUND_EVENT, id, sound);
    }

    public static <T extends Entity> EntityType<T> registerEntityType(Identifier id, EntityType<T> type) {
        return Registry.register(Registry.ENTITY_TYPE, id, type);
    }

    /**
     * Whether entities at this position are loaded and ticking.
     */
    public static boolean shouldTickEntity(ServerWorld world, BlockPos pos) {
        return world.shouldTickEntity(pos);
    }

    public static NbtCompound getNbt(ItemStack stack) {
        return stack.getNbt();
    }

    public static NbtCompound getOrCreateNbt(ItemStack stack) {
        return stack.getOrCreateNbt();
    }
}