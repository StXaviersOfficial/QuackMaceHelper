package com.quack.quackmacehelper.gui;

import com.quack.quackmacehelper.config.ModConfig;
import com.quack.quackmacehelper.keybind.KeyBindings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * QuackMaceHelper V5 menu — exact top-to-bottom spec order:
 *   General (MASTER KILL SWITCH first), Aim Assist, Attribute Swap, Stun
 *   Slam, Pearl Catch, Shield Drain, Always Shield, Elytra Mace, Auto
 *   Mace, Wind Tackle (dead "coming soon" entry).
 */
public class QuackMaceScreen extends Screen {

    // Panel fills 90% of screen, capped at 380x460, min 300x300
    private int W, H, SIDE;
    private int px, py;

    private int tab = 0;
    private boolean awaitingKey = false;
    private String awaitingLabel = "";
    private Consumer<Integer> awaitingCallback;

    /** Screen to return to on close (ModMenu passes the Mods list). */
    private final Screen parent;

    /** Slider changes are saved when the screen closes, not every drag tick. */
    private boolean dirty = false;

    private final String[] TABS = {
        "General", "Aim Assist", "Attr Swap", "Stun Slam",
        "Pearl Catch", "Shield Drain", "Always Shield", "Elytra Mace",
        "Auto Mace", "Wind Tackle"
    };

    // Tab button height — shrinks if screen is small
    private int TAB_H;

    private static final String[] AIM_POINTS = {"Head", "Centre", "Feet", "Auto"};

