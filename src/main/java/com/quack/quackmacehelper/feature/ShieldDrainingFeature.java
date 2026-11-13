package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

/**
 * ShieldDraining — V5: STANDALONE auto-fire feature (no longer just a
 * modifier inside the old ElytraSwapper).
 *
 * Conditions (all must hold):
 *  - the player is 3+ blocks ABOVE the target (R3 body heights),
 *  - the target's shield is RAISED,
 *  - the target is within attack range (3 blocks),
 *  - a mace is in the hotbar (R4).
 *
 * Behavior: WITHOUT waiting for any click from the player, auto-spam the
 * best-damage mace (DamageCalculator) at the configured CPS (20-250,
 * default 50), starting the SAME TICK the target enters range (a real
 * client cannot attack out of range — no "early" start), continuing while
 * the shield stays up. Every blocked 3+ HP hit costs the shield durability
 * equal to the attack damage — high-CPS falling mace hits shred a
 * 336-durability shield near-instantly.
 *
 * COLLISION: while it owns a target, StunSlam must not run at all and
 * AutoMace / ElytraMace only provide aim (no axe, no own attacks) — the
 * shield staying up is the point.
 */
public class ShieldDrainingFeature {

    private static final float ATTACK_RANGE = 3.05f;
    private static final double SCAN_RADIUS = 4.0;

    private static LivingEntity target = null;
    // V5.1: exact per-tick CPS accumulator (ported from the Gemini
    // comparison) — replaces the old millisecond-interval timing, which ran
    // slightly under rate at high CPS. attacks-per-tick = cps/20; the
    // fractional remainder carries into the next tick.
    private static double attackAccumulator = 0.0;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.shieldDraining.enabled) { stop(); return; }
        if (client.player == null || client.world == null) { stop(); return; }
        if (client.currentScreen != null) { stop(); return; }
        if (AlwaysOnShieldFeature.isTriggered()) { stop(); return; }

        ClientPlayerEntity player = client.player;

        // Keep or acquire a target. Priority: keep the current one while it
        // still qualifies; otherwise the nearest qualifying entity enters
        // range and draining starts the SAME tick.
        if (!qualifies(player, target)) {
            target = findTarget(client, player);
        }

        if (target == null) { // idle — no input state touched (R9)
            attackAccumulator = 0.0;
            return;
        }

        int mace = DamageCalculator.bestMaceSlot(player);
        if (mace == -1) { stop(); return; }
        InventoryHelper.switchToSlot(client, mace);

        // Fire at the EXACT configured CPS (accumulator carries fractions)
        attackAccumulator += cfg.shieldDraining.cps / 20.0;
        int hitsThisTick = (int) attackAccumulator;
        attackAccumulator -= hitsThisTick;
        for (int i = 0; i < hitsThisTick; i++) {
            client.interactionManager.attackEntity(player, target);
        }
    }

    private static boolean qualifies(ClientPlayerEntity player, LivingEntity t) {
        if (t == null || !t.isAlive()) return false;
        if (!TargetHelper.is3Above(player, t)) return false;
        if (!InventoryHelper.isHoldingShield(t)) return false;
        return player.distanceTo(t) <= ATTACK_RANGE;
    }

    private static LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity player) {
        return client.world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(SCAN_RADIUS), e ->
                        e != player && qualifies(player, e))
            .stream()
            .min((a, b) -> Double.compare(player.distanceTo(a), player.distanceTo(b)))
            .orElse(null);
    }

    /** True while ShieldDraining owns attack output for this target. */
    public static boolean isTargetOwned(LivingEntity t) {
        return target != null && target == t;
    }

    /** True while a drain burst is running at all. */
    public static boolean isActive() { return target != null; }

    /** Ends the burst (feature toggle off, disconnect, kill switch). */
    public static void stop() {
        target = null;
        attackAccumulator = 0.0;
    }

    /** Called by the client initializer when the config toggle flips. */
    public static void onToggled(boolean enabled) {
        if (!enabled) stop();
    }
}
