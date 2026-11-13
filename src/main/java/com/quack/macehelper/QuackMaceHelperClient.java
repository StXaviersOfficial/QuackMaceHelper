package com.quack.macehelper;

import com.quack.macehelper.config.ConfigScreenFactory;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.feature.AimAssistFeature;
import com.quack.macehelper.feature.AlwaysOnShieldFeature;
import com.quack.macehelper.feature.AttributeSwapFeature;
import com.quack.macehelper.feature.AutoMaceFeature;
import com.quack.macehelper.feature.ElytraMaceFeature;
import com.quack.macehelper.feature.PearlCatchFeature;
import com.quack.macehelper.feature.ShieldDrainingFeature;
import com.quack.macehelper.feature.StunSlamFeature;
import com.quack.macehelper.util.RotationController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class QuackMaceHelperClient implements ClientModInitializer {

    public static final AimAssistFeature AIM_ASSIST = new AimAssistFeature();
    public static final AttributeSwapFeature ATTRIBUTE_SWAP = new AttributeSwapFeature();
    public static final StunSlamFeature STUN_SLAM = new StunSlamFeature();
    public static final PearlCatchFeature PEARL_CATCH = new PearlCatchFeature();
    public static final ShieldDrainingFeature SHIELD_DRAINING = new ShieldDrainingFeature();
    public static final AlwaysOnShieldFeature ALWAYS_ON_SHIELD = new AlwaysOnShieldFeature();
    public static final ElytraMaceFeature ELYTRA_MACE = new ElytraMaceFeature();
    public static final AutoMaceFeature AUTO_MACE = new AutoMaceFeature();

    private static KeyBinding openMenuKeyBinding;
    private static KeyBinding aimAssistKeyBinding;
    private static KeyBinding attributeSwapKeyBinding;
    private static KeyBinding stunSlamKeyBinding;
    private static KeyBinding pearlCatchKeyBinding;
    private static KeyBinding shieldDrainingKeyBinding;
    private static KeyBinding alwaysOnShieldKeyBinding;
    private static KeyBinding elytraMaceKeyBinding;
    private static KeyBinding autoMaceKeyBinding;

    private static boolean SuppressAttackHookRecursion = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // Register all keybindings (all feature keybinds default to UNBOUND except open-menu key)
        openMenuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.quackmacehelper"
        ));

        aimAssistKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.aim_assist",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        attributeSwapKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.attribute_swap",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        stunSlamKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.stun_slam",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        pearlCatchKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.pearl_catch",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        shieldDrainingKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.shield_draining",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        alwaysOnShieldKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.always_on_shield",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        elytraMaceKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.elytra_mace",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        autoMaceKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper.auto_mace",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "key.categories.quackmacehelper"
        ));

        syncKeybindingsFromConfig();

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
    }

    public static void syncKeybindingsFromConfig() {
        ModConfig cfg = ModConfig.get();
        if (openMenuKeyBinding != null) {
            openMenuKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.openMenuKey, 0));
            KeyBinding.updateKeysByCode();
        }
        if (aimAssistKeyBinding != null) {
            aimAssistKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.aimAssistKey, 0));
        }
        if (attributeSwapKeyBinding != null) {
            attributeSwapKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.attributeSwapKey, 0));
        }
        if (stunSlamKeyBinding != null) {
            stunSlamKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.stunSlamKey, 0));
        }
        if (pearlCatchKeyBinding != null) {
            pearlCatchKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.pearlCatchKey, 0));
        }
        if (shieldDrainingKeyBinding != null) {
            shieldDrainingKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.shieldDrainingKey, 0));
        }
        if (alwaysOnShieldKeyBinding != null) {
            alwaysOnShieldKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.alwaysOnShieldKey, 0));
        }
        if (elytraMaceKeyBinding != null) {
            elytraMaceKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.elytraMaceKey, 0));
        }
        if (autoMaceKeyBinding != null) {
            autoMaceKeyBinding.setBoundKey(InputUtil.fromKeyCode(cfg.autoMaceKey, 0));
        }
        KeyBinding.updateKeysByCode();
    }

    private void onEndClientTick(MinecraftClient client) {
        // Rule 3: Open Menu key ALWAYS works even when Master Kill Switch is off
        while (openMenuKeyBinding.wasPressed()) {
            client.setScreen(ConfigScreenFactory.createConfigScreen(client.currentScreen));
        }

        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch) {
            // Drain any pressed events on feature keybinds without acting so they don't queue up
            drainFeatureKeybinds();
            return;
        }

        handleFeatureKeybinds(cfg);

        if (client.player == null || client.world == null) {
            return;
        }

        // Tick features in strict collision-priority order:
        // 1) Always-On Shield (highest priority defense lock; suspends all others when triggered)
        ALWAYS_ON_SHIELD.tick(client);
        if (ALWAYS_ON_SHIELD.isTriggered()) {
            return;
        }

        // 2) Pearl Catch (instant snap throw; yields aim immediately back to combat aim)
        PEARL_CATCH.tick(client);

        // 3) Gliding vs. Falling Mace engagements
        ELYTRA_MACE.tick(client);
        AUTO_MACE.tick(client);

        // 4) Shield Draining (high-CPS mace spam against raised shields from 3+ blocks above)
        SHIELD_DRAINING.tick(client);

        // 5) Pending AttributeSwap & Stun Slam tick chains
        ATTRIBUTE_SWAP.tick(client);
        STUN_SLAM.tick(client);

        // 6) Global Aim Assist (runs only when no combat/defense feature owns aim)
        AIM_ASSIST.tick(client);
    }

    private void drainFeatureKeybinds() {
        while (aimAssistKeyBinding.wasPressed()) {}
        while (attributeSwapKeyBinding.wasPressed()) {}
        while (stunSlamKeyBinding.wasPressed()) {}
        while (pearlCatchKeyBinding.wasPressed()) {}
        while (shieldDrainingKeyBinding.wasPressed()) {}
        while (alwaysOnShieldKeyBinding.wasPressed()) {}
        while (elytraMaceKeyBinding.wasPressed()) {}
        while (autoMaceKeyBinding.wasPressed()) {}
    }

    private void handleFeatureKeybinds(ModConfig cfg) {
        boolean changed = false;
        while (aimAssistKeyBinding.wasPressed()) {
            cfg.aimAssistEnabled = !cfg.aimAssistEnabled;
            if (!cfg.aimAssistEnabled) AIM_ASSIST.disengage();
            changed = true;
        }
        while (attributeSwapKeyBinding.wasPressed()) {
            cfg.attributeSwapEnabled = !cfg.attributeSwapEnabled;
            if (!cfg.attributeSwapEnabled) ATTRIBUTE_SWAP.reset();
            changed = true;
        }
        while (stunSlamKeyBinding.wasPressed()) {
            cfg.stunSlamEnabled = !cfg.stunSlamEnabled;
            if (!cfg.stunSlamEnabled) STUN_SLAM.reset();
            changed = true;
        }
        while (pearlCatchKeyBinding.wasPressed()) {
            cfg.pearlCatchEnabled = !cfg.pearlCatchEnabled;
            if (!cfg.pearlCatchEnabled) PEARL_CATCH.reset();
            changed = true;
        }
        while (shieldDrainingKeyBinding.wasPressed()) {
            cfg.shieldDrainingEnabled = !cfg.shieldDrainingEnabled;
            if (!cfg.shieldDrainingEnabled) SHIELD_DRAINING.reset();
            changed = true;
        }
        while (alwaysOnShieldKeyBinding.wasPressed()) {
            cfg.alwaysOnShieldEnabled = !cfg.alwaysOnShieldEnabled;
            if (!cfg.alwaysOnShieldEnabled) ALWAYS_ON_SHIELD.onDisable();
            changed = true;
        }
        while (elytraMaceKeyBinding.wasPressed()) {
            cfg.elytraMaceEnabled = !cfg.elytraMaceEnabled;
            if (!cfg.elytraMaceEnabled) ELYTRA_MACE.onDisable();
            changed = true;
        }
        while (autoMaceKeyBinding.wasPressed()) {
            cfg.autoMaceEnabled = !cfg.autoMaceEnabled;
            if (!cfg.autoMaceEnabled) AUTO_MACE.onDisable();
            changed = true;
        }
        if (changed) {
            ModConfig.save();
        }
    }

    /**
     * Hooked from ClientPlayerInteractionManagerMixin at HEAD of attackEntity(PlayerEntity, Entity).
     */
    public static void onPlayerAttackEntity(PlayerEntity player, Entity target) {
        if (SuppressAttackHookRecursion) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || player != client.player) return;
        if (!ModConfig.get().masterSwitch) return;
        if (ALWAYS_ON_SHIELD.isTriggered()) return;

        SuppressAttackHookRecursion = true;
        try {
            ClientPlayerEntity clientPlayer = client.player;
            // Collision Rule 7: Attribute Swap owns the same-tick swap chain; Stun Slam queues behind it
            boolean attrTriggered = ATTRIBUTE_SWAP.onPlayerAttack(client, clientPlayer, target);
            STUN_SLAM.onPlayerAttack(client, clientPlayer, target, attrTriggered);
        } finally {
            SuppressAttackHookRecursion = false;
        }
    }

    public static void onMasterDisable() {
        AIM_ASSIST.disengage();
        ATTRIBUTE_SWAP.reset();
        STUN_SLAM.reset();
        PEARL_CATCH.reset();
        SHIELD_DRAINING.reset();
        ALWAYS_ON_SHIELD.onDisable();
        ELYTRA_MACE.onDisable();
        AUTO_MACE.onDisable();
        RotationController.resetAll();
    }
}
