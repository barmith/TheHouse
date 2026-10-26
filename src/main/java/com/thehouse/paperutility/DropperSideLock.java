package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;












public final class DropperSideLock {
    private DropperSideLock() {}

    public static boolean shouldBlock(
            ScreenHandler handler,
            Slot clickedSlot,
            int button,
            SlotActionType actionType
    ) {
        if (!ModState.isDropperSideLockEnabled()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || !(client.currentScreen instanceof Generic3x3ContainerScreen)
                || handler == null) {
            return false;
        }

        PaperItemParser.Side dropperSide = identifyDropperSide(handler);
        if (dropperSide == null) {
            
            return false;
        }

        int clickedIndex = clickedSlot == null ? -1 : handler.slots.indexOf(clickedSlot);
        boolean clickedContainerSlot = clickedIndex >= 0 && clickedIndex < 9;
        boolean clickedPlayerSlot = clickedIndex >= 9;

        ItemStack candidate = ItemStack.EMPTY;
        boolean canInsertIntoContainer = false;

        switch (actionType) {
            case PICKUP -> {
                
                
                if (clickedContainerSlot && !handler.getCursorStack().isEmpty()) {
                    candidate = handler.getCursorStack();
                    canInsertIntoContainer = true;
                }
            }
            case QUICK_MOVE -> {
                
                
                if (clickedPlayerSlot && clickedSlot != null && !clickedSlot.getStack().isEmpty()) {
                    candidate = clickedSlot.getStack();
                    canInsertIntoContainer = true;
                }
            }
            case SWAP -> {
                
                
                if (clickedContainerSlot) {
                    if (button >= 0 && button < 9) {
                        candidate = client.player.getInventory().getStack(button);
                        canInsertIntoContainer = !candidate.isEmpty();
                    } else if (button == 40) {
                        candidate = client.player.getOffHandStack();
                        canInsertIntoContainer = !candidate.isEmpty();
                    }
                }
            }
            case QUICK_CRAFT -> {
                
                
                if (clickedContainerSlot && !handler.getCursorStack().isEmpty()) {
                    candidate = handler.getCursorStack();
                    canInsertIntoContainer = true;
                }
            }
            default -> {
                
                
            }
        }

        if (!canInsertIntoContainer || candidate.isEmpty()) {
            return false;
        }

        PaperItemParser.ParsedItem numbered = PaperItemParser.parseNumberedName(candidate);
        if (numbered == null) {
            
            
            
            return true;
        }

        
        
        return numbered.side() != dropperSide;
    }

    





    public static void beforeAllowedClick(
            ScreenHandler handler,
            Slot clickedSlot,
            int button,
            SlotActionType actionType
    ) {
        if (!ModState.isDropperSideLockEnabled()
                || !InventoryPickupMask.isActive()
                || handler == null
                || clickedSlot == null) {
            return;
        }

        if (identifyDropperSide(handler) != PaperItemParser.Side.RIGHT) {
            return;
        }

        int handlerIndex = handler.slots.indexOf(clickedSlot);

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (handlerIndex < 9 || clickedSlot.getStack().isEmpty()) {
                return;
            }
            int inventoryIndex = InventoryPickupMask.maskedInventoryIndexForHandlerSlot(handlerIndex);
            if (inventoryIndex >= 0) {
                InventoryPickupMask.beginReturnAttempt(inventoryIndex);
            }
            return;
        }

        if (actionType == SlotActionType.PICKUP) {
            
            
            
            if (handlerIndex >= 9 && handler.getCursorStack().isEmpty()) {
                int inventoryIndex = InventoryPickupMask.maskedInventoryIndexForHandlerSlot(handlerIndex);
                if (inventoryIndex >= 0) {
                    InventoryPickupMask.beginCursorPickup(inventoryIndex);
                }
                return;
            }

            
            
            
            if (handlerIndex >= 0 && handlerIndex < 9 && InventoryPickupMask.isCursorCarryActive()) {
                InventoryPickupMask.beginCursorReturnAttempt();
            }
        }
    }

    




    private static PaperItemParser.Side identifyDropperSide(ScreenHandler handler) {
        int leftMatches = 0;
        int rightMatches = 0;

        int limit = Math.min(9, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            PaperItemParser.ParsedItem parsed = PaperItemParser.parseNumberedName(stack);
            if (parsed == null) {
                continue;
            }

            if (parsed.side() == PaperItemParser.Side.LEFT) {
                leftMatches++;
            } else {
                rightMatches++;
            }
        }

        if (leftMatches >= 2 && leftMatches > rightMatches) {
            return PaperItemParser.Side.LEFT;
        }
        if (rightMatches >= 2 && rightMatches > leftMatches) {
            return PaperItemParser.Side.RIGHT;
        }
        return null;
    }
}
