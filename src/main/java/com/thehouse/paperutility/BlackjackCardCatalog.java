package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;






public final class BlackjackCardCatalog {
    private static final Map<BlackjackCard, ItemStack> TEMPLATES =
            new EnumMap<>(BlackjackCard.class);

    private BlackjackCardCatalog() {}

    public static void learnFromOpenDropper(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof Generic3x3ContainerScreen screen)) {
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        int limit = Math.min(9, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            
            
            observe(handler.slots.get(i).getStack());
        }
    }

    







    public static void observe(ItemStack stack) {
        BlackjackCard card = BlackjackCard.fromStack(stack);
        if (card == null || stack == null || stack.isEmpty()) {
            return;
        }
        if (TEMPLATES.containsKey(card)) {
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        TEMPLATES.put(card, copy);
    }

    



    public static boolean replaceAll(Map<BlackjackCard, ItemStack> replacements) {
        if (replacements == null || replacements.size() != BlackjackCard.values().length) {
            return false;
        }

        EnumMap<BlackjackCard, ItemStack> staged = new EnumMap<>(BlackjackCard.class);
        for (BlackjackCard card : BlackjackCard.values()) {
            ItemStack stack = replacements.get(card);
            if (stack == null || stack.isEmpty() || BlackjackCard.fromStack(stack) != card) {
                return false;
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);
            staged.put(card, copy);
        }

        TEMPLATES.clear();
        TEMPLATES.putAll(staged);
        return true;
    }

    public static ItemStack create(BlackjackCard card, int count) {
        if (card == null) {
            return ItemStack.EMPTY;
        }
        ItemStack template = TEMPLATES.get(card);
        if (template == null || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = template.copy();
        result.setCount(Math.max(1, count));
        
        result.set(DataComponentTypes.CUSTOM_NAME, Text.literal(card.displayName()));
        return result;
    }

    public static BlackjackCard[] availableCards() {
        return TEMPLATES.keySet().toArray(BlackjackCard[]::new);
    }

    public static void clear() {
        TEMPLATES.clear();
    }
}
