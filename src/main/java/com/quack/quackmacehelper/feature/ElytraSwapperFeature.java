package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;

import java.util.Random;

/**
 * ElytraSwapper — V4.1 rewrite.
 *
 * Sequence:
 *   IDLE  → gliding, target within TRIGGER_RADIUS, inside the configured FOV
 *           (270° cone recommended — won't trigger while flying away from the
 *           target), not climbing steeply at a target above us.
 *   AIM   → random-point aim (additive, never locks player input) + chestplate
 *           swap via the game's own clickSlot mechanism (no raw packets).
 *   WAIT  → random 0–2 tick delay so the server has processed the swap.
 *   FIGHT → shield detected + ShieldDrain enabled:
 *             no axe at all, mace spam at drain CPS (±10 variance),
 *             minimum 1.5 s non-stop, then normal stop conditions.
 *           shield detected + ShieldDrain disabled:
 *             axe 2–3 hits first, then mace at 3–4 CPS.
 *           no shield: mace spam at 3–4 CPS.
 *   STOP  → target dead / out of range / player lands / player reaches the
 *           target's level (Y within 0.5 blocks) / max duration.
 *           ONLY THEN switch back to the previously held slot (sword).
 *
 * All actions are vanilla calls: switchToSlot = hotbar select,
 * attackEntity = standard left-click, clickSlot SWAP = the F-key swap.
 * Nothing injected, nothing custom.
 */
public class ElytraSwapperFeature {

    private static final Random RNG = new Random();

    // Armor slot index in the player screen handler: 5=head, 6=chest, 7=legs, 8=feet
    private static final int CHEST_ARMOR_SCREEN_SLOT = 6;

    /** Target acquisition / aim range while gliding. */
    private static final float TRIGGER_RADIUS = 5.0f;
    /** Absolute give-up range during FIGHT (target sprinting away). */
    private static final float FIGHT_MAX_RANGE = 6.5f;
    /** Hard cap so a bad stop condition can never spam forever. */
    private static final long MAX_SEQUENCE_MS = 8000L;

    private enum Phase { IDLE, AIM, WAIT, FIGHT }
    private static Phase phase = Phase.IDLE;

    private static int tickDelay = 0;
    private static LivingEntity target = null;
    private static boolean didAxePhase = false;
    private static int axeHitsLeft = 0;
    private static long axeIntervalMs = 0L;
    private static long lastHitMs = 0L;
    private static long maceIntervalMs = 300L; // 3–4 CPS, randomized per sequence
    private static long sequenceStartMs = 0L;
    private static int prevSlot = -1;

