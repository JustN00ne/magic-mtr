package org.justnoone.jme.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.justnoone.jme.client.config.JmeConfigScreens;
import net.minecraft.client.MinecraftClient;

public class JmeModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                final net.minecraft.client.gui.screen.Screen safeParent = parent != null ? parent : MinecraftClient.getInstance().currentScreen;
                return JmeConfigScreens.create(safeParent);
            } catch (Exception ignored) {
                return parent;
            }
        };
    }
}
