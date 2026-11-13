package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

import java.util.Random;

/**
 * ElytraMace — V5 flagship (replaces ElytraSwapper).
 *
 * ACTIVATION REQUIRES gliding with an elytra (firework boost optional) and
 * a chestplate in the hotbar (R4 — the equipped elytra counts as worn, the
 * chestplate is what must be in the hotbar for the swap). While gliding,
 * AutoMace stays idle (collision rule 2).
 *
 * AutoAim ON: scan the hemisphere BELOW the player inside the configured
 * FOV (60-360, default 180); engage this feature's own aim (Smooth or
 * Instant). The moment the target is on the crosshair (and close enough
 * that the post-swap fall still lands), in the FIRST TICK of the
 * engagement:
 *   1) swap the equipped elytra for the chestplate by selecting the
 *      chestplate's hotbar slot and right-clicking it (REAL equipment
 *      swap through the vanilla use path — no packets);
 *   2) target's shield UP  -> axe 5-6 hits within 1 tick, then best-damage
 *      mace 5-6 hits within the next 2 ticks, then end + release aim;
 *   3) target's shield DOWN -> straight to the best-damage mace, same
 *      burst, then end + release aim.
 *
 * AutoAim OFF: no scanning; while gliding 3+ blocks above a target (R3),
 * wait until the target crosses the crosshair, then the same sequence.
 *
 * The aim is released after EACH engagement; the feature stays enabled and
 * re-arms. If ShieldDraining owns the target, this feature provides AIM
 * ONLY (drain owns all attacks — no axe, no own mace hits).
 */
public class ElytraMaceFeature {

    private static final Random RNG = new Random();

    /** Scan/approach range while gliding. */
    private static final double SCAN_RANGE   = 6.0;
    /** Swap range: close enough that the post-swap fall still connects. */
    private static final double ENGAGE_RANGE = 4.0;
    /** Attack range (mace = 3 blocks). */
    private static final float  ATTACK_RANGE = 3.2f;
    /** Hard cap so a bad stop condition can never spam forever. */
    private static final long MAX_SEQUENCE_MS = 8000L;

    private enum Phase { IDLE, AIM, SWAP, AXE, MACE, DONE }
    private static Phase phase = Phase.IDLE;

    private static LivingEntity target = null;
    private static int  prevSlot      = -1;
    private static int  axeHitsLeft   = 0;
    private static int  maceHitsLeft  = 0;
    private static int  maceSpanTicks = 2;
    private static int  maceTick      = 0;
    private static long sequenceStartMs = 0L;

