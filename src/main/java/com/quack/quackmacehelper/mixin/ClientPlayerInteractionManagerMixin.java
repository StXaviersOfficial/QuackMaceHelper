package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.config.ModConfig;
import com.quack.quackmacehelper.feature.AttributeSwapFeature;
import com.quack.quackmacehelper.feature.StunSlamFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the vanilla attack call so AttributeSwap and StunSlam can react to
 * the player's own left-clicks.
 *
 * Order (collision rule 7): AttributeSwap owns the same-tick swap chain;
 * StunSlam queues behind it (its PENDING phase waits the extra tick when
 * AttributeSwap.isSwapping()).
 *
 * The master kill switch gates both features here as well as in the tick
 * loop — belt and braces.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!ModConfig.get().masterEnabled) return;

        AttributeSwapFeature.onAttack(client, living);
        StunSlamFeature.onAttack(client, living);
    }
}
