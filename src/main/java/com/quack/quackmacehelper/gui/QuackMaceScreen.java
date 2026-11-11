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

public class QuackMaceScreen extends Screen {

    // Panel fills 90% of screen, capped at 380×460, min 300×300
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
        "General", "Aim Assist", "Breach Swap", "Stun Slam",
        "Pearl Catch", "Shield Drain", "Elytra Swap",
        "Wind Tackle", "Always Shield"
    };

    // Tab button height — shrinks if screen is small
    private int TAB_H;

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

        // Tab button height: fit all 9 tabs into sidebar with title bar (24px) + 4px padding each side
        int sidebarUsable = H - 28; // 28px title bar
        TAB_H = Math.max(16, Math.min(22, (sidebarUsable - 4) / TABS.length));

        px = (width  - W) / 2;
        py = (height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        // Labels live until the NEXT rebuild — clearing them in render()
        // (the old bug) made every hint line vanish after one frame.
        labels.clear();
        // Sidebar tab buttons — stacked tightly to fit all 9
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
            case 2 -> buildBreachSwap(cx, cy, cw, cfg);
            case 3 -> buildStunSlam(cx, cy, cw, cfg);
            case 4 -> buildPearlCatch(cx, cy, cw, cfg);
            case 5 -> buildShieldDrain(cx, cy, cw, cfg);
            case 6 -> buildElytraSwap(cx, cy, cw, cfg);
            case 7 -> buildWindTackle(cx, cy, cw, cfg);
            case 8 -> buildAlwaysShield(cx, cy, cw, cfg);
        }
    }

    // ── General ──────────────────────────────────────────────────────────
    private void buildGeneral(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,      w, "Target Players", cfg.targetPlayers, v -> { cfg.targetPlayers = v; save(); });
        addToggle(x, y + 24, w, "Target Mobs",    cfg.targetMobs,    v -> { cfg.targetMobs    = v; save(); });
        addToggle(x, y + 48, w, "Target Animals", cfg.targetAnimals, v -> { cfg.targetAnimals = v; save(); });
        addKeybind(x, y + 76, w, "Open Menu Key", cfg.keybinds.openGui, v -> { cfg.keybinds.openGui = v; save(); });
    }

    // ── Aim Assist ───────────────────────────────────────────────────────
    private static final String[] AIM_POINTS = {"Head", "Centre", "Feet"};
    private void buildAimAssist(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.aimAssist.enabled, v -> { cfg.aimAssist.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAimAssist, v -> { cfg.keybinds.toggleAimAssist = v; save(); });
        addSliderInt(x, y + 50, w, "Range: " + (int)cfg.aimAssist.range + " blocks",
                (int)cfg.aimAssist.range, 1, 8, v -> { cfg.aimAssist.range = v; saveSlider(); });
        addSliderFloat(x, y + 72, w, "FOV: " + (int)cfg.aimAssist.fov + "°",
                cfg.aimAssist.fov, 60, 360, v -> { cfg.aimAssist.fov = v; saveSlider(); });
        addSliderFloat(x, y + 94, w, "Speed: " + (int)cfg.aimAssist.speed + "°/s",
                cfg.aimAssist.speed, 600, 7200, v -> { cfg.aimAssist.speed = v; saveSlider(); });
        addToggle(x, y + 118, w, "Speed Randomization", cfg.aimAssist.speedRandomization,
                v -> { cfg.aimAssist.speedRandomization = v; save(); });
        addSliderInt(x, y + 142, w, "Randomness: ±" + (int)cfg.aimAssist.speedRandomRange,
                (int)cfg.aimAssist.speedRandomRange, 0, 500, v -> { cfg.aimAssist.speedRandomRange = v; saveSlider(); });
        // Smooth / Max mode button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Mode: §e" + (cfg.aimAssist.aimMaxMode ? "Max (snap)" : "Smooth")),
                b -> { cfg.aimAssist.aimMaxMode = !cfg.aimAssist.aimMaxMode; save(); rebuild(); })
            .dimensions(x, y + 166, Math.min(w, 200), 20).build());
        // Aim point cycle button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Aim At: §e" + AIM_POINTS[cfg.aimAssist.aimPoint]),
                b -> { cfg.aimAssist.aimPoint = (cfg.aimAssist.aimPoint + 1) % 3; save(); rebuild(); })
            .dimensions(x, y + 190, Math.min(w, 200), 20).build());
        label(x, y + 216, "§7Max snaps instantly; 7200°/s Smooth = Max.");
    }

    // ── Breach Swap ──────────────────────────────────────────────────────
    private void buildBreachSwap(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.breachSwap.enabled, v -> { cfg.breachSwap.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleBreachSwap, v -> { cfg.keybinds.toggleBreachSwap = v; save(); });
        label(x, y + 52, "§7On sword/axe hit: switch to mace,");
        label(x, y + 62, "§7hit again, switch back to sword.");
    }

    // ── Stun Slam ────────────────────────────────────────────────────────
    private void buildStunSlam(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.stunSlam.enabled, v -> { cfg.stunSlam.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleStunSlam, v -> { cfg.keybinds.toggleStunSlam = v; save(); });
        label(x, y + 52, "§7While falling + target has shield:");
        label(x, y + 62, "§7axe hit, then mace 4-5 times.");
        label(x, y + 76, "§cAuto-suppressed while Shield Drain is on.");
    }

    // ── Pearl Catch ──────────────────────────────────────────────────────
    private void buildPearlCatch(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.pearlCatch.enabled, v -> { cfg.pearlCatch.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.togglePearlCatch, v -> { cfg.keybinds.togglePearlCatch = v; save(); });
        // Mode toggle
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Mode: §e" + (cfg.pearlCatch.manualMode ? "Manual" : "Automatic")),
                b -> { cfg.pearlCatch.manualMode = !cfg.pearlCatch.manualMode; save(); rebuild(); })
            .dimensions(x, y + 48, Math.min(w, 200), 20).build());
        if (cfg.pearlCatch.manualMode) {
            // Manual: show delay slider
            addSliderInt(x, y + 72, w, "Delay: " + cfg.pearlCatch.delayTicks + " tick(s)",
                    cfg.pearlCatch.delayTicks, 1, 3, v -> { cfg.pearlCatch.delayTicks = v; saveSlider(); });
            label(x, y + 96, "§7Throws wind charge after delay.");
            label(x, y + 106, "§7No auto-aim — aim manually.");
        } else {
            // Auto: show trigger height
            addSliderInt(x, y + 72, w, "Trigger Height: " + cfg.pearlCatch.triggerHeight + " blocks",
                    cfg.pearlCatch.triggerHeight, 5, 20, v -> { cfg.pearlCatch.triggerHeight = v; saveSlider(); });
            label(x, y + 96, "§7Auto-intercepts pearl above height.");
        }
    }

    // ── Shield Drain ─────────────────────────────────────────────────────
    private void buildShieldDrain(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.shieldDraining.enabled, v -> { cfg.shieldDraining.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleShieldDraining, v -> { cfg.keybinds.toggleShieldDraining = v; save(); });
        addSliderInt(x, y + 50, w, "CPS: " + cfg.shieldDraining.cps,
                cfg.shieldDraining.cps, 5, 300, v -> { cfg.shieldDraining.cps = v; saveSlider(); });
        label(x, y + 76, "§7Modifier — no standalone action.");
        label(x, y + 88, "§7Activates inside Elytra Swap only, when a");
        label(x, y + 99, "§7shield is detected: axe is suppressed and");
        label(x, y + 110, "§7mace CPS is overridden (±10 randomized),");
        label(x, y + 121, "§7running non-stop for at least 1.5s.");
    }

    // ── Elytra Swap ──────────────────────────────────────────────────────
    private void buildElytraSwap(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.elytraSwapper.enabled, v -> { cfg.elytraSwapper.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleElytraSwapper, v -> { cfg.keybinds.toggleElytraSwapper = v; save(); });
        addSliderFloat(x, y + 50, w, "FOV: " + (int)cfg.elytraSwapper.fov + "°",
                cfg.elytraSwapper.fov, 60, 360, v -> { cfg.elytraSwapper.fov = v; saveSlider(); });
        addToggle(x, y + 72, w, "Auto Aim", cfg.elytraSwapper.autoAim, v -> { cfg.elytraSwapper.autoAim = v; save(); });
        label(x, y + 98, "§7Equips chestplate, spams mace 3-4 CPS");
        label(x, y + 109, "§7until you reach the target's level.");
        label(x, y + 123, "§7Shield up + Drain on: no axe, drain CPS.");
    }

    // ── Wind Charge Tackle ───────────────────────────────────────────────
    private void buildWindTackle(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.windChargeTackle.enabled, v -> { cfg.windChargeTackle.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleWindChargeTackle, v -> { cfg.keybinds.toggleWindChargeTackle = v; save(); });
        label(x, y + 52, "§71 wind charge at players falling/");
        label(x, y + 62, "§7gliding toward you (10 block radius).");
    }

    // ── Always On Shield ─────────────────────────────────────────────────
    private void buildAlwaysShield(int x, int y, int w, ModConfig cfg) {
        addToggle(x, y,       w, "Enabled", cfg.alwaysOnShield.enabled, v -> { cfg.alwaysOnShield.enabled = v; save(); });
        addKeybind(x, y + 24, w, "Toggle Key", cfg.keybinds.toggleAlwaysOnShield, v -> { cfg.keybinds.toggleAlwaysOnShield = v; save(); });
        label(x, y + 52, "§7Equips shield (or totem) when a player");
        label(x, y + 62, "§7is falling/flying toward you.");
        label(x, y + 76, "§7Wind Tackle fires first if both enabled.");
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
        // being dragged, which used to make sliders jump one step per drag.
        // updateMessage() already refreshes the number shown on the slider.
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

    /**
     * While "Press any key..." is up, widget clicks are swallowed (the old
     * overlay was purely visual — buttons under it still fired). A mouse
     * click instead BINDS that mouse button (encoded as 500+button).
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (awaitingKey) {
            if (button <= 7) {
                awaitingCallback.accept(com.quack.quackmacehelper.keybind.KeyBindings.MOUSE_BASE + button);
            }
            awaitingKey = false;
            rebuild();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Render ───────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Main panel — semi-transparent so buttons never sit on a solid
        // black box (0xC0 instead of the old solid 0xE8).
        ctx.fill(px, py, px + W, py + H, 0xC0101010);
        // Sidebar background
        ctx.fill(px, py, px + SIDE, py + H, 0xC0181818);
        // Title bar
        ctx.fill(px, py, px + W, py + 24, 0xFF1A1A2E);
        ctx.drawCenteredTextWithShadow(textRenderer, "§eQuackMaceHelper", px + W / 2, py + 7, 0xFFFFFF);

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

        // Draw labels — they persist between frames now (cleared only on
        // rebuild), so hint text no longer disappears after one frame.
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

    /** Slider drags fire applyValue many times a second — saving on every
     *  tick hammered the disk with synchronous writes. Mark dirty instead. */
    private void saveSlider() { dirty = true; }
}
