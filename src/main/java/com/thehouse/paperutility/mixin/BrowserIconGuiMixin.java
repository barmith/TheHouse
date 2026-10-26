package com.thehouse.paperutility.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.thehouse.paperutility.BrowserItemIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiRenderer.class)
public abstract class BrowserIconGuiMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void thehouse$icons(GpuBufferSlice fog, CallbackInfo ci) {
        if (!BrowserItemIcons.owns(this)) BrowserItemIcons.pump(fog);
    }

    @Inject(method = "getWindowScaleFactor", at = @At("HEAD"), cancellable = true)
    private void thehouse$iconScale(CallbackInfoReturnable<Integer> cir) {
        if (BrowserItemIcons.owns(this)) cir.setReturnValue(2);
    }

    @Redirect(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private Framebuffer thehouse$iconFramebuffer(MinecraftClient client) {
        return BrowserItemIcons.owns(this) ? BrowserItemIcons.target() : client.getFramebuffer();
    }

    @Redirect(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferWidth()I"))
    private int thehouse$iconWidth(Window window) {
        return BrowserItemIcons.owns(this) ? 32 : window.getFramebufferWidth();
    }

    @Redirect(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferHeight()I"))
    private int thehouse$iconHeight(Window window) {
        return BrowserItemIcons.owns(this) ? 32 : window.getFramebufferHeight();
    }

    @Redirect(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getScaleFactor()I"))
    private int thehouse$iconOutputScale(Window window) {
        return BrowserItemIcons.owns(this) ? 2 : window.getScaleFactor();
    }
}
