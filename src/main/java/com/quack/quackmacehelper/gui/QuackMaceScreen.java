package com.quack.quackmacehelper.gui;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class QuackMaceScreen extends Screen {

    // Layout
    private static final int W = 340, H = 420;
    private static final int SIDE = 115;
    private int px, py;

    private int tab = 0;
    private boolean awaitingKey = false;
    private String awaitingLabel = "";
    private Consumer<Integer> awaitingCallback;

    private final String[] TABS = {
        "General", "Aim Assist", "Breach Swap", "Stun Slam",
        "Pearl Catch", "Shield Drain", "Elytra Swap",
        "Wind Tackle", "Always Shield"
    };

    public QuackMaceScreen() { super(Text.literal("QuackMaceHelper")); }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        // Sidebar tab buttons
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]),
                    b -> { tab = idx; rebuild(); })
                .dimensions(px + 4, py + 28 + i * 24, SIDE - 6, 20).build());
        }
        buildContent();
    }

    private void buildContent() {
        ModConfig cfg = ModConfig.get();
        int cx = px + SIDE + 6;
        int cy = py + 32;

        switch (tab) {
            case 0 -> buildGeneral(cx, cy, cfg);
            case 1 -> buildAimAssist(cx, cy, cfg);
            case 2 -> buildBreachSwap(cx, cy, cfg);
            case 3 -> buildStunSlam(cx, cy, cfg);
            case 4 -> buildPearlCatch(cx, cy, cfg);
            case 5 -> buildShieldDrain(cx, cy, cfg);
            case 6 -> buildElytraSwap(cx, cy, cfg);
            case 7 -> buildWindTackle(cx, cy, cfg);
            case 8 -> buildAlwaysShield(cx, cy, cfg);
        }
    }

    // ── General ──────────────────────────────────────────────────────────
    private void buildGeneral(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Target Players", cfg.targetPlayers, v -> { cfg.targetPlayers = v; save(); });
        addToggle(x, y + 24, "Target Mobs", cfg.targetMobs, v -> { cfg.targetMobs = v; save(); });
        addToggle(x, y + 48, "Target Animals", cfg.targetAnimals, v -> { cfg.targetAnimals = v; save(); });
        addKeybind(x, y + 80, "Open Menu Key", cfg.keybinds.openGui, v -> { cfg.keybinds.openGui = v; save(); });
    }

    // ── Aim Assist ───────────────────────────────────────────────────────
    private void buildAimAssist(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.aimAssist.enabled, v -> { cfg.aimAssist.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleAimAssist, v -> { cfg.keybinds.toggleAimAssist = v; save(); });
        addSliderInt(x, y + 52, "Range: " + (int)cfg.aimAssist.range + " blocks",
                (int)cfg.aimAssist.range, 1, 8, v -> { cfg.aimAssist.range = v; save(); rebuild(); });
        addSliderFloat(x, y + 76, "FOV: " + (int)cfg.aimAssist.fov + "°",
                cfg.aimAssist.fov, 60, 360, v -> { cfg.aimAssist.fov = v; save(); rebuild(); });
        addSliderFloat(x, y + 100, "Speed: " + (int)cfg.aimAssist.speed + "°/s",
                cfg.aimAssist.speed, 600, 7200, v -> { cfg.aimAssist.speed = v; save(); rebuild(); });
        addToggle(x, y + 126, "Speed Randomization", cfg.aimAssist.speedRandomization,
                v -> { cfg.aimAssist.speedRandomization = v; save(); });
    }

    // ── Breach Swap ──────────────────────────────────────────────────────
    private void buildBreachSwap(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.breachSwap.enabled, v -> { cfg.breachSwap.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleBreachSwap, v -> { cfg.keybinds.toggleBreachSwap = v; save(); });
        label(x, y + 56, "§7Hits with mace on sword/axe attack,");
        label(x, y + 68, "§7then switches back to sword.");
    }

    // ── Stun Slam ────────────────────────────────────────────────────────
    private void buildStunSlam(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.stunSlam.enabled, v -> { cfg.stunSlam.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleStunSlam, v -> { cfg.keybinds.toggleStunSlam = v; save(); });
        label(x, y + 56, "§7When opponent has shield up while");
        label(x, y + 68, "§7falling: sword → axe → mace in 2 ticks.");
        label(x, y + 84, "§cAuto-off when Shield Drain is on.");
    }

    // ── Pearl Catch ──────────────────────────────────────────────────────
    private void buildPearlCatch(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.pearlCatch.enabled, v -> { cfg.pearlCatch.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.togglePearlCatch, v -> { cfg.keybinds.togglePearlCatch = v; save(); });
        addSliderInt(x, y + 52, "Throw Delay: " + cfg.pearlCatch.delayTicks + " tick(s)",
                cfg.pearlCatch.delayTicks, 1, 3, v -> { cfg.pearlCatch.delayTicks = v; save(); rebuild(); });
        addSliderInt(x, y + 76, "Trigger Height: " + cfg.pearlCatch.triggerHeight + " blocks",
                cfg.pearlCatch.triggerHeight, 5, 20, v -> { cfg.pearlCatch.triggerHeight = v; save(); rebuild(); });
    }

    // ── Shield Drain ─────────────────────────────────────────────────────
    private void buildShieldDrain(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.shieldDraining.enabled, v -> { cfg.shieldDraining.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleShieldDraining, v -> { cfg.keybinds.toggleShieldDraining = v; save(); });
        addSliderInt(x, y + 52, "CPS: " + cfg.shieldDraining.cps,
                cfg.shieldDraining.cps, 5, 150, v -> { cfg.shieldDraining.cps = v; save(); rebuild(); });
        label(x, y + 82, "§cDisables Stun Slam while active.");
    }

    // ── Elytra Swap ──────────────────────────────────────────────────────
    private void buildElytraSwap(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.elytraSwapper.enabled, v -> { cfg.elytraSwapper.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleElytraSwapper, v -> { cfg.keybinds.toggleElytraSwapper = v; save(); });
        addSliderFloat(x, y + 52, "FOV: " + (int)cfg.elytraSwapper.fov + "°",
                cfg.elytraSwapper.fov, 60, 360, v -> { cfg.elytraSwapper.fov = v; save(); rebuild(); });
        addToggle(x, y + 78, "Auto Aim", cfg.elytraSwapper.autoAim, v -> { cfg.elytraSwapper.autoAim = v; save(); });
        label(x, y + 106, "§7Equips chestplate, hits with mace");
        label(x, y + 118, "§74-5 times while gliding.");
    }

    // ── Wind Charge Tackle ───────────────────────────────────────────────
    private void buildWindTackle(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.windChargeTackle.enabled, v -> { cfg.windChargeTackle.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleWindChargeTackle, v -> { cfg.keybinds.toggleWindChargeTackle = v; save(); });
        label(x, y + 56, "§7Throws 1 wind charge at players");
        label(x, y + 68, "§7falling/gliding toward you (10 block radius).");
    }

    // ── Always On Shield ─────────────────────────────────────────────────
    private void buildAlwaysShield(int x, int y, ModConfig cfg) {
        addToggle(x, y, "Enabled", cfg.alwaysOnShield.enabled, v -> { cfg.alwaysOnShield.enabled = v; save(); });
        addKeybind(x, y + 26, "Toggle Key", cfg.keybinds.toggleAlwaysOnShield, v -> { cfg.keybinds.toggleAlwaysOnShield = v; save(); });
        label(x, y + 56, "§7Equips shield (or totem) when a player");
        label(x, y + 68, "§7is falling/flying toward you.");
        label(x, y + 84, "§7If Wind Tackle is on, fires wind charge first.");
    }

    // ── Widget helpers ───────────────────────────────────────────────────

    private void addToggle(int x, int y, String name, boolean current, Consumer<Boolean> cb) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal(name + ": " + (current ? "§aON" : "§cOFF")),
                b -> { cb.accept(!current); rebuild(); })
            .dimensions(x, y, 190, 20).build());
    }

    private void addKeybind(int x, int y, String name, int keyCode, Consumer<Integer> cb) {
        String keyName = keyCode == -1 ? "§7[Unbound]"
                : "§e" + InputUtil.fromKeyCode(keyCode, 0).getLocalizedText().getString();
        // Set button
        addDrawableChild(ButtonWidget.builder(
                Text.literal(name + ": " + keyName),
                b -> {
                    awaitingKey = true;
                    awaitingLabel = name;
                    awaitingCallback = cb;
                    rebuild();
                })
            .dimensions(x, y, 155, 20).build());
        // Reset button
        addDrawableChild(ButtonWidget.builder(Text.literal("§cReset"),
                b -> { cb.accept(-1); rebuild(); })
            .dimensions(x + 158, y, 40, 20).build());
    }

    private void addSliderInt(int x, int y, String label, int current, int min, int max, Consumer<Integer> cb) {
        addDrawableChild(new SliderWidget(x, y, 198, 18, Text.literal(label),
                (double)(current - min) / (max - min)) {
            @Override protected void updateMessage() {
                int v = (int)Math.round(value * (max - min)) + min;
                setMessage(Text.literal(label.replaceAll("\\d+", String.valueOf(v))));
            }
            @Override protected void applyValue() {
                cb.accept((int)Math.round(value * (max - min)) + min);
            }
        });
    }

    private void addSliderFloat(int x, int y, String label, float current, float min, float max, Consumer<Float> cb) {
        addDrawableChild(new SliderWidget(x, y, 198, 18, Text.literal(label),
                (double)(current - min) / (max - min)) {
            @Override protected void updateMessage() {
                float v = (float)(value * (max - min)) + min;
                setMessage(Text.literal(label.replaceAll("\\d+", String.valueOf((int)v))));
            }
            @Override protected void applyValue() {
                cb.accept((float)(value * (max - min)) + min);
            }
        });
    }

    private final List<String[]> labels = new ArrayList<>();
    private void label(int x, int y, String text) {
        labels.add(new String[]{String.valueOf(x), String.valueOf(y), text});
    }

    // ── Key capture ──────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                awaitingKey = false;
                rebuild();
                return true;
            }
            awaitingCallback.accept(keyCode);
            awaitingKey = false;
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Render ───────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Main panel
        ctx.fill(px, py, px + W, py + H, 0xE8101010);
        // Sidebar
        ctx.fill(px, py, px + SIDE, py + H, 0xE8181818);
        // Title bar
        ctx.fill(px, py, px + W, py + 26, 0xFF1A1A2E);
        ctx.drawCenteredTextWithShadow(textRenderer, "§eQuackMaceHelper", px + W / 2, py + 8, 0xFFFFFF);

        // Active tab highlight
        ctx.fill(px + 2, py + 28 + tab * 24, px + SIDE - 2, py + 48 + tab * 24, 0x44AAAAFF);

        // "Press a key..." overlay
        if (awaitingKey) {
            ctx.fill(px + SIDE, py + 26, px + W, py + H, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§eSetting: " + awaitingLabel, px + SIDE + (W - SIDE) / 2, py + H / 2 - 10, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Press any key... (ESC to cancel)", px + SIDE + (W - SIDE) / 2, py + H / 2 + 4, 0xAAAAAA);
        }

        // Draw labels
        for (String[] lbl : labels) {
            ctx.drawTextWithShadow(textRenderer, lbl[2], Integer.parseInt(lbl[0]), Integer.parseInt(lbl[1]), 0xAAAAAA);
        }
        labels.clear();

        super.render(ctx, mx, my, delta);
    }

    @Override public boolean shouldPause() { return false; }

    private void save() { ModConfig.save(); }
}
