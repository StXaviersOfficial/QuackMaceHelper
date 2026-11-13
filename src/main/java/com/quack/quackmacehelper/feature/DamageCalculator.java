package com.quack.quackmacehelper.feature;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * V5 best-mace calculator (AttributeSwap falling case, StunSlam, Shield
 * Draining, ElytraMace, AutoMace).
 *
 * Ranked per spec with the verified 1.21.11 numbers:
 *   - base mace damage 6
 *   - smash bonus: +4 HP/block for the first 3 blocks fallen,
 *     +2 HP/block for the next 5, +1 HP/block beyond
 *   - Density (I-V): +0.5 damage per block fallen per level
 *   - Breach (I-IV): -15% target armor effectiveness per level (ranked as
 *     a damage multiplier — it never coexists with Density on one mace)
 * The whole hotbar is evaluated in microseconds (9 slots, pure arithmetic).
 */
public final class DamageCalculator {

    private DamageCalculator() {}

    /** Verified smash bonus curve. */
    public static double smashBonus(double blocksFallen) {
        if (blocksFallen < 1.5) return 0; // smash requires >= 1.5 blocks
        double bonus = 0;
        bonus += Math.min(blocksFallen, 3.0) * 4.0;
        if (blocksFallen > 3.0) bonus += (Math.min(blocksFallen, 8.0) - 3.0) * 2.0;
        if (blocksFallen > 8.0) bonus += (blocksFallen - 8.0) * 1.0;
        return bonus;
    }

    /** Expected damage of one mace hit after falling blocksFallen blocks. */
    public static double scoreMace(ItemStack mace, double blocksFallen) {
        if (mace.getItem() != Items.MACE) return Double.NEGATIVE_INFINITY;
        double score = 6.0 + smashBonus(blocksFallen);
        int density = InventoryHelper.getEnchantmentLevel(mace, "density");
        int breach  = InventoryHelper.getEnchantmentLevel(mace, "breach");
        score += 0.5 * density * blocksFallen;
        score *= 1.0 + 0.15 * breach;
        return score;
    }

    /**
     * Hotbar slot of the highest-damage mace for the given fall distance,
     * or -1 when no mace is in the hotbar.
     */
    public static int bestMaceSlot(ClientPlayerEntity player, double blocksFallen) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            double score = scoreMace(s, blocksFallen);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Same as {@link #bestMaceSlot} but using the live fall distance. */
    public static int bestMaceSlot(ClientPlayerEntity player) {
        return bestMaceSlot(player, Math.max(player.fallDistance, 1.5));
    }
}
