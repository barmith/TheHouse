package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.DropperGuiSimulation;
import com.thehouse.paperutility.FiftyFiftyDropperGui;
import com.thehouse.paperutility.FiftyFiftyInventoryMask;
import com.thehouse.paperutility.InventoryPickupMask;
import com.thehouse.paperutility.BlackjackMasking;
import com.thehouse.paperutility.BlackjackDropperVisualMask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    @Final
    protected ScreenHandler handler;

    @Unique
    private Slot paperutility$modifiedSlot;

    @Unique
    private ItemStack paperutility$originalStack;

    @Unique
    private ItemStack thehouse$originalCursorStack;

    @Unique
    private boolean thehouse$cursorSwapped;

    



    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void paperutility$beforeDrawSlot(
            DrawContext context,
            Slot slot,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        ItemStack replacement = FiftyFiftyDropperGui.replacementForSlot(handler, slot);
        if (replacement == null) {
            replacement = DropperGuiSimulation.replacementForSlot(handler, slot);
        }
        if (replacement == null) {
            replacement = BlackjackMasking.replacementForSlot(MinecraftClient.getInstance(), slot);
        }
        if (replacement == null) {
            replacement = BlackjackDropperVisualMask.replacementForSlot(handler, slot);
        }
        if (replacement == null) {
            return;
        }

        paperutility$modifiedSlot = slot;
        paperutility$originalStack = slot.getStack().copy();
        slot.setStackNoCallbacks(replacement.copy());
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void paperutility$afterDrawSlot(
            DrawContext context,
            Slot slot,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (paperutility$modifiedSlot == slot && paperutility$originalStack != null) {
            slot.setStackNoCallbacks(paperutility$originalStack);
            paperutility$modifiedSlot = null;
            paperutility$originalStack = null;
        }
    }

    




    @Inject(method = "renderCursorStack", at = @At("HEAD"))
    private void thehouse$beforeRenderCursorStack(
            DrawContext context,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        ItemStack realCursor = handler.getCursorStack();
        ItemStack replacement = InventoryPickupMask.cursorReplacement(realCursor);
        if (replacement == null) {
            replacement = FiftyFiftyInventoryMask.cursorReplacement(realCursor);
        }
        if (replacement == null) {
            replacement = BlackjackMasking.cursorReplacement(realCursor);
        }
        if (replacement == null) {
            return;
        }

        thehouse$originalCursorStack = realCursor.copy();
        handler.setCursorStack(replacement.copy());
        thehouse$cursorSwapped = true;
    }

    @Inject(method = "renderCursorStack", at = @At("RETURN"))
    private void thehouse$afterRenderCursorStack(
            DrawContext context,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        if (!thehouse$cursorSwapped || thehouse$originalCursorStack == null) {
            return;
        }

        handler.setCursorStack(thehouse$originalCursorStack);
        thehouse$originalCursorStack = null;
        thehouse$cursorSwapped = false;
    }
}
