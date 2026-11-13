package com.quack.quackmacehelper.feature;

import com.quack.quackmacehelper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Target scanning utilities. V5 adds the R3 rule helpers (player-position
 * measurements) and the shared hemisphere-below scan used by ElytraMace and
 * AutoMace.
 */
public class TargetHelper {

    /**
     * R3: height advantage measured from PLAYER BODY positions — the
     * attacker's feet Y minus the target's feet Y. Never the ground, never
     * the fall-distance counter.
     */
    public static double heightAbove(PlayerEntity attacker, LivingEntity target) {
        return attacker.getEntityPos().y - target.getEntityPos().y;
    }

    /** R3 shortcut: attacker is 3+ blocks above the target. */
    public static boolean is3Above(PlayerEntity attacker, LivingEntity target) {
        return heightAbove(attacker, target) >= 3.0;
    }

    /** The living entity currently under the crosshair (vanilla-updated). */
    public static LivingEntity getCrosshairTarget(MinecraftClient client) {
        if (client.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
        return null;
    }

    public static LivingEntity findTarget(MinecraftClient client, double radius) {
        if (client.player == null || client.world == null) return null;
        ModConfig cfg = ModConfig.get();

        Box box = client.player.getBoundingBox().expand(radius);
        List<LivingEntity> candidates = new ArrayList<>();

        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class, box, en -> true)) {
            if (e == client.player) continue;
            if (!e.isAlive()) continue;
            if (!isValidTarget(e, cfg)) continue;
            if (e.distanceTo(client.player) > radius) continue;
            candidates.add(e);
        }

        if (candidates.isEmpty()) return null;

        // Default: lowest health
        return candidates.stream()
                .min(Comparator.comparingDouble(LivingEntity::getHealth))
                .orElse(null);
    }

    public static boolean isValidTarget(LivingEntity e, ModConfig cfg) {
        if (e instanceof PlayerEntity) return cfg.targetPlayers;
        if (e instanceof AnimalEntity)  return cfg.targetAnimals;
        if (e instanceof MobEntity)     return cfg.targetMobs;
        return false;
    }

    /**
     * Shared "hemisphere BELOW the player inside the FOV" scan used by
     * ElytraMace (gliding) and AutoMace (falling). A candidate must be:
     * valid per the target toggles, alive, inside the FOV cone from the
     * crosshair, strictly below the player's body, and within range.
     * Nearest candidate wins.
     */
    public static LivingEntity findTargetBelowInFov(MinecraftClient client,
                                                    double radius, float fovDeg) {
        if (client.player == null || client.world == null) return null;
        ModConfig cfg = ModConfig.get();
        Vec3d playerPos = client.player.getEntityPos();

        Box box = client.player.getBoundingBox().expand(radius);
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class, box, en -> true)) {
            if (e == client.player || !e.isAlive()) continue;
            if (!isValidTarget(e, cfg)) continue;
            if (e.distanceTo(client.player) > radius) continue;
            if (!isInFov(client, e, fovDeg)) continue;
            // Strictly below the player's body (hemisphere below)
            if (e.getEntityPos().y >= playerPos.y) continue;
            candidates.add(e);
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> client.player.distanceTo(e)))
                .orElse(null);
    }

    /** Checks if entity is within fovDegrees cone around player's look direction */
    public static boolean isInFov(MinecraftClient client, LivingEntity entity, float fovDeg) {
        if (client.player == null) return false;
        if (fovDeg >= 360f) return true;
        double angle = angleToEntity(client, entity);
        return angle <= fovDeg / 2.0;
    }

    public static double angleToEntity(MinecraftClient client, LivingEntity entity) {
        if (client.player == null) return Double.MAX_VALUE;
        Vec3d look = client.player.getRotationVec(1.0f);
        Vec3d toEntity = entity.getEntityPos().subtract(client.player.getEyePos()).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dotProduct(toEntity)));
        return Math.toDegrees(Math.acos(dot));
    }
}
