package com.quack.quackmacehelper;

import com.quack.quackmacehelper.config.ModConfig;
import com.quack.quackmacehelper.feature.AimAssistFeature;
import com.quack.quackmacehelper.gui.QuackMaceScreen;
import com.quack.quackmacehelper.hud.HudOverlay;
import com.quack.quackmacehelper.keybind.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class QuackMaceHelperClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfig.init();
        KeyBindings.register();
        HudOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KeyBindings.openGui.wasPressed())
                client.setScreen(new QuackMaceScreen());

            ModConfig cfg = ModConfig.get();

            if (KeyBindings.toggleAimAssist.wasPressed()) {
                cfg.aimAssist.enabled = !cfg.aimAssist.enabled;
                if (!cfg.aimAssist.enabled) AimAssistFeature.clearTarget();
                ModConfig.save();
                HudOverlay.showMessage("Aim Assist", cfg.aimAssist.enabled);
            }
            if (KeyBindings.toggleBreachSwap.wasPressed()) {
                cfg.breachSwap.enabled = !cfg.breachSwap.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Breach Swap", cfg.breachSwap.enabled);
            }
            if (KeyBindings.toggleStunSlam.wasPressed()) {
                cfg.stunSlam.enabled = !cfg.stunSlam.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Stun Slam", cfg.stunSlam.enabled);
            }
            if (KeyBindings.togglePearlCatch.wasPressed()) {
                cfg.pearlCatch.enabled = !cfg.pearlCatch.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Pearl Catch", cfg.pearlCatch.enabled);
            }
            if (KeyBindings.toggleShieldDraining.wasPressed()) {
                cfg.shieldDraining.enabled = !cfg.shieldDraining.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Shield Drain", cfg.shieldDraining.enabled);
            }
            if (KeyBindings.toggleElytraSwapper.wasPressed()) {
                cfg.elytraSwapper.enabled = !cfg.elytraSwapper.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Elytra Swap", cfg.elytraSwapper.enabled);
            }
            if (KeyBindings.toggleWindChargeTackle.wasPressed()) {
                cfg.windChargeTackle.enabled = !cfg.windChargeTackle.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Wind Tackle", cfg.windChargeTackle.enabled);
            }
            if (KeyBindings.toggleAlwaysOnShield.wasPressed()) {
                cfg.alwaysOnShield.enabled = !cfg.alwaysOnShield.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Always Shield", cfg.alwaysOnShield.enabled);
            }
        });
    }
}
