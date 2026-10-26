package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.InventoryPickupMask;
import com.thehouse.paperutility.FiftyFiftyInventoryMask;
import com.thehouse.paperutility.BlackjackMasking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;






@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private Hand paperutility$renderingHand;

    @ModifyVariable(
            method = "renderFirstPersonItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack paperutility$maskHeldItem(
            ItemStack stack, AbstractClientPlayerEntity player, float tickProgress,
            float pitch, Hand hand, float swingProgress, ItemStack original,
            float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        
        
        paperutility$renderingHand = hand;
        return paperutility$maskedHeldStack(stack);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void paperutility$clearRenderHand(CallbackInfo ci) {
        paperutility$renderingHand = null;
    }

    







    @ModifyVariable(
            method = "renderFirstPersonMap",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack paperutility$maskFirstPersonMap(ItemStack stack) {
        return paperutility$maskedHeldStack(stack);
    }

    private ItemStack paperutility$maskedHeldStack(ItemStack stack) {
        ItemStack replacement = InventoryPickupMask.heldItemReplacement(
                MinecraftClient.getInstance(),
                stack
        );
        if (replacement == null) {
            replacement = FiftyFiftyInventoryMask.heldItemReplacement(MinecraftClient.getInstance(), stack);
        }
        if (replacement == null && paperutility$renderingHand == Hand.MAIN_HAND) {
            replacement = BlackjackMasking.heldItemReplacement(MinecraftClient.getInstance(), stack);
        }
        if (replacement == null) {
            replacement = com.thehouse.paperutility.ArrowInventoryMask.heldReplacement(MinecraftClient.getInstance(), stack);
        }
        return replacement != null ? replacement : stack;
    }
}
