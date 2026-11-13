package com.quack.macehelper.util;

import com.quack.macehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationController {

    public enum AimOwner {
        NONE,
        AIM_ASSIST,
        AUTO_MACE,
        ELYTRA_MACE,
        ALWAYS_ON_SHIELD,
        PEARL_CATCH
    }

    private static AimOwner currentOwner = AimOwner.NONE;
    private static double pendingYawDeltaDeg = 0.0;
    private static double pendingPitchDeltaDeg = 0.0;

    private RotationController() {}

    public static AimOwner getCurrentOwner() {
        return currentOwner;
    }

    public static void acquireAim(AimOwner owner) {
        if (owner.ordinal() >= currentOwner.ordinal()) {
            currentOwner = owner;
        }
    }

    public static void releaseAim(AimOwner owner) {
        if (currentOwner == owner) {
            currentOwner = AimOwner.NONE;
            pendingYawDeltaDeg = 0.0;
            pendingPitchDeltaDeg = 0.0;
        }
    }

    public static void resetAll() {
        currentOwner = AimOwner.NONE;
        pendingYawDeltaDeg = 0.0;
        pendingPitchDeltaDeg = 0.0;
    }

    public static boolean isCombatOrDefenseAimActive() {
        return currentOwner == AimOwner.AUTO_MACE
                || currentOwner == AimOwner.ELYTRA_MACE
                || currentOwner == AimOwner.ALWAYS_ON_SHIELD
                || currentOwner == AimOwner.PEARL_CATCH;
    }

    /**
     * Queues an additive rotation delta (in degrees) to be injected into Mouse.updateMouse
     * so it blends with the player's real mouse input.
     */
    public static void queueAdditiveRotation(double yawDeltaDeg, double pitchDeltaDeg) {
        pendingYawDeltaDeg += yawDeltaDeg;
        pendingPitchDeltaDeg += pitchDeltaDeg;
    }

    /**
     * Consumes the pending rotation delta inside MouseMixin.updateMouse.
     */
    public static double[] consumePendingDelta() {
        double y = pendingYawDeltaDeg;
        double p = pendingPitchDeltaDeg;
        pendingYawDeltaDeg = 0.0;
        pendingPitchDeltaDeg = 0.0;
        return new double[]{y, p};
    }

    /**
     * Computes the target aim point on an entity according to Wurst's exact specification:
     * Head = target eye height;
     * Center = bounding box center;
     * Feet = position + 0.001;
     * Auto = closest point of the target's hitbox to player's eyes (eye position clamped onto the box).
     */
    public static Vec3d getTargetAimPoint(ClientPlayerEntity player, Entity target, ModConfig.AimPoint aimPoint) {
        Box box = target.getBoundingBox();
        return switch (aimPoint) {
            case HEAD -> new Vec3d(target.getX(), target.getEyeY(), target.getZ());
            case CENTER -> box.getCenter();
            case FEET -> new Vec3d(target.getX(), target.getY() + 0.001, target.getZ());
            case AUTO -> {
                Vec3d eyes = player.getEyePos();
                double cx = MathHelper.clamp(eyes.x, box.minX, box.maxX);
                double cy = MathHelper.clamp(eyes.y, box.minY, box.maxY);
                double cz = MathHelper.clamp(eyes.z, box.minZ, box.maxZ);
                yield new Vec3d(cx, cy, cz);
            }
        };
    }

    /**
     * Computes desired [yaw, pitch] from player's eye position to a world coordinate.
     */
    public static float[] getRotationsToVec(ClientPlayerEntity player, Vec3d targetVec) {
        Vec3d eyes = player.getEyePos();
        double diffX = targetVec.x - eyes.x;
        double diffY = targetVec.y - eyes.y;
        double diffZ = targetVec.z - eyes.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{
                player.getYaw() + MathHelper.wrapDegrees(yaw - player.getYaw()),
                MathHelper.clamp(pitch, -90.0F, 90.0F)
        };
    }

    /**
     * Angular distance in degrees between the player's current crosshair vector and the vector to target.
     */
    public static double getAngleFromCrosshair(ClientPlayerEntity player, Vec3d targetVec) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d toTarget = targetVec.subtract(player.getEyePos()).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toTarget), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Checks if the player's crosshair is currently over the given entity.
     */
    public static boolean isCrosshairOnEntity(MinecraftClient client, Entity target) {
        if (client.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == target) {
            return true;
        }
        if (client.player == null) return false;
        Vec3d start = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(6.0));
        return target.getBoundingBox().expand(target.getTargetingMargin()).raycast(start, end).isPresent();
    }

    /**
     * Wurst-exact Smooth rotation step queued into the mouse delta pipeline:
     * - Per-tick rotation budget = speed / 20.0 degrees.
     * - Split budget between yaw and pitch proportional to remaining turn angle on each axis.
     * - If under 1 degree away but not on the hitbox, exaggerate by 1 degree so crosshair lands on it.
     */
    public static void applyWurstSmoothRotation(MinecraftClient client, Vec3d targetVec, Entity targetEntity, double speedDegPerSec) {
        if (client.player == null) return;
        float[] desired = getRotationsToVec(client.player, targetVec);
        double diffYaw = MathHelper.wrapDegrees(desired[0] - client.player.getYaw());
        double diffPitch = MathHelper.wrapDegrees(desired[1] - client.player.getPitch());

        double totalDiff = Math.hypot(diffYaw, diffPitch);
        if (totalDiff < 1e-4) return;

        // If under 1 degree away but not on the hitbox, exaggerate by 1 degree so the crosshair actually lands on it
        if (totalDiff < 1.0 && targetEntity != null && !isCrosshairOnEntity(client, targetEntity)) {
            double scale = (totalDiff + 1.0) / totalDiff;
            diffYaw *= scale;
            diffPitch *= scale;
            totalDiff = Math.hypot(diffYaw, diffPitch);
        }

        double budget = speedDegPerSec / 20.0;
        double absYaw = Math.abs(diffYaw);
        double absPitch = Math.abs(diffPitch);
        double sumAxes = absYaw + absPitch;
        if (sumAxes < 1e-4) return;

        double yawShare = budget * (absYaw / sumAxes);
        double pitchShare = budget * (absPitch / sumAxes);

        double stepYaw = MathHelper.clamp(diffYaw, -yawShare, yawShare);
        double stepPitch = MathHelper.clamp(diffPitch, -pitchShare, pitchShare);

        queueAdditiveRotation(stepYaw, stepPitch);
    }

    /**
     * Instant / Max snap onto target vector (both queues mouse delta and sets client look angles for same-tick actions).
     */
    public static void applyInstantSnap(MinecraftClient client, Vec3d targetVec) {
        if (client.player == null) return;
        float[] desired = getRotationsToVec(client.player, targetVec);
        float diffYaw = MathHelper.wrapDegrees(desired[0] - client.player.getYaw());
        float diffPitch = MathHelper.wrapDegrees(desired[1] - client.player.getPitch());
        client.player.setYaw(client.player.getYaw() + diffYaw);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + diffPitch, -90.0F, 90.0F));
    }
}
