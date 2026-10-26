package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;


public final class FiftyFiftyDropperGui {
    private FiftyFiftyDropperGui() {}

    public static ItemStack replacementForSlot(ScreenHandler handler, Slot slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ModState.isSimulationEnabled() || !ModState.isFiftyFiftyModeEnabled()
                || !(client.currentScreen instanceof Generic3x3ContainerScreen)) return null;
        int index = handler.slots.indexOf(slot);
        if (index < 0 || index > 1 || handler.slots.size() < 2 || !looksLikeGame(handler)) return null;

        FiftyFiftyItem.Outcome actual = FiftyFiftyTracker.getLastActual();
        FiftyFiftyItem.Outcome displayed = FiftyFiftyTracker.getLastDisplayed();
        if (actual == null || displayed == null || actual == displayed
                || FiftyFiftyTracker.isReturnedToDropper()) return null;

        int actualSlot = actual == FiftyFiftyItem.Outcome.HOST ? 0 : 1;
        int displayedSlot = displayed == FiftyFiftyItem.Outcome.HOST ? 0 : 1;
        if (index == actualSlot) {
            ItemStack retained = FiftyFiftyTracker.getLastActualStack();
            if (!retained.isEmpty()) return retained;
            return FiftyFiftyItem.create(actual);
        }
        if (index == displayedSlot) return ItemStack.EMPTY;
        return null;
    }

    private static boolean looksLikeGame(ScreenHandler handler) {
        FiftyFiftyItem.Outcome a = FiftyFiftyItem.parse(handler.slots.get(0).getStack());
        FiftyFiftyItem.Outcome b = FiftyFiftyItem.parse(handler.slots.get(1).getStack());
        
        return (a == FiftyFiftyItem.Outcome.HOST && (b == FiftyFiftyItem.Outcome.VIEWER || handler.slots.get(1).getStack().isEmpty()))
                || (b == FiftyFiftyItem.Outcome.VIEWER && (a == FiftyFiftyItem.Outcome.HOST || handler.slots.get(0).getStack().isEmpty()));
    }
}
