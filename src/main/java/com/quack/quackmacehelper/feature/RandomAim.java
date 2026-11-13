package com.quack.quackmacehelper.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * RandomAim — shared utility for all features that need to aim at an entity.
 *
 * The hitbox is divided into 3 zones (head / center / feet).
 * Within whichever zone is requested, we pick a RANDOM point — never the
 * same position twice — so the rotation pattern is never perfectly periodic.
 *
 * aimPoint values (V5 config domain):
 *   0 = HEAD   (top 33% of hitbox)
 *   1 = CENTER (middle 33%)
 *   2 = FEET   (bottom 33%)
 *   3 = AUTO   (Wurst-style: the CLOSEST point of the hitbox to the
 *               player's eyes — eye position clamped onto the box; the
 *               4th AimAt option added in V5)
 *   4 = FULL   (entire hitbox — internal use by combat features that just
 *               want "somewhere on the target")
 */
public class RandomAim {

    private static final Random RNG = new Random();

    /**
     * Returns a random Vec3d inside the requested zone of the entity's hitbox.
     * X and Z are also randomised within the bounding box width, not just centered.
     */
    public static Vec3d randomPoint(LivingEntity entity, int aimPoint) {
        Box box = entity.getBoundingBox();

        if (aimPoint == 3) { // AUTO — Wurst closest-hitbox-point to the eyes
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                Vec3d eyes = client.player.getEyePos();
                Vec3d clamped = new Vec3d(
                        MathHelper.clamp(eyes.x, box.minX, box.maxX),
                        MathHelper.clamp(eyes.y, box.minY, box.maxY),
                        MathHelper.clamp(eyes.z, box.minZ, box.maxZ));
                // If the eyes are inside the box the clamp is a no-op and we
                // are already as close as possible — return it directly.
                return clamped;
            }
            aimPoint = 1; // fallback: center
        }

        double height = box.maxY - box.minY;

        double yMin, yMax;
        switch (aimPoint) {
            case 0 -> { // HEAD — top third
                yMin = box.minY + height * (2.0 / 3.0);
                yMax = box.maxY - 0.05;
            }
            case 2 -> { // FEET — bottom third
                yMin = box.minY + 0.05;
                yMax = box.minY + height * (1.0 / 3.0);
            }
            case 4 -> { // FULL — anywhere in the box (internal)
                yMin = box.minY + height * 0.05;
                yMax = box.maxY - height * 0.05;
            }
            default -> { // CENTER — middle third
                yMin = box.minY + height * (1.0 / 3.0);
                yMax = box.minY + height * (2.0 / 3.0);
            }
        }

        double y = yMin + RNG.nextDouble() * (yMax - yMin);

        // Randomise X and Z within ~70% of the box width (avoids edges)
        double xRange = (box.maxX - box.minX) * 0.7;
        double zRange = (box.maxZ - box.minZ) * 0.7;
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double x = cx + (RNG.nextDouble() - 0.5) * xRange;
        double z = cz + (RNG.nextDouble() - 0.5) * zRange;

        return new Vec3d(x, y, z);
    }

    /**
     * Convenience: random point anywhere on the hitbox (aimPoint = FULL).
     */
    public static Vec3d randomFull(LivingEntity entity) {
        return randomPoint(entity, 4);
    }
}
