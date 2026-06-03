package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * AlwaysOnShield: When a threat is detected within 10 blocks (falling/gliding toward user),
 * equips shield (or totem if no shield) and holds it until threat lands.
 * Doesn't interfere if user manually switches.
 */
public class AlwaysOnShieldFeature {

    private static LivingEntity activeTarget = null;
    private static int prevSlot = -1;
    private static boolean active = false;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.alwaysOnShield.enabled) { deactivate(client); return; }
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        // Find threat
        LivingEntity threat = findThreat(client, cfg.alwaysOnShield.radius);

        if (threat == null) {
            if (active) deactivate(client);
            return;
        }

        activeTarget = threat;

        // If threat has landed, deactivate
        if (threat.isOnGround()) {
            deactivate(client);
            return;
        }

        if (!active) activate(client, player);

        // Keep AimAssist toward threat
        AimAssistFeature.forceTarget(threat);
    }

    public static void activateFor(LivingEntity target) {
        activeTarget = target;
    }

    private static void activate(MinecraftClient client, ClientPlayerEntity player) {
        // Find shield in hotbar
        int shieldSlot = findShieldSlot(player);
        int totemSlot  = findTotemSlot(player);

        if (shieldSlot != -1) {
            prevSlot = player.getInventory().selectedSlot;
            InventoryHelper.switchToSlot(client, shieldSlot);
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            active = true;
        } else if (totemSlot != -1) {
            prevSlot = player.getInventory().selectedSlot;
            InventoryHelper.switchToSlot(client, totemSlot);
            active = true;
        }
    }

    private static void deactivate(MinecraftClient client) {
        if (active && prevSlot != -1 && client.player != null) {
            // Only switch back if player hasn't manually changed slot
            InventoryHelper.switchToSlot(client, prevSlot);
        }
        active = false;
        prevSlot = -1;
        activeTarget = null;
    }

    private static LivingEntity findThreat(MinecraftClient client, float radius) {
        LivingEntity t = activeTarget != null && activeTarget.isAlive()
                ? activeTarget
                : TargetHelper.findPlayerTarget(client, radius);
        if (t == null) return null;
        boolean falling = !t.isOnGround() && t.getVelocity().y < -0.1;
        boolean gliding = t instanceof net.minecraft.entity.player.PlayerEntity pe && pe.isFallFlying();
        if (!falling && !gliding) return null;
        if (!TargetHelper.isMovingToward(client, t)) return null;
        return t;
    }

    private static int findShieldSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Items.SHIELD) return i;
        }
        return -1;
    }

    private static int findTotemSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) return i;
        }
        return -1;
    }
}
