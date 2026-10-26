package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class RenderBossbarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void thehouse$bossbar(CallbackInfo ci) { if (RenderModules.omit("noBossbar")) ci.cancel(); }
}
