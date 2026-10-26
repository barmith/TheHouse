package com.thehouse.paperutility;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;


public enum BlackjackCard {
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    ACE("A", 11);

    private final String displayName;
    private final int rawValue;

    BlackjackCard(String displayName, int rawValue) {
        this.displayName = displayName;
        this.rawValue = rawValue;
    }

    public String displayName() {
        return displayName;
    }

    public int rawValue() {
        return rawValue;
    }

    public boolean isAce() {
        return this == ACE;
    }

    



    public static BlackjackCard fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) {
            return null;
        }

        String name = stack.getName().getString();
        for (BlackjackCard card : values()) {
            if (card.displayName.equals(name)) {
                return card;
            }
        }
        
        return "Ace".equals(name) ? ACE : null;
    }

}
