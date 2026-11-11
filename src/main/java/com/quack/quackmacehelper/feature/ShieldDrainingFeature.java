package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.entity.LivingEntity;

import java.util.Random;

/**
 * ShieldDraining — V4.1 redesign (pure modifier).
 *
 * Per the design settled in the original development chats:
 *   - It NEVER acts on its own. No tick(), no standalone targeting.
 *   - It only activates INSIDE ElytraSwapper (or any other attack
 *     sequence that asks for it) when a shield is detected.
 *   - While active it SUPPRESSES axe hits from the sequence and
 *     overrides the mace spam CPS with the configured value.
 *   - CPS is randomized by ±10 around the configured value every
 *     burst, for anti-cheat protection.
 *   - The burst runs for a minimum duration (1.5 s) no matter what,
 *     so it never stops "between" hits.
 */
public class ShieldDrainingFeature {

    private static final Random RNG = new Random();

    /** Minimum drain burst duration — the sequence may not stop early. */
    private static final long MIN_BURST_MS = 1500L;

    private static boolean active = false;
    private static long startMs = 0L;
    private static long burstCps = 12L;

    // ── Modifier API — called by ElytraSwapper ─────────────────────────────

    /** Start (or keep) a drain burst. Idempotent. */
    public static void start(LivingEntity t) {
        if (active) return; // never reset mid-burst
        int cfgCps = Math.max(1, ModConfig.get().shieldDraining.cps);
        // ±10 CPS variance, clamped to >= 1 — anti-cheat randomization
        int variance = RNG.nextInt(21) - 10;
        burstCps = Math.max(1L, cfgCps + variance);
        startMs = System.currentTimeMillis();
        active = true;
    }

    /** Milliseconds to wait between mace hits. Jitters slightly per call. */
    public static long getIntervalMs() {
        return (long) Math.ceil(1000.0 / burstCps) + RNG.nextInt(12);
    }

    /** True once the minimum burst duration has elapsed (sequence may stop). */
    public static boolean minDurationElapsed() {
        return System.currentTimeMillis() - startMs >= MIN_BURST_MS;
    }

    /** Ends the burst. Call when the owning sequence finishes. */
    public static void stop() {
        active = false;
    }

    // ── State accessors ─────────────────────────────────────────────────────

    public static boolean isActive() { return active; }
}
