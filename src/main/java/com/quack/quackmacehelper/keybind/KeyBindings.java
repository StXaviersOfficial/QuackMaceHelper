package com.quack.quackmacehelper.keybind;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind detection — reads directly from ModConfig GLFW key codes.
 * No Fabric KeyBinding registration needed. Works on any server.
 *
 * wasPressed() returns true on the RISING EDGE only (key just went down).
 * Call once per tick.
 *
 * Key codes: plain GLFW keyboard codes, OR 500+button for mouse buttons
 * (GLFW keyboard codes stop at ~348, so 500-507 = mouse buttons 0-7 is
 * an unambiguous encoding — lets users bind mouse side buttons etc.)
 *
 * V4.1.1: while ANY screen is open, keybinds are suppressed — typing "n"
 * in chat used to toggle Aim Assist, and the menu key fired from inside
 * other GUIs.
 */
public class KeyBindings {

    public static final int MOUSE_BASE = 500;

    // Previous key states for edge detection
    private static final boolean[] prevState = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] prevMouse = new boolean[8];

    public static void register() {
        // Nothing to register — we poll GLFW directly
    }

    /** True when a screen is open — all toggles are suppressed then. */
    private static boolean screenOpen() {
        return MinecraftClient.getInstance().currentScreen != null;
    }

    /**
     * Returns true if the given key code was just pressed this tick.
     * -1 = unbound, always returns false.
     */
    public static boolean wasPressed(int keyCode) {
        if (keyCode < 0) return false;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (keyCode >= MOUSE_BASE) {
            int button = keyCode - MOUSE_BASE;
            if (button > 7) return false;
            if (screenOpen()) { prevMouse[button] = false; return false; }
            long window = mc.getWindow().getHandle();
            boolean current = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            boolean rising = current && !prevMouse[button];
            prevMouse[button] = current;
            return rising;
        }

        if (keyCode > GLFW.GLFW_KEY_LAST) return false;
        if (screenOpen()) { prevState[keyCode] = false; return false; }
        long window = mc.getWindow().getHandle();
        boolean current = GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
        boolean rising = current && !prevState[keyCode];
        prevState[keyCode] = current;
        return rising;
    }

    /** Human-readable name for a bound code (keyboard or mouse). */
    public static String keyName(int keyCode) {
        if (keyCode == -1) return "[Unbound]";
        if (keyCode >= MOUSE_BASE) {
            int b = keyCode - MOUSE_BASE;
            return switch (b) {
                case 0 -> "Mouse Left";
                case 1 -> "Mouse Right";
                case 2 -> "Mouse Middle";
                default -> "Mouse " + (b + 1);
            };
        }
        return org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0) != null
                ? GLFW.glfwGetKeyName(keyCode, 0).toUpperCase()
                : "Key " + keyCode;
    }

    // Convenience accessors using config values
    public static boolean openGui()                { return wasPressed(ModConfig.get().keybinds.openGui); }
    public static boolean toggleAimAssist()        { return wasPressed(ModConfig.get().keybinds.toggleAimAssist); }
    public static boolean toggleBreachSwap()       { return wasPressed(ModConfig.get().keybinds.toggleBreachSwap); }
    public static boolean toggleStunSlam()         { return wasPressed(ModConfig.get().keybinds.toggleStunSlam); }
    public static boolean togglePearlCatch()       { return wasPressed(ModConfig.get().keybinds.togglePearlCatch); }
    public static boolean toggleShieldDraining()   { return wasPressed(ModConfig.get().keybinds.toggleShieldDraining); }
    public static boolean toggleElytraSwapper()    { return wasPressed(ModConfig.get().keybinds.toggleElytraSwapper); }
    public static boolean toggleWindChargeTackle() { return wasPressed(ModConfig.get().keybinds.toggleWindChargeTackle); }
    public static boolean toggleAlwaysOnShield()   { return wasPressed(ModConfig.get().keybinds.toggleAlwaysOnShield); }
}
