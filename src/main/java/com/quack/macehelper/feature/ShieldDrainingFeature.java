package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;

public class ShieldDrainingFeature {
    private static final double ATTACK_RANGE = 3.0;
    private double attackAccumulator = 0.0;
    private boolean activelyDraining = false;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.shieldDrainingEnabled || client.player == null || client.world == null) {
            reset();
            return;
        }

        // Collision Rule 4: Always-On Shield TRIGGERED suspends all other features
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            reset();
            return;
        }

        ClientPlayerEntity player = client.player;
        Entity target = findShieldedTargetInRange(client, player);
        if (target == null) {
            reset();
            return;
        }

        // Must be 3+ blocks above target (player position measurement) and have a mace in hotbar
        if (PlayerActionHelper.getBodyHeightDifference(player, target) < 3.0) {
            reset();
            return;
        }

        int bestMaceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, target);
        if (bestMaceSlot == -1) {
            reset();
            return;
        }

        activelyDraining = true;
        PlayerActionHelper.selectHotbarSlot(client, bestMaceSlot);

        // Auto-spam the best-damage mace at configured CPS (20-250, default 50)
        // Starting the SAME TICK the target enters attack range
        double attacksPerTick = cfg.shieldDrainingCps / 20.0;
        attackAccumulator += attacksPerTick;
        int attacksThisTick = Math.max(1, (int) Math.floor(attackAccumulator));
        attackAccumulator = Math.max(0.0, attackAccumulator - attacksThisTick);

        for (int i = 0; i < attacksThisTick; i++) {
            PlayerActionHelper.performRealAttack(client, target);
        }
    }

    private Entity findShieldedTargetInRange(MinecraftClient client, ClientPlayerEntity player) {
        if (client.crosshairTarget instanceof EntityHitResult ehr) {
            Entity candidate = ehr.getEntity();
            if (isEligibleDrainTarget(client, player, candidate)) {
                return candidate;
            }
        }

        Entity best = null;
        double bestDist = ATTACK_RANGE;
        for (Entity entity : client.world.getEntities()) {
            if (!isEligibleDrainTarget(client, player, entity)) continue;
            double dist = PlayerActionHelper.getEyeDistance(player, entity);
            if (dist <= bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private boolean isEligibleDrainTarget(MinecraftClient client, ClientPlayerEntity player, Entity entity) {
        if (!PlayerActionHelper.isValidTargetEntity(client, entity)) return false;
        if (!PlayerActionHelper.isShieldRaised(entity)) return false;
        if (PlayerActionHelper.getBodyHeightDifference(player, entity) < 3.0) return false;
        double boxDist = Math.sqrt(player.squaredDistanceTo(entity));
        return boxDist <= ATTACK_RANGE || PlayerActionHelper.getEyeDistance(player, entity) <= ATTACK_RANGE;
    }

    public boolean isActivelyDraining() {
        return activelyDraining;
    }

    public void reset() {
        activelyDraining = false;
        attackAccumulator = 0.0;
    }
}
