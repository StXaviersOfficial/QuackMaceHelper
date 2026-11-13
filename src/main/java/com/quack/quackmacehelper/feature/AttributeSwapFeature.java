package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.registry.tag.ItemTags;

import java.util.Random;

/**
 * AttributeSwap — V5 (replaces BreachSwap).
 *
 * TRIGGER ITEMS: sword (any material), axe, spear WITHOUT Lunge, or EMPTY
 * HAND — nothing else (a spear WITH Lunge never triggers; the Lunge jab owns
 * that hit; bows/crossbows/tridents never trigger).
 *
 * NORMAL CASE (player grounded / jumping / less than 3 blocks above the
 * target, or target HIGHER than the player): the SAME TICK the triggering
 * hit lands -> swap to the BREACH (I-IV) mace -> real attack the SAME tick
 * -> swap back to the original item within 1-4 randomized ticks.
 *
 * FALLING CASE (player 3+ blocks ABOVE the target, R3 = body heights):
 * DamageCalculator picks the most damaging hotbar mace for the current fall
 * (milliseconds), then the same same-tick swap -> hit -> 1-4 tick swap back.
 *
 * COLLISION: queues behind nothing, but every other sequence (ElytraMace,
 * AutoMace, StunSlam, ShieldDraining, AlwaysOnShield triggered) suppresses
 * it entirely; AttributeSwap owns the same-tick swap chain when it fires.
 */
public class AttributeSwapFeature {

    private static final Random RNG = new Random();

    private static boolean attacking           = false;
    private static int     switchBackCountdown = 0;
    private static int     prevSlot            = -1;

    /** True while a swap-hit is winding down — other features use this so
     *  the mod's own swap-back isn't mistaken for a manual slot change. */
    public static boolean isSwapping() { return switchBackCountdown > 0 || attacking; }

    public static void onAttack(MinecraftClient client, LivingEntity target) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.attributeSwap.enabled) return;
        if (attacking) return;
        // Never cascade off our own or other features' sequences.
        if (ElytraMaceFeature.sequenceActive) return;
        if (AutoMaceFeature.isRunning()) return;
        if (ShieldDrainingFeature.isTargetOwned(target)) return;
        if (StunSlamFeature.isRunning()) return;
        if (AlwaysOnShieldFeature.isTriggered()) return;
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        // ── Trigger-item check (V5 domain) ────────────────────────────────
        var held = player.getMainHandStack();
        boolean holdingSword = held.isIn(ItemTags.SWORDS);
        boolean holdingAxe   = held.getItem() instanceof AxeItem;
        boolean holdingSpearNoLunge = InventoryHelper.isSpearWithoutLunge(held);
        boolean emptyHand    = held.isEmpty();
        if (!holdingSword && !holdingAxe && !holdingSpearNoLunge && !emptyHand) return;

        // ── Pick the mace for this hit ────────────────────────────────────
        boolean falling3Above = TargetHelper.is3Above(player, target);
        int maceSlot;
        if (falling3Above) {
            // Falling case: best-damage mace for the current fall (R3 heights
            // decide the case; the damage math uses the fall distance).
            maceSlot = DamageCalculator.bestMaceSlot(player,
                    Math.max(player.fallDistance, 1.5));
            if (maceSlot == -1) maceSlot = InventoryHelper.findBreachMaceSlot(player);
        } else {
            // Normal case (includes target-higher): plain breach mace.
            maceSlot = InventoryHelper.findBreachMaceSlot(player);
            if (maceSlot == -1) maceSlot = InventoryHelper.findAnyMaceSlot(player);
        }
        if (maceSlot == -1) return; // no qualifying mace in hotbar (R4)

        prevSlot = player.getInventory().getSelectedSlot();
        doSwapHit(client, player, target, maceSlot);
    }

    private static void doSwapHit(MinecraftClient client, ClientPlayerEntity player,
                                   LivingEntity target, int slot) {
        attacking = true;
        try {
            InventoryHelper.switchToSlot(client, slot);
            // Random point on the target before the hit (anti fixed-pattern)
            AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
            client.interactionManager.attackEntity(player, target);
        } finally {
            attacking = false;
        }
        // Randomized 1-4 tick delay before switching back (V5 spec)
        switchBackCountdown = 1 + RNG.nextInt(4);
    }

    public static void tick(MinecraftClient client) {
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
    }
}
