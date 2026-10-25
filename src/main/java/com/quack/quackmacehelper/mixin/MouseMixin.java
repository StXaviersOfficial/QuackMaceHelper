package com.quack.quackmacehelper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.quack.quackmacehelper.feature.AimAssistFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.21.11: Mouse.updateMouse() no longer takes delta args. The smoothed
 * deltas are produced by two Smoother.smooth() calls inside the method
 * (ordinal 0 = X, ordinal 1 = Y). We adjust them right after smoothing —
 * the same values the player-turn logic consumes.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow @Final private MinecraftClient client;

    @ModifyExpressionValue(
            method = "updateMouse",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Smoother;smooth(DD)D",
                    ordinal = 0)
    )
    private double modifyMouseX(double smoothedX) {
        double[] adjusted = AimAssistFeature.onMouseUpdate(client, smoothedX, 0);
        return adjusted[0];
    }

    @ModifyExpressionValue(
            method = "updateMouse",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Smoother;smooth(DD)D",
                    ordinal = 1)
    )
    private double modifyMouseY(double smoothedY) {
        double[] adjusted = AimAssistFeature.onMouseUpdate(client, 0, smoothedY);
        return adjusted[1];
    }
}
