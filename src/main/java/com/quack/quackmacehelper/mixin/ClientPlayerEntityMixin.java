package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.feature.BreachSwapFeature;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        BreachSwapFeature.onAttack(client, living);
    }
}
