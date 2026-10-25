package org.justnoone.jme.block;

import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.util.CrossingLinkRules;
import org.justnoone.jme.util.CrossingStateMachine;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The control side of a level crossing (the crossing controller): its start and end axle counters, the
 * {@link CrossingStateMachine} that decides when the crossing is active, and the crossing equipment
 * ({@link CrossingObject}, eg other level crossings) it switches. Held by every level crossing (any of them can be the
 * controller) and by the legacy crossing controller. Saved in the block entity, which also syncs it to clients (for the
 * MTR brush view of the connections).
 */
public final class CrossingControl {

    /**
     * Linked objects are re-synchronised, and links checked, this often, so objects in a chunk that was unloaded when
     * the crossing changed state catch up once loaded.
     */
    private static final int RESYNC_TICKS = 20;
    /**
     * A counter whose chunk is loaded with entities ticking, but which cannot be found this many checks in a row, has
     * been removed (eg killed with a command) and is unlinked.
     */
    private static final int MISSING_COUNTER_CHECKS = 3;
    private static final int MAX_LINKED_OBJECTS = 16;
    /**
     * Counters and objects further than this from the controller cannot be linked.
     */
    public static final double MAX_LINK_DISTANCE = 256;

    private final Runnable markDirty;
    @Nullable
    private CounterLink start;
    @Nullable
    private CounterLink end;
    private final List<BlockPos> objects = new ArrayList<>();
    private final CrossingStateMachine stateMachine = new CrossingStateMachine();
    private int resyncCounter = RESYNC_TICKS;

