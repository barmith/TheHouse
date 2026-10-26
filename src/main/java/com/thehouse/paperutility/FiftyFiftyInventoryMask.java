package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;





public final class FiftyFiftyInventoryMask {
    private static final List<ItemStack> BASELINE = new ArrayList<>();
    private static final Map<Integer, ItemStack> OVERRIDES = new HashMap<>();
    private static final Map<Integer, ItemStack> RENDER_ORIGINALS = new HashMap<>();

    private static boolean baselineCaptured;
    private static boolean renderActive;
    private static FiftyFiftyItem.Outcome actual;
    private static FiftyFiftyItem.Outcome displayed;
    private static int baselineActualTotal;

    private FiftyFiftyInventoryMask() {}

    
    public static void captureBaseline(MinecraftClient client) {
        clear();
        if (client == null || client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) BASELINE.add(inv.getStack(i).copy());
        baselineCaptured = true;
    }

    




    public static void detectPickup(MinecraftClient client,
                                    FiftyFiftyItem.Outcome trueOutcome,
                                    FiftyFiftyItem.Outcome shownOutcome) {
        if (!baselineCaptured || client == null || client.player == null
                || trueOutcome == null || shownOutcome == null || trueOutcome == shownOutcome
                || !ModState.isSimulationEnabled() || !ModState.isFiftyFiftyModeEnabled()) return;

        actual = trueOutcome;
        displayed = shownOutcome;
        baselineActualTotal = totalMatching(BASELINE, actual);
        rebuild(client);

        if (OVERRIDES.isEmpty()) clear();
    }

    
    public static void reconcile(MinecraftClient client) {
        if (!isActive() || client == null || client.player == null) return;

        ItemStack cursor = client.player.currentScreenHandler.getCursorStack();
        int owned = totalMatching(client.player.getInventory(), actual) + matchingCount(cursor, actual);
        if (owned <= baselineActualTotal) {
            clear();
            return;
        }
        rebuild(client);
    }

    private static void rebuild(MinecraftClient client) {
        if (actual == null || displayed == null || client == null || client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        Map<Integer, ItemStack> rebuilt = new HashMap<>();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack current = inv.getStack(i);
            int currentCount = matchingCount(current, actual);
            if (currentCount <= 0) continue;

            ItemStack before = i < BASELINE.size() ? BASELINE.get(i) : ItemStack.EMPTY;
            int beforeCount = matchingCount(before, actual);
            int acquired = currentCount - beforeCount;
            if (acquired <= 0 && !ItemStack.areEqual(before, current)) acquired = currentCount;
            if (acquired <= 0) continue;

            if (beforeCount == 0) {
                ItemStack visual = FiftyFiftyItem.create(displayed);
                visual.setCount(acquired);
                rebuilt.put(i, visual);
            } else {
                ItemStack retained = before.copy();
                retained.setCount(beforeCount);
                rebuilt.put(i, retained);

                int empty = findEmpty(inv, rebuilt, i);
                if (empty >= 0) {
                    ItemStack visual = FiftyFiftyItem.create(displayed);
                    visual.setCount(acquired);
                    rebuilt.put(empty, visual);
                } else {
                    ItemStack visual = FiftyFiftyItem.create(displayed);
                    visual.setCount(current.getCount());
                    rebuilt.put(i, visual);
                }
            }
        }

        OVERRIDES.clear();
        OVERRIDES.putAll(rebuilt);
    }

    public static ItemStack cursorReplacement(ItemStack realCursor) {
        if (!isActive() || realCursor == null || realCursor.isEmpty()
                || FiftyFiftyItem.parse(realCursor) != actual) return null;
        ItemStack visual = FiftyFiftyItem.create(displayed);
        visual.setCount(realCursor.getCount());
        return visual;
    }

    public static ItemStack heldItemReplacement(MinecraftClient client, ItemStack renderedStack) {
        if (!isActive() || client == null || client.player == null || renderedStack == null
                || FiftyFiftyItem.parse(renderedStack) != actual) return null;
        int selected = client.player.getInventory().getSelectedSlot();
        ItemStack visual = OVERRIDES.get(selected);
        if (visual == null || visual.isEmpty()) return null;
        ItemStack replacement = visual.copy();
        replacement.setCount(renderedStack.getCount());
        return replacement;
    }

    public static ItemStack tooltipReplacement(ItemStack cachedStack) {
        if (!isActive() || cachedStack == null || FiftyFiftyItem.parse(cachedStack) != actual) return null;
        ItemStack visual = FiftyFiftyItem.create(displayed);
        visual.setCount(cachedStack.getCount());
        return visual;
    }

    public static void beginRender(MinecraftClient client) {
        if (renderActive || OVERRIDES.isEmpty() || client == null || client.player == null) return;
        PlayerInventory inv = client.player.getInventory();
        RENDER_ORIGINALS.clear();
        for (Map.Entry<Integer, ItemStack> e : OVERRIDES.entrySet()) {
            int i = e.getKey();
            if (i < 0 || i >= inv.size()) continue;
            RENDER_ORIGINALS.put(i, inv.getStack(i).copy());
            inv.setStack(i, e.getValue().copy());
        }
        renderActive = !RENDER_ORIGINALS.isEmpty();
    }

    public static void endRender(MinecraftClient client) {
        if (!renderActive) return;
        if (client != null && client.player != null) {
            PlayerInventory inv = client.player.getInventory();
            for (Map.Entry<Integer, ItemStack> e : RENDER_ORIGINALS.entrySet()) {
                if (e.getKey() >= 0 && e.getKey() < inv.size()) inv.setStack(e.getKey(), e.getValue().copy());
            }
        }
        RENDER_ORIGINALS.clear();
        renderActive = false;
    }

    public static boolean isActive() {
        return actual != null && displayed != null && !OVERRIDES.isEmpty();
    }

    public static void clear() {
        BASELINE.clear();
        OVERRIDES.clear();
        RENDER_ORIGINALS.clear();
        baselineCaptured = false;
        renderActive = false;
        actual = null;
        displayed = null;
        baselineActualTotal = 0;
    }

    private static int totalMatching(List<ItemStack> stacks, FiftyFiftyItem.Outcome outcome) {
        int total = 0;
        for (ItemStack stack : stacks) total += matchingCount(stack, outcome);
        return total;
    }

    private static int totalMatching(PlayerInventory inv, FiftyFiftyItem.Outcome outcome) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) total += matchingCount(inv.getStack(i), outcome);
        return total;
    }

    private static int matchingCount(ItemStack stack, FiftyFiftyItem.Outcome outcome) {
        return stack != null && !stack.isEmpty() && FiftyFiftyItem.parse(stack) == outcome ? stack.getCount() : 0;
    }

    private static int findEmpty(PlayerInventory inv, Map<Integer, ItemStack> overrides, int excluded) {
        for (int i = 0; i < inv.size(); i++) {
            if (i == excluded || overrides.containsKey(i)) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
}
