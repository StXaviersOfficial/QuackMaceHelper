package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.feature.AimAssistFeature;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    // Yarn 1.21.1 — the two accumulated raw mouse deltas before they are
    // consumed by updateMouse() to turn the camera.
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    /**
     * Inject at HEAD of updateMouse so we intercept BOTH axes in one call,
     * exactly like Wurst's handleAccumulatedMovement hook.
     *
     * We read the current accumulated deltas, let AimAssist add its
     * rotation nudge on top, then write them back before MC processes them.
     * This means the player's own mouse movement is fully preserved —
     * AimAssist only adds on top of it (or subtracts if overshoot).
     */
    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void onUpdateMouse(CallbackInfo ci) {
        double[] adjusted = AimAssistFeature.onMouseUpdate(cursorDeltaX, cursorDeltaY);
        cursorDeltaX = adjusted[0];
        cursorDeltaY = adjusted[1];
    }
}
