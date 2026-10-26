package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class RenderOverlayMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void thehouse$fire(CallbackInfo ci) { if (RenderModules.omit("noFire")) ci.cancel(); }
    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void thehouse$water(CallbackInfo ci) { if (RenderModules.omit("noWater")) ci.cancel(); }
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void thehouse$wall(CallbackInfo ci) { if (RenderModules.omit("noWall")) ci.cancel(); }
}
