package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class PearlCatchFeature {
    private final Set<Integer> processedPearlIds = new HashSet<>();
    private EnderPearlEntity trackedPearl = null;
    private int ticksSinceDetection = 0;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.pearlCatchEnabled || client.player == null || client.world == null) {
            reset();
            return;
        }

        // Collision Rule 4: Suspended while Always-On Shield is triggered
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            return;
        }

        ClientPlayerEntity player = client.player;
        int windChargeSlot = PlayerActionHelper.findWindChargeInHotbar(player);
        if (windChargeSlot == -1) {
            return;
        }

        if (trackedPearl == null) {
            for (Entity entity : client.world.getEntities()) {
                if (!(entity instanceof EnderPearlEntity pearl)) continue;
                if (processedPearlIds.contains(pearl.getId())) continue;

                // Must be MY OWN ender pearl (enemy and teammate pearls are IGNORED)
                if (pearl.getOwner() != player) {
                    processedPearlIds.add(pearl.getId());
                    continue;
                }

                // Simulate trajectory to check if arc rises > 10 blocks above player position
                if (willPearlRiseMoreThan10Blocks(player, pearl)) {
                    trackedPearl = pearl;
                    ticksSinceDetection = 0;
                    processedPearlIds.add(pearl.getId());
                    break;
                } else {
                    processedPearlIds.add(pearl.getId());
                }
            }
        }

        if (trackedPearl != null) {
            if (!trackedPearl.isAlive()) {
                trackedPearl = null;
                RotationController.releaseAim(RotationController.AimOwner.PEARL_CATCH);
                return;
            }

            ticksSinceDetection++;
            if (ticksSinceDetection >= cfg.pearlCatchDelay) {
                executeWindChargeCatch(client, player, trackedPearl, windChargeSlot, cfg.pearlCatchMode);
                trackedPearl = null;
            }
        }
    }

    /**
     * Simulates ender pearl kinematics (gravity 0.03/tick, drag 0.99/tick) to verify whether peak Y
     * exceeds player.getY() + 10.0 blocks (measured from player positions, never the ground).
     */
    private boolean willPearlRiseMoreThan10Blocks(ClientPlayerEntity player, EnderPearlEntity pearl) {
        double playerY = player.getY();
        double y = pearl.getY();
        double vy = pearl.getVelocity().y;
        double maxY = y;

        for (int t = 0; t < 80; t++) {
            y += vy;
            if (y > maxY) {
                maxY = y;
            }
            vy = (vy - 0.03) * 0.99;
            if (vy <= 0.0) {
                break;
            }
        }
        return (maxY - playerY) > 10.0;
    }

    private void executeWindChargeCatch(
            MinecraftClient client,
            ClientPlayerEntity player,
            EnderPearlEntity pearl,
            int windChargeSlot,
            ModConfig.PearlCatchMode mode
    ) {
        int prevSlot = player.getInventory().getSelectedSlot();
        RotationController.AimOwner previousAimOwner = RotationController.getCurrentOwner();

        if (mode == ModConfig.PearlCatchMode.AUTOMATIC) {
            // Solve exact intercept point where a wind charge (speed 1.5 blocks/tick, straight line)
            // meets the pearl's simulated trajectory (gravity 0.03, drag 0.99)
            Vec3d interceptPoint = solveInterceptPoint(player, pearl);
            RotationController.acquireAim(RotationController.AimOwner.PEARL_CATCH);
            RotationController.applyInstantSnap(client, interceptPoint);
        }

        PlayerActionHelper.selectHotbarSlot(client, windChargeSlot);
        PlayerActionHelper.performRealUseItem(client, Hand.MAIN_HAND);
        PlayerActionHelper.selectHotbarSlot(client, prevSlot);

        // Collision Rule 8: Release Pearl Catch aim right after the throw so any active combat aim re-engages
        RotationController.releaseAim(RotationController.AimOwner.PEARL_CATCH);
        if (previousAimOwner != RotationController.AimOwner.NONE && previousAimOwner != RotationController.AimOwner.PEARL_CATCH) {
            RotationController.acquireAim(previousAimOwner);
        }
    }

    private Vec3d solveInterceptPoint(ClientPlayerEntity player, EnderPearlEntity pearl) {
        Vec3d eyePos = player.getEyePos();
        Vec3d pPos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
        Vec3d pVel = pearl.getVelocity();
        double windChargeSpeed = 1.5;

        Vec3d bestPoint = pPos;
        double bestError = Double.MAX_VALUE;

        for (int t = 1; t <= 60; t++) {
            pPos = pPos.add(pVel);
            pVel = new Vec3d(pVel.x * 0.99, (pVel.y - 0.03) * 0.99, pVel.z * 0.99);

            double dist = eyePos.distanceTo(pPos);
            double windTravelTicks = dist / windChargeSpeed;
            double error = Math.abs(windTravelTicks - t);

            if (error < bestError) {
                bestError = error;
                bestPoint = pPos;
            }
        }
        return bestPoint;
    }

    public void reset() {
        trackedPearl = null;
        ticksSinceDetection = 0;
        RotationController.releaseAim(RotationController.AimOwner.PEARL_CATCH);
    }
}
