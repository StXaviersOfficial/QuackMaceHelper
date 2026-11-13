package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class AutoMaceFeature {

    private enum State {
        SCANNING,
        MACE_BURST_TICK_1,
        MACE_BURST_TICK_2,
        COOLDOWN_UNTIL_REARM
    }

    private State state = State.SCANNING;
    private Entity activeTarget = null;
    private int totalMaceHits = 0;
    private int firstTickMaceHits = 0;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.autoMaceEnabled || client.player == null || client.world == null) {
            onDisable();
            return;
        }

        // Collision Rule 4: Always-On Shield TRIGGERED suspends all other features
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            onDisable();
            return;
        }

        ClientPlayerEntity player = client.player;

        // Collision Rule 2 & 3: Gliding state -> only Elytra Mace may engage; Auto Mace stays idle
        if (player.isGliding() || QuackMaceHelperClient.ELYTRA_MACE.isEngaged()) {
            onDisable();
            return;
        }

        if (state == State.MACE_BURST_TICK_1 || state == State.MACE_BURST_TICK_2) {
            processMaceBurst(client, player, cfg);
            return;
        }

        if (state == State.COOLDOWN_UNTIL_REARM) {
            // Re-arm once landed, bounced up (e.g. Wind Burst), or target changed
            if (player.isOnGround() || player.getVelocity().y > 0.0 || activeTarget == null || !activeTarget.isAlive()) {
                state = State.SCANNING;
                activeTarget = null;
            }
            return;
        }

        // Requires a mace in the hotbar
        if (PlayerActionHelper.findAnyMaceInHotbar(player) == -1) {
            disengageAim();
            return;
        }

        // Must be FALLING (not gliding)
        boolean isFalling = !player.isOnGround() && (player.fallDistance >= 1.5F || player.getVelocity().y < -0.08);
        if (!isFalling) {
            disengageAim();
            return;
        }

        if (cfg.autoMaceAutoAim) {
            Entity target = findTargetBelowInFov(client, player, cfg.autoMaceFov);
            if (target == null) {
                disengageAim();
                return;
            }
            activeTarget = target;
            RotationController.acquireAim(RotationController.AimOwner.AUTO_MACE);
            Vec3d aimVec = RotationController.getTargetAimPoint(player, target, ModConfig.AimPoint.AUTO);

            if (cfg.autoMaceAimType == ModConfig.MaceAimType.INSTANT) {
                RotationController.applyInstantSnap(client, aimVec);
            } else {
                RotationController.applyWurstSmoothRotation(client, aimVec, target, cfg.aimAssistSpeed);
            }

            if (RotationController.isCrosshairOnEntity(client, target)) {
                executeFirstBurstTick(client, player, target, cfg);
            }
        } else {
            // Auto aim OFF: wait until target crosses crosshair while falling 3+ blocks above
            if (client.crosshairTarget instanceof EntityHitResult ehr) {
                Entity crossed = ehr.getEntity();
                if (PlayerActionHelper.isValidTargetEntity(client, crossed)
                        && PlayerActionHelper.getBodyHeightDifference(player, crossed) >= 3.0) {
                    activeTarget = crossed;
                    executeFirstBurstTick(client, player, crossed, cfg);
                }
            }
        }
    }

    private void executeFirstBurstTick(MinecraftClient client, ClientPlayerEntity player, Entity target, ModConfig cfg) {
        boolean shieldUp = PlayerActionHelper.isShieldRaised(target);
        // Collision Rule 5: If Shield Draining is active against the shielded target, it owns attacks at its CPS
        // and Auto Mace provides aim only (no axe hits while draining)
        boolean drainActive = shieldUp && cfg.shieldDrainingEnabled;

        totalMaceHits = ThreadLocalRandom.current().nextInt(5, 7); // 5 or 6
        firstTickMaceHits = totalMaceHits / 2;

        if (drainActive) {
            state = State.MACE_BURST_TICK_1;
            return;
        }

        if (shieldUp) {
            int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
            if (axeSlot != -1) {
                PlayerActionHelper.selectHotbarSlot(client, axeSlot);
                int axeHits = ThreadLocalRandom.current().nextInt(5, 7); // 5 or 6 hits within 1 tick
                for (int i = 0; i < axeHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
            }
            state = State.MACE_BURST_TICK_1;
        } else {
            // Shield down: best-damage mace directly, 5-6 hit burst across 2 ticks
            int maceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, target);
            if (maceSlot != -1) {
                PlayerActionHelper.selectHotbarSlot(client, maceSlot);
                for (int i = 0; i < firstTickMaceHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
            }
            state = State.MACE_BURST_TICK_2;
        }
    }

    private void processMaceBurst(MinecraftClient client, ClientPlayerEntity player, ModConfig cfg) {
        if (activeTarget == null || !activeTarget.isAlive()) {
            finishBurstAndReleaseAim();
            return;
        }

        if (cfg.autoMaceAutoAim) {
            Vec3d aimVec = RotationController.getTargetAimPoint(player, activeTarget, ModConfig.AimPoint.AUTO);
            if (cfg.autoMaceAimType == ModConfig.MaceAimType.INSTANT) {
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
            finishBurstAndReleaseAim();
        }
    }

    private Entity findTargetBelowInFov(MinecraftClient client, ClientPlayerEntity player, int fovDeg) {
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        double halfFov = fovDeg / 2.0;

        for (Entity entity : client.world.getEntities()) {
            if (!PlayerActionHelper.isValidTargetEntity(client, entity)) continue;
            if (PlayerActionHelper.getBodyHeightDifference(player, entity) < 3.0) continue;

            // If target's shield is raised and Shield Draining is OFF, require an axe in hotbar
            if (PlayerActionHelper.isShieldRaised(entity) && !ModConfig.get().shieldDrainingEnabled) {
                if (PlayerActionHelper.findAxeInHotbar(player) == -1) continue;
            }

            Vec3d aimVec = RotationController.getTargetAimPoint(player, entity, ModConfig.AimPoint.AUTO);
            double dist = player.getEyePos().distanceTo(aimVec);
            if (dist > 6.0) continue;

            double angle = RotationController.getAngleFromCrosshair(player, aimVec);
            if (angle <= halfFov && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private void finishBurstAndReleaseAim() {
        disengageAim();
        state = State.COOLDOWN_UNTIL_REARM;
        totalMaceHits = 0;
        firstTickMaceHits = 0;
    }

    private void disengageAim() {
        RotationController.releaseAim(RotationController.AimOwner.AUTO_MACE);
    }

    public void onDisable() {
        disengageAim();
        state = State.SCANNING;
        activeTarget = null;
        totalMaceHits = 0;
        firstTickMaceHits = 0;
    }
}
