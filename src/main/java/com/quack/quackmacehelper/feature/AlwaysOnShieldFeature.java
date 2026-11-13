package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * AlwaysOnShield ("AutoShield") — V5 rewrite. Anti elytra-macer defense.
 *
 * Continuously scans a hemisphere (default 8 blocks wide) centered at the
 * player's level for players who are FALLING from 3+ blocks above the
 * player OR GLIDING from 3+ blocks above the player (R3 body heights; also
 * works while the player is in the air).
 *
 * On detection: instant snap-lock the aim onto the threat, raise and hold
 * the shield while looking at them (a REAL use-key hold — no packets). Keep
 * holding until the threat descends to player level +2 blocks (the mace
 * smash stops being possible below 1.5 blocks of height advantage), then
 * release and resume scanning.
 *
 * TRIGGERED-STATE RULE (Q12): while TRIGGERED, NO other feature may switch
 * slots or attack — they all check isTriggered() and stand down.
 * "Drop the shield for the player's own swing" is intentionally NOT
 * implemented (rejected by the owner).
 *
 * INPUT OWNERSHIP (R9, the 4.1.2 lesson): the use key is only ever
 * pressed while the hold is engaged and only released if WE pressed it.
 * The player's manual slot switch ends the hold and their slot choice WINS
 * (never yanked back).
 *
 * Shield source: offhand first (no slot switch needed), otherwise a hotbar
 * shield slot.
 */
public class AlwaysOnShieldFeature {

    // Release cutoff: threat descends to player level + 2 (confirmed Q14)
    private static final double RELEASE_HEIGHT = 2.0;
    private static final double TRIGGER_HEIGHT = 3.0;

    private static boolean triggered     = false;
    private static boolean useKeyHeldByUs = false;
    private static int     prevSlot      = -1;
    private static int     shieldSlot    = -1; // -1 = offhand shield
    private static int     lastKnownSlot = -1;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.alwaysOnShield.enabled) { deactivate(client, true); return; }
        if (client.player == null || client.world == null) return;

        // Holding USE with a GUI open is impossible for a real player —
        // stand down instead of fighting the screen.
        if (client.currentScreen != null) { deactivate(client, true); return; }

        ClientPlayerEntity player = client.player;

        if (triggered) {
            // Manual-switch detection: the player changed away from the
            // shield slot. The mod's OWN sequences don't run while we are
            // triggered (Q12 suspends them), so any change is manual.
            // The player's manual choice ALWAYS wins — deactivate without
            // restoring prevSlot (never yank it back).
            if (lastKnownSlot != -1
                    && player.getInventory().getSelectedSlot() != lastKnownSlot) {
                deactivate(client, false);
                return;
            }
        }

        LivingEntity threat = findThreat(client, cfg.alwaysOnShield.radius);

        if (threat == null) {
            if (triggered) deactivate(client, true);
            return;
        }

        if (!triggered) {
            // ── Engage ───────────────────────────────────────────────────
            ItemStack offhand = player.getOffHandStack();
            int hotbarShield = findInHotbar(player, Items.SHIELD);
            if (offhand.getItem() == Items.SHIELD) {
                shieldSlot = -1; // offhand — no slot switch needed
            } else if (hotbarShield != -1) {
                shieldSlot = hotbarShield;
                prevSlot = player.getInventory().getSelectedSlot();
                InventoryHelper.switchToSlot(client, shieldSlot);
            } else {
                return; // R4: no shield in hotbar or offhand -> nothing to do
            }

            // Instant snap-lock onto the threat (spec: no smoothing)
            AimAssistFeature.snapAimAt(client, RandomAim.randomFull(threat));

            // REAL use-key hold — we own it from now on (R9)
            client.options.useKey.setPressed(true);
            useKeyHeldByUs = true;
            triggered = true;
            lastKnownSlot = player.getInventory().getSelectedSlot();
            return;
        }

        // ── Hold: keep looking at the threat, keep the shield up ─────────
        AimAssistFeature.snapAimAt(client, RandomAim.randomFull(threat));
        client.options.useKey.setPressed(true);

        // Release when the threat descends to player level +2
        if (TargetHelper.heightAbove(player, threat) <= RELEASE_HEIGHT) {
            deactivate(client, true);
        }
    }

    /**
     * Threat = a player within the hemisphere, 3+ blocks ABOVE the player
     * (R3), currently falling or gliding.
     */
    private static LivingEntity findThreat(MinecraftClient client, float radius) {
        if (client.player == null || client.world == null) return null;
        ClientPlayerEntity player = client.player;

        return client.world.getEntitiesByClass(PlayerEntity.class,
                player.getBoundingBox().expand(radius), e -> {
                    if (e == player || !e.isAlive()) return false;
                    if (player.distanceTo(e) > radius) return false;
                    if (TargetHelper.heightAbove(player, e) < TRIGGER_HEIGHT) return false;
                    boolean gliding = e.isGliding();
                    boolean falling = !e.isOnGround() && e.getVelocity().y < -0.1;
                    return gliding || falling;
                })
                .stream()
                .min((a, b) -> Double.compare(player.distanceTo(a), player.distanceTo(b)))
                .orElse(null);
    }

    /** Public — called from the disconnect cleanup + kill switch. */
    public static void hardReset(MinecraftClient client) { deactivate(client, true); }

    /** Q12: true while the shield hold is engaged — other features suspend. */
    public static boolean isTriggered() { return triggered; }

    /**
     * V4.1.2 rule (the "can't eat" fix): a complete NO-OP unless this
     * feature was actually engaged. Only touches input state it owns.
     *
     * @param restoreSlot false when the player manually switched slots —
     *                    their manual choice wins, never yank it back.
     */
    private static void deactivate(MinecraftClient client, boolean restoreSlot) {
        boolean wasTriggered = triggered;
        triggered = false;
        if (wasTriggered && client != null && client.options != null && useKeyHeldByUs) {
            client.options.useKey.setPressed(false);
            useKeyHeldByUs = false;
        }
        if (wasTriggered && restoreSlot && client != null
                && client.player != null && prevSlot != -1 && shieldSlot != -1) {
            InventoryHelper.switchToSlot(client, prevSlot);
        }
        prevSlot = -1;
        shieldSlot = -1;
        lastKnownSlot = -1;
    }

    private static int findInHotbar(ClientPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }
}
