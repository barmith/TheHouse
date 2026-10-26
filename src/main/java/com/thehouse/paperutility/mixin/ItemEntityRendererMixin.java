package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.ArrowTracker;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Shadow @Final private ItemModelManager itemModelManager;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void thehouse$maskArrowImpactDrop(ItemEntity item, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        
        
        
        
        ItemStack visualStack = ArrowTracker.droppedItemRenderStack(item);
        if (visualStack != null && !visualStack.isEmpty()) {
            state.update(item, visualStack, this.itemModelManager);
        }
        ArrowTracker.applyDroppedItemRenderMask(item, state);
    }
}
