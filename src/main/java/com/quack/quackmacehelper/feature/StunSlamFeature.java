package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

/**
 * StunSlam — Stage 2 rewrite.
 *
 * Triggers while player is falling + target in range + target has shield.
 *
 * Sequence:
 *   1. Axe hit (1 hit, to break shield blocking)
 *   2. Random 0–2 tick gap
 *   3. Mace hits: 4–5 times, with 0–2 tick random gap between each
 *
 * All aim via RandomAim.randomFull so no fixed aim point is detectable.
 *
 * When ElytraSwapper is active, StunSlam does NOT self-trigger — but
 * ElytraSwapper already handles axe+mace internally so they don't conflict.
 */
public class StunSlamFeature {

    private static final Random RNG = new Random();

    private enum Phase { IDLE, AXE, MACE }

    private static Phase phase = Phase.IDLE;
    private static LivingEntity target = null;
    private static int tickDelay = 0;
    private static int maceHitsLeft = 0;
    /** Hard cap so the MACE phase can never loop forever (no mace found /
     *  target kiting just out of reach used to spin the camera indefinitely). */
    private static long sequenceStartMs = 0L;
    private static final long MAX_SEQUENCE_MS = 4000L;
    /** Give-up range — past this the target has escaped. */
    private static final float GIVE_UP_RANGE = 4.5f;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.stunSlam.enabled) { reset(); return; }
        if (client.player == null || client.world == null) return;

        // Never run with a GUI open (anticheat safety).
        if (client.currentScreen != null) { reset(); return; }

        // Don't self-trigger while ElytraSwapper is running
        if (ElytraSwapperFeature.sequenceActive && phase == Phase.IDLE) return;

        ClientPlayerEntity player = client.player;

        if (phase == Phase.IDLE) {
            // Suppressed while Shield Drain is enabled: drain owns the
            // "shield up" scenario inside ElytraSwapper (no axe at all).
            if (cfg.shieldDraining.enabled) return;

            // Trigger condition: airborne and actually falling. NOTE:
            // fallDistance stays 0 while elytra-gliding (Minecraft resets
            // it during flight), so we also accept a clear downward velocity.
            boolean falling = !player.isOnGround()
                    && (player.fallDistance >= 1.5f || player.getVelocity().y < -0.3);
            if (!falling) return;
            // Elytra gliding is ElytraSwapper's job, not ours.
            if (player.isGliding()) return;

            LivingEntity t = findTarget(client, player);
            if (t == null) return;
            // StunSlam is the anti-shield combo — the target must actually
            // have the shield UP for the axe hit to matter.
            if (!InventoryHelper.isHoldingShield(t)) return;

            target = t;
            maceHitsLeft = 4 + RNG.nextInt(2); // 4 or 5
            sequenceStartMs = System.currentTimeMillis();
            phase = Phase.AXE;
            tickDelay = 0;
        }

        if (tickDelay > 0) { tickDelay--; return; }
        if (target == null || !target.isAlive()) { reset(); return; }

        switch (phase) {
            case AXE -> {
                // Aim randomly at target
                AimAssistFeature.elytraAimTick(client, target);

                int axe = InventoryHelper.findAxeSlot(player);
                if (axe != -1) {
                    InventoryHelper.switchToSlot(client, axe);
                    client.interactionManager.attackEntity(player, target);
                }
                phase = Phase.MACE;
                tickDelay = RNG.nextInt(3); // 0–2 ticks
            }

            case MACE -> {
                if (!target.isAlive()) { reset(); return; }

                // Exit guards — the old code could spin here forever with the
                // camera locked on when the mace was missing or the target
                // hovered just outside 3.5 blocks.
                if (System.currentTimeMillis() - sequenceStartMs > MAX_SEQUENCE_MS) { reset(); return; }
                if (player.isOnGround() && player.distanceTo(target) > GIVE_UP_RANGE) { reset(); return; }

                // Refresh random aim each hit
                AimAssistFeature.elytraAimTick(client, target);

                int mace = InventoryHelper.findDensityMaceSlot(player);
                if (mace == -1) mace = InventoryHelper.findBreachMaceSlot(player);
                if (mace == -1) mace = InventoryHelper.findAnyMaceSlot(player);

                if (mace == -1) { reset(); return; } // no mace — nothing to slam with

                if (player.distanceTo(target) <= 3.5f) {
                    InventoryHelper.switchToSlot(client, mace);
                    client.interactionManager.attackEntity(player, target);
                    maceHitsLeft--;
                }

                if (maceHitsLeft <= 0 || !target.isAlive()) {
                    reset();
                    return;
                }

                // 0–2 tick gap between mace hits
                tickDelay = RNG.nextInt(3);
            }

            default -> reset();
        }
    }

    private static LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity t = AimAssistFeature.getCurrentTarget();
        if (t == null) t = TargetHelper.findTarget(client, 3.5);
        if (t != null && t.isAlive() && player.distanceTo(t) <= 3.5f) return t;
        return null;
    }

    public static boolean isRunning() {
        return phase != Phase.IDLE;
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() { reset(); }

    private static void reset() {
        phase = Phase.IDLE;
        target = null;
        tickDelay = 0;
        maceHitsLeft = 0;
        AimAssistFeature.stopElytraAim();
    }
}
