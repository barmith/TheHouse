package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.BlackjackTracker;
import com.thehouse.paperutility.ArrowTracker;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;









@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameEntityRendererMixin {
    @Redirect(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/ItemFrameEntity;getHeldItemStack()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack paperutility$blackjackFrameRenderStack(ItemFrameEntity frame) {
        ItemStack real = frame.getHeldItemStack();
        ItemStack arrowVisual = ArrowTracker.frameRenderStack(frame, real);
        return BlackjackTracker.frameRenderStack(frame, arrowVisual);
    }
}
