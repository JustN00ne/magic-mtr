package org.justnoone.jme;

import org.justnoone.jme.rail.MagicRailItemGroups;
import org.mtr.mapping.registry.Registry;

public class Jme {

    public static final String MOD_ID = "jme";

    public static void init(Registry registry) {
        MagicRailItemGroups.register();
        org.justnoone.jme.rail.MagicRailTiltRegistry.reloadFromDisk();
        // Registration of ModBlocks, ModItems, ModItemGroups moved to loader-specific subprojects
    }
}
