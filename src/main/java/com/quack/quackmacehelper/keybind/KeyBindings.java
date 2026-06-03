package com.quack.quackmacehelper.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static final String CATEGORY = "key.category.quackmacehelper";

    public static KeyBinding openGui;
    public static KeyBinding toggleAimAssist;
    public static KeyBinding toggleBreachSwap;
    public static KeyBinding toggleStunSlam;
    public static KeyBinding togglePearlCatch;
    public static KeyBinding toggleShieldDraining;
    public static KeyBinding toggleElytraSwapper;
    public static KeyBinding toggleWindChargeTackle;
    public static KeyBinding toggleAlwaysOnShield;

    public static void register() {
        openGui = reg("open_gui", GLFW.GLFW_KEY_UNKNOWN);
        toggleAimAssist = reg("toggle_aimassist", GLFW.GLFW_KEY_N);
        toggleBreachSwap = reg("toggle_breachswap", GLFW.GLFW_KEY_UNKNOWN);
        toggleStunSlam = reg("toggle_stunslam", GLFW.GLFW_KEY_UNKNOWN);
        togglePearlCatch = reg("toggle_pearlcatch", GLFW.GLFW_KEY_UNKNOWN);
        toggleShieldDraining = reg("toggle_shielddraining", GLFW.GLFW_KEY_UNKNOWN);
        toggleElytraSwapper = reg("toggle_elytraswapper", GLFW.GLFW_KEY_UNKNOWN);
        toggleWindChargeTackle = reg("toggle_windchargetackle", GLFW.GLFW_KEY_UNKNOWN);
        toggleAlwaysOnShield = reg("toggle_alwaysonshield", GLFW.GLFW_KEY_UNKNOWN);
    }

    private static KeyBinding reg(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quackmacehelper." + name,
                InputUtil.Type.KEYSYM,
                defaultKey,
                CATEGORY
        ));
    }
}
