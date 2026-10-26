package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.InventoryPickupMask;
import com.thehouse.paperutility.FiftyFiftyInventoryMask;
import com.thehouse.paperutility.BlackjackMasking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;





@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void paperutility$beforeRender(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        
        
        
        
        InventoryPickupMask.reconcile(MinecraftClient.getInstance());
        InventoryPickupMask.beginRender(MinecraftClient.getInstance());
        FiftyFiftyInventoryMask.beginRender(MinecraftClient.getInstance());
        BlackjackMasking.beginRender(MinecraftClient.getInstance());
        com.thehouse.paperutility.ArrowInventoryMask.beginRender(MinecraftClient.getInstance());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void paperutility$afterRender(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        com.thehouse.paperutility.ArrowInventoryMask.endRender(MinecraftClient.getInstance());
        BlackjackMasking.endRender(MinecraftClient.getInstance());
        FiftyFiftyInventoryMask.endRender(MinecraftClient.getInstance());
        InventoryPickupMask.endRender(MinecraftClient.getInstance());
    }
}
