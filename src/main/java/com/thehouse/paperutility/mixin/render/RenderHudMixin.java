package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class RenderHudMixin {
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void thehouse$portal(CallbackInfo ci) { if (RenderModules.omit("noPortal")) ci.cancel(); }
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void thehouse$vignette(CallbackInfo ci) { if (RenderModules.omit("noVignette")) ci.cancel(); }
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void thehouse$scoreboard(CallbackInfo ci) { if (RenderModules.omit("noScoreboard")) ci.cancel(); }
}
