package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssistFeature {

    private static LivingEntity currentTarget = null;

    // Desired rotation calculated each game tick
    private static float nextYaw = 0f;
    private static float nextPitch = 0f;
    private static boolean hasTarget = false;

    /**
     * Called every GAME TICK (20/s). Finds target and calculates desired rotation.
     */
    public static void onGameTick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        hasTarget = false;

        if (!cfg.aimAssist.enabled) { currentTarget = null; return; }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Find/revalidate target
        if (currentTarget == null || !currentTarget.isAlive()
                || currentTarget.distanceTo(client.player) > cfg.aimAssist.range) {
            currentTarget = TargetHelper.findTarget(client, cfg.aimAssist.range);
        }
        if (currentTarget == null) return;

        // FOV check
        if (!TargetHelper.isInFov(client, currentTarget, cfg.aimAssist.fov)) {
            currentTarget = null;
            return;
        }

        // Calculate desired rotation toward target eye
        Vec3d eye = client.player.getEyePos();
        Vec3d targetPos = new Vec3d(
                currentTarget.getX(),
                currentTarget.getEyeY(),
                currentTarget.getZ()
        );
        Vec3d delta = targetPos.subtract(eye);
        double hDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        nextYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        nextPitch = (float) -Math.toDegrees(Math.atan2(delta.y, hDist));
        hasTarget = true;
    }

    /**
     * Called every MOUSE UPDATE (raw input hook via mixin).
     * Nudges mouse delta toward desired rotation — exactly like Wurst.
     * Returns adjusted [deltaX, deltaY].
     */
    public static double[] onMouseUpdate(MinecraftClient client, double deltaX, double deltaY) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.aimAssist.enabled || !hasTarget || client.player == null) {
            return new double[]{deltaX, deltaY};
        }

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();

        // Max rotation per tick based on speed (20 tps base), with randomization
        float speed = cfg.aimAssist.speed;
        if (cfg.aimAssist.speedRandomization) {
            float variance = cfg.aimAssist.speedRandomRange;
            speed += (float)(Math.random() * 2 - 1) * variance;
            speed = Math.max(100f, speed);
        }
        float maxStep = speed / 20f;

        // Calculate how many degrees we need to turn
        float diffYaw   = MathHelper.wrapDegrees(nextYaw - curYaw);
        float diffPitch = MathHelper.wrapDegrees(nextPitch - curPitch);

        // Clamp to max step
        float stepYaw   = Math.abs(diffYaw)   < maxStep ? diffYaw   : Math.signum(diffYaw)   * maxStep;
        float stepPitch = Math.abs(diffPitch) < maxStep ? diffPitch : Math.signum(diffPitch) * maxStep;

        // Add our rotation nudge on top of player's own mouse input
        return new double[]{
                deltaX + stepYaw,
                deltaY + stepPitch
        };
    }

    /**
     * Direct aim used by ElytraSwapper, WindChargeTackle etc — NOT the smooth mouse method.
     * Sets yaw/pitch instantly.
     */
    public static void snapAimAt(MinecraftClient client, LivingEntity target) {
        if (client.player == null || target == null) return;
        Vec3d eye = client.player.getEyePos();
        Vec3d targetPos = new Vec3d(target.getX(), target.getEyeY(), target.getZ());
        Vec3d delta = targetPos.subtract(eye);
        double hDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, hDist));
        client.player.setYaw(yaw);
        client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    /**
     * Smooth aim used by ElytraSwapper AutoAim — uses setYaw/setPitch with speed cap.
     */
    public static void smoothAimAt(MinecraftClient client, LivingEntity target,
                                    float degreesPerSecond, float deltaTime,
                                    boolean randomize, float randomRange) {
        if (client.player == null) return;
        Vec3d eye = client.player.getEyePos();
        Vec3d targetPos = new Vec3d(target.getX(), target.getEyeY(), target.getZ());
        Vec3d delta = targetPos.subtract(eye);
        double hDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float wantYaw   = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(delta.y, hDist));

        float speed = degreesPerSecond;
        if (randomize && randomRange > 0)
            speed = Math.max(100f, speed + (float)(Math.random() * 2 - 1) * randomRange);

        float maxStep = speed * deltaTime;
        float newYaw   = stepToward(client.player.getYaw(), wantYaw, maxStep);
        float newPitch = MathHelper.clamp(stepToward(client.player.getPitch(), wantPitch, maxStep), -90f, 90f);
        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
    }

    private static float stepToward(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    public static LivingEntity getCurrentTarget() { return currentTarget; }
    public static void clearTarget() { currentTarget = null; hasTarget = false; }
    public static void forceTarget(LivingEntity e) { currentTarget = e; }
}
