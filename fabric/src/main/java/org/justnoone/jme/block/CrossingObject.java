package org.justnoone.jme.block;

/**
 * Crossing equipment that a crossing controller ({@link CrossingControl.Holder}) switches: the controller calls
 * {@link #setCrossingState} with the state of its timeline (lights, then gates) while a train is in its section.
 * Implemented by the block entity of the equipment; the Crossing Linker's object mode links any block entity
 * implementing this, so new equipment only has to implement it to be linkable, switched, kept in sync and shown with
 * the MTR brush and in the controller's screen.
 */
public interface CrossingObject {

    /**
     * Called on the server by the linked controller when its crossing becomes active or clear, and again every second
     * so equipment in chunks that were unloaded catches up. Equipment that is not a gate follows the lights.
     *
     * @param lightsOn  whether the controller's lights are on
     * @param gatesDown whether the controller's gates are down (they lower after the lights, see the timeline)
     */
    default void setCrossingState(boolean lightsOn, boolean gatesDown) {
        setCrossingActive(lightsOn);
    }

    /**
     * Called by {@link #setCrossingState} for equipment that only has an on / off state.
     */
    void setCrossingActive(boolean active);

    /**
     * Called on the server when the controller unlinks this object; the object should return to its idle state.
     */
    default void onCrossingUnlinked() {
        setCrossingState(false, false);
    }

    /**
     * The name shown for this equipment in the controller's list of components.
     */
    default String getCrossingObjectName() {
        return "Crossing object";
    }

    /**
     * The height of the equipment above its block, for the blue outline shown while linking it.
     */
    default double getLinkOutlineHeight() {
        return 1;
    }

    /**
     * How far the equipment reaches sideways from the centre of its block, for the outline shown while linking it.
     */
    default double getLinkOutlineHalfWidth() {
        return 0.5;
    }
}
