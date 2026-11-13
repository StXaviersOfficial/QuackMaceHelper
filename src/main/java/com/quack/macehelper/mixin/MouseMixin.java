package com.quack.macehelper.mixin;

import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void quackmacehelper$onUpdateMouseHead(double timeDelta, CallbackInfo ci) {
        if (!ModConfig.get().masterSwitch || this.client.player == null || this.client.currentScreen != null) {
            RotationController.consumePendingDelta();
            return;
        }

        double[] delta = RotationController.consumePendingDelta();
        double deltaYawDeg = delta[0];
        double deltaPitchDeg = delta[1];
        if (deltaYawDeg == 0.0 && deltaPitchDeg == 0.0) {
            return;
        }

        double sensitivity = this.client.options.getMouseSensitivity().getValue() * 0.6000000238418579D + 0.20000000298023224D;
        double mult = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        if (mult <= 1e-7) {
            return;
        }

        boolean invertY = this.client.options.getInvertYMouse().getValue();
        this.cursorDeltaX += deltaYawDeg / mult;
        this.cursorDeltaY += (invertY ? -deltaPitchDeg : deltaPitchDeg) / mult;
    }
}
