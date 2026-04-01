package org.justnoone.jme.fabric.bluemap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Boots the optional BlueMap integration without hard-requiring BlueMap at runtime.
 * <p>
 * This class must not reference BlueMap API types directly, otherwise the JVM may fail to load MAGIC when
 * BlueMap is not installed.
 */
public final class BlueMapIntegrationBootstrap {

    private static final Logger LOGGER = LogManager.getLogger("MAGIC-BlueMap");

    private static volatile boolean attempted;

    private BlueMapIntegrationBootstrap() {
    }

    public static synchronized void initIfEnabled() {
        if (attempted) {
            return;
        }
        attempted = true;

        if (!isBlueMapApiPresent()) {
            // BlueMap not installed; silently no-op.
            return;
        }

        try {
            final Class<?> integrationClass = Class.forName("org.justnoone.jme.fabric.bluemap.BlueMapIntegration");
            integrationClass.getMethod("init").invoke(null);
            LOGGER.info("BlueMap integration initialized");
        } catch (Throwable t) {
            LOGGER.warn("Failed to initialize BlueMap integration", t);
        }
    }

    private static boolean isBlueMapApiPresent() {
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI", false, BlueMapIntegrationBootstrap.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
