package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.FreeCamera;
import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class RenderLookMixin {
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double thehouse$horizontal(double value) {
        return (Object) this == MinecraftClient.getInstance().player ? value / RenderModules.zoomScale() : value;
    }
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double thehouse$vertical(double value) {
        return (Object) this == MinecraftClient.getInstance().player ? value / RenderModules.zoomScale() : value;
    }
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void thehouse$look(double x, double y, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && FreeCamera.active()) {
            FreeCamera.look(x, y);
            ci.cancel();
        }
    }
}
