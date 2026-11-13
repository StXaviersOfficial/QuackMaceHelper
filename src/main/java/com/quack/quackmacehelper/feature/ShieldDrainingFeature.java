package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

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

    private static final Random RNG = new Random();
    private static final float ATTACK_RANGE = 3.05f;
    private static final double SCAN_RADIUS = 4.0;

    private static LivingEntity target   = null;
    private static long  lastHitMs       = 0L;
    private static int   burstCps        = 50;

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

        if (target == null) return; // idle — no input state touched (R9)

        // Fire at the configured CPS (±10 randomized per burst for anti-cheat)
        long intervalMs = (long) Math.ceil(1000.0 / burstCps) + RNG.nextInt(3);
        long now = System.currentTimeMillis();
        if (now - lastHitMs >= intervalMs) {
            int mace = DamageCalculator.bestMaceSlot(player);
            if (mace == -1) { stop(); return; }
            InventoryHelper.switchToSlot(client, mace);
            client.interactionManager.attackEntity(player, target);
            lastHitMs = now;
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
        lastHitMs = 0L;
    }

    /** Called by the client initializer when the config toggle flips. */
    public static void onToggled(boolean enabled) {
        if (!enabled) stop();
        else burstCps = ModConfig.get().shieldDraining.cps;
    }
}
