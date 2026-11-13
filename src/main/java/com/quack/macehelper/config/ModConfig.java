package com.quack.macehelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "quackmacehelper.json");
    private static ModConfig INSTANCE = new ModConfig();

    public enum AimMode {
        SMOOTH("Smooth"),
        MAX("Max");

        private final String label;
        AimMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum AimPoint {
        HEAD("Head"),
        CENTER("Center"),
        FEET("Feet"),
        AUTO("Auto");

        private final String label;
        AimPoint(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum PearlCatchMode {
        AUTOMATIC("Automatic"),
        MANUAL("Manual");

        private final String label;
        PearlCatchMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum MaceAimType {
        SMOOTH("Smooth"),
        INSTANT("Instant");

        private final String label;
        MaceAimType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // GENERAL
    public boolean masterSwitch = true;
    public boolean targetPlayers = true;
    public boolean targetMobs = false;
    public boolean targetAnimals = false;
    public int openMenuKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    // AIM ASSIST
    public boolean aimAssistEnabled = false;
    public int aimAssistKey = InputUtil.UNKNOWN_KEY.getCode();
    public double aimAssistRange = 4.5;
    public int aimAssistFov = 180;
    public int aimAssistSpeed = 3600;
    public boolean aimAssistSpeedRandomization = true;
    public AimMode aimAssistMode = AimMode.SMOOTH;
    public AimPoint aimAssistAimAt = AimPoint.AUTO;

    // ATTRIBUTESWAP
    public boolean attributeSwapEnabled = false;
    public int attributeSwapKey = InputUtil.UNKNOWN_KEY.getCode();

    // STUN SLAM
    public boolean stunSlamEnabled = false;
    public int stunSlamKey = InputUtil.UNKNOWN_KEY.getCode();

    // PEARL CATCH
    public boolean pearlCatchEnabled = false;
    public int pearlCatchKey = InputUtil.UNKNOWN_KEY.getCode();
    public PearlCatchMode pearlCatchMode = PearlCatchMode.AUTOMATIC;
    public int pearlCatchDelay = 1;

    // SHIELD DRAINING
    public boolean shieldDrainingEnabled = false;
    public int shieldDrainingKey = InputUtil.UNKNOWN_KEY.getCode();
    public int shieldDrainingCps = 50;

    // ALWAYS-ON SHIELD
    public boolean alwaysOnShieldEnabled = false;
    public int alwaysOnShieldKey = InputUtil.UNKNOWN_KEY.getCode();

    // ELYTRA MACE
    public boolean elytraMaceEnabled = false;
    public int elytraMaceKey = InputUtil.UNKNOWN_KEY.getCode();
    public MaceAimType elytraMaceAimType = MaceAimType.SMOOTH;
    public boolean elytraMaceAutoAim = true;
    public int elytraMaceFov = 180;

    // AUTO MACE
    public boolean autoMaceEnabled = false;
    public int autoMaceKey = InputUtil.UNKNOWN_KEY.getCode();
    public boolean autoMaceAutoAim = true;
    public MaceAimType autoMaceAimType = MaceAimType.SMOOTH;
    public int autoMaceFov = 180;

    // WIND TACKLE (Coming Soon dead button holder)
    public boolean windTackleComingSoon = false;

    public static ModConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    INSTANCE.validate();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        INSTANCE.validate();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void validate() {
        if (aimAssistRange < 0.1) aimAssistRange = 0.1;
        if (aimAssistRange > 8.0) aimAssistRange = 8.0;
        if (aimAssistFov < 1) aimAssistFov = 1;
        if (aimAssistFov > 360) aimAssistFov = 360;
        if (aimAssistSpeed < 600) aimAssistSpeed = 600;
        if (aimAssistSpeed > 7200) aimAssistSpeed = 7200;
        if (aimAssistMode == null) aimAssistMode = AimMode.SMOOTH;
        if (aimAssistAimAt == null) aimAssistAimAt = AimPoint.AUTO;
        if (pearlCatchMode == null) pearlCatchMode = PearlCatchMode.AUTOMATIC;
        if (pearlCatchDelay < 1) pearlCatchDelay = 1;
        if (pearlCatchDelay > 3) pearlCatchDelay = 3;
        if (shieldDrainingCps < 20) shieldDrainingCps = 20;
        if (shieldDrainingCps > 250) shieldDrainingCps = 250;
        if (elytraMaceAimType == null) elytraMaceAimType = MaceAimType.SMOOTH;
        if (elytraMaceFov < 60) elytraMaceFov = 60;
        if (elytraMaceFov > 360) elytraMaceFov = 360;
        if (autoMaceAimType == null) autoMaceAimType = MaceAimType.SMOOTH;
        if (autoMaceFov < 60) autoMaceFov = 60;
        if (autoMaceFov > 360) autoMaceFov = 360;
        windTackleComingSoon = false;
    }
}
