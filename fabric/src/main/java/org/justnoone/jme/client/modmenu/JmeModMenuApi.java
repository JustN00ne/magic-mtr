package org.justnoone.jme.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.justnoone.jme.client.screen.JmeSettingsScreen;
import org.mtr.mapping.holder.Screen;

public class JmeModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new JmeSettingsScreen(new Screen(parent));
    }
}
