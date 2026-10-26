package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class RenderLightmapMixin {
    @Shadow @Final private GpuTexture glTexture;
    @Inject(method = "update", at = @At("TAIL"))
    private void thehouse$fullLight(CallbackInfo ci) {
        if (RenderModules.bright()) RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glTexture, 0xffffffff);
    }
}
