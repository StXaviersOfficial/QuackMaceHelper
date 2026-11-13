package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class AimAssistFeature {
    private Entity currentTarget = null;
    private double activeAdjustedSpeed = 3600.0;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.aimAssistEnabled || client.player == null || client.world == null) {
            disengage();
            return;
        }

        // Collision Rule 4 & 6: Suspended when Always-On Shield is triggered or another feature runs its own aim
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered() || RotationController.isCombatOrDefenseAimActive()) {
            disengage();
            return;
        }

        ClientPlayerEntity player = client.player;
        Entity nearest = findNearestValidTarget(client, player, cfg.aimAssistRange, cfg.aimAssistFov);

        if (nearest == null) {
            disengage();
            return;
        }

        // Activation detection: (no target -> target) or switching to a different target
        if (nearest != currentTarget) {
            currentTarget = nearest;
            double baseSpeed = cfg.aimAssistSpeed;
            if (cfg.aimAssistSpeedRandomization) {
                int roll = ThreadLocalRandom.current().nextInt(0, 201); // [0, 200]
                boolean add = ThreadLocalRandom.current().nextBoolean();
                activeAdjustedSpeed = add ? (baseSpeed + roll) : (baseSpeed - roll);
            } else {
                activeAdjustedSpeed = baseSpeed;
            }
        }

        RotationController.acquireAim(RotationController.AimOwner.AIM_ASSIST);
        Vec3d aimVec = RotationController.getTargetAimPoint(player, currentTarget, cfg.aimAssistAimAt);

        if (cfg.aimAssistMode == ModConfig.AimMode.MAX) {
            RotationController.applyInstantSnap(client, aimVec);
        } else {
            RotationController.applyWurstSmoothRotation(client, aimVec, currentTarget, activeAdjustedSpeed);
        }
    }

    private Entity findNearestValidTarget(MinecraftClient client, ClientPlayerEntity player, double maxRange, int fovDeg) {
        Entity best = null;
        double bestDist = maxRange;
        double halfFov = fovDeg / 2.0;

        for (Entity entity : client.world.getEntities()) {
            if (!PlayerActionHelper.isValidTargetEntity(client, entity)) continue;

            Vec3d aimPoint = RotationController.getTargetAimPoint(player, entity, ModConfig.get().aimAssistAimAt);
            double dist = player.getEyePos().distanceTo(aimPoint);
            if (dist > maxRange) continue;

            double angle = RotationController.getAngleFromCrosshair(player, aimPoint);
            if (angle <= halfFov && dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    public void disengage() {
        currentTarget = null;
        RotationController.releaseAim(RotationController.AimOwner.AIM_ASSIST);
    }
}
