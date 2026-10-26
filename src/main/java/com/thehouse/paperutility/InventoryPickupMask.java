package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;







public final class InventoryPickupMask {
    private static final List<ItemStack> BASELINE = new ArrayList<>();
    private static final Map<Integer, ItemStack> VISUAL_OVERRIDES = new HashMap<>();
    private static final Map<Integer, ItemStack> RENDER_ORIGINALS = new HashMap<>();

    private static boolean baselineCaptured = false;
    private static boolean renderSwapActive = false;

    
    
    private static int maskedActual = -1;
    private static int maskedDisplayed = -1;
    private static int baselineActualTotal = 0;
    private static boolean returnedToDropperVisual = false;

    
    
    
    
    
    private static boolean returnPending = false;
    private static int returnPendingTicks = 0;
    private static int returnPendingInventorySlot = -1;
    private static ItemStack returnPendingVisual = ItemStack.EMPTY;
    private static final int RETURN_CONFIRM_TIMEOUT_TICKS = 12;
    
    
    private static ItemStack returnHandVisual = ItemStack.EMPTY;
    private static int returnHandTicks = 0;
    private static final int RETURN_HAND_GRACE_TICKS = 4;

    
    
    
    
    private static boolean cursorCarryActive = false;
    private static ItemStack cursorVisual = ItemStack.EMPTY;

    
    
    
    
    private static boolean groundCarryActive = false;
    private static int groundCarryGraceTicks = 0;
    private static final int GROUND_PICKUP_GRACE_TICKS = 6;