    public static boolean sequenceActive = false;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.elytraMace.enabled) { finish(client); return; }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) { finish(client); return; }
        // Q12: AlwaysOnShield triggered suspends every other feature.
        if (AlwaysOnShieldFeature.isTriggered()) { finish(client); return; }

        ClientPlayerEntity player = client.player;

        // ── IDLE: look for a trigger while GLIDING ──────────────────────
        if (phase == Phase.IDLE) {
            if (!player.isGliding()) return;

            // R4: chestplate in the hotbar
            if (InventoryHelper.findChestplateHotbarSlot(player) == -1) return;

            if (cfg.elytraMace.autoAim) {
                LivingEntity t = TargetHelper.findTargetBelowInFov(
                        client, SCAN_RANGE, cfg.elytraMace.fov);
                if (t == null) return;
                target = t;
                prevSlot = player.getInventory().getSelectedSlot();
                sequenceStartMs = System.currentTimeMillis();
                sequenceActive = true;
                phase = Phase.AIM;
            } else {
                // AutoAim OFF: wait for a target to cross the crosshair while
                // gliding 3+ blocks above it (R3).
                LivingEntity t = TargetHelper.getCrosshairTarget(client);
                if (t == null) return;
                if (!TargetHelper.isValidTarget(t, cfg)) return;
                if (!TargetHelper.is3Above(player, t)) return;
                if (t.getEntityPos().y >= player.getEntityPos().y) return;
                if (player.distanceTo(t) > ENGAGE_RANGE) return;
                target = t;
                prevSlot = player.getInventory().getSelectedSlot();
                sequenceStartMs = System.currentTimeMillis();
                sequenceActive = true;
                phase = Phase.SWAP;
            }
            return;
        }

        // ── Shared stop conditions for active phases ────────────────────
        boolean timedOut = System.currentTimeMillis() - sequenceStartMs > MAX_SEQUENCE_MS;
        if (target == null || !target.isAlive() || timedOut) { finish(client); return; }
        if (phase != Phase.SWAP && player.isOnGround()) { finish(client); return; }
        if (player.distanceTo(target) > SCAN_RANGE + 1.0) { finish(client); return; }

        switch (phase) {

            case AIM -> {
                if (!player.isGliding()) { finish(client); return; }

                // Feature aim: smooth budget or instant snap (per config)
                if (cfg.elytraMace.aimInstant) {
                    AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                } else {
                    AimAssistFeature.featureAimTick(client, target);
                }

                // On the crosshair AND close enough -> FIRST TICK: swap.
                boolean onCrosshair = AimAssistFeature.isLookingAt(client, target);
                if (onCrosshair && player.distanceTo(target) <= ENGAGE_RANGE) {
                    phase = Phase.SWAP;
                }
            }

            case SWAP -> {
                // FIRST TICK of the engagement: elytra -> chestplate via a
                // REAL right-click on the chestplate's hotbar slot.
                int chest = InventoryHelper.findChestplateHotbarSlot(player);
                if (chest != -1) {
                    InventoryHelper.switchToSlot(client, chest);
                    client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                }
                // (No chestplate found = requirement vanished mid-flight:
                // fall through — we are already committed and falling with a
                // chestplate-less hotbar still allows a plain mace smash.)

                boolean drainOwns = ShieldDrainingFeature.isTargetOwned(target);
                if (drainOwns) {
                    // Drain owns the attacks — we keep providing AIM only.
                    phase = Phase.AIM;
                    return;
                }

                boolean shield = InventoryHelper.isHoldingShield(target);
                if (shield) {
                    axeHitsLeft = 5 + RNG.nextInt(2);   // 5-6 axe hits
                    phase = Phase.AXE;
                } else {
                    beginMaceBurst();
                }
            }

            case AXE -> {
                int axe = InventoryHelper.findAxeSlot(player);
                if (axe == -1) { beginMaceBurst(); return; } // no axe — mace anyway
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
                if (mace == -1) { finish(client); return; }
                InventoryHelper.switchToSlot(client, mace);
                AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                if (player.distanceTo(target) <= ATTACK_RANGE) {
                    // Spread the 5-6 hits across the next 2 ticks.
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
                    phase = Phase.DONE;
                }
            }

            case DONE -> finish(client);

            default -> finish(client);
        }
    }

    private static void beginMaceBurst() {
        maceHitsLeft  = 5 + RNG.nextInt(2); // 5-6 mace hits
        maceSpanTicks = 2;                  // ... within the next 2 ticks
        maceTick      = 0;
        phase         = Phase.MACE;
    }

    /**
     * Completes the sequence: restores the previously held item (the elytra
     * stays in the hotbar after the swap — re-equipping it mid-fall would
     * risk gliding and negate the smash), releases this feature's aim,
     * re-arms for the next target.
     */
    private static void finish(MinecraftClient client) {
        if (sequenceActive || phase != Phase.IDLE || target != null) {
            if (client != null && client.player != null && prevSlot != -1) {
                int sword = InventoryHelper.findSwordSlot(client.player);
                InventoryHelper.switchToSlot(client, sword != -1 ? sword : prevSlot);
            }
        }
        AimAssistFeature.stopFeatureAim();
        phase = Phase.IDLE;
        target = null;
        prevSlot = -1;
        axeHitsLeft = 0;
        maceHitsLeft = 0;
        maceTick = 0;
        sequenceActive = false;
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset(MinecraftClient client) { finish(client); }
}
