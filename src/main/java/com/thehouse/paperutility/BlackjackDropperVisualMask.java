package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;










public final class BlackjackDropperVisualMask {
    








    private static final int LOGICAL_START_COUNT = 8;

    private static final Map<BlackjackCard, Integer> SLOT_BY_CARD =
            new EnumMap<>(BlackjackCard.class);
    private static final List<MaskedDeal> MASKED_DEALS = new ArrayList<>();

    
    
    
    private static final Map<BlackjackCard, Integer> LAST_REAL_COUNT_BY_CARD =
            new EnumMap<>(BlackjackCard.class);

    




    private static boolean RETURN_PHASE_BASELINE_INITIALIZED = false;

    
    
    private static boolean RELEARN_ARMED = false;
    private static long RELEARN_FEEDBACK_UNTIL_MS = 0L;
    private static boolean RELEARN_LAST_SUCCESS = false;

    private BlackjackDropperVisualMask() {}

    public static void armRelearn() {
        RELEARN_ARMED = true;
        RELEARN_FEEDBACK_UNTIL_MS = 0L;
    }

    public static void cancelRelearn() {
        RELEARN_ARMED = false;
        RELEARN_FEEDBACK_UNTIL_MS = 0L;
    }

    public static boolean isRelearnArmed() {
        return RELEARN_ARMED;
    }

    public static boolean isRelearnFeedbackActive() {
        return System.currentTimeMillis() < RELEARN_FEEDBACK_UNTIL_MS;
    }

    public static boolean wasLastRelearnSuccessful() {
        return RELEARN_LAST_SUCCESS;
    }

    public static boolean isRelearnFlashVisible() {
        
        return RELEARN_ARMED && ((System.currentTimeMillis() / 500L) % 2L == 0L);
    }

    private static void finishRelearn(boolean success) {
        RELEARN_ARMED = false;
        RELEARN_LAST_SUCCESS = success;
        RELEARN_FEEDBACK_UNTIL_MS = System.currentTimeMillis() + 1000L;
    }

    




    public static void tickRelearn(MinecraftClient client) {
        if (!RELEARN_ARMED || client == null
                || !(client.currentScreen instanceof Generic3x3ContainerScreen screen)) {
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        int limit = Math.min(9, handler.slots.size());
        if (limit < 9) {
            finishRelearn(false);
            return;
        }

        EnumMap<BlackjackCard, Integer> stagedSlots = new EnumMap<>(BlackjackCard.class);
        EnumMap<BlackjackCard, ItemStack> stagedTemplates = new EnumMap<>(BlackjackCard.class);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            BlackjackCard card = BlackjackCard.fromStack(stack);
            if (card == null || stagedSlots.containsKey(card)) {
                finishRelearn(false);
                return;
            }
            stagedSlots.put(card, i);
            ItemStack copy = stack.copy();
            copy.setCount(1);
            stagedTemplates.put(card, copy);
        }

        if (stagedSlots.size() != BlackjackCard.values().length
                || !BlackjackCardCatalog.replaceAll(stagedTemplates)) {
            finishRelearn(false);
            return;
        }

        
        
        SLOT_BY_CARD.clear();
        SLOT_BY_CARD.putAll(stagedSlots);

        
        
        MASKED_DEALS.clear();
        LAST_REAL_COUNT_BY_CARD.clear();
        RETURN_PHASE_BASELINE_INITIALIZED = false;

        finishRelearn(true);
    }

    public static void clearRound() {
        MASKED_DEALS.clear();
        LAST_REAL_COUNT_BY_CARD.clear();
        RETURN_PHASE_BASELINE_INITIALIZED = false;
        
        
    }

    
    public static void recordDeal(boolean host, int handIndex, BlackjackCard actual, BlackjackCard display) {
        if (handIndex < 0 || actual == null) {
            return;
        }
        BlackjackCard effectiveDisplay = display == null ? actual : display;
        for (MaskedDeal deal : MASKED_DEALS) {
            if (deal.host == host && deal.handIndex == handIndex) {
                return;
            }
        }
        MASKED_DEALS.add(new MaskedDeal(host, handIndex, actual, effectiveDisplay));
    }

    



    public static boolean prepareForInteraction(ScreenHandler handler) {
        if (handler == null || !learnAndLooksLikeBlackjackDropper(handler)) {
            return false;
        }
        return true;
    }

    
    public static int containerSlotForCard(BlackjackCard card) {
        if (card == null) {
            return -1;
        }
        return SLOT_BY_CARD.getOrDefault(card, -1);
    }

    



    public static ItemStack replacementForSlot(ScreenHandler handler, Slot slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !BlackjackState.isEnabled()
                || !(client.currentScreen instanceof Generic3x3ContainerScreen)
                || handler == null || slot == null || MASKED_DEALS.isEmpty()) {
            return null;
        }

        int handlerIndex = handler.slots.indexOf(slot);
        if (handlerIndex < 0 || handlerIndex >= 9) {
            return null;
        }

