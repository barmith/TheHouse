package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class FiftyFiftyTracker {
    private static final Map<Integer, ItemStack> ORIGINAL = new HashMap<>();
    private static final Map<Integer, ItemStack> LAST_VISUAL = new HashMap<>();
    private static final Set<Integer> PROCESSED = new HashSet<>();
    private static final Map<Integer, FiftyFiftyItem.Outcome> ENTITY_ACTUAL = new HashMap<>();
    private static final Map<Integer, FiftyFiftyItem.Outcome> ENTITY_DISPLAYED = new HashMap<>();
    private static ItemStack learnedHost = ItemStack.EMPTY;
    private static ItemStack learnedViewer = ItemStack.EMPTY;
    private static FiftyFiftyItem.Outcome lastActual;
    private static FiftyFiftyItem.Outcome lastDisplayed;
    
    
    
    private static FiftyFiftyItem.Outcome roundDisplayed;
    private static ItemStack lastActualStack = ItemStack.EMPTY;

    








    private static boolean actualSlotObservedEmptyAfterDeal = false;
    private static boolean returnedToDropper = false;

    private FiftyFiftyTracker() {}

    public static void observeItemImmediately(MinecraftClient client, ItemEntity entity) {
        if (!active() || client == null || client.player == null || entity == null
                || client.player.squaredDistanceTo(entity) > 32.0 * 32.0) return;
        refresh(entity);
        process(entity);
    }

    public static void tick(MinecraftClient client) {
        learnOpenDropper(client);
        if (client == null || client.player == null || client.world == null) {
            clearState();
            return;
        }
        if (!active()) {
            restoreAll(client);
            return;
        }
        Set<Integer> alive = new HashSet<>();
        for (ItemEntity entity : client.world.getEntitiesByClass(ItemEntity.class,
                client.player.getBoundingBox().expand(32.0), e -> true)) {
            alive.add(entity.getId());
            refresh(entity);
            process(entity);
        }
        
        
        
        for (Integer id : new HashSet<>(PROCESSED)) {
            if (!alive.contains(id)) {
                FiftyFiftyItem.Outcome a = ENTITY_ACTUAL.get(id);
                FiftyFiftyItem.Outcome d = ENTITY_DISPLAYED.get(id);
                FiftyFiftyInventoryMask.detectPickup(client, a, d);
            }
        }
        FiftyFiftyInventoryMask.reconcile(client);

        ORIGINAL.keySet().removeIf(id -> !alive.contains(id));
        LAST_VISUAL.keySet().removeIf(id -> !alive.contains(id));
        PROCESSED.removeIf(id -> !alive.contains(id));
        ENTITY_ACTUAL.keySet().removeIf(id -> !alive.contains(id));
        ENTITY_DISPLAYED.keySet().removeIf(id -> !alive.contains(id));
    }

    private static boolean active() {
        return ModState.isSimulationEnabled() && ModState.isFiftyFiftyModeEnabled();
    }

    private static void refresh(ItemEntity entity) {
        int id = entity.getId();
        ItemStack current = entity.getStack();
        ItemStack visual = LAST_VISUAL.get(id);
        if (visual == null || !ItemStack.areEqual(current, visual)) {
            ORIGINAL.put(id, current.copy());
            LAST_VISUAL.remove(id);
        }
    }

    private static void process(ItemEntity entity) {
        int id = entity.getId();
        ItemStack original = ORIGINAL.get(id);
        FiftyFiftyItem.Outcome actual = FiftyFiftyItem.parse(original);
        if (actual == null) return;
        learn(actual, original);

        if (PROCESSED.add(id)) {
            FiftyFiftyInventoryMask.captureBaseline(MinecraftClient.getInstance());

            
            
            if (roundDisplayed == null || returnedToDropper) {
                roundDisplayed = FiftyFiftyItem.preferredOutcome();
            }

            lastActual = actual;
            lastDisplayed = roundDisplayed;
            ENTITY_ACTUAL.put(id, actual);
            ENTITY_DISPLAYED.put(id, roundDisplayed);
            lastActualStack = original.copy();
            actualSlotObservedEmptyAfterDeal = false;
            returnedToDropper = false;
        }

        
        
        FiftyFiftyItem.Outcome displayedForEntity = ENTITY_DISPLAYED.get(id);
        if (displayedForEntity == null) displayedForEntity = roundDisplayed;
        if (displayedForEntity == null || actual == displayedForEntity) {
            restore(entity, original);
            return;
        }
        ItemStack visual = FiftyFiftyItem.create(displayedForEntity);
        visual.setCount(original.getCount());
        LAST_VISUAL.put(id, visual.copy());
        if (!ItemStack.areEqual(entity.getStack(), visual)) entity.setStack(visual);
    }

    private static void learnOpenDropper(MinecraftClient client) {
        if (client == null || !(client.currentScreen instanceof Generic3x3ContainerScreen screen)) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (handler.slots.size() < 2) return;

        ItemStack hostSlotStack = handler.slots.get(0).getStack();
        ItemStack viewerSlotStack = handler.slots.get(1).getStack();
        FiftyFiftyItem.Outcome first = FiftyFiftyItem.parse(hostSlotStack);
        FiftyFiftyItem.Outcome second = FiftyFiftyItem.parse(viewerSlotStack);

        
        if (first == FiftyFiftyItem.Outcome.HOST) learn(first, hostSlotStack);
        if (second == FiftyFiftyItem.Outcome.VIEWER) learn(second, viewerSlotStack);

        
        if (lastActual == null || lastDisplayed == null || lastActual == lastDisplayed || returnedToDropper) {
            return;
        }

        int actualSlot = lastActual == FiftyFiftyItem.Outcome.HOST ? 0 : 1;
        ItemStack physicalActualSlot = handler.slots.get(actualSlot).getStack();

        
        if (physicalActualSlot.isEmpty()) {
            actualSlotObservedEmptyAfterDeal = true;
            return;
        }

        
        
        
        if (actualSlotObservedEmptyAfterDeal
                && FiftyFiftyItem.parse(physicalActualSlot) == lastActual) {
            returnedToDropper = true;
            
            
            roundDisplayed = null;
        }
    }

    private static void learn(FiftyFiftyItem.Outcome outcome, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack copy = stack.copy(); copy.setCount(1);
        if (outcome == FiftyFiftyItem.Outcome.HOST) learnedHost = copy;
        else learnedViewer = copy;
    }

    static ItemStack learnedStack(FiftyFiftyItem.Outcome outcome) {
        ItemStack s = outcome == FiftyFiftyItem.Outcome.HOST ? learnedHost : learnedViewer;
        return s == null ? ItemStack.EMPTY : s.copy();
    }

    public static FiftyFiftyItem.Outcome getLastActual() { return lastActual; }
    public static FiftyFiftyItem.Outcome getLastDisplayed() { return lastDisplayed; }
    public static ItemStack getLastActualStack() { return lastActualStack.copy(); }
    public static boolean isReturnedToDropper() { return returnedToDropper; }

    public static void restoreAll(MinecraftClient client) {
        if (client != null && client.world != null) {
            for (Map.Entry<Integer, ItemStack> e : ORIGINAL.entrySet()) {
                Entity entity = client.world.getEntityById(e.getKey());
                if (entity instanceof ItemEntity item) item.setStack(e.getValue().copy());
            }
        }
        clearState();
    }

    private static void restore(ItemEntity entity, ItemStack original) {
        LAST_VISUAL.remove(entity.getId());
        if (!ItemStack.areEqual(entity.getStack(), original)) entity.setStack(original.copy());
    }

    public static void clearState() {
        ORIGINAL.clear(); LAST_VISUAL.clear(); PROCESSED.clear();
        ENTITY_ACTUAL.clear(); ENTITY_DISPLAYED.clear();
        FiftyFiftyInventoryMask.clear();
        lastActual = null; lastDisplayed = null; roundDisplayed = null; lastActualStack = ItemStack.EMPTY;
        actualSlotObservedEmptyAfterDeal = false;
        returnedToDropper = false;
    }
}
