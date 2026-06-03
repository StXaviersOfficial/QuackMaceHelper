package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;

/**
 * BreachSwap: On sword/axe hit → switch to mace → hit again (same tick) → switch back to sword next tick.
 */
public class BreachSwapFeature {

    private static int switchBackCountdown = 0;
    private static int prevSlot = -1;

    public static void onAttack(MinecraftClient client, LivingEntity target) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.breachSwap.enabled) return;
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;
        boolean holdingSword = player.getMainHandStack().getItem() instanceof SwordItem;
        boolean holdingAxe   = player.getMainHandStack().getItem() instanceof AxeItem;
        if (!holdingSword && !holdingAxe) return;

        prevSlot = player.getInventory().selectedSlot;
        boolean inAir = !player.isOnGround();

        int maceSlot = inAir
                ? InventoryHelper.findDensityMaceSlot(player)
                : InventoryHelper.findBreachMaceSlot(player);
        if (maceSlot == -1) maceSlot = InventoryHelper.findAnyMaceSlot(player);
        if (maceSlot == -1) return;

        InventoryHelper.switchToSlot(client, maceSlot);
        client.interactionManager.attackEntity(player, target);

        switchBackCountdown = 1; // switch back next tick
    }

    public static void tick(MinecraftClient client) {
        if (switchBackCountdown <= 0 || prevSlot == -1) return;
        switchBackCountdown--;
        if (switchBackCountdown == 0) {
            InventoryHelper.switchToSlot(client, prevSlot);
            prevSlot = -1;
        }
    }
}
