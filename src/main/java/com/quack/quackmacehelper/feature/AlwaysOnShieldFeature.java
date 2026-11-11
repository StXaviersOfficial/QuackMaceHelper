package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * AlwaysOnShield — Stage 3 rewrite.
 *
 * Equips shield (or totem) and holds USE when a threat is detected.
 *
 * Threat conditions (ANY of these):
 *   A. Opponent is gliding (isFallFlying) toward us within radius.
 *   B. Opponent is falling (vy < -0.1) AND within our FOV (120°) AND moving toward us.
 *
 * Both A and B use the dual check:
 *   - velocity dot-product toward us > 0.3
 *   - look direction dot-product toward us > 0.4
 *     (prevents triggering on someone flying past, looking away)
 *
 * Deactivates when no qualifying threat exists within radius.
 * Respects manual slot switch — if the player manually changes slot, deactivates.
 */
public class AlwaysOnShieldFeature {

    // Facing threshold — opponent's look vec dot-product toward us
    private static final double LOOK_THRESHOLD     = 0.4;
    // Velocity threshold — opponent's velocity dot-product toward us
    private static final double VELOCITY_THRESHOLD = 0.3;
    // FOV for condition B (falling opponent must be within our view)
    private static final float  OUR_FOV_DEG        = 120f;

    private static boolean active        = false;
    private static int     prevSlot      = -1;
    private static int     shieldSlot    = -1;
    private static int     lastKnownSlot = -1;

    public static void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.alwaysOnShield.enabled) { deactivate(client); return; }
        if (client.player == null || client.world == null) return;

        // Holding USE with a GUI open is impossible for a real player —
        // stand down instead of fighting the screen.
        if (client.currentScreen != null) { deactivate(client); return; }

        ClientPlayerEntity player = client.player;

        // Manual-switch detection: player changed away from the shield slot.
        // V4.1.1: the mod's OWN attack sequences (BreachSwap swap-back,
        // StunSlam, ElytraSwapper) also change slots — those are NOT manual
        // switches, so they no longer spurious-deactivate the shield.
        boolean modOwnsSlot = ElytraSwapperFeature.sequenceActive
                || StunSlamFeature.isRunning()
                || BreachSwapFeature.isSwapping();
        if (!modOwnsSlot && active && lastKnownSlot != -1
                && player.getInventory().selectedSlot != lastKnownSlot) {
            deactivate(client);
            return;
        }

        LivingEntity threat = findThreat(client, cfg.alwaysOnShield.radius);

        if (threat == null) {
            if (active) deactivate(client);
            return;
        }

        // Activate if not already active
        if (!active) {
            int ss = findInHotbar(player, net.minecraft.item.Items.SHIELD);
            int ts = findInHotbar(player, net.minecraft.item.Items.TOTEM_OF_UNDYING);
            int equipSlot = ss != -1 ? ss : ts;
            if (equipSlot == -1) return; // nothing to equip

            prevSlot  = player.getInventory().selectedSlot;
            shieldSlot = equipSlot;
            InventoryHelper.switchToSlot(client, equipSlot);
            lastKnownSlot = equipSlot;

            // Hold USE to block with shield
            if (ss != -1) client.options.useKey.setPressed(true);
            active = true;
        }

        // Keep shield raised every tick while threat is present
        if (shieldSlot != -1
                && player.getInventory().getStack(shieldSlot).getItem()
                   == net.minecraft.item.Items.SHIELD) {
            client.options.useKey.setPressed(true);
        }

        lastKnownSlot = player.getInventory().selectedSlot;
    }

    private static LivingEntity findThreat(MinecraftClient client, float radius) {
        if (client.player == null || client.world == null) return null;
        Vec3d ourPos = client.player.getPos();

        return client.world.getEntitiesByClass(PlayerEntity.class,
            client.player.getBoundingBox().expand(radius), e -> {
                if (e == client.player || !e.isAlive()) return false;
                if (client.player.distanceTo(e) > radius) return false;

                Vec3d toUs = ourPos.subtract(e.getPos()).normalize();

                // Velocity toward us
                Vec3d vel = e.getVelocity();
                if (vel.lengthSquared() < 0.001) return false;
                double velDot = vel.normalize().dotProduct(toUs);
                if (velDot < VELOCITY_THRESHOLD) return false;

                // Look direction toward us
                Vec3d look = e.getRotationVec(1.0f);
                double lookDot = look.dotProduct(toUs);
                if (lookDot < LOOK_THRESHOLD) return false;

                boolean gliding = e.isFallFlying();
                boolean falling = !e.isOnGround() && e.getVelocity().y < -0.1;

                // Condition A: gliding toward us
                if (gliding) return true;

                // Condition B: falling toward us AND within OUR fov
                if (falling && TargetHelper.isInFov(client, e, OUR_FOV_DEG)) return true;

                return false;
            })
            .stream()
            .min((a, b) -> Double.compare(
                client.player.distanceTo(a),
                client.player.distanceTo(b)))
            .orElse(null);
    }

    /** Public — called from the disconnect cleanup in QuackMaceHelperClient. */
    public static void hardReset(MinecraftClient client) { deactivate(client); }

    private static void deactivate(MinecraftClient client) {
        if (client.options != null) client.options.useKey.setPressed(false);
        if (client.player != null && prevSlot != -1)
            InventoryHelper.switchToSlot(client, prevSlot);
        active        = false;
        prevSlot      = -1;
        shieldSlot    = -1;
        lastKnownSlot = -1;
    }

    private static int findInHotbar(ClientPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }
}
