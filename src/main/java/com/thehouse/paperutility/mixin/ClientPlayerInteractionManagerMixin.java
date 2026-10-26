package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.BlackjackMasking;
import com.thehouse.paperutility.BlackjackTracker;
import com.thehouse.paperutility.ArrowTracker;
import com.thehouse.paperutility.ArrowInventoryMask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;






@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    private static boolean paperutility$arrowPlacementRedirect;
    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void paperutility$captureBlackjackFramePlacement(
            PlayerEntity player,
            Entity entity,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (entity instanceof ItemFrameEntity frame) {
            if (!paperutility$arrowPlacementRedirect) {
                ItemFrameEntity redirected = ArrowTracker.redirectedPlacementFrame(frame);
                MinecraftClient client = MinecraftClient.getInstance();
                if (redirected != null && client.interactionManager != null) {
                    
                    
                    
                    
                    
                    ArrowInventoryMask.latchPlacementVisual(client);
                    ArrowInventoryMask.endRender(client);
                    paperutility$arrowPlacementRedirect = true;
                    try {
                        cir.setReturnValue(client.interactionManager.interactEntity(player, redirected, hand));
                    } finally {
                        paperutility$arrowPlacementRedirect = false;
                    }
                    return;
                }
            }
            BlackjackMasking.captureFramePlacement(player, frame, hand);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    private void paperutility$captureBlackjackFramePlacementAtLocation(
            PlayerEntity player,
            Entity entity,
            net.minecraft.util.hit.EntityHitResult hitResult,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (entity instanceof ItemFrameEntity frame) {
            if (!paperutility$arrowPlacementRedirect) {
                ItemFrameEntity redirected = ArrowTracker.redirectedPlacementFrame(frame);
                MinecraftClient client = MinecraftClient.getInstance();
                if (redirected != null && client.interactionManager != null) {
                    
                    
                    ArrowInventoryMask.latchPlacementVisual(client);
                    ArrowInventoryMask.endRender(client);
                    paperutility$arrowPlacementRedirect = true;
                    try {
                        
                        
                        
                        
                        
                        
                        net.minecraft.util.hit.EntityHitResult redirectedHit =
                                new net.minecraft.util.hit.EntityHitResult(redirected, redirected.getBoundingBox().getCenter());
                        ActionResult result = client.interactionManager.interactEntityAtLocation(
                                player, redirected, redirectedHit, hand);
                        if (result == ActionResult.PASS) {
                            result = client.interactionManager.interactEntity(player, redirected, hand);
                        }
                        cir.setReturnValue(result);
                    } finally {
                        paperutility$arrowPlacementRedirect = false;
                    }
                    return;
                }
            }
            BlackjackMasking.captureFramePlacement(player, frame, hand);
        }
    }
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void paperutility$captureBlackjackFrameRemoval(
            PlayerEntity player,
            Entity entity,
            CallbackInfo ci
    ) {
        if (entity instanceof ItemFrameEntity frame) {
            BlackjackTracker.captureFrameRemoval(frame);
        }
    }

}
