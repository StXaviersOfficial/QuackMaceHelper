package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class ElytraMaceFeature {

    private enum State {
        SCANNING,
        MACE_BURST_TICK_1,
        MACE_BURST_TICK_2
    }

    private State state = State.SCANNING;
    private Entity activeTarget = null;
    private int totalMaceHits = 0;
    private int firstTickMaceHits = 0;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.elytraMaceEnabled || client.player == null || client.world == null) {
            onDisable();
            return;
        }

        // Collision Rule 4: Suspended when Always-On Shield is triggered
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            onDisable();
            return;
        }

        ClientPlayerEntity player = client.player;

        // If mid-burst after swapping chestplate (no longer gliding), finish the 2-tick mace burst
        if (state == State.MACE_BURST_TICK_1 || state == State.MACE_BURST_TICK_2) {
            processMaceBurst(client, player, cfg);
            return;
        }

        // Requires: gliding with an elytra, and both an elytra and a chestplate are in the hotbar
        if (!player.isGliding()) {
            disengageAim();
            return;
        }

        int chestplateSlot = PlayerActionHelper.findChestplateInHotbar(player);
        boolean hasElytra = PlayerActionHelper.findElytraInHotbar(player) != -1
                || player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(net.minecraft.item.Items.ELYTRA);
        if (chestplateSlot == -1 || !hasElytra) {
            disengageAim();
            return;
        }

        if (cfg.elytraMaceAutoAim) {
            Entity target = findTargetBelowInFov(client, player, cfg.elytraMaceFov);
            if (target == null) {
                disengageAim();
                return;
            }
            activeTarget = target;
            RotationController.acquireAim(RotationController.AimOwner.ELYTRA_MACE);
            Vec3d aimVec = RotationController.getTargetAimPoint(player, target, ModConfig.AimPoint.AUTO);

            if (cfg.elytraMaceAimType == ModConfig.MaceAimType.INSTANT) {
                RotationController.applyInstantSnap(client, aimVec);
            } else {
                RotationController.applyWurstSmoothRotation(client, aimVec, target, cfg.aimAssistSpeed);
            }

            if (RotationController.isCrosshairOnEntity(client, target)) {
                executeFirstEngagementTick(client, player, target, chestplateSlot, cfg);
            }
        } else {
            // AutoAim OFF: no scanning; while gliding 3+ blocks above a target, wait until target crosses crosshair
            if (client.crosshairTarget instanceof EntityHitResult ehr) {
                Entity crossed = ehr.getEntity();
                if (PlayerActionHelper.isValidTargetEntity(client, crossed)
                        && PlayerActionHelper.getBodyHeightDifference(player, crossed) >= 3.0) {
                    activeTarget = crossed;
                    executeFirstEngagementTick(client, player, crossed, chestplateSlot, cfg);
                }
            }
        }
    }

    private void executeFirstEngagementTick(
            MinecraftClient client,
            ClientPlayerEntity player,
            Entity target,
            int chestplateSlot,
            ModConfig cfg
    ) {
        // 1) In the FIRST TICK of the engagement: swap equipped elytra for chestplate by selecting
        //    the chestplate's hotbar slot and using it (right-click) — a REAL equipment swap, no packets
        PlayerActionHelper.selectHotbarSlot(client, chestplateSlot);
        PlayerActionHelper.performRealUseItem(client, Hand.MAIN_HAND);

        boolean shieldUp = PlayerActionHelper.isShieldRaised(target);
        boolean drainActive = shieldUp && cfg.shieldDrainingEnabled;

        // 2) If target's shield is up (and Shield Draining is not overriding):
        //    switch to axe slot and hit 5-6 times within 1 tick
        if (shieldUp && !drainActive) {
            int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
            if (axeSlot != -1) {
                PlayerActionHelper.selectHotbarSlot(client, axeSlot);
                int axeHits = ThreadLocalRandom.current().nextInt(5, 7); // 5 or 6
                for (int i = 0; i < axeHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
            }
        }

        // Prepare the 5-6 mace hits across the next 2 ticks
        totalMaceHits = ThreadLocalRandom.current().nextInt(5, 7); // 5 or 6
        firstTickMaceHits = totalMaceHits / 2;

        // If no shield, start best-mace spam right away in tick 1 and finish in tick 2
        if (!shieldUp && !drainActive) {
            int maceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, target);
            if (maceSlot != -1) {
                PlayerActionHelper.selectHotbarSlot(client, maceSlot);
                for (int i = 0; i < firstTickMaceHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
            }
            state = State.MACE_BURST_TICK_2;
        } else {
            state = State.MACE_BURST_TICK_1;
        }
    }

    private void processMaceBurst(MinecraftClient client, ClientPlayerEntity player, ModConfig cfg) {
        if (activeTarget == null || !activeTarget.isAlive()) {
            finishEngagement();
            return;
        }

        // Keep aim maintained during the 2-tick burst
        if (cfg.elytraMaceAutoAim) {
            Vec3d aimVec = RotationController.getTargetAimPoint(player, activeTarget, ModConfig.AimPoint.AUTO);
            if (cfg.elytraMaceAimType == ModConfig.MaceAimType.INSTANT) {
                RotationController.applyInstantSnap(client, aimVec);
            } else {
                RotationController.applyWurstSmoothRotation(client, aimVec, activeTarget, cfg.aimAssistSpeed);
            }
        }

        boolean shieldUp = PlayerActionHelper.isShieldRaised(activeTarget);
        boolean drainActive = shieldUp && cfg.shieldDrainingEnabled;

        if (!drainActive) {
            int maceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, activeTarget);
            if (maceSlot != -1) {
                PlayerActionHelper.selectHotbarSlot(client, maceSlot);
                int count = (state == State.MACE_BURST_TICK_1) ? firstTickMaceHits : (totalMaceHits - firstTickMaceHits);
                for (int i = 0; i < count; i++) {
                    PlayerActionHelper.performRealAttack(client, activeTarget);
                }
            }
        }

        if (state == State.MACE_BURST_TICK_1) {
            state = State.MACE_BURST_TICK_2;
        } else {
            finishEngagement();
        }
    }

    private Entity findTargetBelowInFov(MinecraftClient client, ClientPlayerEntity player, int fovDeg) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        double halfFov = fovDeg / 2.0;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity) && !PlayerActionHelper.isValidTargetEntity(client, entity)) continue;
            if (entity == player || !entity.isAlive() || entity.isSpectator()) continue;

            // Scan hemisphere BELOW player (3+ blocks above target or below eye level)
            if (PlayerActionHelper.getBodyHeightDifference(player, entity) < 3.0) continue;

            Vec3d aimVec = RotationController.getTargetAimPoint(player, entity, ModConfig.AimPoint.AUTO);
            double dist = player.getEyePos().distanceTo(aimVec);
            if (dist > 8.0) continue;

            double angle = RotationController.getAngleFromCrosshair(player, aimVec);
            if (angle <= halfFov && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    public boolean isEngaged() {
        return state != State.SCANNING;
    }

    private void finishEngagement() {
        state = State.SCANNING;
        activeTarget = null;
        totalMaceHits = 0;
        firstTickMaceHits = 0;
        disengageAim();
    }

    private void disengageAim() {
        RotationController.releaseAim(RotationController.AimOwner.ELYTRA_MACE);
    }

    public void onDisable() {
        finishEngagement();
    }
}
