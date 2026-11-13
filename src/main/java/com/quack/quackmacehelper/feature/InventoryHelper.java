package com.quack.quackmacehelper.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Optional;

public class InventoryHelper {

    // ── Slot finders ────────────────────────────────────────────────────────

    /** Returns hotbar slot (0-8) of first sword, or -1.
     *  1.21.11: SwordItem class no longer exists (swords are plain Items) —
     *  detection goes through the vanilla #minecraft:swords item tag. */
    public static int findSwordSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) return i;
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
            if (isMace(stack) && getEnchantmentLevel(stack, "breach") > 0) return i;
        }
        return -1;
    }

    /** Returns hotbar slot of mace with Density enchantment, or -1 */
    public static int findDensityMaceSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMace(stack) && getEnchantmentLevel(stack, "density") > 0) return i;
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

    /** Returns hotbar slot of elytra (plain Item in 1.21.11 — no ElytraItem class). */
    public static int findElytraInHotbar(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() == Items.ELYTRA) return i;
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

    /** Returns hotbar slot of a chestplate (best tier first), or -1. */
    public static int findChestplateHotbarSlot(ClientPlayerEntity player) {
        Item[] tiers = {
            Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
            Items.COPPER_CHESTPLATE, Items.GOLDEN_CHESTPLATE,
            Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE
        };
        for (Item tier : tiers)
            for (int i = 0; i < 9; i++)
                if (player.getInventory().getStack(i).getItem() == tier) return i;
        return -1;
    }

    // ── Trigger-item checks (V5 AttributeSwap domain) ──────────────────────

    /** 1.21.11 spears ("Mounts of Mayhem"): item id path ends with "_spear". */
    public static boolean isSpear(ItemStack stack) {
        return !stack.isEmpty() && net.minecraft.registry.Registries.ITEM
                .getId(stack.getItem()).getPath().endsWith("_spear");
    }

    /** True when the stack is a spear WITHOUT Lunge (the AttributeSwap trigger). */
    public static boolean isSpearWithoutLunge(ItemStack stack) {
        return isSpear(stack) && getEnchantmentLevel(stack, "lunge") == 0;
    }

    // ── Swap helpers ─────────────────────────────────────────────────────────

    /**
     * Switches to a hotbar slot AND tells the server immediately.
     *
     * CRITICAL (V4.1.1): writing only the client-side field is not enough.
     * Vanilla sends {@link UpdateSelectedSlotC2SPacket} from
     * ClientPlayerEntity.tick(), which runs BEFORE the mod's END_CLIENT_TICK
     * hooks. Any feature that switches and attacks/uses in the same tick used
     * to send the action packet BEFORE the slot packet, so the server resolved
     * the action with the PREVIOUSLY held item. Sending the packet explicitly
     * here fixes all of them.
     */
    public static void switchToSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot > 8 || client.player == null) return;
        if (client.player.getInventory().getSelectedSlot() == slot) return;
        client.player.getInventory().setSelectedSlot(slot);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    // ── Checks ───────────────────────────────────────────────────────────────

    public static boolean isMace(ItemStack stack) {
        return stack.getItem() == Items.MACE;
    }

    /**
     * True when the entity is ACTIVELY BLOCKING with a raised shield
     * (getActiveItem is the shield AND isBlocking). This is the semantic the
     * attack features want: the axe hit's shield-disable effect only applies
     * against a raised shield.
     */
    public static boolean isHoldingShield(net.minecraft.entity.LivingEntity entity) {
        return entity.isBlocking() && entity.getActiveItem().getItem() == Items.SHIELD;
    }

    public static int getEnchantmentLevel(ItemStack stack, String enchId) {
        var enchantments = EnchantmentHelper.getEnchantments(stack);
        int best = 0;
        for (var entry : enchantments.getEnchantments()) {
            Optional<RegistryKey<Enchantment>> key = entry.getKey();
            if (key.isPresent() && key.get().getValue().getPath().contains(enchId)) {
                best = Math.max(best, enchantments.getLevel(entry));
            }
        }
        return best;
    }
}
