package com.quack.quackmacehelper.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Optional;

public class InventoryHelper {

    // ── Slot finders ────────────────────────────────────────────────────────

    /** Returns hotbar slot (0-8) of first sword, or -1 */
    public static int findSwordSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof SwordItem) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of first axe, or -1 */
    public static int findAxeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of mace with Breach enchantment, or -1 */
    public static int findBreachMaceSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMace(stack) && hasEnchantmentType(stack, "breach")) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of mace with Density enchantment, or -1 */
    public static int findDensityMaceSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMace(stack) && hasEnchantmentType(stack, "density")) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of any mace, or -1 */
    public static int findAnyMaceSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (isMace(player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of elytra chestplate item (held), or -1 */
    public static int findElytraInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof ElytraItem) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of wind charge, or -1 */
    public static int findWindChargeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            Item item = player.getInventory().getStack(i).getItem();
            if (item == Items.WIND_CHARGE) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of ender pearl, or -1 */
    public static int findEnderPearlSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    // ── Swap helpers ─────────────────────────────────────────────────────────

    /** Switches to hotbar slot instantly */
    public static void switchToSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot > 8) return;
        client.player.getInventory().selectedSlot = slot;
    }

    // ── Damage calculators ───────────────────────────────────────────────────

    /**
     * Calculates effective mace fall damage bonus for a given fall distance.
     * Breach reduces armor; Density adds flat damage per block fallen.
     * Returns expected damage for a rough comparison.
     */
    public static float estimateMaceDamage(ClientPlayerEntity player, int slot, float fallDistance, float targetArmor) {
        ItemStack stack = player.getInventory().getStack(slot);
        if (!isMace(stack)) return 0f;

        float base = 6f; // base mace damage
        float fallBonus = Math.max(0, fallDistance - 1.5f) * 3f;

        int densityLevel = getEnchantmentLevel(stack, "density");
        int breachLevel = getEnchantmentLevel(stack, "breach");

        float densityBonus = densityLevel * fallDistance * 1f;
        float breachArmorReduction = breachLevel * 0.15f; // reduces armor effectiveness

        float effectiveArmor = targetArmor * (1f - breachArmorReduction);
        float rawDamage = base + fallBonus + densityBonus;
        float armorReduction = effectiveArmor / (effectiveArmor + 2f); // simplified

        return rawDamage * (1f - armorReduction * 0.5f);
    }

    /**
     * Picks the better mace slot (breach vs density) based on fall distance.
     * On ground (fallDistance < 2), always prefer breach.
     */
    public static int pickBestMaceSlot(ClientPlayerEntity player, float fallDistance, float targetArmor) {
        int breachSlot = findBreachMaceSlot(player);
        int densitySlot = findDensityMaceSlot(player);

        if (breachSlot == -1 && densitySlot == -1) return findAnyMaceSlot(player);
        if (breachSlot == -1) return densitySlot;
        if (densitySlot == -1) return breachSlot;

        // On ground or low fall → always breach
        if (fallDistance < 3f) return breachSlot;

        float breachDmg = estimateMaceDamage(player, breachSlot, fallDistance, targetArmor);
        float densityDmg = estimateMaceDamage(player, densitySlot, fallDistance, targetArmor);
        return densityDmg > breachDmg ? densitySlot : breachSlot;
    }

    // ── Checks ───────────────────────────────────────────────────────────────

    public static boolean isMace(ItemStack stack) {
        return stack.getItem() == Items.MACE;
    }

    public static boolean isHoldingShield(net.minecraft.entity.LivingEntity entity) {
        return entity.getActiveItem().getItem() == Items.SHIELD && entity.isBlocking();
    }

    private static boolean hasEnchantmentType(ItemStack stack, String enchId) {
        return getEnchantmentLevel(stack, enchId) > 0;
    }

    private static int getEnchantmentLevel(ItemStack stack, String enchId) {
        var enchantments = EnchantmentHelper.getEnchantments(stack);
        for (var entry : enchantments.getEnchantments()) {
            Optional<RegistryKey<Enchantment>> key = entry.getKey();
            if (key.isPresent() && key.get().getValue().getPath().contains(enchId)) {
                return enchantments.getLevel(entry);
            }
        }
        return 0;
    }
}
