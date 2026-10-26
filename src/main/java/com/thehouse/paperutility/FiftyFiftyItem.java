package com.thehouse.paperutility;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class FiftyFiftyItem {
    public enum Outcome { HOST, VIEWER }

    private FiftyFiftyItem() {}

    public static Outcome parse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.PAPER)) return null;
        String name = stack.getName().getString();
        if ("Host win".equals(name)) return Outcome.HOST;
        if ("Viewer win".equals(name)) return Outcome.VIEWER;
        return null;
    }

    public static Outcome preferredOutcome() {
        return ModState.getWinningSide() == WinningSide.RIGHT ? Outcome.HOST : Outcome.VIEWER;
    }

    public static ItemStack create(Outcome outcome) {
        ItemStack learned = FiftyFiftyTracker.learnedStack(outcome);
        if (!learned.isEmpty()) return learned;
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(outcome == Outcome.HOST ? "Host win" : "Viewer win"));
        return stack;
    }
}
