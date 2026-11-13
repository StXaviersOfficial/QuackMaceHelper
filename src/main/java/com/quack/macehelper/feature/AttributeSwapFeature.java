package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class AttributeSwapFeature {
    private boolean swapBackPending = false;
    private int originalSlot = -1;
    private int ticksUntilSwapBack = 0;

    /**
     * Returns true if AttributeSwap handled this attack (so Stun Slam can queue behind it per Collision Rule 7).
     */
    public boolean onPlayerAttack(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.attributeSwapEnabled) return false;
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) return false;

        ItemStack held = player.getMainHandStack();
        if (!PlayerActionHelper.isAttributeSwapTriggerItem(held)) {
            return false;
        }

        double heightDiff = PlayerActionHelper.getBodyHeightDifference(player, target);
        int maceSlot;

        if (heightDiff >= 3.0) {
            // Falling case (3+ blocks above target): pick mace that deals the MOST effective damage
            maceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, target);
        } else {
            // Normal case (grounded, jumping, or < 3 blocks above target): mace with Breach (I-IV)
            maceSlot = PlayerActionHelper.findBreachMaceInHotbar(player);
        }

        if (maceSlot == -1) {
            return false;
        }

        int prevSlot = player.getInventory().getSelectedSlot();
        if (!swapBackPending) {
            originalSlot = prevSlot;
        }

        // Same-tick swap to qualifying mace and same-tick extra attack
        PlayerActionHelper.selectHotbarSlot(client, maceSlot);
        PlayerActionHelper.performRealAttack(client, target);

        // Swap back within the next 1-4 ticks (randomized)
        swapBackPending = true;
        ticksUntilSwapBack = ThreadLocalRandom.current().nextInt(1, 5);
        return true;
    }

    public void tick(MinecraftClient client) {
        if (!ModConfig.get().masterSwitch || client.player == null) {
            reset();
            return;
        }
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            return;
        }

        if (swapBackPending) {
            ticksUntilSwapBack--;
            if (ticksUntilSwapBack <= 0) {
                if (originalSlot >= 0 && originalSlot <= 8) {
                    PlayerActionHelper.selectHotbarSlot(client, originalSlot);
                }
                swapBackPending = false;
                originalSlot = -1;
            }
        }
    }

    public boolean isChainActive() {
        return swapBackPending;
    }

    public void reset() {
        swapBackPending = false;
        originalSlot = -1;
        ticksUntilSwapBack = 0;
    }
}
