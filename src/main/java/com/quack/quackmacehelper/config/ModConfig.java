package com.quack.quackmacehelper.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

@Config(name = "quackmacehelper")
public class ModConfig implements ConfigData {

    public boolean targetPlayers = true;
    public boolean targetMobs    = true;
    public boolean targetAnimals = false;

    // ── AimAssist ────────────────────────────────────────────────────────
    public static class AimAssistConfig {
        public boolean enabled  = false;
        public float   range    = 3f;
        public float   fov      = 120f;
        /** Degrees per second. 7200 (slider max) = instant snap, like Max mode. */
        public float   speed    = 1800f;
        public boolean speedRandomization = true;
        public float   speedRandomRange   = 200f;
        /** true = Max mode: snap straight onto the aim point every tick. */
        public boolean aimMaxMode = false;
        // 0=Head, 1=Centre, 2=Feet
        public int     aimPoint = 0;
    }
    public AimAssistConfig aimAssist = new AimAssistConfig();

    // ── BreachSwap ───────────────────────────────────────────────────────
    public static class BreachSwapConfig { public boolean enabled = false; }
    public BreachSwapConfig breachSwap = new BreachSwapConfig();

    // ── StunSlam ─────────────────────────────────────────────────────────
    public static class StunSlamConfig { public boolean enabled = true; }
    public StunSlamConfig stunSlam = new StunSlamConfig();

    // ── PearlCatch ───────────────────────────────────────────────────────
    public static class PearlCatchConfig {
        public boolean enabled       = true;
        public boolean manualMode    = false;
        public int     delayTicks    = 1;
        public int     triggerHeight = 10;
    }
    public PearlCatchConfig pearlCatch = new PearlCatchConfig();

    // ── ShieldDraining (pure modifier — driven by ElytraSwapper) ──────
    public static class ShieldDrainingConfig {
        public boolean enabled = false;
        /** Mace CPS used while a shield is detected (±10 randomized). */
        @ConfigEntry.BoundedDiscrete(min = 5, max = 300)
        public int     cps     = 12;
    }
    public ShieldDrainingConfig shieldDraining = new ShieldDrainingConfig();

    // ── ElytraSwapper ────────────────────────────────────────────────────
    public static class ElytraSwapperConfig {
        public boolean enabled = true;
        public float   fov     = 360f;
        public boolean autoAim = true;
    }
    public ElytraSwapperConfig elytraSwapper = new ElytraSwapperConfig();

    // ── WindChargeTackle ─────────────────────────────────────────────────
    public static class WindChargeTackleConfig {
        public boolean enabled = false;
        public float   radius  = 10f;
    }
    public WindChargeTackleConfig windChargeTackle = new WindChargeTackleConfig();

    // ── AlwaysOnShield ───────────────────────────────────────────────────
    public static class AlwaysOnShieldConfig {
        public boolean enabled = false;
        public float   radius  = 10f;
    }
    public AlwaysOnShieldConfig alwaysOnShield = new AlwaysOnShieldConfig();

    // ── Keybinds ─────────────────────────────────────────────────────────
    public static class KeybindConfig {
        public int openGui                = -1;
        public int toggleAimAssist        = 78;  // N
        public int toggleBreachSwap       = -1;
        public int toggleStunSlam         = -1;
        public int togglePearlCatch       = -1;
        public int toggleShieldDraining   = -1;
        public int toggleElytraSwapper    = -1;
        public int toggleWindChargeTackle = -1;
        public int toggleAlwaysOnShield   = -1;
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
