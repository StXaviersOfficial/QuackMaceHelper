package com.quack.macehelper.config;

import com.quack.macehelper.QuackMaceHelperClient;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ConfigScreenFactory {

    public static Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("title.quackmacehelper.config"))
                .setSavingRunnable(() -> {
                    ModConfig.save();
                    QuackMaceHelperClient.syncKeybindingsFromConfig();
                });

        ConfigEntryBuilder eb = builder.entryBuilder();

        // Single unified screen or categories in exact top-to-bottom order
        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.general"));

        // 1. GENERAL (Master kill switch MUST be the very first setting)
        general.addEntry(eb.startBooleanToggle(Text.literal("Master Kill Switch"), config.masterSwitch)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Master on/off toggle. When off, the entire mod is completely inert."))
                .setSaveConsumer(val -> {
                    config.masterSwitch = val;
                    if (!val) {
                        QuackMaceHelperClient.onMasterDisable();
                    }
                })
                .build());

        general.addEntry(eb.startBooleanToggle(Text.literal("Target Players"), config.targetPlayers)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.targetPlayers = val)
                .build());

        general.addEntry(eb.startBooleanToggle(Text.literal("Target Mobs"), config.targetMobs)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.targetMobs = val)
                .build());

        general.addEntry(eb.startBooleanToggle(Text.literal("Target Animals"), config.targetAnimals)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.targetAnimals = val)
                .build());

        general.addEntry(eb.startKeyCodeField(Text.literal("Open Menu Key"), InputUtil.fromKeyCode(config.openMenuKey, 0))
                .setDefaultValue(InputUtil.fromKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT, 0))
                .setKeySaveConsumer(key -> config.openMenuKey = key.getCode())
                .build());

        // 2. AIM ASSIST
        ConfigCategory aimAssist = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.aim_assist"));
        aimAssist.addEntry(eb.startBooleanToggle(Text.literal("Aim Assist"), config.aimAssistEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.aimAssistEnabled = val)
                .build());

        aimAssist.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.aimAssistKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.aimAssistKey = key.getCode())
                .build());

        aimAssist.addEntry(eb.startDoubleField(Text.literal("Range (0.1 - 8.0 Blocks)"), config.aimAssistRange)
                .setDefaultValue(4.5)
                .setMin(0.1)
                .setMax(8.0)
                .setSaveConsumer(val -> config.aimAssistRange = Math.max(0.1, Math.min(8.0, val)))
                .build());

        aimAssist.addEntry(eb.startIntSlider(Text.literal("FOV (Degrees)"), config.aimAssistFov, 1, 360)
                .setDefaultValue(180)
                .setSaveConsumer(val -> config.aimAssistFov = val)
                .build());

        aimAssist.addEntry(eb.startIntSlider(Text.literal("Speed (Degrees/Second)"), config.aimAssistSpeed, 600, 7200)
                .setDefaultValue(3600)
                .setSaveConsumer(val -> config.aimAssistSpeed = val)
                .build());

        aimAssist.addEntry(eb.startBooleanToggle(Text.literal("Speed Randomization"), config.aimAssistSpeedRandomization)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Rolls +/- [0..200] deg/s on each new target activation."))
                .setSaveConsumer(val -> config.aimAssistSpeedRandomization = val)
                .build());

        aimAssist.addEntry(eb.startEnumSelector(Text.literal("Mode"), ModConfig.AimMode.class, config.aimAssistMode)
                .setDefaultValue(ModConfig.AimMode.SMOOTH)
                .setSaveConsumer(val -> config.aimAssistMode = val)
                .build());

        aimAssist.addEntry(eb.startEnumSelector(Text.literal("Aim At"), ModConfig.AimPoint.class, config.aimAssistAimAt)
                .setDefaultValue(ModConfig.AimPoint.AUTO)
                .setSaveConsumer(val -> config.aimAssistAimAt = val)
                .build());

        // 3. ATTRIBUTESWAP
        ConfigCategory attrSwap = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.attribute_swap"));
        attrSwap.addEntry(eb.startBooleanToggle(Text.literal("AttributeSwap"), config.attributeSwapEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.attributeSwapEnabled = val)
                .build());

        attrSwap.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.attributeSwapKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.attributeSwapKey = key.getCode())
                .build());

        // 4. STUN SLAM
        ConfigCategory stunSlam = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.stun_slam"));
        stunSlam.addEntry(eb.startBooleanToggle(Text.literal("Stun Slam"), config.stunSlamEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.stunSlamEnabled = val)
                .build());

        stunSlam.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.stunSlamKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.stunSlamKey = key.getCode())
                .build());

        // 5. PEARL CATCH
        ConfigCategory pearlCatch = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.pearl_catch"));
        pearlCatch.addEntry(eb.startBooleanToggle(Text.literal("Pearl Catch"), config.pearlCatchEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.pearlCatchEnabled = val)
                .build());

        pearlCatch.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.pearlCatchKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.pearlCatchKey = key.getCode())
                .build());

        pearlCatch.addEntry(eb.startEnumSelector(Text.literal("Mode"), ModConfig.PearlCatchMode.class, config.pearlCatchMode)
                .setDefaultValue(ModConfig.PearlCatchMode.AUTOMATIC)
                .setSaveConsumer(val -> config.pearlCatchMode = val)
                .build());

        pearlCatch.addEntry(eb.startIntSlider(Text.literal("Delay (Ticks)"), config.pearlCatchDelay, 1, 3)
                .setDefaultValue(1)
                .setSaveConsumer(val -> config.pearlCatchDelay = val)
                .build());

        // 6. SHIELD DRAINING
        ConfigCategory shieldDraining = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.shield_draining"));
        shieldDraining.addEntry(eb.startBooleanToggle(Text.literal("Shield Draining"), config.shieldDrainingEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.shieldDrainingEnabled = val)
                .build());

        shieldDraining.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.shieldDrainingKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.shieldDrainingKey = key.getCode())
                .build());

        shieldDraining.addEntry(eb.startIntSlider(Text.literal("CPS"), config.shieldDrainingCps, 20, 250)
                .setDefaultValue(50)
                .setSaveConsumer(val -> config.shieldDrainingCps = val)
                .build());

        // 7. ALWAYS-ON SHIELD
        ConfigCategory alwaysOnShield = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.always_on_shield"));
        alwaysOnShield.addEntry(eb.startBooleanToggle(Text.literal("Always-On Shield"), config.alwaysOnShieldEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> {
                    config.alwaysOnShieldEnabled = val;
                    if (!val) {
                        QuackMaceHelperClient.ALWAYS_ON_SHIELD.onDisable();
                    }
                })
                .build());

        alwaysOnShield.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.alwaysOnShieldKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.alwaysOnShieldKey = key.getCode())
                .build());

        // 8. ELYTRA MACE
        ConfigCategory elytraMace = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.elytra_mace"));
        elytraMace.addEntry(eb.startBooleanToggle(Text.literal("Elytra Mace"), config.elytraMaceEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> {
                    config.elytraMaceEnabled = val;
                    if (!val) {
                        QuackMaceHelperClient.ELYTRA_MACE.onDisable();
                    }
                })
                .build());

        elytraMace.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.elytraMaceKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.elytraMaceKey = key.getCode())
                .build());

        elytraMace.addEntry(eb.startEnumSelector(Text.literal("Aim Type"), ModConfig.MaceAimType.class, config.elytraMaceAimType)
                .setDefaultValue(ModConfig.MaceAimType.SMOOTH)
                .setSaveConsumer(val -> config.elytraMaceAimType = val)
                .build());

        elytraMace.addEntry(eb.startBooleanToggle(Text.literal("AutoAim"), config.elytraMaceAutoAim)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.elytraMaceAutoAim = val)
                .build());

        elytraMace.addEntry(eb.startIntSlider(Text.literal("FOV (Degrees)"), config.elytraMaceFov, 60, 360)
                .setDefaultValue(180)
                .setSaveConsumer(val -> config.elytraMaceFov = val)
                .build());

        // 9. AUTO MACE
        ConfigCategory autoMace = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.auto_mace"));
        autoMace.addEntry(eb.startBooleanToggle(Text.literal("Auto Mace"), config.autoMaceEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(val -> {
                    config.autoMaceEnabled = val;
                    if (!val) {
                        QuackMaceHelperClient.AUTO_MACE.onDisable();
                    }
                })
                .build());

        autoMace.addEntry(eb.startKeyCodeField(Text.literal("Toggle Keybind"), InputUtil.fromKeyCode(config.autoMaceKey, 0))
                .setDefaultValue(InputUtil.UNKNOWN_KEY)
                .setKeySaveConsumer(key -> config.autoMaceKey = key.getCode())
                .build());

        autoMace.addEntry(eb.startBooleanToggle(Text.literal("Auto Aim"), config.autoMaceAutoAim)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.autoMaceAutoAim = val)
                .build());

        autoMace.addEntry(eb.startEnumSelector(Text.literal("Aim Type"), ModConfig.MaceAimType.class, config.autoMaceAimType)
                .setDefaultValue(ModConfig.MaceAimType.SMOOTH)
                .setSaveConsumer(val -> config.autoMaceAimType = val)
                .build());

        autoMace.addEntry(eb.startIntSlider(Text.literal("FOV (Degrees)"), config.autoMaceFov, 60, 360)
                .setDefaultValue(180)
                .setSaveConsumer(val -> config.autoMaceFov = val)
                .build());

        // 10. WIND TACKLE (Dead "Coming Soon" button at the very bottom)
        ConfigCategory windTackle = builder.getOrCreateCategory(Text.translatable("category.quackmacehelper.wind_tackle"));
        windTackle.addEntry(eb.startBooleanToggle(Text.literal("Wind Tackle (Coming Soon)"), false)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Coming soon - currently does nothing."))
                .setSaveConsumer(val -> config.windTackleComingSoon = false)
                .build());

        return builder.build();
    }
}
