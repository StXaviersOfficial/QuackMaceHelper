package com.quack.quackmacehelper;

import com.quack.quackmacehelper.gui.QuackMaceScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Use our custom GUI, not AutoConfig's auto-generated one.
        // V4.1.1: pass the parent through so ESC returns to the Mods list
        // instead of dropping into the game.
        return QuackMaceScreen::new;
    }
}
