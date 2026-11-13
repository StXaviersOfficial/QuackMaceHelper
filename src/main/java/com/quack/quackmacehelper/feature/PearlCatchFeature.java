package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * PearlCatch — V5.
 *
 * Watches for THE PLAYER'S OWN ender pearl being thrown (owner check —
 * enemy and teammate pearls are IGNORED, confirmed Q3) whose arc will rise
 * MORE than 10 blocks of height (V5: predicted APEX height above the
 * player, measured from PLAYER positions per R3 — not the ground).
 *
 * AUTOMATIC (default): simulate both trajectories precisely; solve where a
 * wind charge thrown after the configured delay (1-3 ticks) will meet the
 * pearl; instantly snap the camera onto the throw direction (no smoothing)
 * and throw at exactly the right tick so the wind charge collides with the
 * pearl mid-air.
 *
 * MANUAL: qualifying pearl detected -> just throw the wind charge after
 * the configured delay. No camera movement, no math.
 *
 * ONE wind-charge throw per pearl — no retry on a miss (confirmed Q13).
 * Requires a wind charge in the hotbar (R4).
 */
public class PearlCatchFeature {

    private static final Random RNG = new Random();

    private static EnderPearlEntity trackedPearl  = null;
    private static int              countdown      = -1;
    private static boolean          fired          = false;
    private static boolean          pearlWasAlive  = false;

    private static final double WIND_CHARGE_SPEED = 1.5;
    private static final int    SOLVER_ITERATIONS = 8;
    private static final double PEARL_GRAVITY     = 0.03;
    private static final double PEARL_DRAG        = 0.99;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.pearlCatch.enabled) { reset(); return; }
        if (client.player == null || client.world == null) return;

        // Never auto-use items while a GUI is open (anticheat safety).
        if (client.currentScreen != null) return;
        // AlwaysOnShield triggered owns all other feature actions (Q12)
        if (AlwaysOnShieldFeature.isTriggered()) return;

        ClientPlayerEntity player = client.player;
        EnderPearlEntity   pearl  = findOurPearl(client);

        if (pearl == null) {
            if (pearlWasAlive) reset();
            pearlWasAlive = false;
            trackedPearl  = null;
            return;
        }

        pearlWasAlive = true;
        if (trackedPearl == null) {
            trackedPearl = pearl;
            fired        = false;
            countdown    = -1; // not yet qualified
        }

        if (fired) return;

        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) return;

        // V5 trigger, both modes: the PREDICTED APEX above the player
        // (player heights, R3) gates everything — a pearl that never rises
        // 10+ blocks never triggers a throw, manual mode included.
        double apexAbove = predictedApexHeight(pearl) - player.getY();
        if (apexAbove < cfg.pearlCatch.triggerHeight) return;

        // V5.1: the delay slider is honored in BOTH modes (spec: "throw the
        // wind charge at the selected delay"), counted from the first
        // qualifying tick.
        if (countdown == -1) countdown = cfg.pearlCatch.delayTicks;
        if (countdown > 0) { countdown--; return; }
        fireAt(client, player, pearl, wcSlot, cfg.pearlCatch.manualMode);
    }

    /**
     * @param manual true when fired in manual mode — the camera is NEVER
     *               redirected at the pearl (the player aims themselves);
     *               we only switch to the wind charge and right-click it.
     */
    private static void fireAt(MinecraftClient client, ClientPlayerEntity player,
                               EnderPearlEntity pearl, int wcSlot, boolean manual) {
        double px  = pearl.getX(),  py  = pearl.getY(),  pz  = pearl.getZ();
        double pvx = pearl.getVelocity().x;
        double pvy = pearl.getVelocity().y;
        double pvz = pearl.getVelocity().z;

        Vec3d  eyes  = player.getEyePos();
        double dist  = eyes.distanceTo(new Vec3d(px, py, pz));
        int    ticks = Math.max(1, (int)(dist / WIND_CHARGE_SPEED));
        Vec3d intercept = new Vec3d(px, py, pz);

        for (int i = 0; i < SOLVER_ITERATIONS; i++) {
            intercept = simulatePearl(px, py, pz, pvx, pvy, pvz, ticks);
            double nd = eyes.distanceTo(intercept);
            int nt = Math.max(1, (int)(nd / WIND_CHARGE_SPEED));
            if (nt == ticks) break;
            ticks = nt;
        }

        // Small random offset — aim looks human, still accurate enough to hit
        intercept = intercept.add(
            (RNG.nextDouble() - 0.5) * 0.3,
            (RNG.nextDouble() - 0.5) * 0.2,
            (RNG.nextDouble() - 0.5) * 0.3
        );

        // Manual mode: no auto-aim at all — the camera never redirects at
        // the pearl, the wind charge flies wherever the player is aiming.
        if (!manual) {
            AimAssistFeature.snapAimAt(client, intercept);
        }
        int prevSlot = player.getInventory().getSelectedSlot();
        InventoryHelper.switchToSlot(client, wcSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        InventoryHelper.switchToSlot(client, prevSlot);
        fired = true; // ONE throw per pearl — no retry (Q13)
    }

    /** Simulates the pearl forward and returns the highest Y it will reach. */
    private static double predictedApexHeight(EnderPearlEntity pearl) {
        double vx = pearl.getVelocity().x;
        double vy = pearl.getVelocity().y;
        double vz = pearl.getVelocity().z;
        double x = pearl.getX(), y = pearl.getY(), z = pearl.getZ();
        double apex = y;
        // Drag-then-gravity vanilla order; stop at apex (vy <= 0)
        for (int i = 0; i < 200 && vy > 0; i++) {
            vx *= PEARL_DRAG; vy *= PEARL_DRAG; vz *= PEARL_DRAG;
            vy -= PEARL_GRAVITY;
            x += vx; y += vy; z += vz;
            if (y > apex) apex = y;
        }
        return apex;
    }

    private static Vec3d simulatePearl(double x, double y, double z,
                                        double vx, double vy, double vz, int ticks) {
        // Vanilla order: DRAG first, then gravity.
        for (int i = 0; i < ticks; i++) {
            vx *= PEARL_DRAG; vy *= PEARL_DRAG; vz *= PEARL_DRAG;
            vy -= PEARL_GRAVITY;
            x += vx; y += vy; z += vz;
        }
        return new Vec3d(x, y, z);
    }

    private static EnderPearlEntity findOurPearl(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        List<EnderPearlEntity> list = client.world.getEntitiesByClass(
            EnderPearlEntity.class,
            client.player.getBoundingBox().expand(128),
            p -> p.getOwner() == client.player   // OWN pearls only (Q3)
        );
        return list.isEmpty() ? null : list.get(0);
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() { reset(); }

    private static void reset() {
        trackedPearl = null; countdown = -1;
        fired = false; pearlWasAlive = false;
    }
}
