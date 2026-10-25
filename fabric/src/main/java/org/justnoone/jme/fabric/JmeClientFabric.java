package org.justnoone.jme.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.justnoone.jme.client.JmeClient;

public class JmeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        JmeClient.initClient();
    }
}
