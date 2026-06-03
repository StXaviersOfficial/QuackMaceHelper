package com.quack.quackmacehelper.mixin;

import com.quack.quackmacehelper.gui.QuackMaceScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.trim().equalsIgnoreCase("/quackmace")) {
            MinecraftClient.getInstance().setScreen(new QuackMaceScreen());
            ci.cancel();
        }
    }
}
