package org.justnoone.jme.util;

/**
 * Decides when a level crossing is active from its two axle counters.
 * <p>
 * The crossing used to switch on only when the activator counter saw a train and off only when the deactivator saw
 * one. A train running the other way passed the deactivator first (ignored), then switched the crossing on at the
 * activator after it had already crossed, leaving it on until some later train reached the deactivator; and a second
 * train following closely was left on a crossing that had already opened. Here both counters work in both directions:
 * <ul>
 *     <li>while idle, whichever counter sees a train first activates the crossing and becomes the entry side;</li>
 *     <li>each new train reaching the entry side while active is counted, and each train leaving the far (exit)
 *     counter is subtracted;</li>
 *     <li>the crossing clears once every counted train has left the exit counter and neither counter is occupied;</li>
 *     <li>with only one counter linked, the crossing is active exactly while that counter is occupied;</li>
 *     <li>as a fail-safe (eg a train that reversed back out of the section), it also clears after
 *     {@code timeoutTicks} without any detection.</li>
 * </ul>
 * Pure logic (no Minecraft types) so it can be persisted in the block entity and unit tested.
 */
public final class CrossingStateMachine {

    public static final int SIDE_NONE = 0;
    public static final int SIDE_ACTIVATOR = 1;
    public static final int SIDE_DEACTIVATOR = 2;
    public static final long DEFAULT_TIMEOUT_TICKS = 20L * 120;

    private boolean active;
    private int entrySide = SIDE_NONE;
    private int trainsInside;
    private boolean lastActivator;
    private boolean lastDeactivator;
    private long ticksWithoutDetection;

    /**
     * @param hasActivator     whether an activator counter is linked
     * @param activator        whether the activator counter currently detects a train
     * @param hasDeactivator   whether a deactivator counter is linked
     * @param deactivator      whether the deactivator counter currently detects a train
     * @param elapsedTicks     ticks since the previous update
     * @param timeoutTicks     fail-safe: clear after this many ticks without any detection (0 or less disables it)
     * @return whether the crossing is active after this update
     */
    public boolean update(boolean hasActivator, boolean activator, boolean hasDeactivator, boolean deactivator, long elapsedTicks, long timeoutTicks) {
        activator &= hasActivator;
        deactivator &= hasDeactivator;
        return update(
                hasActivator, activator, activator && !lastActivator ? 1 : 0, !activator && lastActivator ? 1 : 0,
                hasDeactivator, deactivator, deactivator && !lastDeactivator ? 1 : 0, !deactivator && lastDeactivator ? 1 : 0,
                elapsedTicks, timeoutTicks
        );
    }

    /**
     * The same for several counters on each side (eg one per track): a side detects while any of its counters does,
     * and every counter that starts or stops detecting counts as a train entering or leaving, so two trains reaching
     * different counters of the same side in the same tick are both counted.
     *
     * @param activatorRises   how many activator counters started detecting since the previous update
     * @param activatorFalls   how many activator counters stopped detecting since the previous update
     * @param deactivatorRises how many deactivator counters started detecting since the previous update
     * @param deactivatorFalls how many deactivator counters stopped detecting since the previous update
     */
    public boolean update(boolean hasActivator, boolean activator, int activatorRises, int activatorFalls, boolean hasDeactivator, boolean deactivator, int deactivatorRises, int deactivatorFalls, long elapsedTicks, long timeoutTicks) {
        activator &= hasActivator;
        deactivator &= hasDeactivator;
        if (!hasActivator) {
            activatorRises = activatorFalls = 0;
        }
        if (!hasDeactivator) {
            deactivatorRises = deactivatorFalls = 0;
        }
        lastActivator = activator;
        lastDeactivator = deactivator;
        final boolean anyDetecting = activator || deactivator;

        if (!active) {
            if (anyDetecting) {
                active = true;
                entrySide = activator ? SIDE_ACTIVATOR : SIDE_DEACTIVATOR;
                trainsInside = Math.max(1, activator ? activatorRises : deactivatorRises);
                ticksWithoutDetection = 0;
            }
            return active;
        }

        final boolean entryIsActivator = entrySide == SIDE_ACTIVATOR;
        final boolean hasExit = entryIsActivator ? hasDeactivator : hasActivator;
        final boolean entryDetecting = entryIsActivator ? activator : deactivator;
        final int entryRises = entryIsActivator ? activatorRises : deactivatorRises;
        final int exitFalls = entryIsActivator ? deactivatorFalls : activatorFalls;

        if (!hasExit) {
            if (!entryDetecting) {
                clear();
            }
            return active;
        }

        trainsInside = Math.max(0, trainsInside + Math.max(0, entryRises) - Math.max(0, exitFalls));

        if (anyDetecting) {
            ticksWithoutDetection = 0;
        } else {
            ticksWithoutDetection += Math.max(0, elapsedTicks);
        }

        if (!anyDetecting && (trainsInside <= 0 || timeoutTicks > 0 && ticksWithoutDetection >= timeoutTicks)) {
            clear();
        }
        return active;
    }

    /**
     * Forces the crossing inactive (eg when its counters are unlinked).
     */
    public void clear() {
        active = false;
        entrySide = SIDE_NONE;
        trainsInside = 0;
        ticksWithoutDetection = 0;
    }

    public boolean isActive() {
        return active;
    }

    public int getEntrySide() {
        return entrySide;
    }

    public int getTrainsInside() {
        return trainsInside;
    }

    // Persistence: these are written to and read from the controller's NBT.

    public long getTicksWithoutDetection() {
        return ticksWithoutDetection;
    }

    public boolean getLastActivator() {
        return lastActivator;
    }

    public boolean getLastDeactivator() {
        return lastDeactivator;
    }

    public void restore(boolean active, int entrySide, int trainsInside, boolean lastActivator, boolean lastDeactivator, long ticksWithoutDetection) {
        this.active = active;
        this.entrySide = active ? (entrySide == SIDE_DEACTIVATOR ? SIDE_DEACTIVATOR : SIDE_ACTIVATOR) : SIDE_NONE;
        this.trainsInside = active ? Math.max(1, trainsInside) : 0;
        this.lastActivator = lastActivator;
        this.lastDeactivator = lastDeactivator;
        this.ticksWithoutDetection = Math.max(0, ticksWithoutDetection);
    }
}
