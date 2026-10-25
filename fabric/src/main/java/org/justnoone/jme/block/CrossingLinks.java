package org.justnoone.jme.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.util.CrossingLinkRules;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side entry points for changing crossing connections from outside the block package (the Crossing Linker and
 * the axle counters).
 */
public final class CrossingLinks {

    private CrossingLinks() {
    }

    @Nullable
    public static CrossingControl getControl(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return null;
        }
        final net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof CrossingControl.Holder ? ((CrossingControl.Holder) blockEntity).getCrossingControl() : null;
    }

    public static CrossingLinkRules.CounterLinkResult linkCounter(World world, BlockPos controllerPos, CrossingControl control, boolean startRole, AxleCounterEntity counter) {
        return control.linkCounter(new org.mtr.mapping.holder.World(world), new org.mtr.mapping.holder.BlockPos(controllerPos), startRole, counter);
    }

    /**
     * @return {@code 1} if linked, {@code -1} if unlinked, {@code 0} if the maximum is already linked
     */
    public static int toggleObject(World world, CrossingControl control, BlockPos objectPos) {
        return control.toggleObject(new org.mtr.mapping.holder.World(world), new org.mtr.mapping.holder.BlockPos(objectPos));
    }

    public static int getLinkedObjectsCount(CrossingControl control) {
        return control.getLinkedObjectsCount();
    }

    /**
     * Unlinks a removed axle counter from the (loaded) controllers it was linked to. Controllers in unloaded chunks
     * notice it themselves once loaded.
     */
    public static void onCounterRemoved(World world, UUID counterId, Set<Long> controllers) {
        for (final long packed : controllers) {
            final CrossingControl control = getControl(world, BlockPos.fromLong(packed));
            if (control != null) {
                control.onCounterRemoved(counterId);
            }
        }
    }
}
