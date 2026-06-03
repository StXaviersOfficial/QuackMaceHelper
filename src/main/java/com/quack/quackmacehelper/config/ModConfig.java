package com.quack.quackmacehelper.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

@Config(name = "quackmacehelper")
public class ModConfig implements ConfigData {

    // ── Global target selector (ticks, multi-select) ─────────────────────
    public boolean targetPlayers = true;
    public boolean targetMobs = true;
    public boolean targetAnimals = false;

    // ── AimAssist ────────────────────────────────────────────────────────
    public static class AimAssistConfig {
        public boolean enabled = false;
        public float range = 3f;           // 1-8 blocks
        public float fov = 120f;           // 60-360 degrees
        public float speed = 1800f;        // 600-7200 deg/s
        public boolean speedRandomization = true;
        public float speedRandomRange = 200f; // ± variance in deg/s
    }
    public AimAssistConfig aimAssist = new AimAssistConfig();

    // ── BreachSwap ───────────────────────────────────────────────────────
    public static class BreachSwapConfig {
        public boolean enabled = false;
    }
    public BreachSwapConfig breachSwap = new BreachSwapConfig();

    // ── StunSlam ─────────────────────────────────────────────────────────
    public static class StunSlamConfig {
        public boolean enabled = true;
    }
    public StunSlamConfig stunSlam = new StunSlamConfig();

    // ── PearlCatch ───────────────────────────────────────────────────────
    public static class PearlCatchConfig {
        public boolean enabled = true;
        public int delayTicks = 1;         // 1-3 ticks
        public int triggerHeight = 10;     // blocks above player
    }
    public PearlCatchConfig pearlCatch = new PearlCatchConfig();

    // ── ShieldDraining ───────────────────────────────────────────────────
    public static class ShieldDrainingConfig {
        public boolean enabled = false;
        public int cps = 50;              // 5-150
    }
    public ShieldDrainingConfig shieldDraining = new ShieldDrainingConfig();

    // ── ElytraSwapper ────────────────────────────────────────────────────
    public static class ElytraSwapperConfig {
        public boolean enabled = true;
        public float fov = 360f;          // 60-360
        public boolean autoAim = true;
    }
    public ElytraSwapperConfig elytraSwapper = new ElytraSwapperConfig();

    // ── WindChargeTackle ─────────────────────────────────────────────────
    public static class WindChargeTackleConfig {
        public boolean enabled = false;
        public float radius = 10f;        // fixed 10 blocks
    }
    public WindChargeTackleConfig windChargeTackle = new WindChargeTackleConfig();

    // ── AlwaysOnShield ───────────────────────────────────────────────────
    public static class AlwaysOnShieldConfig {
        public boolean enabled = false;
        public float radius = 10f;
    }
    public AlwaysOnShieldConfig alwaysOnShield = new AlwaysOnShieldConfig();

    // ── Keybinds (stored as GLFW key codes, -1 = unbound) ───────────────
    public static class KeybindConfig {
        public int openGui = -1;
        public int toggleAimAssist = 78;    // N
        public int toggleBreachSwap = -1;
        public int toggleStunSlam = -1;
        public int togglePearlCatch = -1;
        public int toggleShieldDraining = -1;
        public int toggleElytraSwapper = -1;
        public int toggleWindChargeTackle = -1;
        public int toggleAlwaysOnShield = -1;
    }
    public KeybindConfig keybinds = new KeybindConfig();

    // ── Static helpers ───────────────────────────────────────────────────
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
