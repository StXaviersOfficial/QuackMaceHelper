package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

/**
 * StunSlam — V5 rewrite.
 *
 * Constantly watches the player UNDER THE CROSSHAIR for a raised shield.
 * No shield -> the feature does nothing at all.
 *
 * Shield UP + the player attacks:
 *  - GROUND CASE (player on ground / jumping / less than 3 blocks above the
 *    target): same tick switch to axe -> axe hit lands SAME tick OR next
 *    tick (random 0-1) -> switch back to the held item.
 *  - FALLING CASE (player 3+ blocks above the target, R3 body heights):
 *    same tick switch to axe -> 4-5 axe hits within the next 0-1 ticks ->
 *    switch to the best-damage mace (DamageCalculator) -> 5-6 mace hits
 *    within the next 2-3 ticks -> NEXT tick switch back to the original.
 *
 * COLLISION:
 *  - ShieldDraining enabled + target shielded -> StunSlam must NOT run at
 *    all (spec rev 3, collision rule 5, taken literally: applies to BOTH
 *    height cases, even when the drain itself is not currently in range —
 *    no axe hits while draining is the point; the shield stays up).
 *  - AttributeSwap owns the same-tick swap chain: if it fires on the same
 *    hit, StunSlam queues behind it (starts the next tick).
 */
public class StunSlamFeature {

    private static final Random RNG = new Random();

    private enum Phase { IDLE, PENDING, AXE, MACE_GAP, MACE, RESTORE }
    private static Phase phase = Phase.IDLE;

    private static LivingEntity target = null;
    private static int  tickDelay      = 0;
    private static int  axeHitsLeft    = 0;
    private static int  maceHitsLeft   = 0;
    private static int  maceSpanTicks  = 2;   // 2-3 randomized
    private static int  maceCurrentTick = 0;
    private static int  prevSlot       = -1;
    private static long sequenceStartMs = 0L;
    private static final long MAX_SEQUENCE_MS = 6000L;
    private static final float ATTACK_RANGE   = 3.2f;

    /** Called from the attack mixin on the player's own attacks. */
    public static void onAttack(MinecraftClient client, LivingEntity hitTarget) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.stunSlam.enabled) return;
        if (phase != Phase.IDLE) return;
        if (client.player == null || client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;

        // Only reacts to the crosshair-watched target having a raised shield.
        // (The mixin target IS the crosshair target in practice — the player
        // attacks what they are looking at.)
        if (hitTarget == null || !hitTarget.isAlive()) return;
        if (!TargetHelper.isValidTarget(hitTarget, cfg)) return;
        if (!InventoryHelper.isHoldingShield(hitTarget)) return;

        // COLLISION: ShieldDraining owns the shielded scenario entirely
        // (V5.1: spec-literal — enabled + shield up means NO StunSlam axe,
        // both height cases, even if the drain is not yet in range).
        if (cfg.shieldDraining.enabled) return;
        // Other owning sequences suppress StunSlam.
        if (ElytraMaceFeature.sequenceActive || AutoMaceFeature.isRunning()) return;
        if (AlwaysOnShieldFeature.isTriggered()) return;

        boolean falling3Above = TargetHelper.is3Above(player, hitTarget)
                && !player.isOnGround();

        target = hitTarget;
        prevSlot = player.getInventory().getSelectedSlot();
        sequenceStartMs = System.currentTimeMillis();

        if (falling3Above) {
            // FALLING CASE: axe 4-5 hits in 0-1 ticks, then mace 5-6 in 2-3.
            axeHitsLeft  = 4 + RNG.nextInt(2);
            maceHitsLeft = 5 + RNG.nextInt(2);
            maceSpanTicks = 2 + RNG.nextInt(2);
            phase = Phase.PENDING; // queue behind AttributeSwap if it fired
            tickDelay = AttributeSwapFeature.isSwapping() ? 1 : RNG.nextInt(2);
        } else {
            // GROUND CASE: one axe hit, same tick or next (random 0-1).
            axeHitsLeft  = 1;
            maceHitsLeft = 0;
            phase = Phase.PENDING;
            tickDelay = AttributeSwapFeature.isSwapping() ? 1 : RNG.nextInt(2);
        }
    }

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.stunSlam.enabled) { reset(); return; }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) { reset(); return; }

        if (phase == Phase.IDLE) return;

        ClientPlayerEntity player = client.player;

        // Hard stops
        if (target == null || !target.isAlive()
                || System.currentTimeMillis() - sequenceStartMs > MAX_SEQUENCE_MS) {
            reset();
            return;
        }
        // Collision re-check while running (drain may have been enabled
        // mid-sequence — same spec-literal rule, both height cases)
        if (cfg.shieldDraining.enabled) {
            reset();
            return;
        }

        if (tickDelay > 0) { tickDelay--; return; }

        switch (phase) {
            case PENDING -> {
                int axe = InventoryHelper.findAxeSlot(player);
                if (axe == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, axe);
                phase = Phase.AXE;
            }

            case AXE -> {
                int axe = InventoryHelper.findAxeSlot(player);
                if (axe == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, axe);
                AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                if (player.distanceTo(target) <= ATTACK_RANGE) {
                    client.interactionManager.attackEntity(player, target);
                    axeHitsLeft--;
                } else {
                    axeHitsLeft = 0;
                }
                if (axeHitsLeft <= 0) {
                    if (maceHitsLeft > 0) {
                        phase = Phase.MACE_GAP;
                        tickDelay = RNG.nextInt(2); // 0-1 tick gap after axe burst
                    } else {
                        phase = Phase.RESTORE;
                        tickDelay = 0;
                    }
                }
                // Remaining axe hits stay inside the same 0-1 tick window:
                // extra hits land next tick at the latest.
                if (axeHitsLeft > 0) tickDelay = Math.min(tickDelay, 1);
            }

            case MACE_GAP -> {
                int mace = DamageCalculator.bestMaceSlot(player);
                if (mace == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, mace);
                phase = Phase.MACE;
                maceCurrentTick = 0;
            }

            case MACE -> {
                int mace = DamageCalculator.bestMaceSlot(player);
                if (mace == -1) { reset(); return; }
                InventoryHelper.switchToSlot(client, mace);
                AimAssistFeature.snapAimAt(client, RandomAim.randomFull(target));
                if (player.distanceTo(target) <= ATTACK_RANGE) {
                    // Spread the 5-6 hits evenly across the 2-3 tick span
                    int remainingTicks = Math.max(1, maceSpanTicks - maceCurrentTick);
                    int hitsThisTick = (int) Math.ceil((double) maceHitsLeft / remainingTicks);
                    for (int i = 0; i < hitsThisTick && maceHitsLeft > 0; i++) {
                        client.interactionManager.attackEntity(player, target);
                        maceHitsLeft--;
                    }
                } else {
                    maceHitsLeft = 0;
                }
                maceCurrentTick++;
                if (maceHitsLeft <= 0 || maceCurrentTick >= maceSpanTicks) {
                    phase = Phase.RESTORE;
                    tickDelay = 1; // swap back the NEXT tick (spec)
                }
            }

            case RESTORE -> {
                if (prevSlot != -1) {
                    int sword = InventoryHelper.findSwordSlot(player);
                    InventoryHelper.switchToSlot(client, sword != -1 ? sword : prevSlot);
                }
                reset();
            }
        }
    }

    public static boolean isRunning() { return phase != Phase.IDLE; }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset() { reset(); }

    private static void reset() {
        phase = Phase.IDLE;
        target = null;
        tickDelay = 0;
        axeHitsLeft = 0;
        maceHitsLeft = 0;
        prevSlot = -1;
    }
}
