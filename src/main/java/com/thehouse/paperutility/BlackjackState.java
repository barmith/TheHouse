package com.thehouse.paperutility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class BlackjackState {
    private static boolean enabled = false;
    private static boolean hostTurn = false;
    private static boolean hudEnabled = true;

    
    
    
    private static int sideTogglePresses = 0;
    private static boolean maskLocked = false;

    private static final List<BlackjackCard> VIEWER = new ArrayList<>();
    private static final List<BlackjackCard> HOST_ACTUAL = new ArrayList<>();
    private static final List<BlackjackCard> HOST_DISPLAY = new ArrayList<>();

    private BlackjackState() {}

    public static void applyPersisted(boolean enabledValue, boolean hudValue) {
        
        enabled = enabledValue;
        hudEnabled = hudValue;
        resetHand();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        
        
        resetHand();
        if (enabled) {
            recomputeDisplayedHand();
        }
        ModConfig.save();
    }

    public static void toggleEnabled() {
        setEnabled(!enabled);
    }

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static void toggleHud() {
        hudEnabled = !hudEnabled;
        ModConfig.save();
    }

    public static boolean isHostTurn() {
        return hostTurn;
    }

    






    public static boolean isMaskLocked() {
        return maskLocked;
    }

    


    public static void advanceHostTurnOrReset() {
        if (!enabled || maskLocked) {
            return;
        }

        hostTurn = !hostTurn;
        sideTogglePresses++;

        if (sideTogglePresses >= 4) {
            maskLocked = true;
        }
    }

    public static DealAssignment registerDeal(BlackjackCard card) {
        
        
        if (!enabled || maskLocked || card == null) {
            return DealAssignment.NONE;
        }

        
        
        boolean host = hostTurn;

        if (host) {
            HOST_ACTUAL.add(card);
            appendDisplayedHostCard(card);
            int handIndex = HOST_ACTUAL.size() - 1;
            BlackjackCard display = handIndex < HOST_DISPLAY.size() ? HOST_DISPLAY.get(handIndex) : card;
            BlackjackDropperVisualMask.recordDeal(true, handIndex, card, display);
            return new DealAssignment(true, handIndex);
        }

        VIEWER.add(card);
        int handIndex = VIEWER.size() - 1;
        BlackjackDropperVisualMask.recordDeal(false, handIndex, card, card);
        
        
        return new DealAssignment(false, handIndex);
    }

    public static void resetHand() {
        hostTurn = false;
        sideTogglePresses = 0;
        maskLocked = false;
        VIEWER.clear();
        HOST_ACTUAL.clear();
        HOST_DISPLAY.clear();
        BlackjackMasking.clearTransient();
        BlackjackTracker.clearTransient();
        BlackjackDropperVisualMask.clearRound();
    }

    public static List<BlackjackCard> hostActualCards() {
        return Collections.unmodifiableList(HOST_ACTUAL);
    }

    public static BlackjackCard displayedCardForHostIndex(int index) {
        if (index < 0 || index >= HOST_ACTUAL.size()) {
            return null;
        }
        if (!isEnabled() || index >= HOST_DISPLAY.size()) {
            return HOST_ACTUAL.get(index);
        }
        return HOST_DISPLAY.get(index);
    }

    public static int viewerTotal() {
        return score(VIEWER);
    }

    public static int score(List<BlackjackCard> cards) {
        int total = 0;
        int aces = 0;
        for (BlackjackCard card : cards) {
            if (card == null) {
                continue;
            }
            total += card.rawValue();
            if (card.isAce()) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    private static void recomputeDisplayedHand() {
        if (HOST_ACTUAL.isEmpty()) {
            HOST_DISPLAY.clear();
            return;
        }
        if (!isEnabled()) {
            HOST_DISPLAY.clear();
            HOST_DISPLAY.addAll(HOST_ACTUAL);
            return;
        }

        
        
        while (HOST_DISPLAY.size() > HOST_ACTUAL.size()) {
            HOST_DISPLAY.remove(HOST_DISPLAY.size() - 1);
        }
        while (HOST_DISPLAY.size() < HOST_ACTUAL.size()) {
            int index = HOST_DISPLAY.size();
            appendDisplayedHostCard(HOST_ACTUAL.get(index));
        }
    }

    private static void appendDisplayedHostCard(BlackjackCard actual) {
        if (!isEnabled()) {
            HOST_DISPLAY.add(actual);
            return;
        }

        
        
        if (HOST_DISPLAY.isEmpty()) {
            HOST_DISPLAY.add(actual);
            return;
        }

        
        
        
        
        BlackjackCard chosen = BlackjackRigging.chooseNextMaskedCard(HOST_DISPLAY, viewerTotal());
        if (chosen != null) {
            int hostIndex = HOST_DISPLAY.size();
            HOST_DISPLAY.add(chosen);
            return;
        }

        
        
        
        HOST_DISPLAY.add(null);
    }


    public record DealAssignment(boolean host, int handIndex) {
        public static final DealAssignment NONE = new DealAssignment(false, -1);
    }
}
