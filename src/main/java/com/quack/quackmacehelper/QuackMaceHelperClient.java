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
 * QuackMaceHelper — client entrypoint.
 *
 * Menu access: ModMenu ("Mods" -> QuackMaceHelper -> Config) or the
 * configurable "Open Menu Key" in the General tab. There is intentionally
 * NO /qm or /quackmace chat command — chat commands get forwarded to the
 * server and rejected on servers that don't know them.
 */
public class QuackMaceHelperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfig.init();
        KeyBindings.register();
        HudOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // ── Keybind toggles ────────────────────────────────────────────
            ModConfig cfg = ModConfig.get();

            if (KeyBindings.openGui())
                openQuackMaceScreen(client);

            if (KeyBindings.toggleAimAssist()) {
                cfg.aimAssist.enabled = !cfg.aimAssist.enabled;
                if (!cfg.aimAssist.enabled) AimAssistFeature.clearTarget();
                ModConfig.save();
                HudOverlay.showMessage("Aim Assist", cfg.aimAssist.enabled);
            }
            if (KeyBindings.toggleBreachSwap()) {
                cfg.breachSwap.enabled = !cfg.breachSwap.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Breach Swap", cfg.breachSwap.enabled);
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
                ModConfig.save();
                HudOverlay.showMessage("Shield Drain", cfg.shieldDraining.enabled);
            }
            if (KeyBindings.toggleElytraSwapper()) {
                cfg.elytraSwapper.enabled = !cfg.elytraSwapper.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Elytra Swap", cfg.elytraSwapper.enabled);
            }
            if (KeyBindings.toggleWindChargeTackle()) {
                cfg.windChargeTackle.enabled = !cfg.windChargeTackle.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Wind Tackle", cfg.windChargeTackle.enabled);
            }
            if (KeyBindings.toggleAlwaysOnShield()) {
                cfg.alwaysOnShield.enabled = !cfg.alwaysOnShield.enabled;
                ModConfig.save();
                HudOverlay.showMessage("Always Shield", cfg.alwaysOnShield.enabled);
            }

            // ── Feature ticks (single source of truth — see note) ─────────
            // NOTE: features are ticked ONLY here. An earlier version also
            // ticked them from GameRendererMixin, which made every feature
            // run TWICE per game tick (double CPS, halved delays — the root
            // cause of PearlCatch manual mode firing instantly).
            if (client.player == null || client.world == null) {
                // Left a world / disconnected: clear every feature's state so
                // nothing holds a stale entity across worlds.
                AimAssistFeature.clearTarget();
                ElytraSwapperFeature.hardReset(client);
                StunSlamFeature.hardReset();
                PearlCatchFeature.hardReset();
                BreachSwapFeature.hardReset();
                ShieldDrainingFeature.stop();
                AlwaysOnShieldFeature.hardReset(client);
                return;
            }

            AimAssistFeature.onGameTick(client);
            BreachSwapFeature.tick(client);
            StunSlamFeature.tick(client);
            PearlCatchFeature.tick(client);
            ElytraSwapperFeature.tick(client);
            WindChargeTackleFeature.tick(client);
            AlwaysOnShieldFeature.tick(client);
            // ShieldDrainingFeature no longer has a tick() — it is a pure
            // modifier driven by ElytraSwapper when a shield is detected.
        });
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
