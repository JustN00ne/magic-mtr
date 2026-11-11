package org.justnoone.jme.block;

import org.justnoone.jme.entity.AxleCounterEntity;
import org.justnoone.jme.entity.RailAttachment;
import org.justnoone.jme.util.CrossingLinkRules;
import org.justnoone.jme.util.CrossingStateMachine;
import org.justnoone.jme.util.CrossingTimeline;
import org.mtr.core.data.Rail;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The control side of a level crossing (the crossing controller): its start and end axle counters (several of each,
 * eg one per track, at any distance), the {@link CrossingStateMachine} that decides when the crossing is active, the
 * {@link CrossingTimeline} that switches the lights and then the gates, and the crossing equipment
 * ({@link CrossingObject}: other level crossings, crossing gates) it switches. Held by every level crossing (any of them
 * can be the controller) and by the legacy crossing controller. Saved in the block entity, which also syncs it to
 * clients (for the MTR brush view of the connections and the controller's screen).
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
    public static final int MAX_LINKED_DETECTORS = 16;
    /**
     * How many axle counters each of the start and end roles can hold.
     */
    public static final int MAX_COUNTERS_PER_ROLE = 16;
    /**
     * Objects further than this from the controller cannot be linked (they must be loaded to be switched). Axle counters
     * can be linked at any distance.
     */
    public static final double MAX_LINK_DISTANCE = 256;
    // Each linked counter is saved as its UUID (2 longs, both 0 for a legacy counter block), its position (3 doubles as
    // long bits) and whether it detected a train at the last tick.
    private static final int COUNTER_STRIDE = 6;
    private static final int DETECT_TICKS = 3;

    private final Runnable markDirty;
    private final List<CounterLink> starts = new ArrayList<>();
    private final List<CounterLink> ends = new ArrayList<>();
    private final List<BlockPos> objects = new ArrayList<>();
    /**
     * Train detectors (MAGIC's, or any of MTR's powered train sensors) linked in Object Linking mode: while any of them
     * is powered the crossing closes as if a train were in its section. A redstone signal wired into a controller only
     * flashes its own lamps (its output would feed back into it); a linked detector has no such loop, so it switches the
     * whole crossing: timeline, linked objects and redstone output.
     */
    private final List<BlockPos> detectors = new ArrayList<>();
    private boolean detectorActive;
    private final CrossingStateMachine stateMachine = new CrossingStateMachine();
    private final CrossingTimeline timeline = new CrossingTimeline();
    /**
     * Set from the controller's screen: the crossing stays closed (lights and gates) until set back to automatic.
     */
    private boolean manualClosed;
    private int resyncCounter = RESYNC_TICKS;

    CrossingControl(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void read(CompoundTag compoundTag) {
        readCounters(compoundTag, starts, "jme_crossing_starts", "jme_crossing_start", "activator");
        readCounters(compoundTag, ends, "jme_crossing_ends", "jme_crossing_end", "deactivator");

        objects.clear();
        final long[] objectsList = compoundTag.getLongArray("jme_crossing_lights_list");
        for (final long packed : objectsList) {
            objects.add(BlockPos.fromLong(packed));
        }
        // Controllers saved before multiple lights were supported store a single position.
        if (objects.isEmpty() && compoundTag.getBoolean("jme_crossing_has_lights")) {
            objects.add(new BlockPos(compoundTag.getInt("jme_crossing_lights_x"), compoundTag.getInt("jme_crossing_lights_y"), compoundTag.getInt("jme_crossing_lights_z")));
        }

        detectors.clear();
        for (final long packed : compoundTag.getLongArray("jme_crossing_detectors")) {
            detectors.add(BlockPos.fromLong(packed));
        }
        detectorActive = compoundTag.getBoolean("jme_crossing_detector_active");

        stateMachine.restore(
                compoundTag.getBoolean("jme_crossing_active"),
                compoundTag.getInt("jme_crossing_entry_side"),
                compoundTag.getInt("jme_crossing_trains_inside"),
                compoundTag.getBoolean("jme_crossing_last_activator"),
                compoundTag.getBoolean("jme_crossing_last_deactivator"),
                compoundTag.getLong("jme_crossing_idle_ticks")
        );

        manualClosed = compoundTag.getBoolean("jme_crossing_manual_closed");
        if (compoundTag.data.contains("jme_crossing_gates_lower_delay")) {
            timeline.setDelays(
                    compoundTag.getInt("jme_crossing_lights_on_delay"),
                    compoundTag.getInt("jme_crossing_gates_lower_delay"),
                    compoundTag.getInt("jme_crossing_gates_raise_delay"),
                    compoundTag.getInt("jme_crossing_lights_off_delay")
            );
            timeline.restore(
                    compoundTag.getBoolean("jme_crossing_timeline_active"),
                    compoundTag.getBoolean("jme_crossing_lights_on"),
                    compoundTag.getBoolean("jme_crossing_gates_down"),
                    compoundTag.getInt("jme_crossing_timeline_ticks")
            );
        } else {
            // Saved before the timeline existed: an active crossing already has its lights on.
            final boolean active = stateMachine.isActive();
            timeline.restore(active, active, false, 0);
        }
    }

    void write(CompoundTag compoundTag) {
        writeCounters(compoundTag, starts, "jme_crossing_starts", "jme_crossing_start", "activator");
        writeCounters(compoundTag, ends, "jme_crossing_ends", "jme_crossing_end", "deactivator");

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

        final long[] detectorsList = new long[detectors.size()];
        for (int i = 0; i < detectorsList.length; i++) {
            detectorsList[i] = detectors.get(i).asLong();
        }
        compoundTag.putLongArray("jme_crossing_detectors", detectorsList);
        compoundTag.putBoolean("jme_crossing_detector_active", detectorActive);

        compoundTag.putBoolean("jme_crossing_active", stateMachine.isActive());
        compoundTag.putInt("jme_crossing_entry_side", stateMachine.getEntrySide());
        compoundTag.putInt("jme_crossing_trains_inside", stateMachine.getTrainsInside());
        compoundTag.putBoolean("jme_crossing_last_activator", stateMachine.getLastActivator());
        compoundTag.putBoolean("jme_crossing_last_deactivator", stateMachine.getLastDeactivator());
        compoundTag.putLong("jme_crossing_idle_ticks", stateMachine.getTicksWithoutDetection());

        compoundTag.putBoolean("jme_crossing_manual_closed", manualClosed);
        compoundTag.putInt("jme_crossing_lights_on_delay", timeline.getLightsOnDelay());
        compoundTag.putInt("jme_crossing_gates_lower_delay", timeline.getGatesLowerDelay());
        compoundTag.putInt("jme_crossing_gates_raise_delay", timeline.getGatesRaiseDelay());
        compoundTag.putInt("jme_crossing_lights_off_delay", timeline.getLightsOffDelay());
        compoundTag.putBoolean("jme_crossing_timeline_active", timeline.isActive());
        compoundTag.putBoolean("jme_crossing_lights_on", timeline.isLightsOn());
        compoundTag.putBoolean("jme_crossing_gates_down", timeline.isGatesDown());
        compoundTag.putInt("jme_crossing_timeline_ticks", timeline.getTicksInPhase());
    }

    /**
     * Called every server tick: updates the crossing from its axle counters, runs the timeline and switches the linked
     * objects.
     *
     * @return whether the crossing is active (a train is in the section between the counters, or it is closed by hand)
     */
    boolean tick(World world) {
        final boolean resync = ++resyncCounter >= RESYNC_TICKS;
        if (resync) {
            resyncCounter = 0;
            boolean changed = starts.removeIf(link -> link.checkStale(world));
            changed |= ends.removeIf(link -> link.checkStale(world));
            changed |= removeInvalidObjects(world);
            changed |= detectors.removeIf(detectorPos -> isLoaded(world, detectorPos) && !isTrainSensor(world, detectorPos));
            if (changed) {
                markDirty.run();
            }
        }

        // The axle counters do the (cached, throttled) train detection; this only reads their latest result. Every
        // counter that starts or stops detecting is a train entering or leaving, so trains on different tracks count.
        final int[] startEdges = new int[2];
        final int[] endEdges = new int[2];
        final boolean startDetected = readCounters(world, starts, startEdges);
        final boolean endDetected = readCounters(world, ends, endEdges);

        final boolean wasActive = isActive();
        final int previousTrainsInside = stateMachine.getTrainsInside();
        stateMachine.update(!starts.isEmpty(), startDetected, startEdges[0], startEdges[1], !ends.isEmpty(), endDetected, endEdges[0], endEdges[1], 1, CrossingStateMachine.DEFAULT_TIMEOUT_TICKS);
        detectorActive = readDetectors(world);
        final boolean active = isActive();

        if (timeline.update(active)) {
            applyToObjects(world);
            markDirty.run();
        } else if (active != wasActive || previousTrainsInside != stateMachine.getTrainsInside()) {
            markDirty.run();
        } else if (resync) {
            applyToObjects(world);
        }
        return active;
    }

    /**
     * Reads the counters of one role, counting those that started ({@code edges[0]}) and stopped ({@code edges[1]})
     * detecting since the last tick.
     *
     * @return whether any of them detects a train
     */
    private static boolean readCounters(World world, List<CounterLink> links, int[] edges) {
        boolean any = false;
        for (final CounterLink link : links) {
            final boolean detecting = link.isDetecting(world);
            if (detecting && !link.lastDetecting) {
                edges[0]++;
            } else if (!detecting && link.lastDetecting) {
                edges[1]++;
            }
            link.lastDetecting = detecting;
            any |= detecting;
        }
        return any;
    }

    /**
     * @return whether any linked train detector is powered (only loaded ones are read)
     */
    private boolean readDetectors(World world) {
        for (final BlockPos detectorPos : detectors) {
            if (isLoaded(world, detectorPos)) {
                final net.minecraft.block.BlockState state = world.data.getBlockState(detectorPos.data);
                final net.minecraft.state.property.IntProperty powered = org.mtr.mod.block.BlockTrainPoweredSensorBase.POWERED.data;
                if (state.contains(powered) && state.get(powered) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the block is a train detector that can be linked: MAGIC's train detector or one of MTR's powered train
     * sensors.
     */
    static boolean isTrainSensor(World world, BlockPos pos) {
        final net.minecraft.block.BlockState state = world.data.getBlockState(pos.data);
        return state.contains(org.mtr.mod.block.BlockTrainPoweredSensorBase.POWERED.data) && world.data.getBlockEntity(pos.data) instanceof org.mtr.mod.block.BlockTrainSensorBase.BlockEntityBase;
    }

    /**
     * Links a train detector so the crossing closes while it is powered, or unlinks it if it already was.
     *
     * @return {@code 1} if linked, {@code -1} if unlinked, {@code 0} if the maximum is already linked
     */
    int toggleDetector(BlockPos pos) {
        for (int i = 0; i < detectors.size(); i++) {
            if (samePos(detectors.get(i), pos)) {
                detectors.remove(i);
                markDirty.run();
                return -1;
            }
        }
        if (detectors.size() >= MAX_LINKED_DETECTORS) {
            return 0;
        }
        detectors.add(pos);
        markDirty.run();
        return 1;
    }

    /**
     * Unlinks a train detector, from the controller's screen.
     */
    boolean unlinkDetector(int index) {
        if (index < 0 || index >= detectors.size()) {
            return false;
        }
        detectors.remove(index);
        markDirty.run();
        return true;
    }

    /**
     * Links an axle counter as a start or end node, or unlinks it if it already is one. A controller can have several
     * of each, and they can be any distance away.
     *
     * @param railId       the MTR rail the counter is on, so it detects trains even while its chunk is not loaded
     * @param railProgress where the counter is along the rail
     * @param counter      the counter entity, to store the back reference in, or {@code null} if it is not loaded (the
     *                     controller then notices by itself when it is removed)
     */
    CrossingLinkRules.CounterLinkResult linkCounter(BlockPos controllerPos, boolean startRole, UUID id, double x, double y, double z, String railId, double railProgress, @Nullable AxleCounterEntity counter) {
        final List<CounterLink> role = startRole ? starts : ends;
        final List<CounterLink> other = startRole ? ends : starts;
        final CrossingLinkRules.CounterLinkResult result = CrossingLinkRules.toggleCounter(id, ids(role), ids(other), MAX_COUNTERS_PER_ROLE);
        switch (result) {
            case UNLINKED:
                role.removeIf(link -> id.equals(link.entityId));
                if (counter != null) {
                    counter.removeController(controllerPos.data);
                }
                break;
            case LINKED:
                final CounterLink link = new CounterLink(id, null, x, y, z);
                link.railId = railId;
                link.railProgress = railProgress;
                link.direction = counter == null ? 0 : counter.getDetectionDirection();
                role.add(link);
                if (counter != null) {
                    counter.addController(controllerPos.data);
                }
                break;
            default:
                return result;
        }
        markDirty.run();
        return result;
    }

    /**
     * Unlinks a start or end node, from the controller's screen.
     *
     * @return whether a counter was unlinked
     */
    boolean unlinkCounter(World world, BlockPos controllerPos, boolean startRole, int index) {
        final List<CounterLink> role = startRole ? starts : ends;
        if (index < 0 || index >= role.size()) {
            return false;
        }
        role.remove(index).removeBackReference(world, controllerPos);
        markDirty.run();
        return true;
    }

    /**
     * Called when a linked axle counter is removed (broken, or its rail removed).
     */
    void onCounterRemoved(UUID id) {
        final boolean changed = starts.removeIf(link -> id.equals(link.entityId)) | ends.removeIf(link -> id.equals(link.entityId));
        if (changed) {
            markDirty.run();
        }
    }

    /**
     * Removes the back references from the linked counters, when the controller is broken.
     */
    void onControllerRemoved(World world, BlockPos controllerPos) {
        for (final CounterLink link : starts) {
            link.removeBackReference(world, controllerPos);
        }
        for (final CounterLink link : ends) {
            link.removeBackReference(world, controllerPos);
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

    /**
     * Applies the settings edited in the controller's screen.
     */
    void configure(boolean manualClosed, int lightsOnDelay, int gatesLowerDelay, int gatesRaiseDelay, int lightsOffDelay) {
        this.manualClosed = manualClosed;
        timeline.setDelays(lightsOnDelay, gatesLowerDelay, gatesRaiseDelay, lightsOffDelay);
        markDirty.run();
    }

    /**
     * The first start node, or {@code null}.
     */
    @Nullable
    public CounterLink getStart() {
        return starts.isEmpty() ? null : starts.get(0);
    }

    /**
     * The first end node, or {@code null}.
     */
    @Nullable
    public CounterLink getEnd() {
        return ends.isEmpty() ? null : ends.get(0);
    }

    public List<CounterLink> getStarts() {
        return Collections.unmodifiableList(starts);
    }

    public List<CounterLink> getEnds() {
        return Collections.unmodifiableList(ends);
    }

    public List<BlockPos> getObjects() {
        return Collections.unmodifiableList(objects);
    }

    public List<BlockPos> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    int getLinkedObjectsCount() {
        return objects.size();
    }

    /**
     * Whether a train is in the crossing's section, a linked train detector is powered, or the crossing is closed by
     * hand.
     */
    public boolean isActive() {
        return stateMachine.isActive() || manualClosed || detectorActive;
    }

    /**
     * Whether a linked train detector is powered.
     */
    public boolean isDetectorActive() {
        return detectorActive;
    }

    public boolean isTrainDetected() {
        return stateMachine.isActive();
    }

    public int getTrainsInside() {
        return stateMachine.getTrainsInside();
    }

    /**
     * Whether the lights are on, following the timeline.
     */
    public boolean isLightsOn() {
        return timeline.isLightsOn();
    }

    /**
     * Whether the gates are down, following the timeline.
     */
    public boolean isGatesDown() {
        return timeline.isGatesDown();
    }

    public boolean isManualClosed() {
        return manualClosed;
    }

    public CrossingTimeline getTimeline() {
        return timeline;
    }

    private void applyToObjects(World world) {
        for (final BlockPos objectPos : objects) {
            final CrossingObject object = getObject(world, objectPos);
            if (object != null) {
                object.setCrossingState(timeline.isLightsOn(), timeline.isGatesDown());
            }
        }
    }

    private static List<UUID> ids(List<CounterLink> links) {
        final List<UUID> ids = new ArrayList<>();
        for (final CounterLink link : links) {
            if (link.entityId != null) {
                ids.add(link.entityId);
            }
        }
        return ids;
    }

    private static void readCounters(CompoundTag compoundTag, List<CounterLink> links, String listKey, String key, String legacyKey) {
        links.clear();
        // The rail and position along it of each counter, "railId|progress" separated by ";", in the same order.
        final String[] rails = compoundTag.getString(listKey + "_rails").split(";", -1);
        // Saved apart from the rails, so older MAGIC versions still read those.
        final int[] directions = compoundTag.data.getIntArray(listKey + "_directions");
        if (compoundTag.data.contains(listKey)) {
            final long[] data = compoundTag.getLongArray(listKey);
            for (int i = 0; i + COUNTER_STRIDE <= data.length; i += COUNTER_STRIDE) {
                final double x = Double.longBitsToDouble(data[i + 2]);
                final double y = Double.longBitsToDouble(data[i + 3]);
                final double z = Double.longBitsToDouble(data[i + 4]);
                final CounterLink link = data[i] == 0 && data[i + 1] == 0
                        ? new CounterLink(null, new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)), x, y, z)
                        : new CounterLink(new UUID(data[i], data[i + 1]), null, x, y, z);
                link.lastDetecting = data[i + 5] != 0;
                final int index = i / COUNTER_STRIDE;
                if (index < rails.length) {
                    link.readRail(rails[index]);
                }
                if (index < directions.length) {
                    link.direction = Integer.signum(directions[index]);
                }
                links.add(link);
            }
        } else {
            // Saved before several counters per role were supported.
            final CounterLink link = CounterLink.read(compoundTag, key, legacyKey);
            if (link != null) {
                links.add(link);
            }
        }
    }

    private static void writeCounters(CompoundTag compoundTag, List<CounterLink> links, String listKey, String key, String legacyKey) {
        final long[] data = new long[links.size() * COUNTER_STRIDE];
        for (int i = 0; i < links.size(); i++) {
            final CounterLink link = links.get(i);
            final int offset = i * COUNTER_STRIDE;
            data[offset] = link.entityId == null ? 0 : link.entityId.getMostSignificantBits();
            data[offset + 1] = link.entityId == null ? 0 : link.entityId.getLeastSignificantBits();
            data[offset + 2] = Double.doubleToLongBits(link.x);
            data[offset + 3] = Double.doubleToLongBits(link.y);
            data[offset + 4] = Double.doubleToLongBits(link.z);
            data[offset + 5] = link.lastDetecting ? 1 : 0;
        }
        compoundTag.putLongArray(listKey, data);
        final StringBuilder rails = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) {
                rails.append(';');
            }
            rails.append(links.get(i).railId).append('|').append(links.get(i).railProgress);
        }
        compoundTag.putString(listKey + "_rails", rails.toString());
        final int[] directions = new int[links.size()];
        for (int i = 0; i < directions.length; i++) {
            directions[i] = links.get(i).direction;
        }
        compoundTag.data.putIntArray(listKey + "_directions", directions);
        // Keep the first counter in the old keys too, so the world still loads correctly in older MAGIC versions.
        CounterLink.write(compoundTag, links.isEmpty() ? null : links.get(0), key, legacyKey);
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
    static CrossingObject getObject(World world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return null;
        }
        final org.mtr.mapping.holder.BlockEntity wrappedBE = world.getBlockEntity(pos);
        return wrappedBE != null && wrappedBE.data instanceof CrossingObject ? (CrossingObject) wrappedBE.data : null;
    }

    /**
     * An axle counter linked as a start or end node: a counter attached to an MTR rail ({@link AxleCounterEntity},
     * by UUID), or a legacy axle counter block linked before counters were attached to rails. The position is the
     * counter's last known position, for drawing the connection on clients and listing it in the controller's screen.
     */
    public static final class CounterLink {

        @Nullable
        private final UUID entityId;
        @Nullable
        private final BlockPos legacyPos;
        private double x, y, z;
        private int missingChecks;
        /**
         * Whether the counter detected a train at the last tick, to count the trains entering and leaving.
         */
        private boolean lastDetecting;
        /**
         * The MTR rail the counter is on and where along it, so the controller can check that point itself from MTR's
         * simulation, which runs everywhere: a counter far away, in a chunk that is not loaded, still detects trains.
         * Empty for counters linked before 1.4.2 until their entity is seen once.
         */
        private String railId = "";
        private double railProgress;
        /**
         * Copied from the counter ({@link AxleCounterEntity#getDetectionDirection()}): {@code 0} detects trains running
         * either way, otherwise only those running towards increasing ({@code 1}) or decreasing rail progress.
         */
        private int direction;
        private int detectCooldown;
        private boolean railDetecting;

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

        /**
         * @return {@code 0} if it detects trains running either way, otherwise {@code 1} or {@code -1}
         */
        public int getDirection() {
            return direction;
        }

        private boolean isDetecting(World world) {
            if (!railId.isEmpty()) {
                // Like the counter entity, the rail point is checked every few ticks.
                if (--detectCooldown <= 0) {
                    detectCooldown = DETECT_TICKS;
                    final Simulator simulator = BlockAxleCounter.getSimulator(world);
                    final Rail rail = RailAttachment.getRail(simulator, railId);
                    railDetecting = rail != null && BlockAxleCounter.isRailPointOccupied(simulator, rail, railProgress, direction);
                }
                return railDetecting;
            }
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
                // Follows the counter if its rail was reshaped, and fills in counters linked before 1.4.2.
                railId = counter.getRailId();
                railProgress = counter.getRailProgress();
                direction = counter.getDetectionDirection();
                return false;
            }
            // Only conclude it is gone where its entities are loaded; in unloaded chunks it may simply not be loaded.
            if (world.data instanceof net.minecraft.server.world.ServerWorld && org.justnoone.jme.compat.McCompat.shouldTickEntity((net.minecraft.server.world.ServerWorld) world.data, new net.minecraft.util.math.BlockPos(net.minecraft.util.math.MathHelper.floor(x), net.minecraft.util.math.MathHelper.floor(y), net.minecraft.util.math.MathHelper.floor(z)))) {
                return ++missingChecks >= MISSING_COUNTER_CHECKS;
            }
            missingChecks = 0;
            return false;
        }

        private void readRail(String saved) {
            final int separator = saved.lastIndexOf('|');
            if (separator > 0) {
                try {
                    railProgress = Double.parseDouble(saved.substring(separator + 1));
                    railId = saved.substring(0, separator);
                } catch (NumberFormatException ignored) {
                }
            }
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

        /**
         * The height of the controller above its block, for the outline shown while linking. Level crossings (and the
         * legacy controller, drawn as one) are tall.
         */
        default double getLinkOutlineHeight() {
            return BlockCrossingLights.LINK_OUTLINE_HEIGHT;
        }

        default double getLinkOutlineHalfWidth() {
            return BlockCrossingLights.LINK_OUTLINE_HALF_WIDTH;
        }
    }
}
