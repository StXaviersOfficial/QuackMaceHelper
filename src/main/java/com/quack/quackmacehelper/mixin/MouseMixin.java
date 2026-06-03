package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.feature.AimAssistFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow @Final private MinecraftClient client;

    @ModifyVariable(method = "updateMouse", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double modifyMouseX(double deltaX) {
        double[] adjusted = AimAssistFeature.onMouseUpdate(client, deltaX, 0);
        return adjusted[0];
    }

    @ModifyVariable(method = "updateMouse", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double modifyMouseY(double deltaY) {
        double[] adjusted = AimAssistFeature.onMouseUpdate(client, 0, deltaY);
        return adjusted[1];
    }
}
