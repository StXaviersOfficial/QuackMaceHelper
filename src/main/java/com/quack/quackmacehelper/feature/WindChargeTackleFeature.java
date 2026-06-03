package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * WindChargeTackle: Detects players falling/gliding toward user within 10 blocks.
 * Aims precisely at their hitbox, throws exactly 1 wind charge.
 * Then if AlwaysOnShield is on, that takes over immediately after.
 */
public class WindChargeTackleFeature {

    private static boolean fired = false;
    private static LivingEntity lastTarget = null;
    private static long lastFireMs = 0;
    private static final long COOLDOWN_MS = 3000; // 3 sec cooldown per target

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.windChargeTackle.enabled) { reset(); return; }
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;

        // Find a player flying/falling toward us
        LivingEntity threat = findThreat(client, cfg.windChargeTackle.radius);
        if (threat == null) { fired = false; lastTarget = null; return; }

        // Cooldown per target
        if (threat == lastTarget && System.currentTimeMillis() - lastFireMs < COOLDOWN_MS) return;

        // Must have wind charge
        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) return;

        // Aim precisely at their center/hitbox
        aimAtHitbox(client, player, threat);

        // Fire wind charge
        InventoryHelper.switchToSlot(client, wcSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);

        lastTarget = threat;
        lastFireMs = System.currentTimeMillis();

        // Hand off to AlwaysOnShield immediately after if enabled
        if (cfg.alwaysOnShield.enabled) {
            AlwaysOnShieldFeature.activateFor(threat);
        }
    }

    private static LivingEntity findThreat(MinecraftClient client, float radius) {
        LivingEntity t = TargetHelper.findPlayerTarget(client, radius);
        if (t == null) return null;
        // Must be falling or gliding
        boolean falling = !t.isOnGround() && t.getVelocity().y < -0.1;
        boolean gliding = t instanceof net.minecraft.entity.player.PlayerEntity pe && pe.isFallFlying();
        if (!falling && !gliding) return null;
        // Must be moving toward player
        if (!TargetHelper.isMovingToward(client, t)) return null;
        return t;
    }

    private static void aimAtHitbox(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
        // Predict position: estimate wind charge travel ticks
        double dist = player.distanceTo(target);
        int travelTicks = Math.max(1, (int)(dist / 1.5));
        Vec3d vel = target.getVelocity();
        Vec3d future = target.getPos()
                .add(vel.x * travelTicks, vel.y * travelTicks, vel.z * travelTicks)
                .add(0, target.getHeight() / 2.0, 0); // aim at center

        Vec3d d = future.subtract(player.getEyePos());
        double hDist = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, hDist));
        player.setYaw(yaw);
        player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    private static void reset() { fired = false; lastTarget = null; }
}
