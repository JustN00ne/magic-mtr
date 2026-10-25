package org.justnoone.jme.block;

/**
 * Crossing equipment that a crossing controller ({@link CrossingControl.Holder}) switches: the controller calls
 * {@link #setCrossingActive} while a train is in its section. Implemented by the block entity of the equipment; the
 * Crossing Linker's object mode links any block entity implementing this, so future equipment (eg barrier gates) only
 * has to implement it to be linkable, switched, kept in sync and shown with the MTR brush.
 */
public interface CrossingObject {

    /**
     * Called on the server by the linked controller when its crossing becomes active or clear, and again every second
     * so equipment in chunks that were unloaded catches up.
     */
    void setCrossingActive(boolean active);

    /**
     * Called on the server when the controller unlinks this object; the object should return to its idle state.
     */
    default void onCrossingUnlinked() {
        setCrossingActive(false);
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
