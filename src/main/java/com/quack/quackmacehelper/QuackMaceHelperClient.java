package com.quack.quackmacehelper;

import com.quack.quackmacehelper.config.ModConfig;
import com.quack.quackmacehelper.feature.*;
import com.quack.quackmacehelper.gui.QuackMaceScreen;
import com.quack.quackmacehelper.hud.HudOverlay;
import com.quack.quackmacehelper.keybind.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * QuackMaceHelper — V5 client entrypoint.
 *
 * MASTER KILL SWITCH (R1): when masterEnabled is false the mod behaves as
 * if it never existed — no feature ticks, no mixins act, no HUD, no
 * feature keybinds respond. ONLY the mod-menu open key (and the ModMenu
 * entry) stay alive so the mod can be re-enabled in-game. All feature
 * state is hard-reset the moment the switch turns off.
 *
 * Menu access: ModMenu ("Mods" -> QuackMaceHelper -> Config) or the
 * configurable "Open Menu Key" in the General tab. There is intentionally
 * NO chat command — chat commands get forwarded to the server and rejected
 * on servers that don't know them.
 */
public class QuackMaceHelperClient implements ClientModInitializer {

    /** Tracks the kill-switch edge so state is cleared exactly once. */
    private static boolean masterWasOn = true;

    @Override
    public void onInitializeClient() {
        ModConfig.init();
        KeyBindings.register();
        HudOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // ── The menu key ALWAYS works (kill switch exemption, R1) ────
            if (KeyBindings.openGui())
                openQuackMaceScreen(client);

            ModConfig cfg = ModConfig.get();

            // ── MASTER KILL SWITCH ───────────────────────────────────────
            if (!cfg.masterEnabled) {
                if (masterWasOn) hardResetEverything(client);
                masterWasOn = false;
                return; // nothing else runs — mod fully inert
            }
            if (!masterWasOn) masterWasOn = true;

            // ── Keybind toggles (all features, save + HUD toast) ────────
            if (KeyBindings.toggleAimAssist()) {
                cfg.aimAssist.enabled = !cfg.aimAssist.enabled;
                if (!cfg.aimAssist.enabled) AimAssistFeature.clearTarget();
                ModConfig.save();
                HudOverlay.showMessage("Aim Assist", cfg.aimAssist.enabled);
            }
            if (KeyBindings.toggleAttributeSwap()) {
                cfg.attributeSwap.enabled = !cfg.attributeSwap.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Attribute Swap", cfg.attributeSwap.enabled);
            }
            if (KeyBindings.toggleStunSlam()) {
                cfg.stunSlam.enabled = !cfg.stunSlam.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Stun Slam", cfg.stunSlam.enabled);
            }
            if (KeyBindings.togglePearlCatch()) {
                cfg.pearlCatch.enabled = !cfg.pearlCatch.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Pearl Catch", cfg.pearlCatch.enabled);
            }
            if (KeyBindings.toggleShieldDraining()) {
                cfg.shieldDraining.enabled = !cfg.shieldDraining.enabled;
                ShieldDrainingFeature.onToggled(cfg.shieldDraining.enabled);
                ModConfig.save();
                HudOverlay.showMessage("Shield Draining", cfg.shieldDraining.enabled);
            }
            if (KeyBindings.toggleAlwaysOnShield()) {
                cfg.alwaysOnShield.enabled = !cfg.alwaysOnShield.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Always Shield", cfg.alwaysOnShield.enabled);
            }
            if (KeyBindings.toggleElytraMace()) {
                cfg.elytraMace.enabled = !cfg.elytraMace.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Elytra Mace", cfg.elytraMace.enabled);
            }
            if (KeyBindings.toggleAutoMace()) {
                cfg.autoMace.enabled = !cfg.autoMace.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Auto Mace", cfg.autoMace.enabled);
            }

            // ── Feature ticks ────────────────────────────────────────────
            if (client.player == null || client.world == null) {
                hardResetEverything(client);
                return;
            }

            AimAssistFeature.onGameTick(client);
            AttributeSwapFeature.tick(client);
            StunSlamFeature.tick(client);
            PearlCatchFeature.tick(client);
            ShieldDrainingFeature.tick(client);
            AlwaysOnShieldFeature.tick(client);
            ElytraMaceFeature.tick(client);
            AutoMaceFeature.tick(client);
        });
    }

    /** Clears every feature's state (kill switch, world exit, disconnect). */
    private static void hardResetEverything(MinecraftClient client) {
        AimAssistFeature.clearTarget();
        AimAssistFeature.stopFeatureAim();
        AttributeSwapFeature.hardReset();
        StunSlamFeature.hardReset();
        PearlCatchFeature.hardReset();
        ShieldDrainingFeature.stop();
        AlwaysOnShieldFeature.hardReset(client);
        ElytraMaceFeature.hardReset(client);
        AutoMaceFeature.hardReset();
    }

    /**
     * Opens the QuackMace screen, cleanly closing any currently open screen first.
     */
    public static void openQuackMaceScreen(MinecraftClient mc) {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof QuackMaceScreen)) {
            mc.setScreen(null);
        }
        mc.setScreen(new QuackMaceScreen());
    }
}
