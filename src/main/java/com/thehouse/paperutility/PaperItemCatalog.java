package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;











public final class PaperItemCatalog {
    private static final Item[] DEFAULT_ITEMS = {
            Items.DIRT,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.DIAMOND,
            Items.TOTEM_OF_UNDYING,
            Items.WITHER_SKELETON_SKULL,
            Items.MACE,
            Items.NETHERITE_INGOT,
            Items.TRIDENT
    };

    private static final Item[] SLOT_ITEMS = DEFAULT_ITEMS.clone();

    
    private static final ItemStack[] STACK_TEMPLATES = new ItemStack[9];

    
    private static final ItemStack[] LEFT_OBSERVED = new ItemStack[9];
    private static final ItemStack[] RIGHT_OBSERVED = new ItemStack[9];

    private PaperItemCatalog() {}

    public static Item getItem(int value) {
        if (value < 1 || value > 9) {
            return Items.AIR;
        }
        Item item = SLOT_ITEMS[value - 1];
        return item == null ? DEFAULT_ITEMS[value - 1] : item;
    }

    public static void setItem(int value, Item item) {
        if (value < 1 || value > 9 || item == null || item == Items.AIR) {
            return;
        }
        SLOT_ITEMS[value - 1] = item;
        clearLearnedTemplate(value);
    }

    public static void resetDefaults() {
        System.arraycopy(DEFAULT_ITEMS, 0, SLOT_ITEMS, 0, DEFAULT_ITEMS.length);
        clearLearnedTemplates();
    }

    public static String getItemId(int value) {
        Identifier id = Registries.ITEM.getId(getItem(value));
        return id == null ? "minecraft:air" : id.toString();
    }

    public static boolean setItemById(int value, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return false;
        }
        Identifier id = Identifier.tryParse(rawId.trim());
        if (id == null || !Registries.ITEM.containsId(id)) {
            return false;
        }
        Item item = Registries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return false;
        }
        setItem(value, item);
        return true;
    }

    
    public static List<Item> allItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item != null && item != Items.AIR) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparing(item -> {
            Identifier id = Registries.ITEM.getId(item);
            return id == null ? "" : id.toString();
        }));
        return result;
    }

    




    public static void learnFromOpenDropper(MinecraftClient client) {
        if (client == null
                || !(client.currentScreen instanceof Generic3x3ContainerScreen screen)) {
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        int limit = Math.min(9, handler.slots.size());

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(stack);
            if (parsed == null) {
                continue;
            }
            observeExactStack(parsed.value(), parsed.side(), stack);
        }
    }

    private static void observeExactStack(int value, PaperItemParser.Side side, ItemStack stack) {
        if (value < 1 || value > 9 || stack == null || stack.isEmpty()
                || !matchesExpectedItem(stack, value)) {
            return;
        }

        int index = value - 1;
        ItemStack observed = stack.copy();
        observed.setCount(1);

        if (side == PaperItemParser.Side.LEFT) {
            LEFT_OBSERVED[index] = observed;
        } else {
            RIGHT_OBSERVED[index] = observed;
        }

        ItemStack other = side == PaperItemParser.Side.LEFT
                ? RIGHT_OBSERVED[index]
                : LEFT_OBSERVED[index];

        if (other == null || other.isEmpty()) {
            
            
            
            STACK_TEMPLATES[index] = observed.copy();
            return;
        }

        if (sameTemplateData(observed, other, value)) {
            STACK_TEMPLATES[index] = observed.copy();
        }
        
        
    }

    
    private static boolean sameTemplateData(ItemStack a, ItemStack b, int value) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()
                || !a.isOf(getItem(value)) || !b.isOf(getItem(value))) {
            return false;
        }

        ItemStack aa = a.copy();
        ItemStack bb = b.copy();
        aa.setCount(1);
        bb.setCount(1);
        Text canonicalName = Text.literal(Integer.toString(value));
        aa.set(DataComponentTypes.CUSTOM_NAME, canonicalName);
        bb.set(DataComponentTypes.CUSTOM_NAME, canonicalName);
        return ItemStack.areEqual(aa, bb);
    }

    public static void clearLearnedTemplate(int value) {
        if (value < 1 || value > 9) {
            return;
        }
        int index = value - 1;
        STACK_TEMPLATES[index] = null;
        LEFT_OBSERVED[index] = null;
        RIGHT_OBSERVED[index] = null;
    }

    public static void clearLearnedTemplates() {
        for (int i = 0; i < 9; i++) {
            STACK_TEMPLATES[i] = null;
            LEFT_OBSERVED[i] = null;
            RIGHT_OBSERVED[i] = null;
        }
    }

    public static ItemStack createRightStack(int value) {
        return createStack(value, false);
    }

    public static ItemStack createStack(int value, boolean leftSide) {
        Item item = getItem(value);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack template = value >= 1 && value <= 9 ? STACK_TEMPLATES[value - 1] : null;
        ItemStack stack;
        if (template != null && !template.isEmpty() && template.isOf(item)) {
            stack = template.copy();
            stack.setCount(1);
        } else {
            stack = new ItemStack(item);
        }

        
        String name = leftSide ? value + ".1" : Integer.toString(value);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    public static boolean matchesExpectedItem(ItemStack stack, int value) {
        Item expected = getItem(value);
        return expected != Items.AIR && stack.isOf(expected);
    }
}
