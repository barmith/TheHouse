package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.DropperSideLock;
import com.thehouse.paperutility.BlackjackQuickMove;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(HandledScreen.class)
public abstract class HandledScreenSideLockMixin {
    @Shadow
    @Final
    protected ScreenHandler handler;

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void thehouse$sideLockClick(
            Slot slot,
            int slotId,
            int button,
            SlotActionType actionType,
            CallbackInfo ci
    ) {
        if (DropperSideLock.shouldBlock(handler, slot, button, actionType)) {
            ci.cancel();
            return;
        }

        
        
        
        
        if (BlackjackQuickMove.handle(handler, slot, slotId, button, actionType)) {
            ci.cancel();
            return;
        }

        
        
        
        DropperSideLock.beforeAllowedClick(handler, slot, button, actionType);
    }
}
