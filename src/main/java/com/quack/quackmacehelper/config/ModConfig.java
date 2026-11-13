package com.quack.quackmacehelper.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * V5 config schema. Section order mirrors the in-game menu order.
 *
 * MASTER KILL SWITCH (masterEnabled) is the first setting: when false the
 * whole mod is inert except the mod menu itself (QuackMaceHelperClient
 * gates every tick on it).
 */
@Config(name = "quackmacehelper")
public class ModConfig implements ConfigData {

    /** MASTER KILL SWITCH — first setting in General. */
    public boolean masterEnabled = true;

    public boolean targetPlayers = true;
    public boolean targetMobs    = true;
    public boolean targetAnimals = false;

    // ── AimAssist ────────────────────────────────────────────────────────
    public static class AimAssistConfig {
        public boolean enabled  = false;
        /** Blocks, 0.1 - 8. */
        public float   range    = 3f;
        /** Cone from the crosshair, 1-360, default 180 (V5). */
        public float   fov      = 180f;
        /** Degrees per second, slider 600-7200, default 3600 (V5).
         *  7200 (top) = full turn per tick = behaves like Max mode. */
        public float   speed    = 3600f;
        /** V5: one roll in [0, 200] (add OR subtract) per ACTIVATION,
         *  held for the whole activation. Range is fixed at 200 per spec. */
        public boolean speedRandomization = true;
        /** true = Max mode: snap straight onto the aim point every tick. */
        public boolean aimMaxMode = false;
        /** 0=Head, 1=Centre, 2=Feet, 3=Auto (Wurst closest-hitbox-point). */
        public int     aimPoint = 0;
    }
    public AimAssistConfig aimAssist = new AimAssistConfig();

    // ── AttributeSwap (V5: replaces BreachSwap) ──────────────────────────
    public static class AttributeSwapConfig { public boolean enabled = false; }
    public AttributeSwapConfig attributeSwap = new AttributeSwapConfig();

    // ── StunSlam ─────────────────────────────────────────────────────────
    public static class StunSlamConfig { public boolean enabled = false; }
    public StunSlamConfig stunSlam = new StunSlamConfig();

    // ── PearlCatch ───────────────────────────────────────────────────────
    public static class PearlCatchConfig {
        public boolean enabled       = false;
        public boolean manualMode    = false;
        /** 1-3 ticks. */
        public int     delayTicks    = 1;
        /** Pearl arc must exceed this height (from PLAYER positions). */
        public int     triggerHeight = 10;
    }
    public PearlCatchConfig pearlCatch = new PearlCatchConfig();

    // ── ShieldDraining (V5: standalone auto-fire, 20-250 CPS default 50) ─
    public static class ShieldDrainingConfig {
        public boolean enabled = false;
        @ConfigEntry.BoundedDiscrete(min = 20, max = 250)
        public int     cps     = 50;
    }
    public ShieldDrainingConfig shieldDraining = new ShieldDrainingConfig();

    // ── AlwaysOnShield ───────────────────────────────────────────────────
    public static class AlwaysOnShieldConfig {
        public boolean enabled = false;
        /** Hemisphere width at user level, default 8 (V5 spec). */
        public float   radius  = 8f;
    }
    public AlwaysOnShieldConfig alwaysOnShield = new AlwaysOnShieldConfig();

    // ── ElytraMace (V5: replaces ElytraSwapper) ──────────────────────────
    public static class ElytraMaceConfig {
        public boolean enabled    = false;
        /** 60-360, default 180 (V5). */
        public float   fov        = 180f;
        public boolean autoAim    = true;
        /** true = instant snap aim; false = smooth (V5 aim type). */
        public boolean aimInstant = false;
    }
    public ElytraMaceConfig elytraMace = new ElytraMaceConfig();

    // ── AutoMace (V5: the ONE falling-attack feature) ────────────────────
    public static class AutoMaceConfig {
        public boolean enabled    = false;
        /** 60-360, default 180 (V5). */
        public float   fov        = 180f;
        public boolean autoAim    = true;
        public boolean aimInstant = false;
    }
    public AutoMaceConfig autoMace = new AutoMaceConfig();

    // ── Keybinds (V5: ALL unbound except the open-menu key) ──────────────
    public static class KeybindConfig {
        public int openGui              = 78;  // N — the menu key exception
        public int toggleAimAssist      = -1;
        public int toggleAttributeSwap  = -1;
        public int toggleStunSlam       = -1;
        public int togglePearlCatch     = -1;
        public int toggleShieldDraining = -1;
        public int toggleAlwaysOnShield = -1;
        public int toggleElytraMace     = -1;
        public int toggleAutoMace       = -1;
    }
    public KeybindConfig keybinds = new KeybindConfig();

    public static ModConfig get() {
        return AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    }
    public static void init() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
    }
    public static void save() {
        AutoConfig.getConfigHolder(ModConfig.class).save();
    }
}
