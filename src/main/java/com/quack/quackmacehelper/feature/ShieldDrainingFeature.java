package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

/**
 * ShieldDraining: While falling and target has shield up within 3 blocks,
 * hits with mace at set CPS. Stops ~0.5 blocks from ground, switches back to sword.
 */
public class ShieldDrainingFeature {

    private static long lastHitMs = 0;
    private static int prevSlot = -1;

    // Approximate: stop when fall distance indicates we're near ground
    private static final float STOP_FALL_DIST = 0.5f;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.shieldDraining.enabled) { prevSlot = -1; return; }
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        // Only while falling
        if (player.isOnGround()) {
            if (prevSlot != -1) {
                InventoryHelper.switchToSlot(client, prevSlot);
                prevSlot = -1;
            }
            return;
        }

        // Stop near ground — velocity approaching 0 from below or tiny fall distance remaining
        double vy = player.getVelocity().y;
        if (vy > -0.08 && player.fallDistance > 0.5f) {
            if (prevSlot != -1) {
                InventoryHelper.switchToSlot(client, prevSlot);
                prevSlot = -1;
            }
            return;
        }

        LivingEntity target = AimAssistFeature.getCurrentTarget();
        if (target == null) return;
        if (!InventoryHelper.isHoldingShield(target)) return;
        if (player.distanceTo(target) > 3.0f) return;

        // Find sword slot to remember
        if (prevSlot == -1) {
            int sword = InventoryHelper.findSwordSlot(player);
            prevSlot = sword != -1 ? sword : player.getInventory().selectedSlot;
        }

        long nowMs = System.currentTimeMillis();
        long intervalMs = 1000L / cfg.shieldDraining.cps;
        long variance = (long)(Math.random() * 10 - 5);
        if (nowMs - lastHitMs < intervalMs + variance) return;

        int mace = InventoryHelper.findAnyMaceSlot(player);
        if (mace == -1) return;

        InventoryHelper.switchToSlot(client, mace);
        client.interactionManager.attackEntity(player, target);
        lastHitMs = nowMs;
    }
}
