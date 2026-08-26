package org.justnoone.jme.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.javafmlmod.FMLJavaModLoadingContext;
import org.justnoone.jme.Jme;
import org.justnoone.jme.client.JmeClient;

@Mod(Jme.MOD_ID)
public class JmeForge {
    public JmeForge() {
        Jme.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        JmeClient.initClient();
    }
}
