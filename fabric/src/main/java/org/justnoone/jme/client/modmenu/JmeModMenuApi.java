package org.justnoone.jme.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.justnoone.jme.client.screen.JmeSettingsScreen;
import org.mtr.mapping.holder.Screen;
import net.minecraft.client.MinecraftClient;

public class JmeModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                final net.minecraft.client.gui.screen.Screen safeParent = parent != null ? parent : MinecraftClient.getInstance().currentScreen;
                return new JmeSettingsScreen(new Screen(safeParent));
            } catch (Exception ignored) {
                return parent;
            }
        };
    }
}
