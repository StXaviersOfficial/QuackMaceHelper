package com.quack.macehelper.util;

import com.quack.macehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Optional;

public final class PlayerActionHelper {
    private PlayerActionHelper() {}

    /**
     * Switches hotbar slot via genuine PlayerInventory selection and immediately syncs
     * UpdateSelectedSlotC2SPacket so same-tick attacks resolve with the new slot on the server.
     */
    public static void selectHotbarSlot(MinecraftClient client, int slot) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (slot < 0 || slot > 8) return;
        if (client.player.getInventory().getSelectedSlot() != slot) {
            client.player.getInventory().setSelectedSlot(slot);
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    /**
     * Performs a genuine client attack through ClientPlayerInteractionManager and swings main hand.
     */
    public static void performRealAttack(MinecraftClient client, Entity target) {
        if (client.player == null || client.interactionManager == null || target == null) return;
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Performs a genuine right-click item use (e.g., equipping chestplate/elytra or throwing wind charge).
     */
    public static void performRealUseItem(MinecraftClient client, Hand hand) {
        if (client.player == null || client.interactionManager == null) return;
        client.interactionManager.interactItem(client.player, hand);
        client.player.swingHand(hand);
    }

    /**
     * Checks whether the stack is a sword (1.21.11 has no SwordItem class; uses ItemTags.SWORDS).
     */
    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
    }

    /**
     * Checks whether the stack is an axe.
     */
    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof AxeItem;
    }

