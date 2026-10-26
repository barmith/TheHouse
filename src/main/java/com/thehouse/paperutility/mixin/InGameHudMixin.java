package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.InventoryPickupMask;
import com.thehouse.paperutility.FiftyFiftyInventoryMask;
import com.thehouse.paperutility.BlackjackMasking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;






@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow private ItemStack currentStack;

    private ItemStack paperutility$savedTooltipStack;

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"))
    private void paperutility$beforeHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        ItemStack replacement = InventoryPickupMask.tooltipReplacement(this.currentStack);
        if (replacement == null) {
            replacement = FiftyFiftyInventoryMask.tooltipReplacement(this.currentStack);
        }
        if (replacement == null) {
            replacement = BlackjackMasking.tooltipReplacement(this.currentStack);
        }
        if (replacement == null) {
            replacement = com.thehouse.paperutility.ArrowInventoryMask.tooltipReplacement(this.currentStack);
        }
        if (replacement != null) {
            this.paperutility$savedTooltipStack = this.currentStack;
            this.currentStack = replacement;
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("RETURN"))
    private void paperutility$afterHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (this.paperutility$savedTooltipStack != null) {
            this.currentStack = this.paperutility$savedTooltipStack;
            this.paperutility$savedTooltipStack = null;
        }
    }
}
