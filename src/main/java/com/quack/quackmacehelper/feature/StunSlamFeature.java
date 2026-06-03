package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

/**
 * StunSlam: When opponent's shield is detected (before it breaks) and player is falling,
 * hits with current weapon → axe → mace in 2 ticks.
 */
public class StunSlamFeature {

    private static int phase = 0;
    private static LivingEntity target = null;
    private static int tickDelay = 0;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        // StunSlam disabled when ShieldDraining is on
        if (!cfg.stunSlam.enabled || cfg.shieldDraining.enabled) { reset(); return; }
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        if (phase == 0) {
            LivingEntity t = AimAssistFeature.getCurrentTarget();
            if (t == null) return;
            if (!player.isOnGround() && player.fallDistance >= 1.5f
                    && InventoryHelper.isHoldingShield(t)
                    && player.distanceTo(t) <= 3.5f) {
                target = t;
                phase = 1;
                tickDelay = 0;
            }
            return;
        }

        if (tickDelay > 0) { tickDelay--; return; }
        if (target == null || !target.isAlive() || player.isOnGround()) { reset(); return; }

        switch (phase) {
            case 1 -> {
                // Hit with current weapon
                client.interactionManager.attackEntity(player, target);
                // Switch to axe
                int axe = InventoryHelper.findAxeSlot(player);
                if (axe != -1) InventoryHelper.switchToSlot(client, axe);
                phase = 2; tickDelay = 0;
            }
            case 2 -> {
                // Hit with axe
                client.interactionManager.attackEntity(player, target);
                // Switch to mace
                int mace = InventoryHelper.pickBestMaceSlot(player, player.fallDistance, target.getArmor());
                if (mace != -1) InventoryHelper.switchToSlot(client, mace);
                phase = 3; tickDelay = 1;
            }
            case 3 -> {
                // Hit with mace
                client.interactionManager.attackEntity(player, target);
                reset();
            }
        }
    }

    public static void triggerIndependent(MinecraftClient client, LivingEntity t) {
        if (client.player == null || t == null) return;
        target = t;
        phase = 1;
        tickDelay = 0;
    }

    private static void reset() { phase = 0; target = null; tickDelay = 0; }
}
