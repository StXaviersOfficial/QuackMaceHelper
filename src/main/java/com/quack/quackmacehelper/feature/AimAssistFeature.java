package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * AimAssist — V5 rework.
 *
 * Smooth mode reproduces the Wurst client's AimAssist feel:
 *   - per-tick rotation budget = speed/20 degrees,
 *   - budget split between yaw and pitch proportional to how far each axis
 *     still has to turn, wrap-aware clamping,
 *   - applied as an additive delta on top of the player's REAL mouse input
 *     (mouse pipeline), so movement looks human,
 *   - under 1 degree away but not on the hitbox -> exaggerate by 1 degree.
 *
 * V5 speed randomization (confirmed): ONE roll in [0, 200] (added OR
 * subtracted) each time the assist ACTIVATES a target; the adjusted speed
 * is held for the whole activation. Activation = engaging a target (no
 * target -> target). Switching to a different target or re-engaging after
 * a loss is a NEW activation with a NEW roll. Never re-rolled per tick.
 *
 * Feature-local aim (ElytraMace / AutoMace / StunSlam / AlwaysOnShield)
 * runs through featureAimTick()/snapAimAt() and suspends the global assist
 * while active (collision rule 6).
 */
public class AimAssistFeature {

    private static final Random RNG = new Random();
    private static final float RANDOM_RANGE = 200f; // fixed per V5 spec

    private static LivingEntity currentTarget = null;
    private static float nextYaw   = 0f;
    private static float nextPitch = 0f;
    private static boolean hasTarget = false;

    // Per-activation randomization state
    private static LivingEntity engagedFor   = null;
    private static float        activeSpeed  = 0f;

    // Refreshing random aim point inside the zone
    private static Vec3d currentAimPoint = null;
    private static int   aimPointRefreshCountdown = 0;

    // Feature-local smooth aim (ElytraMace / AutoMace smooth path)
    private static boolean featureAimActive = false;
    private static float   featureNextYaw   = 0f;
    private static float   featureNextPitch = 0f;

    private static float lastYaw   = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static final float CAMERA_SHIFT_THRESHOLD = 8.0f;

    // ── Game tick (20/s) ────────────────────────────────────────────────────

    public static void onGameTick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        hasTarget = false;

        if (!cfg.masterEnabled || !cfg.aimAssist.enabled) {
            currentTarget = null;
            engagedFor = null;
            return;
        }
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;
        // A feature running its own aim suspends the global assist (rule 6).
        if (featureAimActive) return;
        // Q12: AlwaysOnShield triggered owns the aim.
        if (AlwaysOnShieldFeature.isTriggered()) return;
        // Disable while the player is eating / using an item
        if (client.player.isUsingItem()) { hasTarget = false; return; }

        LivingEntity found = findBestTarget(client, cfg);

        // ── V5 activation tracking: a NEW engagement rolls a NEW offset ──
        if (found != currentTarget) {
            currentTarget = found;
            if (found != null) {
                float offset = (RNG.nextBoolean() ? 1f : -1f) * RNG.nextInt(201);
                activeSpeed = cfg.aimAssist.speed + offset;
                engagedFor = found;
                currentAimPoint = null; // fresh aim point for the new target
            } else {
                engagedFor = null;
            }
        }

        if (currentTarget == null) return;

        // Refresh aim point every 3-6 ticks so it wanders, never fixed,
        // and always relative to the CURRENT bounding box.
        if (currentAimPoint == null || aimPointRefreshCountdown <= 0
                || !currentTarget.isAlive()) {
            currentAimPoint = RandomAim.randomPoint(currentTarget, cfg.aimAssist.aimPoint);
            aimPointRefreshCountdown = 3 + RNG.nextInt(4); // 3-6 ticks
        }
        aimPointRefreshCountdown--;

