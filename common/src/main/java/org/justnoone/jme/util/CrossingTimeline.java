package org.justnoone.jme.util;

/**
 * The sequence a crossing controller plays when its crossing becomes active (a train is detected) and clear again,
 * edited with the controller's timeline:
 * <ul>
 *     <li>closing: the lights switch on {@link #getLightsOnDelay} ticks after the crossing became active, and the gates
 *     start lowering {@link #getGatesLowerDelay} ticks after it;</li>
 *     <li>opening: the gates start rising {@link #getGatesRaiseDelay} ticks after the crossing became clear, and the
 *     lights switch off {@link #getLightsOffDelay} ticks after it.</li>
 * </ul>
 * If the crossing becomes active again while it is still opening, lights that are on and gates that are down stay so
 * (they never flicker); the closing sequence then only switches on what is still off. Pure logic (no Minecraft types)
 * so it can be persisted in the block entity and unit tested.
 */
public final class CrossingTimeline {

    public static final int TICKS_PER_SECOND = 20;
    /**
     * The longest delay the timeline can be set to.
     */
    public static final int MAX_DELAY_TICKS = 15 * TICKS_PER_SECOND;
    public static final int DEFAULT_LIGHTS_ON_DELAY = 0;
    /**
     * Gates usually start lowering a few seconds after the lights, so road users have time to stop or clear.
     */
    public static final int DEFAULT_GATES_LOWER_DELAY = 3 * TICKS_PER_SECOND;
    public static final int DEFAULT_GATES_RAISE_DELAY = 0;
    /**
     * Zero keeps the behaviour of crossings without gates: the lights stop as soon as the train has passed.
     */
    public static final int DEFAULT_LIGHTS_OFF_DELAY = 0;

    private int lightsOnDelay = DEFAULT_LIGHTS_ON_DELAY;
    private int gatesLowerDelay = DEFAULT_GATES_LOWER_DELAY;
    private int gatesRaiseDelay = DEFAULT_GATES_RAISE_DELAY;
    private int lightsOffDelay = DEFAULT_LIGHTS_OFF_DELAY;

    private boolean active;
    private boolean lightsOn;
    private boolean gatesDown;
    private int ticksInPhase;

    /**
     * Called every tick with whether the crossing is active.
     *
     * @return whether the lights or the gates changed
     */
    public boolean update(boolean active) {
        if (active != this.active) {
            this.active = active;
            ticksInPhase = 0;
        } else if (ticksInPhase < MAX_DELAY_TICKS) {
            ticksInPhase++;
        }

        final boolean oldLightsOn = lightsOn;
        final boolean oldGatesDown = gatesDown;
        if (active) {
            lightsOn |= ticksInPhase >= lightsOnDelay;
            gatesDown |= ticksInPhase >= gatesLowerDelay;
        } else {
            gatesDown &= ticksInPhase < gatesRaiseDelay;
            lightsOn &= ticksInPhase < lightsOffDelay;
        }
        return lightsOn != oldLightsOn || gatesDown != oldGatesDown;
    }

    public boolean isLightsOn() {
        return lightsOn;
    }

    public boolean isGatesDown() {
        return gatesDown;
    }

    public int getLightsOnDelay() {
        return lightsOnDelay;
    }

    public int getGatesLowerDelay() {
        return gatesLowerDelay;
    }

    public int getGatesRaiseDelay() {
        return gatesRaiseDelay;
    }

    public int getLightsOffDelay() {
        return lightsOffDelay;
    }

    public void setDelays(int lightsOnDelay, int gatesLowerDelay, int gatesRaiseDelay, int lightsOffDelay) {
        this.lightsOnDelay = clampDelay(lightsOnDelay);
        this.gatesLowerDelay = clampDelay(gatesLowerDelay);
        this.gatesRaiseDelay = clampDelay(gatesRaiseDelay);
        this.lightsOffDelay = clampDelay(lightsOffDelay);
    }

    public static int clampDelay(int ticks) {
        return Math.max(0, Math.min(MAX_DELAY_TICKS, ticks));
    }

    // Persistence: these are written to and read from the controller's NBT.

    public boolean isActive() {
        return active;
    }

    public int getTicksInPhase() {
        return ticksInPhase;
    }

    public void restore(boolean active, boolean lightsOn, boolean gatesDown, int ticksInPhase) {
        this.active = active;
        this.lightsOn = lightsOn;
        this.gatesDown = gatesDown;
        this.ticksInPhase = Math.max(0, Math.min(MAX_DELAY_TICKS, ticksInPhase));
    }
}
