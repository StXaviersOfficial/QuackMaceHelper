package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * WindChargeTackle — Stage 4 polish.
 *
 * Fires a wind charge at an opponent who is:
 *   1. Airborne (falling OR gliding)
 *   2. Moving toward us  (velocity dot > 0.4)
 *   3. Facing us         (look dot > 0.5)
 *   4. We are on the ground
 *
 * Anti-cheat polish:
 *   - Intercept point gets a small random offset (±0.2 blocks) so aim is
 *     never pixel-perfect and looks human.
 *   - 0–1 tick random jitter before firing after a threat is found.
 *   - Ground-reset: cooldown clears immediately when target lands so the
 *     feature re-fires the instant they re-launch at us.
 */
public class WindChargeTackleFeature {

    private static final Random RNG = new Random();

    private static LivingEntity lastTarget          = null;
    private static long         lastFireMs          = 0;
    private static final long   COOLDOWN_MS         = 3000;
    /** Global rate limit so a SECOND attacker can't bypass the per-target
     *  cooldown — the old per-target-only check allowed rapid multi-target
     *  wind-charge spam. */
    private static final long   GLOBAL_COOLDOWN_MS  = 750;
    private static final double WIND_CHARGE_SPEED   = 1.5;
    private static final int    SOLVER_ITERATIONS   = 6;

    private static final double VELOCITY_THRESHOLD = 0.4;
    private static final double FACING_THRESHOLD   = 0.5;

    private static boolean lastTargetWasAirborne = false;

    // Jitter state — pending fire
    private static boolean   pendingFire    = false;
    private static int       jitterDelay    = 0;
    private static LivingEntity pendingTarget = null;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.windChargeTackle.enabled) { pendingFire = false; return; }
        if (client.player == null || client.world == null) return;

        // Never auto-fire while a GUI is open (anticheat safety).
        if (client.currentScreen != null) { pendingFire = false; return; }

        ClientPlayerEntity player = client.player;
        if (!player.isOnGround()) { pendingFire = false; return; }

        // Drop stale target references (entity unloaded / world changed)
        if (lastTarget != null && !lastTarget.isAlive()) lastTarget = null;

        // Ground-reset: if last target just landed, reset cooldown immediately
        if (lastTarget != null && lastTarget.isAlive()) {
            boolean nowOnGround = lastTarget.isOnGround();
            if (lastTargetWasAirborne && nowOnGround) lastFireMs = 0;
            lastTargetWasAirborne = !nowOnGround;
        } else {
            lastTargetWasAirborne = false;
        }

        // Handle pending fire after jitter delay
        if (pendingFire) {
            if (jitterDelay > 0) { jitterDelay--; return; }
            pendingFire = false;
            if (pendingTarget != null && pendingTarget.isAlive()) {
                fireAt(client, player, pendingTarget);
            }
            pendingTarget = null;
            return;
        }

        LivingEntity threat = findThreat(client, cfg.windChargeTackle.radius);
        if (threat == null) return;

        long sinceFire = System.currentTimeMillis() - lastFireMs;
        if (threat == lastTarget && sinceFire < COOLDOWN_MS) return;
        if (threat != lastTarget && sinceFire < GLOBAL_COOLDOWN_MS) return;

        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) return;

        // Queue with 0–1 tick jitter
        pendingTarget = threat;
        pendingFire   = true;
        jitterDelay   = RNG.nextInt(2);
    }

    private static void fireAt(MinecraftClient client, ClientPlayerEntity player,
                               LivingEntity threat) {
        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) return;

        Vec3d intercept = solveIntercept(player.getEyePos(), threat);

        // Small random offset on the intercept so aim isn't robotically perfect
        intercept = intercept.add(
            (RNG.nextDouble() - 0.5) * 0.4,
            (RNG.nextDouble() - 0.5) * 0.3,
            (RNG.nextDouble() - 0.5) * 0.4
        );

        AimAssistFeature.snapAimAt(client, intercept);

        int prevSlot = player.getInventory().getSelectedSlot();
        InventoryHelper.switchToSlot(client, wcSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        InventoryHelper.switchToSlot(client, prevSlot);

        lastTarget             = threat;
        lastFireMs             = System.currentTimeMillis();
        lastTargetWasAirborne  = !threat.isOnGround();
    }

    private static LivingEntity findThreat(MinecraftClient client, float radius) {
        if (client.player == null || client.world == null) return null;
        Vec3d ourPos = client.player.getEntityPos();

        return client.world.getEntitiesByClass(PlayerEntity.class,
            client.player.getBoundingBox().expand(radius), e -> {
                if (e == client.player || !e.isAlive()) return false;

                boolean falling = !e.isOnGround() && e.getVelocity().y < -0.05;
                boolean gliding = e.isGliding();
                if (!falling && !gliding) return false;

                // Must be at least 5 blocks above us
                if (e.getY() - client.player.getY() < 5.0) return false;

                Vec3d toUs = ourPos.subtract(e.getEntityPos()).normalize();

                Vec3d vel = e.getVelocity();
                if (vel.lengthSquared() < 0.001) return false;
                if (vel.normalize().dotProduct(toUs) < VELOCITY_THRESHOLD) return false;

                Vec3d look = e.getRotationVec(1.0f);
                if (look.dotProduct(toUs) < FACING_THRESHOLD) return false;

                return true;
            })
            .stream()
            .min((a, b) -> Double.compare(
                client.player.distanceTo(a), client.player.distanceTo(b)))
            .orElse(null);
    }

    private static Vec3d solveIntercept(Vec3d eyes, LivingEntity target) {
        Vec3d pos = target.getEntityPos().add(0, target.getHeight() / 2.0, 0);
        Vec3d vel = target.getVelocity();
        int ticks = Math.max(1, (int)(eyes.distanceTo(pos) / WIND_CHARGE_SPEED));
        for (int i = 0; i < SOLVER_ITERATIONS; i++) {
            Vec3d predicted = pos.add(vel.multiply(ticks));
            int nt = Math.max(1, (int)(eyes.distanceTo(predicted) / WIND_CHARGE_SPEED));
            if (nt == ticks) { pos = predicted; break; }
            ticks = nt; pos = predicted;
        }
        return pos;
    }
}