        // Max mode = instant snap (no speed limiting). The top of the
        // slider (7200 deg/s = full turn per tick) behaves like Max too.
        boolean snap = cfg.aimAssist.aimMaxMode || activeSpeed >= 7200f;
        computeNextAngles(client, currentAimPoint, activeSpeed, snap);
        hasTarget = true;
    }

    // ── Mouse update (every frame) ───────────────────────────────────────────

    /**
     * Converts degrees-of-rotation into the equivalent RAW mouse delta.
     *
     * updateMouse() scales cursorDelta by (sens*0.6+0.2)^3*8 and
     * changeLookDirection() by another 0.15 — so adding diffYaw (degrees)
     * straight onto the raw delta used to produce diffYaw*factor degrees,
     * making the configured deg/s speed wrong by up to 100x depending on
     * the user's mouse sensitivity. Dividing by the factor here makes the
     * assist strength independent of sensitivity.
     */
    private static double degToRawFactor(MinecraftClient client) {
        double sens = client.options.getMouseSensitivity().getValue();
        double e = Math.pow(sens * 0.6 + 0.2, 3.0) * 8.0;
        return 1.0 / Math.max(1e-6, e * 0.15);
    }

    public static double[] onMouseUpdate(double deltaX, double deltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return new double[]{deltaX, deltaY};

        // Feature-local smooth aim wins while active (rule 6: global assist
        // is suspended, the feature's rotation rides the same pipeline).
        if (featureAimActive) {
            float curYaw   = client.player.getYaw();
            float curPitch = client.player.getPitch();
            float diffYaw   = featureNextYaw   - curYaw;
            float diffPitch = featureNextPitch - curPitch;
            double conv = degToRawFactor(client);
            return new double[]{deltaX + diffYaw * conv, deltaY + diffPitch * conv};
        }

        ModConfig cfg = ModConfig.get();
        if (!cfg.masterEnabled || !cfg.aimAssist.enabled
                || !hasTarget || currentTarget == null)
            return new double[]{deltaX, deltaY};

        float curYaw   = client.player.getYaw();
        float curPitch = client.player.getPitch();
        float diffYaw   = nextYaw   - curYaw;
        float diffPitch = nextPitch - curPitch;

        if (Math.abs(diffYaw) < 0.05f && Math.abs(diffPitch) < 0.05f
                && !isLookingAt(client, currentTarget)) {
            // Wurst trick: under 1 degree away but not on the hitbox yet —
            // exaggerate so the crosshair actually lands on it.
            diffYaw   = nextYaw   < curYaw   ? -1 : 1;
            diffPitch = nextPitch < curPitch ? -1 : 1;
        }

        double conv = degToRawFactor(client);
        return new double[]{deltaX + diffYaw * conv, deltaY + diffPitch * conv};
    }

    // ── Feature-local aim (ElytraMace / AutoMace / StunSlam) ────────────────

    /** Smooth feature aim at a fresh random full-body point (Wurst budget). */
    public static void featureAimTick(MinecraftClient client, LivingEntity target) {
        if (client.player == null || target == null) return;
        featureAimActive = true;
        Vec3d hitVec = RandomAim.randomFull(target);
        computeFeatureAngles(client, hitVec, 3600f);
    }

    /** Ends the feature-local aim (every feature MUST call this on finish). */
    public static void stopFeatureAim() {
        featureAimActive = false;
    }

    /** True while a feature-local aim is active (global assist suspended). */
    public static boolean isFeatureAimActive() { return featureAimActive; }

    /**
     * "Target on the crosshair": a ray from the player's eyes along the
     * look direction intersects the target's bounding box.
     */
    public static boolean isLookingAt(MinecraftClient client, LivingEntity target) {
        if (client.player == null || target == null) return false;
        Vec3d start = client.player.getEyePos();
        Vec3d look  = client.player.getRotationVec(1.0f);
        Vec3d end   = start.add(look.multiply(6.0));
        return target.getBoundingBox().raycast(start, end).isPresent();
    }

    // ── Snap aim (instant, single shot — AlwaysOnShield / bursts) ──────────

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
        snapAimAt(client, RandomAim.randomFull(target));
    }

    // ── Angle computation helpers ────────────────────────────────────────────

    private static void computeNextAngles(MinecraftClient client, Vec3d hitVec,
                                           float speed, boolean snap) {
        Vec3d eyes   = client.player.getEyePos();
        double diffX = hitVec.x - eyes.x;
        double diffZ = hitVec.z - eyes.z;
        float wantYaw   = (float)(Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        double diffY  = hitVec.y - eyes.y;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float wantPitch = (float)-Math.toDegrees(Math.atan2(diffY, diffXZ));

        if (snap) {
            nextYaw   = MathHelper.wrapDegrees(wantYaw);
            nextPitch = MathHelper.clamp(wantPitch, -90f, 90f);
            return;
        }

        // Wurst budget split: speed/20 deg per tick, shared between axes
        // proportional to how far each still has to turn.
        float maxChange = speed / 20f;

        float startYaw   = client.player.lastYaw;
        float startPitch = client.player.lastPitch;

        float yDelta = Math.abs(MathHelper.wrapDegrees(wantYaw   - startYaw));
        float pDelta = Math.abs(MathHelper.wrapDegrees(wantPitch - startPitch));

        float maxY = pDelta == 0 ? maxChange : Math.min(maxChange, maxChange * yDelta / pDelta);
        float maxP = yDelta == 0 ? maxChange : Math.min(maxChange, maxChange * pDelta / yDelta);

        nextYaw   = limitAngle(startYaw,   wantYaw,   maxY);
        nextPitch = limitAngle(startPitch, wantPitch, maxP);
    }

    private static void computeFeatureAngles(MinecraftClient client, Vec3d hitVec, float speed) {
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

        featureNextYaw   = limitAngle(startYaw,   wantYaw,   maxY);
        featureNextPitch = limitAngle(startPitch, wantPitch, maxP);
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

    // ── State accessors ───────────────────────────────────────────────────────

    public static LivingEntity getCurrentTarget() { return currentTarget; }
    public static void clearTarget() {
        currentTarget = null; hasTarget = false;
        currentAimPoint = null; engagedFor = null;
    }
}
