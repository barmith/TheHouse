package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.FreeCamera;
import com.thehouse.paperutility.render.FreecamInteraction;
import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class RenderGameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void thehouse$frame(CallbackInfo ci) {
        RenderModules.frame();
        FreeCamera.updateCulling();
    }

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void thehouse$freecamTarget(float tickProgress, CallbackInfo ci) {
        FreecamInteraction.updateTarget(MinecraftClient.getInstance(), tickProgress);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void thehouse$fov(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue((float) (cir.getReturnValueF() / RenderModules.zoomScale()));
    }
    @Inject(method = {"renderHand", "bobView", "tiltViewWhenHurt"}, at = @At("HEAD"), cancellable = true)
    private void thehouse$freeCameraView(CallbackInfo ci) {
        if (FreeCamera.active()) ci.cancel();
    }
}
