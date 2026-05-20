package org.justnoone.jme.client.ui;

/**
 * Optional helper interface for screens that display custom overlay menus/dropdowns.
 * Other mixins (eg list drag handlers) can query this to avoid reacting to clicks
 * that were intentionally consumed by an overlay.
 */
public interface OverlayMenuState {

    /**
     * @return true if an overlay menu/dropdown is currently open.
     */
    boolean jme$isOverlayMenuOpen();
}

