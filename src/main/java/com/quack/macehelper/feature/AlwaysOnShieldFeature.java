package com.quack.macehelper.feature;

import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

public class AlwaysOnShieldFeature {
    private boolean triggered = false;
    private boolean holdingUseKey = false;
    private PlayerEntity activeThreat = null;
    private int lockedSlot = -1;

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.alwaysOnShieldEnabled || client.player == null || client.world == null) {
            onDisable();
            return;
        }

        ClientPlayerEntity player = client.player;
        if (!PlayerActionHelper.hasShieldInHotbarOrOffhand(player)) {
            onDisable();
            return;
        }

        // Collision Rule 9: Manual slot switch ends the hold and player's slot choice WINS
        if (triggered && lockedSlot != -1 && player.getInventory().getSelectedSlot() != lockedSlot) {
            releaseHold(client);
            return;
        }

        if (triggered) {
            // Keep holding until threat descends to <= 2.0 blocks above player's level or becomes invalid
            if (activeThreat == null || !activeThreat.isAlive()
                    || PlayerActionHelper.getBodyHeightDifference(activeThreat, player) <= 2.0
                    || horizontalDistance(player, activeThreat) > 8.0) {
                PlayerEntity nextThreat = scanForOverheadThreat(client, player);
                if (nextThreat != null) {
                    activeThreat = nextThreat;
                } else {
                    releaseHold(client);
                    return;
                }
            }

            engageShieldLock(client, player, activeThreat);
        } else {
            PlayerEntity threat = scanForOverheadThreat(client, player);
            if (threat != null) {
                activeThreat = threat;
                triggered = true;
                engageShieldLock(client, player, activeThreat);
            }
        }
    }

    private void engageShieldLock(MinecraftClient client, ClientPlayerEntity player, PlayerEntity threat) {
        // Equip shield in main hand if not already in offhand
        if (!player.getOffHandStack().isOf(Items.SHIELD)) {
            int shieldSlot = PlayerActionHelper.findShieldInHotbar(player);
            if (shieldSlot == -1) {
                releaseHold(client);
                return;
            }
            PlayerActionHelper.selectHotbarSlot(client, shieldSlot);
        }
        lockedSlot = player.getInventory().getSelectedSlot();

        // Instantly snap-lock aim onto the threat
        RotationController.acquireAim(RotationController.AimOwner.ALWAYS_ON_SHIELD);
        RotationController.applyInstantSnap(client, threat.getEyePos());

        // Raise and hold shield via real use-key hold, tracking strict input ownership
        client.options.useKey.setPressed(true);
        holdingUseKey = true;
    }

    /**
     * Scans a hemisphere 8 blocks wide (radius 4.0, diameter 8.0; up to 8.0 horizontal reach)
     * centered at player's level for players falling or gliding 3+ blocks above player.
     */
    private PlayerEntity scanForOverheadThreat(MinecraftClient client, ClientPlayerEntity self) {
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity candidate) || candidate == self) continue;
            if (!candidate.isAlive() || candidate.isSpectator()) continue;

            double heightAboveMe = PlayerActionHelper.getBodyHeightDifference(candidate, self);
            if (heightAboveMe < 3.0) continue;

            double horizDist = horizontalDistance(self, candidate);
            if (horizDist > 8.0) continue;

            boolean isFallingOrGliding = candidate.isGliding()
                    || candidate.fallDistance > 0.0F
                    || (!candidate.isOnGround() && candidate.getVelocity().y < -0.08);
            if (!isFallingOrGliding) continue;

            double dist = self.squaredDistanceTo(candidate);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private double horizontalDistance(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void releaseHold(MinecraftClient client) {
        if (holdingUseKey && client != null && client.options != null) {
            client.options.useKey.setPressed(false);
        }
        holdingUseKey = false;
        triggered = false;
        activeThreat = null;
        lockedSlot = -1;
        RotationController.releaseAim(RotationController.AimOwner.ALWAYS_ON_SHIELD);
    }

    public void onDisable() {
        releaseHold(MinecraftClient.getInstance());
    }

    public boolean isTriggered() {
        return triggered;
    }
}
