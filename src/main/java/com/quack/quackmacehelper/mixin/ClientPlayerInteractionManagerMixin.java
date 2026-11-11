package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.feature.BreachSwapFeature;
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
 * Hooks the vanilla attack call so BreachSwap can react to the player's own
 * left-clicks. (Previously misnamed "ClientPlayerEntityMixin" — this mixes
 * into ClientPlayerInteractionManager.)
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) return;
        // Reentrance guard lives inside BreachSwapFeature itself
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        BreachSwapFeature.onAttack(client, living);
    }
}
