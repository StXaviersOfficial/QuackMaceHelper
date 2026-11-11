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
 * PearlCatch — Stage 4 polish.
 *
 * Fires a wind charge to intercept the player's own ender pearl mid-flight,
 * knocking them upward at the pearl's landing point for a mace combo.
 *
 * Anti-cheat polish:
 *   - Intercept point gets a small random offset (±0.15 blocks) so the aim
 *     isn't a robotically perfect ballistic solution every single time.
 *   - Physics simulation unchanged (still accurate enough to hit).
 */
public class PearlCatchFeature {

    private static final Random RNG = new Random();

    private static EnderPearlEntity trackedPearl  = null;
    private static int              countdown      = -1;
    private static boolean          fired          = false;
    private static boolean          pearlWasAlive  = false;
    private static boolean          autoReady      = false;

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
            autoReady    = false;
            countdown    = cfg.pearlCatch.manualMode ? cfg.pearlCatch.delayTicks : -1;
        }

        if (fired) return;

        int wcSlot = InventoryHelper.findWindChargeSlot(player);
        if (wcSlot == -1) return;

        if (cfg.pearlCatch.manualMode) {
            if (countdown > 0) { countdown--; return; }
            fireAt(client, player, pearl, wcSlot, true);

        } else {
            double heightAbove = trackedPearl.getY() - player.getY();
            if (heightAbove < cfg.pearlCatch.triggerHeight) return;
            if (!autoReady) { autoReady = true; return; } // 1-tick buffer
            fireAt(client, player, pearl, wcSlot, false);
        }
    }

    /**
     * @param manual true when the user fired in manual mode — the camera is
     *               NEVER redirected at the pearl (the user aims themselves);
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
        int prevSlot = player.getInventory().selectedSlot;
        InventoryHelper.switchToSlot(client, wcSlot);
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        InventoryHelper.switchToSlot(client, prevSlot);
        fired = true;
    }

    private static Vec3d simulatePearl(double x, double y, double z,
                                        double vx, double vy, double vz, int ticks) {
        // Vanilla order: DRAG first, then gravity. The old gravity-then-drag
        // order damped gravity itself, so long flights drifted low.
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
            p -> p.getOwner() == client.player
        );
        return list.isEmpty() ? null : list.get(0);
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() { reset(); }

    private static void reset() {
        trackedPearl = null; countdown = -1;
        fired = false; pearlWasAlive = false; autoReady = false;
    }
}
