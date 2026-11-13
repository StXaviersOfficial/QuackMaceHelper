package com.quack.quackmacehelper.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HudOverlay {

    private static final List<HudMessage> messages = new ArrayList<>();
    /** Time-based: ~2.5 seconds. The old tick-based counter was decremented
     *  every render FRAME, so it faded in ~1s at 50fps (instantly at high fps). */
    private static final long DISPLAY_MS = 2500L;

    public static void register() {
        HudRenderCallback.EVENT.register(HudOverlay::onHudRender);
    }

    public static void showMessage(String text, boolean enabled) {
        String colored = enabled ? "§a" + text + " Enabled" : "§c" + text + " Disabled";
        // Remove existing message for same feature
        messages.removeIf(m -> m.feature.equals(text));
        messages.add(new HudMessage(text, colored, DISPLAY_MS));
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || messages.isEmpty()) return;
        // Master kill switch: no HUD either (R1 — mod fully inert).
        if (!com.quack.quackmacehelper.config.ModConfig.get().masterEnabled) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        // Position: above hotbar (~44px from bottom)
        int baseY = screenHeight - 59;

        Iterator<HudMessage> it = messages.iterator();
        int offset = 0;
        List<HudMessage> toRender = new ArrayList<>();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            HudMessage msg = it.next();
            if (now >= msg.expiresAt) {
                it.remove();
            } else {
                toRender.add(msg);
            }
        }

        for (int i = toRender.size() - 1; i >= 0; i--) {
            HudMessage msg = toRender.get(i);
            long left = msg.expiresAt - now;
            float alpha = left < 500 ? left / 500f : 1f;
            int alphaInt = (int)(alpha * 255) << 24;
            int textWidth = client.textRenderer.getWidth(msg.text);
            int x = (screenWidth - textWidth) / 2;
            int y = baseY - offset;
            context.drawTextWithShadow(client.textRenderer, msg.text, x, y, 0xFFFFFF | alphaInt);
            offset += 12;
        }
    }

    private static class HudMessage {
        String feature;
        String text;
        long expiresAt;

        HudMessage(String feature, String text, long durationMs) {
            this.feature = feature;
            this.text = text;
            this.expiresAt = System.currentTimeMillis() + durationMs;
        }
    }
}