    CrossingControl(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void read(CompoundTag compoundTag) {
        start = CounterLink.read(compoundTag, "jme_crossing_start", "activator");
        end = CounterLink.read(compoundTag, "jme_crossing_end", "deactivator");

        objects.clear();
        final long[] objectsList = compoundTag.getLongArray("jme_crossing_lights_list");
        for (final long packed : objectsList) {
            objects.add(BlockPos.fromLong(packed));
        }
        // Controllers saved before multiple lights were supported store a single position.
        if (objects.isEmpty() && compoundTag.getBoolean("jme_crossing_has_lights")) {
            objects.add(new BlockPos(compoundTag.getInt("jme_crossing_lights_x"), compoundTag.getInt("jme_crossing_lights_y"), compoundTag.getInt("jme_crossing_lights_z")));
        }

        stateMachine.restore(
                compoundTag.getBoolean("jme_crossing_active"),
                compoundTag.getInt("jme_crossing_entry_side"),
                compoundTag.getInt("jme_crossing_trains_inside"),
                compoundTag.getBoolean("jme_crossing_last_activator"),
                compoundTag.getBoolean("jme_crossing_last_deactivator"),
                compoundTag.getLong("jme_crossing_idle_ticks")
        );
    }

    void write(CompoundTag compoundTag) {
        CounterLink.write(compoundTag, start, "jme_crossing_start", "activator");
        CounterLink.write(compoundTag, end, "jme_crossing_end", "deactivator");

        final long[] objectsList = new long[objects.size()];
        for (int i = 0; i < objectsList.length; i++) {
            objectsList[i] = objects.get(i).asLong();
        }
        compoundTag.putLongArray("jme_crossing_lights_list", objectsList);
        // Keep the first object in the old keys too, so the world still loads correctly in older MAGIC versions.
        compoundTag.putBoolean("jme_crossing_has_lights", !objects.isEmpty());
        if (!objects.isEmpty()) {
            compoundTag.putInt("jme_crossing_lights_x", objects.get(0).getX());
            compoundTag.putInt("jme_crossing_lights_y", objects.get(0).getY());
            compoundTag.putInt("jme_crossing_lights_z", objects.get(0).getZ());
        }

        compoundTag.putBoolean("jme_crossing_active", stateMachine.isActive());
        compoundTag.putInt("jme_crossing_entry_side", stateMachine.getEntrySide());
        compoundTag.putInt("jme_crossing_trains_inside", stateMachine.getTrainsInside());
        compoundTag.putBoolean("jme_crossing_last_activator", stateMachine.getLastActivator());
        compoundTag.putBoolean("jme_crossing_last_deactivator", stateMachine.getLastDeactivator());
        compoundTag.putLong("jme_crossing_idle_ticks", stateMachine.getTicksWithoutDetection());
    }

    /**
     * Called every server tick: updates the crossing from its axle counters and switches the linked objects.
     *
     * @return whether the crossing is active (a train is in the section between the counters)
     */
    boolean tick(World world) {
        final boolean resync = ++resyncCounter >= RESYNC_TICKS;
        if (resync) {
            resyncCounter = 0;
            boolean changed = false;
            if (start != null && start.checkStale(world)) {
                start = null;
                changed = true;
            }
            if (end != null && end.checkStale(world)) {
                end = null;
                changed = true;
            }
            changed |= removeInvalidObjects(world);
            if (changed) {
                markDirty.run();
            }
        }

        // The axle counters do the (cached, throttled) train detection; this only reads their latest result.
        final boolean startDetected = start != null && start.isDetecting(world);
        final boolean endDetected = end != null && end.isDetecting(world);

        final boolean wasActive = stateMachine.isActive();
        final int previousTrainsInside = stateMachine.getTrainsInside();
        final boolean active = stateMachine.update(start != null, startDetected, end != null, endDetected, 1, CrossingStateMachine.DEFAULT_TIMEOUT_TICKS);

        if (active != wasActive) {
            applyToObjects(world, active);
            markDirty.run();
        } else if (previousTrainsInside != stateMachine.getTrainsInside()) {
            markDirty.run();
        } else if (resync) {
            applyToObjects(world, active);
        }
        return active;
    }

    /**
     * Links a rail-attached axle counter as the start or end node, replacing the counter that held the role, or unlinks
     * it if it already held it.
     */
    CrossingLinkRules.CounterLinkResult linkCounter(World world, BlockPos controllerPos, boolean startRole, AxleCounterEntity counter) {
        final UUID id = counter.getUuid();
        final CounterLink current = startRole ? start : end;
        final CounterLink other = startRole ? end : start;
        final CrossingLinkRules.CounterLinkResult result = CrossingLinkRules.linkCounter(id, current == null ? null : current.entityId, other == null ? null : other.entityId);
        switch (result) {
            case UNLINKED:
                setRole(startRole, null);
                counter.removeController(controllerPos.data);
                break;
            case LINKED:
            case REPLACED:
                if (current != null) {
                    current.removeBackReference(world, controllerPos);
                }
                setRole(startRole, new CounterLink(id, null, counter.getX(), counter.getY(), counter.getZ()));
                counter.addController(controllerPos.data);
                break;
            default:
                return result;
        }
        markDirty.run();
        return result;
    }

    /**
     * Called when a linked axle counter is removed (broken, or its rail removed).
     */
    void onCounterRemoved(UUID id) {
        boolean changed = false;
        if (start != null && id.equals(start.entityId)) {
            start = null;
            changed = true;
        }
        if (end != null && id.equals(end.entityId)) {
            end = null;
            changed = true;
        }
        if (changed) {
            markDirty.run();
        }
    }

    /**
     * Removes the back references from the linked counters, when the controller is broken.
     */
    void onControllerRemoved(World world, BlockPos controllerPos) {
        if (start != null) {
            start.removeBackReference(world, controllerPos);
        }
        if (end != null) {
            end.removeBackReference(world, controllerPos);
        }
        for (final BlockPos objectPos : objects) {
            final CrossingObject object = getObject(world, objectPos);
            if (object != null) {
                object.onCrossingUnlinked();
            }
        }
    }

    /**
     * Links a crossing object so it switches with this controller, or unlinks it if it already was.
     *
     * @return {@code 1} if linked, {@code -1} if unlinked, {@code 0} if the maximum is already linked
     */
    int toggleObject(World world, BlockPos pos) {
        for (int i = 0; i < objects.size(); i++) {
            if (samePos(objects.get(i), pos)) {
                objects.remove(i);
                final CrossingObject object = world == null || world.isClient() ? null : getObject(world, pos);
                if (object != null) {
                    object.onCrossingUnlinked();
                }
                markDirty.run();
                return -1;
            }
        }
        if (objects.size() >= MAX_LINKED_OBJECTS) {
            return 0;
        }
        objects.add(pos);
        resyncCounter = RESYNC_TICKS;
        markDirty.run();
        return 1;
    }

    @Nullable
    public CounterLink getStart() {
        return start;
    }

    @Nullable
    public CounterLink getEnd() {
        return end;
    }

    public List<BlockPos> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    int getLinkedObjectsCount() {
        return objects.size();
    }

    boolean isActive() {
        return stateMachine.isActive();
    }

    private void setRole(boolean startRole, @Nullable CounterLink link) {
        if (startRole) {
            start = link;
        } else {
            end = link;
        }
    }

    private void applyToObjects(World world, boolean active) {
        for (final BlockPos objectPos : objects) {
            final CrossingObject object = getObject(world, objectPos);
            if (object != null) {
                object.setCrossingActive(active);
            }
        }
    }

    /**
     * Unlinks objects whose block (in a loaded chunk) is no longer crossing equipment, eg after it was broken.
     */
    private boolean removeInvalidObjects(World world) {
        return objects.removeIf(objectPos -> isLoaded(world, objectPos) && getObject(world, objectPos) == null);
    }

    private static boolean samePos(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private static boolean isLoaded(World world, BlockPos pos) {
        // Never load chunks from a block entity tick just to look at a linked block.
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Nullable
    private static CrossingObject getObject(World world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        final org.mtr.mapping.holder.BlockEntity wrappedBE = world.getBlockEntity(pos);
        return wrappedBE != null && wrappedBE.data instanceof CrossingObject ? (CrossingObject) wrappedBE.data : null;
    }

    /**
     * An axle counter linked as a start or end node: a counter attached to an MTR rail ({@link AxleCounterEntity},
     * by UUID), or a legacy axle counter block linked before counters were attached to rails. The position is the
     * counter's last known position, for drawing the connection on clients.
     */
    public static final class CounterLink {

        @Nullable
        private final UUID entityId;
        @Nullable
        private final BlockPos legacyPos;
        private double x, y, z;
        private int missingChecks;

        CounterLink(@Nullable UUID entityId, @Nullable BlockPos legacyPos, double x, double y, double z) {
            this.entityId = entityId;
            this.legacyPos = legacyPos;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        @Nullable
        public UUID getEntityId() {
            return entityId;
        }

        public boolean isLegacyBlock() {
            return legacyPos != null;
        }

        private boolean isDetecting(World world) {
            if (entityId != null) {
                final AxleCounterEntity counter = findCounter(world);
                return counter != null && counter.isDetecting();
            }
            if (legacyPos == null || !isLoaded(world, legacyPos)) {
                return false;
            }
            final org.mtr.mapping.holder.BlockEntity wrappedBE = world.getBlockEntity(legacyPos);
            return wrappedBE != null && wrappedBE.data instanceof BlockAxleCounter.BlockEntity && ((BlockAxleCounter.BlockEntity) wrappedBE.data).isDetecting();
        }

        /**
         * Refreshes the drawn position of a rail-attached counter, and reports it as stale when it is gone.
         */
        private boolean checkStale(World world) {
            if (entityId == null) {
                return false;
            }
            final AxleCounterEntity counter = findCounter(world);
            if (counter != null) {
                missingChecks = 0;
                x = counter.getX();
                y = counter.getY();
                z = counter.getZ();
                return false;
            }
            // Only conclude it is gone where its entities are loaded; in unloaded chunks it may simply not be loaded.
            if (world.data instanceof net.minecraft.server.world.ServerWorld && org.justnoone.jme.compat.McCompat.shouldTickEntity((net.minecraft.server.world.ServerWorld) world.data, new net.minecraft.util.math.BlockPos(net.minecraft.util.math.MathHelper.floor(x), net.minecraft.util.math.MathHelper.floor(y), net.minecraft.util.math.MathHelper.floor(z)))) {
                return ++missingChecks >= MISSING_COUNTER_CHECKS;
            }
            missingChecks = 0;
            return false;
        }

        private void removeBackReference(World world, BlockPos controllerPos) {
            final AxleCounterEntity counter = findCounter(world);
            if (counter != null) {
                counter.removeController(controllerPos.data);
            }
        }

        @Nullable
        private AxleCounterEntity findCounter(World world) {
            if (entityId == null || !(world.data instanceof net.minecraft.server.world.ServerWorld)) {
                return null;
            }
            final net.minecraft.entity.Entity entity = ((net.minecraft.server.world.ServerWorld) world.data).getEntity(entityId);
            return entity instanceof AxleCounterEntity && entity.isAlive() ? (AxleCounterEntity) entity : null;
        }

        @Nullable
        private static CounterLink read(CompoundTag compoundTag, String key, String legacyKey) {
            final net.minecraft.nbt.NbtCompound nbt = compoundTag.data;
            if (nbt.containsUuid(key + "_uuid")) {
                return new CounterLink(nbt.getUuid(key + "_uuid"), null, nbt.getDouble(key + "_x"), nbt.getDouble(key + "_y"), nbt.getDouble(key + "_z"));
            }
            if (compoundTag.getBoolean("jme_crossing_has_" + legacyKey)) {
                final BlockPos pos = new BlockPos(compoundTag.getInt("jme_crossing_" + legacyKey + "_x"), compoundTag.getInt("jme_crossing_" + legacyKey + "_y"), compoundTag.getInt("jme_crossing_" + legacyKey + "_z"));
                return new CounterLink(null, pos, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
            return null;
        }

        private static void write(CompoundTag compoundTag, @Nullable CounterLink link, String key, String legacyKey) {
            final net.minecraft.nbt.NbtCompound nbt = compoundTag.data;
            if (link != null && link.entityId != null) {
                nbt.putUuid(key + "_uuid", link.entityId);
                nbt.putDouble(key + "_x", link.x);
                nbt.putDouble(key + "_y", link.y);
                nbt.putDouble(key + "_z", link.z);
            }
            final boolean legacy = link != null && link.legacyPos != null;
            compoundTag.putBoolean("jme_crossing_has_" + legacyKey, legacy);
            if (legacy) {
                compoundTag.putInt("jme_crossing_" + legacyKey + "_x", link.legacyPos.getX());
                compoundTag.putInt("jme_crossing_" + legacyKey + "_y", link.legacyPos.getY());
                compoundTag.putInt("jme_crossing_" + legacyKey + "_z", link.legacyPos.getZ());
            }
        }
    }

    /**
     * A block entity that is a crossing controller: it holds a {@link CrossingControl}, so the Crossing Linker can link
     * axle counters and crossing objects to it.
     */
    public interface Holder {
        CrossingControl getCrossingControl();
    }
}
