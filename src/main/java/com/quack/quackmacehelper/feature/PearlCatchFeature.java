package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * PearlCatch: When YOUR pearl goes above triggerHeight blocks,
 * calculates exact intercept point, aims there, fires exactly 1 wind charge.
 */
public class PearlCatchFeature {

    private static EnderPearlEntity trackedPearl = null;
    private static int countdown = -1;
    private static boolean fired = false;

    // Wind charge approximate speed (blocks per tick)
    private static final double WIND_CHARGE_SPEED = 1.5;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.pearlCatch.enabled) { reset(); return; }
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        // Find our pearl
        if (trackedPearl == null || !trackedPearl.isAlive()) {
            trackedPearl = findOurPearl(client);
            fired = false;
            countdown = -1;
            return;
        }

        if (fired) return;

        double heightAbovePlayer = trackedPearl.getY() - player.getY();
        if (heightAbovePlayer < cfg.pearlCatch.triggerHeight) return;

        // Check we have wind charge
        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) { reset(); return; }

        // Start countdown
        if (countdown == -1) {
            countdown = cfg.pearlCatch.delayTicks;
        }

        if (countdown > 0) {
            countdown--;
            return;
        }

        // Fire! Calculate intercept
        // Estimate travel time for wind charge to reach current pearl distance
        double dist = player.distanceTo(trackedPearl);
        int travelTicks = Math.max(1, (int)(dist / WIND_CHARGE_SPEED));

        // Predict pearl position at that time
        Vec3d intercept = predictPearl(trackedPearl, travelTicks);

        // Aim exactly at intercept
        aimAt(client, player, intercept);

        // Switch to wind charge and fire
        InventoryHelper.switchToSlot(client, wcSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);

        fired = true;
    }

    private static Vec3d predictPearl(EnderPearlEntity pearl, int ticks) {
        double x = pearl.getX(), y = pearl.getY(), z = pearl.getZ();
        double vx = pearl.getVelocity().x;
        double vy = pearl.getVelocity().y;
        double vz = pearl.getVelocity().z;
        double gravity = 0.03;
        double drag = 0.99;

        for (int i = 0; i < ticks; i++) {
            vy -= gravity;
            vx *= drag; vy *= drag; vz *= drag;
            x += vx; y += vy; z += vz;
        }
        return new Vec3d(x, y, z);
    }

    private static void aimAt(MinecraftClient client, ClientPlayerEntity player, Vec3d target) {
        Vec3d d = target.subtract(player.getEyePos());
        double hDist = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, hDist));
        player.setYaw(yaw);
        player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    private static EnderPearlEntity findOurPearl(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        List<EnderPearlEntity> list = client.world.getEntitiesByClass(
                EnderPearlEntity.class,
                client.player.getBoundingBox().expand(128),
                p -> p.getOwner() == client.player
        );
        return list.isEmpty() ? null : list.get(0);
    }

    private static void reset() {
        trackedPearl = null;
        countdown = -1;
        fired = false;
    }
}
