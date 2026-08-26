package org.justnoone.jme.client.ui;

/**
 * Implemented by screens (via mixins) that want to consume mouse clicks before the vanilla widget
 * dispatch runs. This is used for custom overlay menus/dropdowns so clicks don't leak to widgets
 * behind the overlay.
 */
public interface OverlayClickHandler {

    /**
     * @return true if the click was handled and should be consumed (not passed to underlying UI).
     */
    boolean jme$handleOverlayClick(double mouseX, double mouseY, int button);
}