        if (!learnAndLooksLikeBlackjackDropper(handler)) {
            return null;
        }

        






        if (BlackjackState.isMaskLocked()) {
            if (!RETURN_PHASE_BASELINE_INITIALIZED) {
                seedReturnPhaseBaseline(handler);
            } else {
                refreshReturnedDeals(handler);
            }
        }

        BlackjackCard slotCard = cardForContainerSlot(handlerIndex, slot.getStack());
        if (slotCard == null) {
            return null;
        }

        ItemStack underlying = slot.getStack();
        int realCount = BlackjackCard.fromStack(underlying) == slotCard
                ? underlying.getCount() : 0;

        
        
        
        
        
        
        
        int visualCount = Math.max(
                0,
                LOGICAL_START_COUNT - outstandingDisplayedDealCount(slotCard)
        );

        if (visualCount == realCount) {
            return null;
        }

        if (visualCount <= 0) {
            return ItemStack.EMPTY;
        }

        
        
        
        if (!underlying.isEmpty() && BlackjackCard.fromStack(underlying) == slotCard) {
            ItemStack visual = underlying.copy();
            visual.setCount(visualCount);
            return visual;
        }

        ItemStack visual = BlackjackCardCatalog.create(slotCard, visualCount);
        return visual.isEmpty() ? null : visual;
    }

    private static boolean learnAndLooksLikeBlackjackDropper(ScreenHandler handler) {
        int matches = 0;
        int limit = Math.min(9, handler.slots.size());

        
        
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (BlackjackCard.fromStack(stack) != null) {
                matches++;
            }
        }

        
        if (matches < 2) {
            return false;
        }

        
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            BlackjackCard card = BlackjackCard.fromStack(stack);
            if (card == null) {
                continue;
            }

            











            SLOT_BY_CARD.putIfAbsent(card, i);

            
            
            BlackjackCardCatalog.observe(stack);
        }

        return true;
    }

    private static BlackjackCard cardForContainerSlot(int slotIndex, ItemStack current) {
        






        for (Map.Entry<BlackjackCard, Integer> entry : SLOT_BY_CARD.entrySet()) {
            if (entry.getValue() == slotIndex) {
                return entry.getKey();
            }
        }

        




        return BlackjackCard.fromStack(current);
    }

    private static int outstandingDisplayedDealCount(BlackjackCard card) {
        int count = 0;
        for (MaskedDeal deal : MASKED_DEALS) {
            if (!deal.returned && deal.display == card) {
                count++;
            }
        }
        return count;
    }

    




    private static void seedReturnPhaseBaseline(ScreenHandler handler) {
        LAST_REAL_COUNT_BY_CARD.clear();

        Map<BlackjackCard, Integer> currentRealCounts =
                new EnumMap<>(BlackjackCard.class);

        int limit = Math.min(9, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            BlackjackCard card = BlackjackCard.fromStack(stack);
            if (card != null) {
                currentRealCounts.merge(card, stack.getCount(), Integer::sum);
            }
        }

        for (BlackjackCard card : BlackjackCard.values()) {
            LAST_REAL_COUNT_BY_CARD.put(
                    card,
                    currentRealCounts.getOrDefault(card, 0)
            );
        }

        RETURN_PHASE_BASELINE_INITIALIZED = true;
    }

    private static void refreshReturnedDeals(ScreenHandler handler) {
        if (!BlackjackState.isMaskLocked() || !RETURN_PHASE_BASELINE_INITIALIZED) {
            return;
        }

        Map<BlackjackCard, Integer> currentRealCounts =
                new EnumMap<>(BlackjackCard.class);

        int limit = Math.min(9, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            BlackjackCard card = BlackjackCard.fromStack(stack);
            if (card != null) {
                currentRealCounts.merge(card, stack.getCount(), Integer::sum);
            }
        }

        
        
        
        
        for (BlackjackCard card : BlackjackCard.values()) {
            int current = currentRealCounts.getOrDefault(card, 0);
            Integer previousBoxed = LAST_REAL_COUNT_BY_CARD.get(card);

            if (previousBoxed != null) {
                int increase = current - previousBoxed;
                if (increase > 0) {
                    for (MaskedDeal deal : MASKED_DEALS) {
                        if (increase <= 0) {
                            break;
                        }
                        if (!deal.returned && deal.actual == card) {
                            deal.returned = true;
                            increase--;
                        }
                    }
                }
            }

            LAST_REAL_COUNT_BY_CARD.put(card, current);
        }

        











    }

    private static final class MaskedDeal {
        final boolean host;
        final int handIndex;
        final BlackjackCard actual;
        final BlackjackCard display;
        boolean returned;

        MaskedDeal(boolean host, int handIndex, BlackjackCard actual, BlackjackCard display) {
            this.host = host;
            this.handIndex = handIndex;
            this.actual = actual;
            this.display = display;
        }
    }

}
