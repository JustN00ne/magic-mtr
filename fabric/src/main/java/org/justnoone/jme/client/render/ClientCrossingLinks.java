package org.justnoone.jme.client.render;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side crossing link state: the crossing controller selected with the Crossing Linker (sent by the server), and
 * the crossing controllers in render distance (registered by {@link RenderCrossing}, which draws every loaded controller
 * each frame), whose connections the MTR brush shows.
 */
public final class ClientCrossingLinks {

    @Nullable
    private static BlockPos selectedController;
    private static final Map<Long, BlockEntity> CONTROLLERS = new HashMap<>();

    private ClientCrossingLinks() {
    }

    @Nullable
    public static BlockPos getSelectedController() {
        return selectedController;
    }

    public static void setSelectedController(@Nullable BlockPos pos) {
        selectedController = pos;
    }

    static void onControllerRendered(BlockEntity blockEntity) {
        CONTROLLERS.put(blockEntity.getPos().asLong(), blockEntity);
    }

    /**
     * The registered controllers, dropping those that were removed or belong to another world.
     */
    static Iterable<BlockEntity> getControllers(net.minecraft.world.World world) {
        final Iterator<BlockEntity> iterator = CONTROLLERS.values().iterator();
        while (iterator.hasNext()) {
            final BlockEntity blockEntity = iterator.next();
            if (blockEntity.isRemoved() || blockEntity.getWorld() != world) {
                iterator.remove();
            }
        }
        return CONTROLLERS.values();
    }

    public static void clear() {
        selectedController = null;
        CONTROLLERS.clear();
    }
}
