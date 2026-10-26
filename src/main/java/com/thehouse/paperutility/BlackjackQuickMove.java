package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;














public final class BlackjackQuickMove {
    private BlackjackQuickMove() {}

    



    public static boolean handle(
            ScreenHandler handler,
            Slot clickedSlot,
            int slotId,
            int button,
            SlotActionType actionType
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null
                || !BlackjackState.isEnabled()
                || !(client.currentScreen instanceof Generic3x3ContainerScreen)
                || handler == null || clickedSlot == null
                || actionType != SlotActionType.QUICK_MOVE
                || !handler.getCursorStack().isEmpty()) {
            return false;
        }

        int handlerIndex = handler.slots.indexOf(clickedSlot);
        if (handlerIndex < 9) {
            return false; 
        }

        PlayerInventory inventory = client.player.getInventory();
        int clickedInventorySlot = clickedSlot.getIndex();
        if (clickedInventorySlot < 0 || clickedInventorySlot >= inventory.size()
                || clickedSlot.getStack() != inventory.getStack(clickedInventorySlot)) {
            return false;
        }

        if (!BlackjackDropperVisualMask.prepareForInteraction(handler)) {
            return false;
        }

        List<BlackjackMasking.VirtualReturnSource> sources =
                BlackjackMasking.virtualReturnSources(inventory, clickedInventorySlot);
        if (sources.isEmpty()) {
            return false;
        }

        
        
        
        for (BlackjackMasking.VirtualReturnSource source : sources) {
            int sourceHandlerSlot = handlerSlotForInventoryIndex(handler, source.inventoryIndex());
            int targetContainerSlot = BlackjackDropperVisualMask.containerSlotForCard(source.actual());
            if (sourceHandlerSlot < 9 || targetContainerSlot < 0 || targetContainerSlot >= 9) {
                return false;
            }
            ItemStack realSource = inventory.getStack(source.inventoryIndex());
            if (BlackjackCard.fromStack(realSource) != source.actual()
                    || source.count() <= 0 || realSource.getCount() < source.count()) {
                return false;
            }
        }

        
        
        client.interactionManager.clickSlot(
                handler.syncId, slotId, button, SlotActionType.QUICK_MOVE, client.player
        );

        
        
        
        
        for (BlackjackMasking.VirtualReturnSource source : sources) {
            int sourceHandlerSlot = handlerSlotForInventoryIndex(handler, source.inventoryIndex());
            if (sourceHandlerSlot < 9) {
                continue;
            }

            ItemStack current = inventory.getStack(source.inventoryIndex());
            int physicalCount = current.getCount();
            int moveCount = Math.min(source.count(), physicalCount);
            if (moveCount <= 0) {
                continue;
            }

            if (moveCount == physicalCount) {
                client.interactionManager.clickSlot(
                        handler.syncId, sourceHandlerSlot, 0, SlotActionType.QUICK_MOVE, client.player
                );
                continue;
            }

            int targetContainerSlot = BlackjackDropperVisualMask.containerSlotForCard(source.actual());

            
            
            
            client.interactionManager.clickSlot(
                    handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player
            );
            for (int n = 0; n < moveCount; n++) {
                client.interactionManager.clickSlot(
                        handler.syncId, targetContainerSlot, 1, SlotActionType.PICKUP, client.player
                );
            }
            client.interactionManager.clickSlot(
                    handler.syncId, sourceHandlerSlot, 0, SlotActionType.PICKUP, client.player
            );
        }

        return true;
    }

    private static int handlerSlotForInventoryIndex(ScreenHandler handler, int inventoryIndex) {
        for (int i = 9; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot.getIndex() == inventoryIndex) {
                return i;
            }
        }
        return -1;
    }
}