    public QuackMaceScreen() { this(null); }
    public QuackMaceScreen(Screen parent) {
        super(Text.literal("QuackMaceHelper"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Fit to screen
        W    = Math.min(380, (int)(width  * 0.92));
        H    = Math.min(460, (int)(height * 0.92));
        SIDE = Math.min(110, (int)(W * 0.32));

        // Tab button height: fit all 10 tabs into the sidebar
        int sidebarUsable = H - 28; // 28px title bar
        TAB_H = Math.max(15, Math.min(22, (sidebarUsable - 4) / TABS.length));

        px = (width  - W) / 2;
        py = (height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        labels.clear();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]),
                    b -> { tab = idx; rebuild(); })
                .dimensions(px + 2, py + 26 + i * (TAB_H + 2), SIDE - 4, TAB_H).build());
        }
        buildContent();
    }

    private void buildContent() {
        ModConfig cfg = ModConfig.get();
        int cx = px + SIDE + 8;
        int cy = py + 30;
        int cw = W - SIDE - 12; // usable content width

        switch (tab) {
            case 0 -> buildGeneral(cx, cy, cw, cfg);
            case 1 -> buildAimAssist(cx, cy, cw, cfg);
            case 2 -> buildAttributeSwap(cx, cy, cw, cfg);
            case 3 -> buildStunSlam(cx, cy, cw, cfg);
            case 4 -> buildPearlCatch(cx, cy, cw, cfg);
            case 5 -> buildShieldDrain(cx, cy, cw, cfg);
            case 6 -> buildAlwaysShield(cx, cy, cw, cfg);
            case 7 -> buildElytraMace(cx, cy, cw, cfg);
            case 8 -> buildAutoMace(cx, cy, cw, cfg);
            case 9 -> buildWindTackle(cx, cy, cw, cfg);
        }
    }

    // ── General (master kill switch FIRST) ──────────────────────────────
    private void buildGeneral(int x, int y, int w, ModConfig cfg) {
        ButtonWidget kill = ButtonWidget.builder(
                Text.literal("MASTER KILL SWITCH: " + (cfg.masterEnabled ? "§aON" : "§cOFF")),
                b -> { cfg.masterEnabled = !cfg.masterEnabled; save(); rebuild(); })
            .dimensions(x, y, Math.min(w, 220), 20).build();
        addDrawableChild(kill);
        label(x, y + 22, "§7OFF = the mod does not exist. Only this menu");
        label(x, y + 32, "§7stays alive so it can be re-enabled.");
        addToggle(x, y + 52,  w, "Target Players", cfg.targetPlayers, v -> { cfg.targetPlayers = v; save(); });
        addToggle(x, y + 76,  w, "Target Mobs",    cfg.targetMobs,    v -> { cfg.targetMobs    = v; save(); });
        addToggle(x, y + 100, w, "Target Animals", cfg.targetAnimals, v -> { cfg.targetAnimals = v; save(); });
        addKeybind(x, y + 128, w, "Open Menu Key", cfg.keybinds.openGui, v -> { cfg.keybinds.openGui = v; save(); });
    }

    // ── Aim Assist ───────────────────────────────────────────────────────
    private void buildAimAssist(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.aimAssist.enabled, v -> { cfg.aimAssist.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAimAssist, v -> { cfg.keybinds.toggleAimAssist = v; save(); });
        addSliderRange(x, y + 50, w, "Range: " + String.format("%.1f", cfg.aimAssist.range) + " blocks",
                cfg.aimAssist.range, 0.1f, 8f, 0.1f, v -> { cfg.aimAssist.range = v; saveSlider(); });
        addSliderFloat(x, y + 72, w, "FOV: " + (int)cfg.aimAssist.fov + "°",
                cfg.aimAssist.fov, 1, 360, v -> { cfg.aimAssist.fov = v; saveSlider(); });
        addSliderFloat(x, y + 94, w, "Speed: " + (int)cfg.aimAssist.speed + "°/s",
                cfg.aimAssist.speed, 600, 7200, v -> { cfg.aimAssist.speed = v; saveSlider(); });
        addToggle(x, y + 118, w, "Speed Randomization", cfg.aimAssist.speedRandomization,
                v -> { cfg.aimAssist.speedRandomization = v; save(); });
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Mode: §e" + (cfg.aimAssist.aimMaxMode ? "Max (snap)" : "Smooth")),
                b -> { cfg.aimAssist.aimMaxMode = !cfg.aimAssist.aimMaxMode; save(); rebuild(); })
            .dimensions(x, y + 142, Math.min(w, 200), 20).build());
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Aim At: §e" + AIM_POINTS[cfg.aimAssist.aimPoint]),
                b -> { cfg.aimAssist.aimPoint = (cfg.aimAssist.aimPoint + 1) % 4; save(); rebuild(); })
            .dimensions(x, y + 166, Math.min(w, 200), 20).build());
        label(x, y + 192, "§7Randomization: ±0-200 rolled once per");
        label(x, y + 202, "§7activation. Max snaps; 7200°/s = Max.");
    }

    // ── Attribute Swap ───────────────────────────────────────────────────
    private void buildAttributeSwap(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.attributeSwap.enabled, v -> { cfg.attributeSwap.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAttributeSwap, v -> { cfg.keybinds.toggleAttributeSwap = v; save(); });
        label(x, y + 52, "§7Hit with sword / axe / spear (no Lunge) /");
        label(x, y + 62, "§7empty hand -> same-tick swap to the mace,");
        label(x, y + 72, "§7hit, swap back within 1-4 ticks. Falling 3+");
        label(x, y + 82, "§7blocks: best-damage mace is calculated.");
    }

    // ── Stun Slam ────────────────────────────────────────────────────────
    private void buildStunSlam(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.stunSlam.enabled, v -> { cfg.stunSlam.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleStunSlam, v -> { cfg.keybinds.toggleStunSlam = v; save(); });
        label(x, y + 52, "§7Watches the crosshair target's shield.");
        label(x, y + 62, "§7Falling 3+ blocks: 4-5 axe hits, then the");
        label(x, y + 72, "§7best mace 5-6 hits, swap back next tick.");
        label(x, y + 86, "§cFully suppressed while Shield Drain runs.");
    }

    // ── Pearl Catch ──────────────────────────────────────────────────────
    private void buildPearlCatch(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.pearlCatch.enabled, v -> { cfg.pearlCatch.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.togglePearlCatch, v -> { cfg.keybinds.togglePearlCatch = v; save(); });
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Mode: §e" + (cfg.pearlCatch.manualMode ? "Manual" : "Automatic")),
                b -> { cfg.pearlCatch.manualMode = !cfg.pearlCatch.manualMode; save(); rebuild(); })
            .dimensions(x, y + 48, Math.min(w, 200), 20).build());
        addSliderInt(x, y + 72, w, "Delay: " + cfg.pearlCatch.delayTicks + " tick(s)",
                cfg.pearlCatch.delayTicks, 1, 3, v -> { cfg.pearlCatch.delayTicks = v; saveSlider(); });
        if (!cfg.pearlCatch.manualMode) {
            addSliderInt(x, y + 94, w, "Trigger Height: " + cfg.pearlCatch.triggerHeight + " blocks",
                    cfg.pearlCatch.triggerHeight, 5, 20, v -> { cfg.pearlCatch.triggerHeight = v; saveSlider(); });
            label(x, y + 118, "§7Own pearls only. One throw, no retry.");
        } else {
            label(x, y + 96, "§7Own pearls only. One throw, no retry.");
        }
    }

    // ── Shield Drain ─────────────────────────────────────────────────────
    private void buildShieldDrain(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.shieldDraining.enabled, v -> { cfg.shieldDraining.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleShieldDraining, v -> { cfg.keybinds.toggleShieldDraining = v; save(); });
        addSliderInt(x, y + 50, w, "CPS: " + cfg.shieldDraining.cps,
                cfg.shieldDraining.cps, 20, 250, v -> { cfg.shieldDraining.cps = v; saveSlider(); });
        label(x, y + 76, "§7Falling 3+ above a shielded target in range:");
        label(x, y + 86, "§7auto-spams the best mace without any clicks.");
        label(x, y + 96, "§7Owns all attacks vs shielded targets.");
    }

    // ── Always Shield ────────────────────────────────────────────────────
    private void buildAlwaysShield(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.alwaysOnShield.enabled, v -> { cfg.alwaysOnShield.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAlwaysOnShield, v -> { cfg.keybinds.toggleAlwaysOnShield = v; save(); });
        addSliderFloat(x, y + 50, w, "Radius: " + (int)cfg.alwaysOnShield.radius + " blocks",
                cfg.alwaysOnShield.radius, 4, 16, v -> { cfg.alwaysOnShield.radius = v; saveSlider(); });
        label(x, y + 76, "§7Players falling/gliding 3+ above you get");
        label(x, y + 86, "§7snap-locked + shield held until they drop");
        label(x, y + 96, "§7to +2. While triggered, all other features");
        label(x, y + 106, "§7stand down. Shield: offhand or hotbar.");
    }

    // ── Elytra Mace ──────────────────────────────────────────────────────
    private void buildElytraMace(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.elytraMace.enabled, v -> { cfg.elytraMace.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleElytraMace, v -> { cfg.keybinds.toggleElytraMace = v; save(); });
        addSliderFloat(x, y + 50, w, "FOV: " + (int)cfg.elytraMace.fov + "°",
                cfg.elytraMace.fov, 60, 360, v -> { cfg.elytraMace.fov = v; saveSlider(); });
        addToggle(x, y + 72, w, "Auto Aim", cfg.elytraMace.autoAim, v -> { cfg.elytraMace.autoAim = v; save(); });
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Aim Type: §e" + (cfg.elytraMace.aimInstant ? "Instant" : "Smooth")),
                b -> { cfg.elytraMace.aimInstant = !cfg.elytraMace.aimInstant; save(); rebuild(); })
            .dimensions(x, y + 96, Math.min(w, 200), 20).build());
        label(x, y + 122, "§7Gliding + target below in FOV: elytra->");
        label(x, y + 132, "§7chestplate swap in tick 1, then shield? axe");
        label(x, y + 142, "§7burst : best-mace burst. Needs a chestplate");
        label(x, y + 152, "§7in the hotbar.");
    }

    // ── Auto Mace ────────────────────────────────────────────────────────
    private void buildAutoMace(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.autoMace.enabled, v -> { cfg.autoMace.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAutoMace, v -> { cfg.keybinds.toggleAutoMace = v; save(); });
        addSliderFloat(x, y + 50, w, "FOV: " + (int)cfg.autoMace.fov + "°",
                cfg.autoMace.fov, 60, 360, v -> { cfg.autoMace.fov = v; saveSlider(); });
        addToggle(x, y + 72, w, "Auto Aim", cfg.autoMace.autoAim, v -> { cfg.autoMace.autoAim = v; save(); });
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Aim Type: §e" + (cfg.autoMace.aimInstant ? "Instant" : "Smooth")),
                b -> { cfg.autoMace.aimInstant = !cfg.autoMace.aimInstant; save(); rebuild(); })
            .dimensions(x, y + 96, Math.min(w, 200), 20).build());
        label(x, y + 122, "§7Falling (not gliding) 3+ above a target:");
        label(x, y + 132, "§7aim, then ONE precise burst (shield? axe");
        label(x, y + 142, "§7then mace : mace). No chestplate swap.");
    }

    // ── Wind Tackle (dead stub — spec) ───────────────────────────────────
    private void buildWindTackle(int x, int y, int w, ModConfig cfg) {
        ButtonWidget stub = ButtonWidget.builder(
                Text.literal("WindTackle — coming soon"),
                b -> { }) // intentionally does nothing
            .dimensions(x, y, Math.min(w, 200), 20).build();
        stub.active = false; // dead button
        addDrawableChild(stub);
        label(x, y + 30, "§7Not implemented yet.");
    }

    // ── Widget helpers ───────────────────────────────────────────────────

    private void addToggle(int x, int y, int w, String name, boolean current, Consumer<Boolean> cb) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal(name + ": " + (current ? "§aON" : "§cOFF")),
                b -> { cb.accept(!current); rebuild(); })
            .dimensions(x, y, Math.min(w, 200), 20).build());
    }

    private void addKeybind(int x, int y, int w, String name, int keyCode, Consumer<Integer> cb) {
        String keyName = "§7" + KeyBindings.keyName(keyCode);
        if (keyCode != -1) keyName = "§e" + KeyBindings.keyName(keyCode);
        int btnW = Math.min(w - 44, 160);
        addDrawableChild(ButtonWidget.builder(
                Text.literal(name + ": " + keyName),
                b -> {
                    awaitingKey = true;
                    awaitingLabel = name;
                    awaitingCallback = cb;
                    rebuild();
                })
            .dimensions(x, y, btnW, 20).build());
        // Reset button
        addDrawableChild(ButtonWidget.builder(Text.literal("§cReset"),
                b -> { cb.accept(-1); rebuild(); })
            .dimensions(x + btnW + 4, y, 38, 20).build());
    }

    private void addSliderInt(int x, int y, int w, String label, int current, int min, int max, Consumer<Integer> cb) {
        int sw = Math.min(w, 200);
        addDrawableChild(new SliderWidget(x, y, sw, 18, Text.literal(label),
                (double)(current - min) / (max - min)) {
            @Override protected void updateMessage() {
                int v = (int)Math.round(value * (max - min)) + min;
                setMessage(Text.literal(label.replaceAll("\\d+", String.valueOf(v))));
            }
            @Override protected void applyValue() {
                cb.accept((int)Math.round(value * (max - min)) + min);
            }
        });
        // NOTE: no rebuild() here — rebuilding mid-drag destroys the slider
        // being dragged. updateMessage() already refreshes the number.
    }

    private void addSliderFloat(int x, int y, int w, String label, float current, float min, float max, Consumer<Float> cb) {
        int sw = Math.min(w, 200);
        addDrawableChild(new SliderWidget(x, y, sw, 18, Text.literal(label),
                (double)(current - min) / (max - min)) {
            @Override protected void updateMessage() {
                float v = (float)(value * (max - min)) + min;
                setMessage(Text.literal(label.replaceAll("\\d+", String.valueOf((int)v))));
            }
            @Override protected void applyValue() {
                cb.accept((float)(value * (max - min)) + min);
            }
        });
        // no rebuild() mid-drag — see addSliderInt
    }

    /** Float slider snapped to a step (e.g. 0.1 for the Aim Assist range). */
    private void addSliderRange(int x, int y, int w, String label, float current,
                                float min, float max, float step, Consumer<Float> cb) {
        int sw = Math.min(w, 200);
        int steps = Math.max(1, Math.round((max - min) / step));
        addDrawableChild(new SliderWidget(x, y, sw, 18, Text.literal(label),
                (Math.round(current / step) - Math.round(min / step)) / (double) steps) {
            @Override protected void updateMessage() {
                int s = (int)Math.round(value * steps) + Math.round(min / step);
                float v = s * step;
                setMessage(Text.literal(label.replaceAll("[0-9]+\\.[0-9]|\\d+", String.format("%.1f", v))));
            }
            @Override protected void applyValue() {
                int s = (int)Math.round(value * steps) + Math.round(min / step);
                cb.accept(s * step);
            }
        });
    }

    private final List<String[]> labels = new ArrayList<>();
    private void label(int x, int y, String text) {
        labels.add(new String[]{String.valueOf(x), String.valueOf(y), text});
    }

    // ── Key capture ──────────────────────────────────────────────────────

    /** 1.21.11: input events are now objects — keyPressed(KeyInput). */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (awaitingKey) {
            int keyCode = keyInput.key();
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
        return super.keyPressed(keyInput);
    }

    /**
     * While "Press any key..." is up, widget clicks are swallowed. A mouse
     * click instead BINDS that mouse button (encoded as 500+button).
     * 1.21.11: mouseClicked now takes a Click record.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (awaitingKey) {
            int button = click.button();
            if (button <= 7) {
                awaitingCallback.accept(com.quack.quackmacehelper.keybind.KeyBindings.MOUSE_BASE + button);
            }
            awaitingKey = false;
            rebuild();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    // ── Render ───────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Main panel — semi-transparent
        ctx.fill(px, py, px + W, py + H, 0xC0101010);
        // Sidebar background
        ctx.fill(px, py, px + SIDE, py + H, 0xC0181818);
        // Title bar
        ctx.fill(px, py, px + W, py + 24, 0xFF1A1A2E);
        ctx.drawCenteredTextWithShadow(textRenderer, "§eQuackMaceHelper §75.0.0", px + W / 2, py + 7, 0xFFFFFF);

        // Active tab highlight
        int tabY = py + 26 + tab * (TAB_H + 2);
        ctx.fill(px + 1, tabY - 1, px + SIDE - 1, tabY + TAB_H + 1, 0x55AAAAFF);

        // "Press a key..." overlay
        if (awaitingKey) {
            ctx.fill(px + SIDE, py + 24, px + W, py + H, 0xCC000000);
            int cx2 = px + SIDE + (W - SIDE) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§eSetting: " + awaitingLabel, cx2, py + H / 2 - 10, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Press any key... (ESC to cancel)", cx2, py + H / 2 + 4, 0xAAAAAA);
        }

        // Draw labels
        for (String[] lbl : labels) {
            ctx.drawTextWithShadow(textRenderer, lbl[2],
                    Integer.parseInt(lbl[0]), Integer.parseInt(lbl[1]), 0xAAAAAA);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override public boolean shouldPause() { return false; }

    /** Slider edits get flushed here, whenever the screen goes away. */
    @Override
    public void removed() {
        if (dirty) { ModConfig.save(); dirty = false; }
        super.removed();
    }

    /** ESC / close returns to the parent screen (ModMenu's Mods list). */
    @Override
    public void close() {
        if (dirty) { ModConfig.save(); dirty = false; }
        this.client.setScreen(parent);
    }

    private void save() { ModConfig.save(); }

    /** Slider drags fire applyValue many times a second — mark dirty. */
    private void saveSlider() { dirty = true; }
}
