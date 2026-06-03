package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.feature.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow @Final MinecraftClient client;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
        if (client.player == null || client.world == null) return;
        if (!tick) return;

        // AimAssist target finding + rotation calc happens on game tick
        AimAssistFeature.onGameTick(client);

        BreachSwapFeature.tick(client);
        StunSlamFeature.tick(client);
        ShieldDrainingFeature.tick(client);
        ElytraSwapperFeature.tick(client);
        PearlCatchFeature.tick(client);
        WindChargeTackleFeature.tick(client);
        AlwaysOnShieldFeature.tick(client);
    }
}