    public static boolean sequenceActive = false;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.elytraSwapper.enabled) { finish(client); return; }
        if (client.player == null || client.world == null) return;

        // Never fight with a GUI open — abort cleanly (anticheat safety).
        if (client.currentScreen != null) { finish(client); return; }

        ClientPlayerEntity player = client.player;

        // Must be gliding while the sequence is being prepared. Once FIGHT
        // starts we keep going until a stop condition fires (landing etc.).
        if (!player.isFallFlying() && (phase == Phase.AIM || phase == Phase.WAIT)) {
            finish(client);
            return;
        }

        // ── IDLE: look for trigger ───────────────────────────────────────────
        if (phase == Phase.IDLE) {
            double vy = player.getVelocity().y;
            if (vy > 0.15) return; // climbing hard — never trigger

            LivingEntity t = findTarget(client, cfg);
            if (t == null) return;

            // Climbing slightly is only OK when the target is below us
            if (vy > 0 && t.getY() >= player.getY()) return;

            target = t;
            prevSlot = player.getInventory().selectedSlot;
            didAxePhase = false;
            axeHitsLeft = 0;
            // 3–4 CPS, randomized once per sequence
            int cps = 3 + RNG.nextInt(2);
            maceIntervalMs = 1000L / cps;
            lastHitMs = 0L;
            sequenceStartMs = System.currentTimeMillis();
            sequenceActive = true;
            phase = Phase.AIM;
            tickDelay = 1;
            return;
        }

        // Tick delay gate
        if (tickDelay > 0) { tickDelay--; return; }

        // Guard: target must still be valid
        if (target == null || !target.isAlive()) { finish(client); return; }

        switch (phase) {

            case AIM -> {
                if (cfg.elytraSwapper.autoAim) {
                    AimAssistFeature.elytraAimTick(client, target);
                }
                int chestSlot = findChestplateHotbarSlot(player);
                if (chestSlot != -1) {
                    doChestplateSwap(client, player, chestSlot);
                }
                phase = Phase.WAIT;
                tickDelay = RNG.nextInt(3); // 0–2 ticks for server round-trip
            }

            case WAIT -> {
                phase = Phase.FIGHT;
                tickDelay = 0;
            }

            case FIGHT -> {
                long nowMs = System.currentTimeMillis();

                // ── Stop conditions ─────────────────────────────────────
                // Target death and the hard cap always end the sequence.
                boolean hardStop = !target.isAlive()
                        || nowMs - sequenceStartMs > MAX_SEQUENCE_MS;

                // Soft stops (landing / out of range / reached their level)
                // are suppressed while a ShieldDrain burst is still inside
                // its 1.5 s minimum — the burst must never be cut short.
                boolean drainHold = ShieldDrainingFeature.isActive()
                        && !ShieldDrainingFeature.minDurationElapsed();

                if (!drainHold) {
                    if (player.isOnGround())                         { finish(client); return; } // landed
                    if (player.distanceTo(target) > FIGHT_MAX_RANGE) { finish(client); return; }
                    // Dive geometry: we come from ABOVE. Stop once we have
                    // fallen to the target's level (within +0.5 above their
                    // feet). The old >= comparison was inverted — it aborted
                    // on the FIRST tick of every normal dive attack.
                    if (player.getY() <= target.getY() + 0.5f)       { finish(client); return; }
                }
                if (hardStop) { finish(client); return; }

                boolean shield = InventoryHelper.isHoldingShield(target);
                boolean drain = shield && cfg.shieldDraining.enabled;

                // ShieldDrain modifier takes over: no axe, drain CPS, min 1.5 s
                if (drain) ShieldDrainingFeature.start(target);

                // Refresh random aim before every hit (additive, input never locked)
                if (cfg.elytraSwapper.autoAim) {
                    AimAssistFeature.elytraAimTick(client, target);
                }

                // ── Axe opener: only when shield is up AND drain is OFF ──
                if (shield && !drain && !didAxePhase) {
                    didAxePhase = true;
                    axeHitsLeft = 2 + RNG.nextInt(2); // 2–3 axe hits
                    axeIntervalMs = 1000L / (3 + RNG.nextInt(2));
                    lastHitMs = nowMs; // axe phase starts now
                }
                if (axeHitsLeft > 0) {
                    if (nowMs - lastHitMs >= axeIntervalMs) {
                        int axe = InventoryHelper.findAxeSlot(player);
                        if (axe != -1) {
                            InventoryHelper.switchToSlot(client, axe);
                            client.interactionManager.attackEntity(player, target);
                        }
                        axeHitsLeft--;
                        lastHitMs = nowMs;
                    }
                    return; // finish axe phase before any mace hit
                }

                // ── Mace spam ───────────────────────────────────────────
                long interval = drain ? ShieldDrainingFeature.getIntervalMs() : maceIntervalMs;
                if (nowMs - lastHitMs >= interval) {
                    int mace = InventoryHelper.findDensityMaceSlot(player);
                    if (mace == -1) mace = InventoryHelper.findBreachMaceSlot(player);
                    if (mace == -1) mace = InventoryHelper.findAnyMaceSlot(player);
                    if (mace == -1) { finish(client); return; }

                    if (player.distanceTo(target) <= 3.5f) {
                        InventoryHelper.switchToSlot(client, mace);
                        client.interactionManager.attackEntity(player, target);
                    }
                    lastHitMs = nowMs;
                }
            }

            default -> finish(client);
        }
    }

    /**
     * Completes the sequence: ends the drain burst, restores the previously
     * held slot (sword). Only called on real completion / abort — never on
     * idle ticks, so the sword switch can never fire spuriously.
     */
    private static void finish(MinecraftClient client) {
        if (sequenceActive || phase != Phase.IDLE || target != null) {
            if (client != null && client.player != null && prevSlot != -1) {
                // Put the elytra back on (the chestplate SWAP during AIM left
                // it in the hotbar) — same clickSlot mechanism, reversed.
                swapElytraBackOn(client, client.player);
                // Prefer a sword if one exists (that is what we came from),
                // otherwise restore the exact slot we started with.
                int sword = InventoryHelper.findSwordSlot(client.player);
                InventoryHelper.switchToSlot(client, sword != -1 ? sword : prevSlot);
            }
        }
        AimAssistFeature.stopElytraAim();
        ShieldDrainingFeature.stop();
        phase = Phase.IDLE;
        tickDelay = 0;
        target = null;
        didAxePhase = false;
        axeHitsLeft = 0;
        lastHitMs = 0L;
        prevSlot = -1;
        sequenceActive = false;
    }

    /** Public hard reset — called when the player disconnects. */
    public static void hardReset(MinecraftClient client) { finish(client); }

    /**
     * After the fight the chestplate is on the chest and the elytra sits in
     * the hotbar. Swap them back so the player is ready for the next glide.
     */
    private static void swapElytraBackOn(MinecraftClient client, ClientPlayerEntity player) {
        int elytraSlot = InventoryHelper.findElytraInHotbar(player);
        if (elytraSlot == -1) return;
        // Only makes sense if the chest slot currently holds a chestplate
        // (ElytraItem is NOT an ArmorItem, so an elytra already on passes).
        if (!(player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem)) return;
        client.interactionManager.clickSlot(
            player.currentScreenHandler.syncId,
            CHEST_ARMOR_SCREEN_SLOT,
            elytraSlot,
            net.minecraft.screen.slot.SlotActionType.SWAP,
            player
        );
    }

    /**
     * Swaps a hotbar chestplate into the chest armor slot using the game's
     * own inventory swap mechanism (same as pressing F on a slot). Goes
     * through ClientPlayerInteractionManager, so both client and server
     * process it in the normal tick order — no desync.
     */
    private static void doChestplateSwap(MinecraftClient client,
                                          ClientPlayerEntity player, int hotbarSlot) {
        // SWAP uses the hotbar number passed as "button" — the selected slot
        // is irrelevant, so the old set/restore around this call was a no-op
        // (same-tick write + restore never sent any packet) and is gone.
        client.interactionManager.clickSlot(
            player.currentScreenHandler.syncId,
            CHEST_ARMOR_SCREEN_SLOT,
            hotbarSlot,
            net.minecraft.screen.slot.SlotActionType.SWAP,
            player
        );
    }

    private static LivingEntity findTarget(MinecraftClient client, ModConfig cfg) {
        if (client.player == null || client.world == null) return null;

        // Prefer AimAssist's current lock if it's in range and in FOV
        LivingEntity aaTarget = AimAssistFeature.getCurrentTarget();
        if (aaTarget != null && aaTarget.isAlive()
                && client.player.distanceTo(aaTarget) <= TRIGGER_RADIUS
                && TargetHelper.isInFov(client, aaTarget, cfg.elytraSwapper.fov)) {
            return aaTarget;
        }

        return client.world.getEntitiesByClass(LivingEntity.class,
            client.player.getBoundingBox().expand(TRIGGER_RADIUS), e ->
                e != client.player
                && e.isAlive()
                && TargetHelper.isValidTarget(e, cfg)
                && client.player.distanceTo(e) <= TRIGGER_RADIUS
                && TargetHelper.isInFov(client, e, cfg.elytraSwapper.fov)
        ).stream()
        .min((a, b) -> Double.compare(
            client.player.distanceTo(a),
            client.player.distanceTo(b)))
        .orElse(null);
    }

    /** Best chestplate in the hotbar — tier-ordered, not first-found. */
    private static final Item[] CHESTPLATE_TIERS = {
        Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE,
        Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE
    };

    private static int findChestplateHotbarSlot(ClientPlayerEntity player) {
        for (Item tier : CHESTPLATE_TIERS) {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).getItem() == tier) return i;
            }
        }
        return -1;
    }
}
