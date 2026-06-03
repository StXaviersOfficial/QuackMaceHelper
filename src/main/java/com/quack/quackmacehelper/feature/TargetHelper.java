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

public class TargetHelper {

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

    /** Finds target within given radius, players only (for WindChargeTackle / AlwaysOnShield) */
    public static LivingEntity findPlayerTarget(MinecraftClient client, double radius) {
        if (client.player == null || client.world == null) return null;
        Box box = client.player.getBoundingBox().expand(radius);
        return client.world.getEntitiesByClass(PlayerEntity.class, box,
                e -> e != client.player && e.isAlive() && e.distanceTo(client.player) <= radius)
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceTo(client.player)))
                .orElse(null);
    }

    public static boolean isValidTarget(LivingEntity e, ModConfig cfg) {
        if (e instanceof PlayerEntity) return cfg.targetPlayers;
        if (e instanceof AnimalEntity)  return cfg.targetAnimals;
        if (e instanceof MobEntity)     return cfg.targetMobs;
        return false;
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
        Vec3d toEntity = entity.getPos().subtract(client.player.getEyePos()).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dotProduct(toEntity)));
        return Math.toDegrees(Math.acos(dot));
    }

    /** Returns true if the entity appears to be moving/flying toward the client player */
    public static boolean isMovingToward(MinecraftClient client, LivingEntity entity) {
        if (client.player == null) return false;
        Vec3d vel = entity.getVelocity();
        if (vel.lengthSquared() < 0.001) return false;
        Vec3d toPlayer = client.player.getPos().subtract(entity.getPos()).normalize();
        return vel.normalize().dotProduct(toPlayer) > 0.5;
    }
}
