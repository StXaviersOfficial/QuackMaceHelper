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
    private static final int DISPLAY_TICKS = 50; // ~2.5 seconds at 20tps

    public static void register() {
        HudRenderCallback.EVENT.register(HudOverlay::onHudRender);
    }

    public static void showMessage(String text, boolean enabled) {
        String colored = enabled ? "§a" + text + " Enabled" : "§c" + text + " Disabled";
        // Remove existing message for same feature
        messages.removeIf(m -> m.feature.equals(text));
        messages.add(new HudMessage(text, colored, DISPLAY_TICKS));
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || messages.isEmpty()) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        // Position: above hotbar (~44px from bottom)
        int baseY = screenHeight - 59;

        Iterator<HudMessage> it = messages.iterator();
        int offset = 0;
        List<HudMessage> toRender = new ArrayList<>();
        while (it.hasNext()) {
            HudMessage msg = it.next();
            msg.ticksLeft--;
            if (msg.ticksLeft <= 0) {
                it.remove();
            } else {
                toRender.add(msg);
            }
        }

        for (int i = toRender.size() - 1; i >= 0; i--) {
            HudMessage msg = toRender.get(i);
            float alpha = msg.ticksLeft < 10 ? msg.ticksLeft / 10f : 1f;
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
        int ticksLeft;

        HudMessage(String feature, String text, int ticksLeft) {
            this.feature = feature;
            this.text = text;
            this.ticksLeft = ticksLeft;
        }
    }
}
