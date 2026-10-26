package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.FreeCamera;
import com.thehouse.paperutility.render.FreecamInteraction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class RenderClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void thehouse$attack(CallbackInfoReturnable<Boolean> cir) {
        if (!FreeCamera.active()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON)
                || !FreecamInteraction.prepare(client)) cir.setReturnValue(false);
    }
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void thehouse$interaction(CallbackInfo ci) {
        if (FreeCamera.active() && !FreecamInteraction.prepare(MinecraftClient.getInstance())) ci.cancel();
    }
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void thehouse$breaking(boolean breaking, CallbackInfo ci) {
        if (!FreeCamera.active() || !breaking) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!FreecamInteraction.prepare(client)) {
            if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            ci.cancel();
        }
    }
    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void thehouse$pick(CallbackInfo ci) {
        if (FreeCamera.active()) ci.cancel();
    }
    @Redirect(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult thehouse$blockUseOnly(ClientPlayerInteractionManager manager, PlayerEntity player, Hand hand) {
        return FreeCamera.active() ? ActionResult.PASS : manager.interactItem(player, hand);
    }
}
