package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class DropperGuiSimulation {
    private DropperGuiSimulation() {}

    public static ItemStack replacementForSlot(ScreenHandler handler, Slot slot) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!ModState.isSimulationEnabled()
                || !(client.currentScreen instanceof Generic3x3ContainerScreen)) {
            return null;
        }

        
        int handlerIndex = handler.slots.indexOf(slot);
        if (handlerIndex < 0 || handlerIndex > 8) {
            return null;
        }

        if (!looksLikeRightDropper(handler)) {
            return null;
        }

        int actual = ModState.getLastActualRight();
        int displayed = ModState.getLastDisplayedRight();

        if (actual < 1 || actual > 9 || displayed < 1 || displayed > 9 || actual == displayed) {
            return null;
        }

        
        
        int actualSlot = actual - 1;
        int displayedSlot = displayed - 1;

        if (handlerIndex == actualSlot) {
            
            ItemStack retainedActual = ModState.getLastActualRightStack();
            if (!retainedActual.isEmpty()) {
                return retainedActual;
            }
            return PaperItemCatalog.createRightStack(actual);
        }

        if (handlerIndex == displayedSlot) {
            
            
            
            if (InventoryPickupMask.isReturnedToDropperVisual()) {
                ItemStack visualReturned = PaperItemCatalog.createRightStack(displayed);
                ItemStack underlying = slot.getStack();
                if (!underlying.isEmpty()) {
                    visualReturned.setCount(underlying.getCount());
                }
                return visualReturned;
            }

            
            
            
            if (InventoryPickupMask.isActive()) {
                return ItemStack.EMPTY;
            }

            
            
            
            
            ItemStack underlying = slot.getStack();
            PaperItemParser.ParsedItem returned = PaperItemParser.parse(underlying);

            if (returned != null
                    && returned.side() == PaperItemParser.Side.RIGHT
                    && returned.value() == actual) {
                ItemStack visualReturned = PaperItemCatalog.createRightStack(displayed);
                visualReturned.setCount(underlying.getCount());
                return visualReturned;
            }

            
            
            
            
            if (returned != null
                    && returned.side() == PaperItemParser.Side.RIGHT
                    && returned.value() == displayed) {
                return ItemStack.EMPTY;
            }

            return ItemStack.EMPTY;
        }

        return null;
    }

    




    private static boolean looksLikeRightDropper(ScreenHandler handler) {
        int rightMatches = 0;
        int leftMatches = 0;

        for (int i = 0; i < Math.min(9, handler.slots.size()); i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(stack);

            if (parsed == null) {
                continue;
            }

            if (parsed.side() == PaperItemParser.Side.RIGHT) {
                rightMatches++;
            } else {
                leftMatches++;
            }
        }

        
        
        
        
        return rightMatches >= 2 && rightMatches > leftMatches;
    }
}
