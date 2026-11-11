package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class AimAssistFeature {

    private static final Random RNG = new Random();

    private static LivingEntity currentTarget = null;
    private static float nextYaw   = 0f;
    private static float nextPitch = 0f;
    private static boolean hasTarget = false;

    // Each tick we recompute a fresh random aim point so the aim wanders
    // naturally inside the zone rather than locking to one pixel.
    private static Vec3d currentAimPoint = null;
    private static int aimPointRefreshCountdown = 0;

    private static boolean elytraAimActive = false;
    private static float elytraNextYaw   = 0f;
    private static float elytraNextPitch = 0f;

    private static float lastYaw   = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static final float CAMERA_SHIFT_THRESHOLD = 8.0f;

    // ── Game tick (20/s) ────────────────────────────────────────────────────

    public static void onGameTick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        hasTarget = false;

        if (!cfg.aimAssist.enabled) { currentTarget = null; return; }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        // Disable while player is eating / using item
        if (client.player.isUsingItem()) { hasTarget = false; return; }

        currentTarget = findBestTarget(client, cfg);
        if (currentTarget == null) return;

        // Refresh aim point every 3-6 ticks so it wanders, never fixed,
        // and always relative to the CURRENT bounding box (so a target
        // that jumps/moves vertically never gets aimed at stale feet Y).
        if (currentAimPoint == null || aimPointRefreshCountdown <= 0
                || !currentTarget.isAlive()) {
            currentAimPoint = RandomAim.randomPoint(currentTarget, cfg.aimAssist.aimPoint);
            aimPointRefreshCountdown = 3 + RNG.nextInt(4); // 3-6 ticks
        }
        aimPointRefreshCountdown--;

        // Max mode = instant snap (no speed limiting). Smooth at the top of
        // the slider (7200°/s) behaves exactly like Max too.
        boolean snap = cfg.aimAssist.aimMaxMode || cfg.aimAssist.speed >= 7200f;
        computeNextAngles(client, currentAimPoint, cfg.aimAssist.speed,
                cfg.aimAssist.speedRandomization, cfg.aimAssist.speedRandomRange, snap);
        hasTarget = true;
    }

    // ── Mouse update (every frame) ───────────────────────────────────────────

    /**
     * Converts degrees-of-rotation into the equivalent RAW mouse delta.
     *
     * updateMouse() scales cursorDelta by (sens*0.6+0.2)³·8 and
     * changeLookDirection() by another 0.15 — so adding diffYaw (degrees)
     * straight onto the raw delta used to produce diffYaw·factor degrees,
     * making the configured °/s speed wrong by up to 100× depending on the
     * user's mouse sensitivity. Dividing by the factor here makes the assist
     * strength independent of sensitivity.
     */
    private static double degToRawFactor(MinecraftClient client) {
        double sens = client.options.getMouseSensitivity().getValue();
        double e = Math.pow(sens * 0.6 + 0.2, 3.0) * 8.0;
        return 1.0 / Math.max(1e-6, e * 0.15);
    }

    public static double[] onMouseUpdate(double deltaX, double deltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return new double[]{deltaX, deltaY};

        if (elytraAimActive) {
            float curYaw   = client.player.getYaw();
            float curPitch = client.player.getPitch();
            // float, not int — int truncation floors sub-degree corrections
            // to zero, which is the "aim moves 1cm per year" bug.
            float diffYaw   = elytraNextYaw   - curYaw;
            float diffPitch = elytraNextPitch - curPitch;
            double conv = degToRawFactor(client);
            return new double[]{deltaX + diffYaw * conv, deltaY + diffPitch * conv};
        }

        ModConfig cfg = ModConfig.get();
        if (!cfg.aimAssist.enabled || !hasTarget || currentTarget == null)
            return new double[]{deltaX, deltaY};

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();
        // float, not int — sub-degree corrections must actually apply.
        float diffYaw   = nextYaw   - curYaw;
        float diffPitch = nextPitch - curPitch;

        if (Math.abs(diffYaw) < 0.05f && Math.abs(diffPitch) < 0.05f && !isFacingHitbox(client, cfg)) {
            diffYaw   = nextYaw   < curYaw   ? -1 : 1;
            diffPitch = nextPitch < curPitch ? -1 : 1;
        }

        double conv = degToRawFactor(client);
        return new double[]{deltaX + diffYaw * conv, deltaY + diffPitch * conv};
    }

    // ── ElytraSwapper / StunSlam / ShieldDrain instant aim ──────────────────
    // All these features call this; they get a fresh random full-body point each time.

    public static void elytraAimTick(MinecraftClient client, LivingEntity target) {
        if (client.player == null || target == null) return;
        elytraAimActive = true;

        // Random point across the full hitbox — never the same spot twice
        Vec3d hitVec = RandomAim.randomFull(target);
        computeElytraAngles(client, hitVec, 3600f);
    }

    public static void stopElytraAim() {
        elytraAimActive = false;
    }

    // ── Snap aim (WindChargeTackle — single instant) ──────────────────────────

    public static void snapAimAt(MinecraftClient client, Vec3d target) {
        if (client.player == null) return;
        Vec3d d = target.subtract(client.player.getEyePos());
        double hDist = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw   = (float)(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float)-Math.toDegrees(Math.atan2(d.y, hDist));
        client.player.setYaw(MathHelper.wrapDegrees(yaw));
        client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    public static void snapAimAt(MinecraftClient client, LivingEntity target) {
        if (target == null) return;
        // Use a random full-body point even for snap
        snapAimAt(client, RandomAim.randomFull(target));
    }

    // ── Angle computation helpers ────────────────────────────────────────────

    private static void computeNextAngles(MinecraftClient client, Vec3d hitVec,
                                           float speed, boolean randomize, float randomRange,
                                           boolean snap) {
        Vec3d eyes   = client.player.getEyePos();
        double diffX = hitVec.x - eyes.x;
        double diffZ = hitVec.z - eyes.z;
        float wantYaw   = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        double diffY  = hitVec.y - eyes.y;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float wantPitch = (float)-Math.toDegrees(Math.atan2(diffY, diffXZ));

        if (snap) {
            // Max mode: aim straight at the point this tick.
            nextYaw   = MathHelper.wrapDegrees(wantYaw);
            nextPitch = MathHelper.clamp(wantPitch, -90f, 90f);
            return;
        }

        float s = speed;
        if (randomize) s = Math.max(100f, s + (float)(RNG.nextGaussian() * randomRange * 0.5));
        float maxChange = s / 20f;

        float startYaw   = client.player.lastYaw;
        float startPitch = client.player.lastPitch;

        float yDelta = Math.abs(MathHelper.wrapDegrees(wantYaw   - startYaw));
        float pDelta = Math.abs(MathHelper.wrapDegrees(wantPitch - startPitch));

        float maxY = pDelta == 0 ? maxChange : Math.min(maxChange, maxChange * yDelta / pDelta);
        float maxP = yDelta == 0 ? maxChange : Math.min(maxChange, maxChange * pDelta / yDelta);

        nextYaw   = limitAngle(startYaw,   wantYaw,   maxY);
        nextPitch = limitAngle(startPitch, wantPitch, maxP);
    }

    private static void computeElytraAngles(MinecraftClient client, Vec3d hitVec, float speed) {
        Vec3d eyes   = client.player.getEyePos();
        double diffX = hitVec.x - eyes.x;
        double diffZ = hitVec.z - eyes.z;
        float wantYaw   = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        double diffY  = hitVec.y - eyes.y;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float wantPitch = (float)-Math.toDegrees(Math.atan2(diffY, diffXZ));

        float maxStep = speed / 20f;
        float startYaw   = client.player.lastYaw;
        float startPitch = client.player.lastPitch;
        float yDelta = Math.abs(MathHelper.wrapDegrees(wantYaw   - startYaw));
        float pDelta = Math.abs(MathHelper.wrapDegrees(wantPitch - startPitch));
        float maxY = pDelta == 0 ? maxStep : Math.min(maxStep, maxStep * yDelta / pDelta);
        float maxP = yDelta == 0 ? maxStep : Math.min(maxStep, maxStep * pDelta / yDelta);

        elytraNextYaw   = limitAngle(startYaw,   wantYaw,   maxY);
        elytraNextPitch = limitAngle(startPitch, wantPitch, maxP);
    }

    // ── Target finding ───────────────────────────────────────────────────────

    private static LivingEntity findBestTarget(MinecraftClient client, ModConfig cfg) {
        if (client.player == null || client.world == null) return null;
        double radius = cfg.aimAssist.range;

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();
        boolean playerShifted = false;
        if (!Float.isNaN(lastYaw)) {
            float dy = Math.abs(MathHelper.wrapDegrees(curYaw   - lastYaw));
            float dp = Math.abs(MathHelper.wrapDegrees(curPitch - lastPitch));
            playerShifted = dy > CAMERA_SHIFT_THRESHOLD || dp > CAMERA_SHIFT_THRESHOLD;
        }
        lastYaw   = curYaw;
        lastPitch = curPitch;

        if (!playerShifted && currentTarget != null && currentTarget.isAlive()
                && currentTarget.distanceTo(client.player) <= radius
                && TargetHelper.isValidTarget(currentTarget, cfg)
                && TargetHelper.isInFov(client, currentTarget, cfg.aimAssist.fov)) {
            return currentTarget;
        }

        Box box = client.player.getBoundingBox().expand(radius);
        return client.world.getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == client.player || !e.isAlive()) return false;
            if (!TargetHelper.isValidTarget(e, cfg)) return false;
            if (e.distanceTo(client.player) > radius) return false;
            return TargetHelper.isInFov(client, e, cfg.aimAssist.fov);
        }).stream()
        .min((a, b) -> Double.compare(
            TargetHelper.angleToEntity(client, a),
            TargetHelper.angleToEntity(client, b)))
        .orElse(null);
    }

    // ── Misc helpers ─────────────────────────────────────────────────────────

    private static float limitAngle(float current, float intended, float maxChange) {
        float cw = MathHelper.wrapDegrees(current);
        float iw = MathHelper.wrapDegrees(intended);
        float change = MathHelper.wrapDegrees(iw - cw);
        change = MathHelper.clamp(change, -maxChange, maxChange);
        return current + change;
    }

    private static boolean isFacingHitbox(MinecraftClient client, ModConfig cfg) {
        if (client.player == null || currentTarget == null) return false;
        Vec3d start = client.player.getEyePos();
        Vec3d look  = client.player.getRotationVec(1.0f);
        Vec3d end   = start.add(look.multiply(cfg.aimAssist.range + 1.0));
        return currentTarget.getBoundingBox().raycast(start, end).isPresent();
    }

    // ── State accessors ───────────────────────────────────────────────────────

    public static LivingEntity getCurrentTarget()          { return currentTarget; }
    public static void clearTarget()                       { currentTarget = null; hasTarget = false; currentAimPoint = null; }
}
