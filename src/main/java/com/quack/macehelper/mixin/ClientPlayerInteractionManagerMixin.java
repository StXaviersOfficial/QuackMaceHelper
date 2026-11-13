package com.quack.macehelper.mixin;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void quackmacehelper$onAttackEntityHead(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!ModConfig.get().masterSwitch) {
            return;
        }
        QuackMaceHelperClient.onPlayerAttackEntity(player, target);
    }
}
