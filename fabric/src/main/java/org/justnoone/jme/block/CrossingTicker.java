package org.justnoone.jme.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.mtr.mapping.mapper.BlockEntityExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps crossing equipment (controllers, level crossings, gates) working in every loaded chunk. Minecraft only ticks
 * block entities within the simulation distance (1.18+), which is usually shorter than the render distance: a crossing
 * the player could see, but further away than that, stayed dark with its gates up while trains went through. Every
 * server tick, the equipment Minecraft did not tick itself is ticked here, as long as its chunk is loaded. (The axle
 * counters need no chunk at all: their controller checks their rail point in MTR's simulation.)
 */
public final class CrossingTicker {

    /**
     * The equipment seen so far, with the game time it was last ticked at. Weak, so unloaded equipment is forgotten.
     */
    private static final Map<Ticking, Long> LAST_TICKS = new WeakHashMap<>();

    private CrossingTicker() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tickMissed());
    }

    /**
     * Remembers equipment that was loaded, so it is ticked even if Minecraft never ticks it. Called when its data is
     * read (also on clients, where it is ignored).
     */
    static void track(Ticking ticking) {
        synchronized (LAST_TICKS) {
            if (!LAST_TICKS.containsKey(ticking)) {
                LAST_TICKS.put(ticking, Long.MIN_VALUE);
            }
        }
    }

    /**
     * Called from the equipment's own block entity tick.
     */
    static void onTick(Ticking ticking) {
        final org.mtr.mapping.holder.World world = ((BlockEntityExtension) ticking).getWorld2();
        if (world == null || world.isClient()) {
            return;
        }
        synchronized (LAST_TICKS) {
            LAST_TICKS.put(ticking, world.data.getTime());
        }
        ticking.crossingTick(world);
    }

    private static void tickMissed() {
        final List<Ticking> missed = new ArrayList<>();
        synchronized (LAST_TICKS) {
            LAST_TICKS.entrySet().removeIf(entry -> {
                final BlockEntityExtension blockEntity = (BlockEntityExtension) entry.getKey();
                if (blockEntity.isRemoved()) {
                    return true;
                }
                final org.mtr.mapping.holder.World world = blockEntity.getWorld2();
                if (world == null) {
                    return false;
                }
                if (world.isClient()) {
                    return true;
                }
                final net.minecraft.util.math.BlockPos pos = blockEntity.getPos2().data;
                if (!world.data.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || world.data.getBlockEntity(pos) != blockEntity) {
                    return true;
                }
                if (entry.getValue() != world.data.getTime()) {
                    missed.add(entry.getKey());
                }
                return false;
            });
        }
        // Ticked outside the lock: a tick can load other equipment, which tracks itself.
        for (final Ticking ticking : missed) {
            onTick(ticking);
        }
    }

    /**
     * A crossing block entity, ticked by Minecraft or, where Minecraft does not tick it, by {@link CrossingTicker}.
     */
    interface Ticking {
        /**
         * The block entity's server tick; runs once per game tick.
         */
        void crossingTick(org.mtr.mapping.holder.World world);
    }
}
