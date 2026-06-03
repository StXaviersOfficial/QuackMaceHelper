package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * ElytraSwapper: While gliding with elytra, if target enters hit range:
 * - Equip chestplate (from hotbar) → hit with mace 4-5 times in ~2 ticks
 * - If target has shield → auto stun slam (independent)
 * - AutoAim at max speed during sequence
 */
public class ElytraSwapperFeature {

    private static int phase = 0;
    private static int tickDelay = 0;
    private static LivingEntity target = null;
    private static int hitCount = 0;
    private static final int MAX_HITS = 5;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.elytraSwapper.enabled) { reset(); return; }
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        // Must be gliding with elytra
        if (!player.isFallFlying()) { reset(); return; }

        if (phase == 0) {
            LivingEntity t = findTarget(client, cfg);
            if (t == null) return;
            target = t;
            phase = 1;
            tickDelay = 0;
        }

        if (tickDelay > 0) { tickDelay--; return; }
        if (target == null || !target.isAlive()) { reset(); return; }

        // AutoAim at max speed toward target
        if (cfg.elytraSwapper.autoAim) {
            AimAssistFeature.smoothAimAt(client, target, 7200f, 0.05f, false, 0f);
        }

        switch (phase) {
            case 1 -> {
                // Equip chestplate from hotbar
                int chestSlot = findChestplateSlot(player);
                if (chestSlot == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, chestSlot);
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                phase = 2; tickDelay = 1;
            }
            case 2 -> {
                // Check shield — if shielding, do stun slam
                if (InventoryHelper.isHoldingShield(target)) {
                    StunSlamFeature.triggerIndependent(client, target);
                    reset();
                    return;
                }
                // Switch to mace and start hitting
                int mace = InventoryHelper.pickBestMaceSlot(player, player.fallDistance, target.getArmor());
                if (mace == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, mace);
                phase = 3; hitCount = 0; tickDelay = 0;
            }
            case 3 -> {
                // Hit rapidly — 4-5 times
                if (player.distanceTo(target) <= 3.5f && target.isAlive()) {
                    client.interactionManager.attackEntity(player, target);
                    hitCount++;
                }
                if (hitCount >= MAX_HITS || !target.isAlive()) {
                    reset();
                } else {
                    tickDelay = 0; // hit again next tick
                }
            }
        }
    }

    private static LivingEntity findTarget(MinecraftClient client, ModConfig cfg) {
        if (client.player == null) return null;
        // Use aim assist target if available and in FOV
        LivingEntity aaTarget = AimAssistFeature.getCurrentTarget();
        if (aaTarget != null && client.player.distanceTo(aaTarget) <= 3.5f) return aaTarget;
        // Otherwise find closest in FOV
        return TargetHelper.findTarget(client, 3.5);
    }

    private static int findChestplateSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            var stack = player.getInventory().getStack(i);
            var item = stack.getItem();
            if (item == Items.NETHERITE_CHESTPLATE || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.IRON_CHESTPLATE || item == Items.GOLDEN_CHESTPLATE
                    || item == Items.CHAINMAIL_CHESTPLATE || item == Items.LEATHER_CHESTPLATE) {
                return i;
            }
        }
        return -1;
    }

    private static void reset() { phase = 0; tickDelay = 0; target = null; hitCount = 0; }
}
