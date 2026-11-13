package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

/**
 * AutoMace — V5. THE one falling-attack feature (ElytraMace's attack
 * sequence WITHOUT the elytra part; behaves like the owner's "Elite Maze"
 * reference but is not a copy).
 *
 * Triggers only while the player is FALLING (not gliding, not grounded)
 * from 3+ blocks above a target (R3 body heights). Already falling means
 * the smash already counts — NO elytra/chestplate swap happens.
 *
 * Auto aim ON: scan the hemisphere BELOW the player inside the configured
 * FOV (60-360, default 180) and engage the selected aim type (Smooth or
 * Instant). Auto aim OFF: wait until the target crosses the crosshair.
 *
 * The moment the target is on the crosshair, run ElytraMace's exact burst:
 *   - shield UP   -> axe 5-6 hits within 1 tick, then best-damage mace 5-6
 *                    hits within the next 2 ticks, then release the aim;
 *   - shield DOWN -> best-damage mace directly, same 5-6-hit burst.
 *
 * ONE precise burst per engagement — no continued spam afterwards. If
 * ShieldDraining owns the target, this feature provides AIM ONLY. Idle
 * while gliding (ElytraMace owns gliding). Suspended while AlwaysOnShield
 * is triggered (Q12).
 */
public class AutoMaceFeature {

    private static final Random RNG = new Random();

    private static final double SCAN_RANGE   = 4.5;
    private static final float  ATTACK_RANGE = 3.2f;
    private static final long   MAX_SEQUENCE_MS = 4000L;

    private enum Phase { IDLE, AIM, AXE, MACE, DONE, COOLDOWN }
    private static Phase phase = Phase.IDLE;

    private static LivingEntity target = null;
    private static LivingEntity lastBurstTarget = null;
    private static int  axeHitsLeft   = 0;
    private static int  maceHitsLeft  = 0;
    private static int  maceSpanTicks = 2;
    private static int  maceTick      = 0;
    private static long sequenceStartMs = 0L;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.autoMace.enabled) { reset(); return; }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) { reset(); return; }
        // Q12: AlwaysOnShield triggered suspends every other feature.
        if (AlwaysOnShieldFeature.isTriggered()) { reset(); return; }

        ClientPlayerEntity player = client.player;

        // Falling state only — gliding belongs to ElytraMace (collision
        // rule 2), grounded is not a smash setup for this feature.
        boolean falling = !player.isOnGround() && !player.isGliding()
                && (player.fallDistance >= 1.5f || player.getVelocity().y < -0.3);

        // ── IDLE: look for a trigger while FALLING 3+ above a target ────
        if (phase == Phase.IDLE) {
            if (!falling) { lastBurstTarget = null; return; }
            // R4: mace in the hotbar
            if (InventoryHelper.findAnyMaceSlot(player) == -1) return;

            if (cfg.autoMace.autoAim) {
                LivingEntity t = TargetHelper.findTargetBelowInFov(
                        client, SCAN_RANGE, cfg.autoMace.fov);
                if (t == null) return;
                if (!TargetHelper.is3Above(player, t)) return;
                if (t == lastBurstTarget) return; // one burst per engagement
                target = t;
                sequenceStartMs = System.currentTimeMillis();
                phase = Phase.AIM;
            } else {
                LivingEntity t = TargetHelper.getCrosshairTarget(client);
                if (t == null) return;
                if (!TargetHelper.isValidTarget(t, cfg)) return;
                if (!TargetHelper.is3Above(player, t)) return;
                if (t.getEntityPos().y >= player.getEntityPos().y) return;
                if (t == lastBurstTarget) return;
                target = t;
                sequenceStartMs = System.currentTimeMillis();
                phase = Phase.AXE; // on the crosshair already — burst now
            }
            return;
        }

        if (phase == Phase.COOLDOWN) {
            // Re-arm once the engagement is clearly over: player landed or
            // the target is gone — then the next fall can engage anything.
            if (player.isOnGround() || target == null || !target.isAlive()) {
                lastBurstTarget = null;
                phase = Phase.IDLE;
            }
            return;
        }

        // ── Shared stop conditions for active phases ────────────────────
        boolean timedOut = System.currentTimeMillis() - sequenceStartMs > MAX_SEQUENCE_MS;
        if (target == null || !target.isAlive() || timedOut || player.isOnGround()) {
            reset();
            return;
        }

        switch (phase) {

            case AIM -> {
                if (!falling) { reset(); return; }

                if (cfg.autoMace.aimInstant) {
                    AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                } else {
                    AimAssistFeature.featureAimTick(client, target);
                }

                boolean onCrosshair = AimAssistFeature.isLookingAt(client, target);
                boolean inRange = player.distanceTo(target) <= ATTACK_RANGE;
                if (onCrosshair && inRange) {
                    // ShieldDraining owns the attacks? Aim only.
                    if (ShieldDrainingFeature.isTargetOwned(target)) return;

                    if (InventoryHelper.isHoldingShield(target)) {
                        axeHitsLeft = 5 + RNG.nextInt(2);
                        phase = Phase.AXE;
                    } else {
                        beginMaceBurst();
                    }
                }
            }

            case AXE -> {
                int axe = InventoryHelper.findAxeSlot(player);
                if (axe == -1) { beginMaceBurst(); return; }
                InventoryHelper.switchToSlot(client, axe);
                AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                // 5-6 axe hits WITHIN 1 tick: all in this tick.
                if (player.distanceTo(target) <= ATTACK_RANGE) {
                    int burst = Math.max(1, axeHitsLeft);
                    for (int i = 0; i < burst; i++) {
                        client.interactionManager.attackEntity(player, target);
                    }
                }
                axeHitsLeft = 0;
                beginMaceBurst();
            }

            case MACE -> {
                int mace = DamageCalculator.bestMaceSlot(player);
                if (mace == -1) { finishBurst(); return; }
                InventoryHelper.switchToSlot(client, mace);
                AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                if (player.distanceTo(target) <= ATTACK_RANGE) {
                    int remainingTicks = Math.max(1, maceSpanTicks - maceTick);
                    int hitsThisTick = (int) Math.ceil((double) maceHitsLeft / remainingTicks);
                    for (int i = 0; i < hitsThisTick && maceHitsLeft > 0; i++) {
                        client.interactionManager.attackEntity(player, target);
                        maceHitsLeft--;
                    }
                } else {
                    maceHitsLeft = 0;
                }
                maceTick++;
                if (maceHitsLeft <= 0 || maceTick >= maceSpanTicks) {
                    finishBurst();
                }
            }

            case DONE -> finishBurst();

            default -> reset();
        }
    }

    private static void beginMaceBurst() {
        maceHitsLeft  = 5 + RNG.nextInt(2); // 5-6 mace hits
        maceSpanTicks = 2;                  // ... within the next 2 ticks
        maceTick      = 0;
        phase         = Phase.MACE;
    }

    /** ONE precise burst per engagement: release the aim, re-arm. */
    private static void finishBurst() {
        lastBurstTarget = target;
        AimAssistFeature.stopFeatureAim();
        phase = Phase.COOLDOWN;
        maceHitsLeft = 0;
        maceTick = 0;
    }

    public static boolean isRunning() { return phase != Phase.IDLE && phase != Phase.COOLDOWN; }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() { reset(); }

    private static void reset() {
        phase = Phase.IDLE;
        target = null;
        lastBurstTarget = null;
        axeHitsLeft = 0;
        maceHitsLeft = 0;
        maceTick = 0;
        AimAssistFeature.stopFeatureAim();
    }
}