    /**
     * Checks whether the stack is a 1.21.11 spear without the Lunge enchantment.
     */
    public static boolean isSpearWithoutLunge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String path = id.getPath();
        boolean isSpear = path.equals("spear") || path.endsWith("_spear");
        if (!isSpear) return false;
        return getEnchantmentLevel(stack, "lunge") <= 0;
    }

    /**
     * Checks whether the held stack qualifies as an AttributeSwap trigger weapon:
     * SWORD, AXE, SPEAR without Lunge, or EMPTY HAND.
     */
    public static boolean isAttributeSwapTriggerItem(ItemStack stack) {
        if (stack.isEmpty()) return true;
        return isSword(stack) || isAxe(stack) || isSpearWithoutLunge(stack);
    }

    /**
     * Checks whether an ItemStack is a chestplate (and not an Elytra).
     */
    public static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.isOf(Items.ELYTRA)) return false;
        if (stack.isIn(ItemTags.CHEST_ARMOR)) return true;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    /**
     * Returns the enchantment level on a stack by matching the RegistryKey path substring
     * ("breach", "density", "lunge", "wind_burst").
     */
    public static int getEnchantmentLevel(ItemStack stack, String enchantName) {
        if (stack.isEmpty()) return 0;
        ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(stack);
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            Optional<RegistryKey<Enchantment>> keyOpt = entry.getKey();
            if (keyOpt.isPresent()) {
                String path = keyOpt.get().getValue().getPath();
                if (path.contains(enchantName)) {
                    return enchants.getLevel(entry);
                }
            }
        }
        return 0;
    }

    /**
     * Finds the first hotbar slot (0..8) containing an axe, or -1 if none.
     */
    public static int findAxeInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (isAxe(player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first hotbar slot (0..8) containing a mace with Breach (I-IV), or -1 if none.
     */
    public static int findBreachMaceInHotbar(ClientPlayerEntity player) {
        int bestSlot = -1;
        int bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.MACE)) {
                int breach = getEnchantmentLevel(stack, "breach");
                if (breach > bestLevel) {
                    bestLevel = breach;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    /**
     * Finds any mace in hotbar (0..8), or -1 if none.
     */
    public static int findAnyMaceInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the hotbar slot (0..8) with the mace that deals the MOST effective damage
     * against the target for the current relative height / fall distance.
     */
    public static int findBestDamageMaceInHotbar(ClientPlayerEntity attacker, Entity target) {
        double heightDiff = getBodyHeightDifference(attacker, target);
        double blocksFallen = Math.max(attacker.fallDistance, Math.max(0.0, heightDiff));

        double targetArmor = 0.0;
        double targetToughness = 0.0;
        if (target instanceof LivingEntity living) {
            targetArmor = living.getArmor();
            targetToughness = living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        }

        int bestSlot = -1;
        double bestEffectiveDamage = -1.0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = attacker.getInventory().getStack(i);
            if (!stack.isOf(Items.MACE)) continue;

            double rawDamage = 6.0; // Base mace damage
            if (blocksFallen >= 1.5) {
                double first3 = Math.min(3.0, blocksFallen);
                double next5 = Math.max(0.0, Math.min(5.0, blocksFallen - 3.0));
                double beyond = Math.max(0.0, blocksFallen - 8.0);
                double smashBonus = (first3 * 4.0) + (next5 * 2.0) + (beyond * 1.0);

                int densityLevel = getEnchantmentLevel(stack, "density");
                double densityBonus = densityLevel * 0.5 * blocksFallen;

                rawDamage += smashBonus + densityBonus;
            }

            int breachLevel = getEnchantmentLevel(stack, "breach");
            // Vanilla armor damage reduction formula with Breach (-15% armor effectiveness per level)
            double baseArmorFraction = Math.min(20.0, Math.max(targetArmor / 5.0, targetArmor - (4.0 * rawDamage) / (targetToughness + 8.0))) / 25.0;
            double effectiveArmorFraction = Math.max(0.0, baseArmorFraction - (0.15 * breachLevel));
            double effectiveDamage = rawDamage * (1.0 - effectiveArmorFraction);

            if (effectiveDamage > bestEffectiveDamage) {
                bestEffectiveDamage = effectiveDamage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Finds a wind charge in hotbar (0..8), or -1 if none.
     */
    public static int findWindChargeInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.WIND_CHARGE)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds a shield in hotbar (0..8), or -1 if none.
     */
    public static int findShieldInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.SHIELD)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if the player has a shield in offhand or hotbar.
     */
    public static boolean hasShieldInHotbarOrOffhand(ClientPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.SHIELD)) return true;
        return findShieldInHotbar(player) != -1;
    }

    /**
     * Finds a chestplate in hotbar (0..8), or -1 if none.
     */
    public static int findChestplateInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (isChestplate(player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds an elytra in hotbar (0..8), or -1 if none.
     */
    public static int findElytraInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.ELYTRA)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks whether the entity currently has its shield actively raised.
     * Verified 1.21.11 landmine: entity.isBlocking() && entity.getActiveItem().getItem() == Items.SHIELD.
     */
    public static boolean isShieldRaised(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return living.isBlocking() && living.getActiveItem().getItem() == Items.SHIELD;
    }

    /**
     * Player-position height difference (attacker body Y minus target body Y).
     * Never measures from the ground.
     */
    public static double getBodyHeightDifference(Entity attacker, Entity target) {
        return attacker.getY() - target.getY();
    }

    /**
     * Player-position eye-to-eye or eye-to-target distance.
     */
    public static double getEyeDistance(Entity attacker, Entity target) {
        return attacker.getEyePos().distanceTo(target.getEyePos());
    }

    /**
     * Checks if the entity matches the General Target toggles (Players / Mobs / Animals).
     */
    public static boolean isValidTargetEntity(MinecraftClient client, Entity entity) {
        if (entity == null || client.player == null || entity == client.player) return false;
        if (!entity.isAlive() || entity.isSpectator()) return false;
        if (!(entity instanceof LivingEntity)) return false;

        ModConfig cfg = ModConfig.get();
        if (entity instanceof PlayerEntity) {
            return cfg.targetPlayers;
        }
        if (entity instanceof Monster) {
            return cfg.targetMobs;
        }
        if (entity instanceof PassiveEntity || entity instanceof WaterCreatureEntity || entity instanceof AmbientEntity) {
            return cfg.targetAnimals;
        }
        return false;
    }
}
