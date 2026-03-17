package org.justnoone.jme.client;

public class JmeClient {
    public static void initClient() {
        MagicRailClientHooks.register();
        MagicClientCommands.register();
    }
}
