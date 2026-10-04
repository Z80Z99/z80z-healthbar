package com.z80z99.z80zhealthbar.fabric;

import com.z80z99.z80zhealthbar.gui.ModSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModSettingsScreen::create;
    }
}
