package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class RenderMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void thehouse$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || client.world == null || client.player == null
                || client.currentScreen != null || client.getOverlay() != null) return;
        if (RenderModules.scrollZoom(vertical)) {
            client.getInactivityFpsLimiter().onInput();
            ci.cancel();
        }
    }
}
