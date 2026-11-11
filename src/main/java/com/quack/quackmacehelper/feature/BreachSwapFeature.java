package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;

import java.util.Random;

/**
 * BreachSwap — Stage 4 polish.
 *
 * IN AIR:   swap to density mace → random aim → hit → swap back after 0–1 tick.
 * ON GROUND, breach mace available: swap to breach mace → random aim → hit → swap back.
 * ON GROUND, no breach, target has shield: axe hit to disable shield.
 *
 * Anti-cheat polish:
 *   - Aim via RandomAim.randomFull before each hit — never a fixed point.
 *   - Swap-back delayed 0–1 tick randomly so it doesn't look like an instant
 *     mechanical toggle every single time.
 */
public class BreachSwapFeature {

    private static final Random RNG = new Random();

    private static boolean attacking          = false;
    private static int     switchBackCountdown = 0;
    private static int     prevSlot            = -1;
    private static int     axeShieldCooldown   = 0;

    /** True while a swap-hit is winding down — AlwaysOnShield uses this so
     *  the mod's own swap-back isn't mistaken for a manual slot change. */
    public static boolean isSwapping() { return switchBackCountdown > 0 || attacking; }

    public static void onAttack(MinecraftClient client, LivingEntity target) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.breachSwap.enabled) return;
        if (attacking) return;
        // Never cascade off our own automated sequences.
        if (ElytraSwapperFeature.sequenceActive) return;
        if (ShieldDrainingFeature.isActive()) return;
        if (StunSlamFeature.isRunning()) return;
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;
        boolean holdingSword = player.getMainHandStack().getItem() instanceof SwordItem;
        boolean holdingAxe   = player.getMainHandStack().getItem() instanceof AxeItem;
        if (!holdingSword && !holdingAxe) return;

        prevSlot = player.getInventory().selectedSlot;
        boolean inAir = !player.isOnGround();

        if (inAir) {
            int mace = InventoryHelper.findDensityMaceSlot(player);
            if (mace == -1) mace = InventoryHelper.findAnyMaceSlot(player);
            if (mace == -1) return;
            doSwapHit(client, player, target, mace);

        } else {
            int breachMace = InventoryHelper.findBreachMaceSlot(player);
            if (breachMace != -1) {
                doSwapHit(client, player, target, breachMace);
            } else {
                if (!InventoryHelper.isHoldingShield(target)) return;
                if (axeShieldCooldown > 0) return;

                int axe = holdingAxe ? prevSlot : InventoryHelper.findAxeSlot(player);
                if (axe == -1) return;
                doSwapHit(client, player, target, axe);
                axeShieldCooldown = 100;
            }
        }
    }

    private static void doSwapHit(MinecraftClient client, ClientPlayerEntity player,
                                   LivingEntity target, int slot) {
        attacking = true;
        try {
            InventoryHelper.switchToSlot(client, slot);
            // Random aim before the hit
            AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
            client.interactionManager.attackEntity(player, target);
        } finally {
            attacking = false;
        }
        // 0–1 tick random delay before switching back
        switchBackCountdown = 1 + RNG.nextInt(2);
    }

    public static void tick(MinecraftClient client) {
        if (axeShieldCooldown > 0) axeShieldCooldown--;

        if (switchBackCountdown <= 0 || prevSlot == -1) return;
        switchBackCountdown--;
        if (switchBackCountdown == 0) {
            ClientPlayerEntity player = client.player;
            if (player == null) { prevSlot = -1; return; }
            int sword = InventoryHelper.findSwordSlot(player);
            InventoryHelper.switchToSlot(client, sword != -1 ? sword : prevSlot);
            prevSlot = -1;
        }
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() {
        attacking = false;
        switchBackCountdown = 0;
        prevSlot = -1;
        axeShieldCooldown = 0;
    }
}