    private InventoryPickupMask() {}

    
    public static void captureBaseline(MinecraftClient client) {
        clear();
        returnedToDropperVisual = false;
        if (client.player == null) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            BASELINE.add(inventory.getStack(i).copy());
        }
        baselineCaptured = true;
        
        
    }

    




    public static void detectPickup(MinecraftClient client, int actual, int displayed) {
        if (!baselineCaptured
                || client.player == null
                || !ModState.isSimulationEnabled()
                || actual < 1 || actual > 9
                || displayed < 1 || displayed > 9
                || actual == displayed) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        VISUAL_OVERRIDES.clear();
        maskedActual = actual;
        maskedDisplayed = displayed;
        baselineActualTotal = totalMatchingActual(BASELINE, actual);

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack current = inventory.getStack(i);
            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(current);
            if (parsed == null
                    || parsed.side() != PaperItemParser.Side.RIGHT
                    || parsed.value() != actual) {
                continue;
            }

            ItemStack before = i < BASELINE.size() ? BASELINE.get(i) : ItemStack.EMPTY;
            int beforeCount = matchingActualCount(before, actual);
            int acquiredCount = current.getCount() - beforeCount;

            if (acquiredCount <= 0 && !ItemStack.areEqual(before, current)) {
                
                acquiredCount = current.getCount();
            }

            if (acquiredCount <= 0) {
                continue;
            }

            if (beforeCount == 0) {
                
                
                ItemStack visual = PaperItemCatalog.createRightStack(displayed);
                visual.setCount(acquiredCount);
                VISUAL_OVERRIDES.put(i, visual);
                continue;
            }

            
            
            
            ItemStack retained = before.copy();
            retained.setCount(beforeCount);
            VISUAL_OVERRIDES.put(i, retained);

            int emptySlot = findVisuallyEmptySlot(inventory, i);
            if (emptySlot >= 0) {
                ItemStack visual = PaperItemCatalog.createRightStack(displayed);
                visual.setCount(acquiredCount);
                VISUAL_OVERRIDES.put(emptySlot, visual);
            } else {
                
                
                ItemStack visual = PaperItemCatalog.createRightStack(displayed);
                visual.setCount(current.getCount());
                VISUAL_OVERRIDES.put(i, visual);
            }
        }

        if (VISUAL_OVERRIDES.isEmpty()) {
            maskedActual = -1;
            maskedDisplayed = -1;
            baselineActualTotal = 0;
        }
    }

    





    public static void reconcile(MinecraftClient client) {
        if (returnHandTicks > 0) {
            returnHandTicks--;
            if (returnHandTicks == 0) {
                returnHandVisual = ItemStack.EMPTY;
            }
        }
        if (maskedActual < 1 || client.player == null) {
            return;
        }

        ItemStack cursor = client.player.currentScreenHandler.getCursorStack();
        int inventoryTotal = totalMatchingActual(client.player.getInventory(), maskedActual);
        int cursorTotal = matchingActualCount(cursor, maskedActual);
        int currentOwnedTotal = inventoryTotal + cursorTotal;

        
        
        
        
        rebuildInventoryOverrides(client);

        if (cursorTotal > 0) {
            cursorCarryActive = true;
            if (cursorVisual.isEmpty()) {
                cursorVisual = PaperItemCatalog.createRightStack(maskedDisplayed);
            }
        } else if (cursorCarryActive && !returnPending) {
            
            
            cursorCarryActive = false;
            cursorVisual = ItemStack.EMPTY;
        }

        if (currentOwnedTotal <= baselineActualTotal) {
            
            
            
            
            
            
            if (returnPending) {
                returnedToDropperVisual = true;
                returnPendingTicks++;
                if (returnPendingTicks < 3) {
                    return;
                }
                clearInventoryOverridesOnly();
                returnedToDropperVisual = true;
                return;
            }

            
            
            
            
            if (groundCarryActive || groundCarryGraceTicks > 0) {
                VISUAL_OVERRIDES.clear();
                cursorCarryActive = false;
                cursorVisual = ItemStack.EMPTY;
                if (!groundCarryActive && groundCarryGraceTicks > 0) {
                    groundCarryGraceTicks--;
                }
                return;
            }

            
            
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen) {
                returnedToDropperVisual = true;
            }
            clearInventoryOverridesOnly();
            return;
        }

        
        
        if (returnPending) {
            returnPendingTicks++;
            if (returnPendingTicks >= RETURN_CONFIRM_TIMEOUT_TICKS) {
                if (returnPendingInventorySlot >= 0 && !returnPendingVisual.isEmpty()) {
                    VISUAL_OVERRIDES.put(returnPendingInventorySlot, returnPendingVisual.copy());
                }
                returnPending = false;
                returnPendingTicks = 0;
                returnPendingInventorySlot = -1;
                returnPendingVisual = ItemStack.EMPTY;
                returnHandVisual = ItemStack.EMPTY;
                returnHandTicks = 0;
                returnedToDropperVisual = false;
            }
        }
    }

    
    private static void rebuildInventoryOverrides(MinecraftClient client) {
        if (!baselineCaptured || client.player == null || maskedActual < 1 || maskedDisplayed < 1) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        Map<Integer, ItemStack> rebuilt = new HashMap<>();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack current = inventory.getStack(i);
            PaperItemParser.ParsedItem parsed = PaperItemParser.parse(current);
            if (parsed == null
                    || parsed.side() != PaperItemParser.Side.RIGHT
                    || parsed.value() != maskedActual) {
                continue;
            }

            ItemStack before = i < BASELINE.size() ? BASELINE.get(i) : ItemStack.EMPTY;
            int beforeCount = matchingActualCount(before, maskedActual);
            int acquiredCount = current.getCount() - beforeCount;

            if (acquiredCount <= 0 && !ItemStack.areEqual(before, current)) {
                acquiredCount = current.getCount();
            }
            if (acquiredCount <= 0) {
                continue;
            }

            if (beforeCount == 0) {
                ItemStack visual = PaperItemCatalog.createRightStack(maskedDisplayed);
                visual.setCount(acquiredCount);
                rebuilt.put(i, visual);
                continue;
            }

            ItemStack retained = before.copy();
            retained.setCount(beforeCount);
            rebuilt.put(i, retained);

            int emptySlot = findVisuallyEmptySlotFor(rebuilt, inventory, i);
            if (emptySlot >= 0) {
                ItemStack visual = PaperItemCatalog.createRightStack(maskedDisplayed);
                visual.setCount(acquiredCount);
                rebuilt.put(emptySlot, visual);
            } else {
                ItemStack visual = PaperItemCatalog.createRightStack(maskedDisplayed);
                visual.setCount(current.getCount());
                rebuilt.put(i, visual);
            }
        }


        VISUAL_OVERRIDES.clear();
        VISUAL_OVERRIDES.putAll(rebuilt);
    }



    





    public static void beginReturnAttempt(int inventorySlot) {
        if (!isActive() || inventorySlot < 0) {
            return;
        }

        ItemStack visual = VISUAL_OVERRIDES.get(inventorySlot);
        if (visual == null || visual.isEmpty()) {
            return;
        }

        returnPending = true;
        returnPendingTicks = 0;
        returnPendingInventorySlot = inventorySlot;
        returnPendingVisual = visual.copy();
        returnHandVisual = visual.copy();
        returnHandTicks = RETURN_HAND_GRACE_TICKS;
        returnedToDropperVisual = true;
    }

    
    public static void beginCursorPickup(int inventorySlot) {
        if (maskedActual < 1 || maskedDisplayed < 1 || inventorySlot < 0) {
            return;
        }

        ItemStack visual = VISUAL_OVERRIDES.get(inventorySlot);
        if (visual == null || visual.isEmpty()) {
            return;
        }

        cursorCarryActive = true;
        cursorVisual = visual.copy();

        
        
        
        VISUAL_OVERRIDES.put(inventorySlot, ItemStack.EMPTY);
    }


    
    public static boolean matchesMaskedGroundStack(ItemStack stack) {
        if (maskedActual < 1 || maskedDisplayed < 1 || stack == null || stack.isEmpty()) {
            return false;
        }
        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(stack);
        return parsed != null
                && parsed.side() == PaperItemParser.Side.RIGHT
                && parsed.value() == maskedActual;
    }

    
    public static ItemStack groundReplacement(ItemStack realStack) {
        if (!matchesMaskedGroundStack(realStack)) {
            return null;
        }
        ItemStack visual = PaperItemCatalog.createRightStack(maskedDisplayed);
        visual.setCount(realStack.getCount());
        return visual;
    }

    
    public static void markGroundCarrySeen() {
        if (maskedActual < 1 || maskedDisplayed < 1) {
            return;
        }
        groundCarryActive = true;
        groundCarryGraceTicks = GROUND_PICKUP_GRACE_TICKS;
        
        
        VISUAL_OVERRIDES.clear();
        cursorCarryActive = false;
        cursorVisual = ItemStack.EMPTY;
    }

    
    public static void markGroundCarryNotSeen() {
        groundCarryActive = false;
    }

    
    public static void beginCursorReturnAttempt() {
        if (!cursorCarryActive || maskedActual < 1 || maskedDisplayed < 1) {
            return;
        }

        returnPending = true;
        returnPendingTicks = 0;
        returnPendingInventorySlot = -1;
        returnPendingVisual = cursorVisual.copy();
        returnedToDropperVisual = true;
    }

    
    public static ItemStack cursorReplacement(ItemStack realCursor) {
        if (!cursorCarryActive || maskedActual < 1 || maskedDisplayed < 1 || realCursor == null || realCursor.isEmpty()) {
            return null;
        }

        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(realCursor);
        if (parsed == null
                || parsed.side() != PaperItemParser.Side.RIGHT
                || parsed.value() != maskedActual) {
            return null;
        }

        ItemStack visual = cursorVisual.isEmpty()
                ? PaperItemCatalog.createRightStack(maskedDisplayed)
                : cursorVisual.copy();
        visual.setCount(realCursor.getCount());
        return visual;
    }

    public static boolean isCursorCarryActive() {
        return cursorCarryActive;
    }

    




    public static int maskedInventoryIndexForHandlerSlot(int handlerSlotIndex) {
        int inventoryIndex;
        if (handlerSlotIndex >= 9 && handlerSlotIndex <= 35) {
            inventoryIndex = handlerSlotIndex;
        } else if (handlerSlotIndex >= 36 && handlerSlotIndex <= 44) {
            inventoryIndex = handlerSlotIndex - 36;
        } else {
            return -1;
        }

        ItemStack visual = VISUAL_OVERRIDES.get(inventoryIndex);
        if (visual == null || visual.isEmpty()) {
            return -1;
        }

        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(visual);
        if (parsed == null
                || parsed.side() != PaperItemParser.Side.RIGHT
                || parsed.value() != maskedDisplayed) {
            return -1;
        }

        return inventoryIndex;
    }

    
    public static boolean isReturnedToDropperVisual() {
        return returnedToDropperVisual;
    }

    
    public static boolean isActive() {
        return maskedActual >= 1
                && maskedDisplayed >= 1
                && (!VISUAL_OVERRIDES.isEmpty() || cursorCarryActive || returnPending
                    || groundCarryActive || groundCarryGraceTicks > 0);
    }

    





    public static ItemStack heldItemReplacement(MinecraftClient client, ItemStack renderedStack) {
        if (!isActive() || client.player == null) {
            return null;
        }

        
        
        
        if (returnHandTicks > 0 && !returnHandVisual.isEmpty()) {
            PaperItemParser.ParsedItem cached = PaperItemParser.parse(renderedStack);
            if (cached != null && cached.side() == PaperItemParser.Side.RIGHT
                    && cached.value() == maskedActual) {
                ItemStack replacement = returnHandVisual.copy();
                replacement.setCount(renderedStack.getCount());
                return replacement;
            }
        }

        PlayerInventory inventory = client.player.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack visual = VISUAL_OVERRIDES.get(selected);
        if (visual == null) {
            return null;
        }
        if (visual.isEmpty()) {
            
            
            
            if (returnPending && selected == returnPendingInventorySlot) {
                return ItemStack.EMPTY;
            }
            return null;
        }

        
        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(renderedStack);
        if (parsed == null
                || parsed.side() != PaperItemParser.Side.RIGHT
                || parsed.value() != maskedActual) {
            return null;
        }

        ItemStack replacement = visual.copy();
        replacement.setCount(renderedStack.getCount());
        return replacement;
    }

    

    public static ItemStack tooltipReplacement(ItemStack cachedStack) {
        if (VISUAL_OVERRIDES.isEmpty() || maskedActual < 1 || maskedDisplayed < 1) {
            return null;
        }

        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(cachedStack);
        if (returnPending
                && parsed != null
                && parsed.side() == PaperItemParser.Side.RIGHT
                && parsed.value() == maskedActual) {
            
            return ItemStack.EMPTY;
        }
        if (parsed == null
                || parsed.side() != PaperItemParser.Side.RIGHT
                || parsed.value() != maskedActual) {
            return null;
        }

        ItemStack visual = PaperItemCatalog.createRightStack(maskedDisplayed);
        visual.setCount(cachedStack.getCount());
        return visual;
    }

    private static int totalMatchingActual(List<ItemStack> stacks, int actual) {
        int total = 0;
        for (ItemStack stack : stacks) {
            total += matchingActualCount(stack, actual);
        }
        return total;
    }

    private static int totalMatchingActual(PlayerInventory inventory, int actual) {
        int total = 0;
        for (int i = 0; i < inventory.size(); i++) {
            total += matchingActualCount(inventory.getStack(i), actual);
        }
        return total;
    }

    private static int matchingActualCount(ItemStack stack, int actual) {
        PaperItemParser.ParsedItem parsed = PaperItemParser.parse(stack);
        if (parsed != null
                && parsed.side() == PaperItemParser.Side.RIGHT
                && parsed.value() == actual) {
            return stack.getCount();
        }
        return 0;
    }

    private static int findVisuallyEmptySlot(PlayerInventory inventory, int excluded) {
        for (int i = 0; i < inventory.size(); i++) {
            if (i == excluded || VISUAL_OVERRIDES.containsKey(i)) {
                continue;
            }
            if (inventory.getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int findVisuallyEmptySlotFor(Map<Integer, ItemStack> overrides, PlayerInventory inventory, int excluded) {
        for (int i = 0; i < inventory.size(); i++) {
            if (i == excluded || overrides.containsKey(i)) {
                continue;
            }
            if (inventory.getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    
    public static void beginRender(MinecraftClient client) {
        if (renderSwapActive || VISUAL_OVERRIDES.isEmpty() || client.player == null) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        RENDER_ORIGINALS.clear();

        for (Map.Entry<Integer, ItemStack> entry : VISUAL_OVERRIDES.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.size()) {
                continue;
            }
            RENDER_ORIGINALS.put(slot, inventory.getStack(slot).copy());
            inventory.setStack(slot, entry.getValue().copy());
        }

        renderSwapActive = !RENDER_ORIGINALS.isEmpty();
    }

    
    public static void endRender(MinecraftClient client) {
        if (!renderSwapActive) {
            return;
        }

        if (client.player != null) {
            PlayerInventory inventory = client.player.getInventory();
            for (Map.Entry<Integer, ItemStack> entry : RENDER_ORIGINALS.entrySet()) {
                int slot = entry.getKey();
                if (slot >= 0 && slot < inventory.size()) {
                    inventory.setStack(slot, entry.getValue().copy());
                }
            }
        }

        RENDER_ORIGINALS.clear();
        renderSwapActive = false;
    }

    private static void clearInventoryOverridesOnly() {
        BASELINE.clear();
        VISUAL_OVERRIDES.clear();
        RENDER_ORIGINALS.clear();
        baselineCaptured = false;
        renderSwapActive = false;
        maskedActual = -1;
        maskedDisplayed = -1;
        baselineActualTotal = 0;
        returnPending = false;
        returnPendingTicks = 0;
        returnPendingInventorySlot = -1;
        returnPendingVisual = ItemStack.EMPTY;
        cursorCarryActive = false;
        cursorVisual = ItemStack.EMPTY;
        groundCarryActive = false;
        groundCarryGraceTicks = 0;
    }

    public static void clear() {
        clearInventoryOverridesOnly();
        returnHandVisual = ItemStack.EMPTY;
        returnHandTicks = 0;
        returnedToDropperVisual = false;
    }
}
