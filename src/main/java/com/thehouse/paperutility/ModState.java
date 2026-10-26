package com.thehouse.paperutility;

import net.minecraft.item.ItemStack;

public final class ModState {
    private static boolean simulationEnabled = false;
    private static boolean hudEnabled = true;
    private static WinningSide winningSide = WinningSide.RIGHT;
    private static boolean fiftyFiftyModeEnabled = false;

    private static int lastLeft = -1;
    private static int lastActualRight = -1;
    private static int lastDisplayedRight = -1;
    private static ItemStack lastActualRightStack = ItemStack.EMPTY;

    private ModState() {}

    static void applyPersistedSettings(boolean simulation, boolean hud, WinningSide side, boolean fiftyFiftyMode) {
        simulationEnabled = simulation;
        hudEnabled = hud;
        winningSide = side == WinningSide.LEFT ? WinningSide.LEFT : WinningSide.RIGHT;
        fiftyFiftyModeEnabled = fiftyFiftyMode;
    }

    public static boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    public static void toggleSimulation() { setSimulationEnabled(!simulationEnabled); }

    public static void setSimulationEnabled(boolean value) {
        simulationEnabled = value;
        ModConfig.save();
    }

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static void toggleHud() {
        hudEnabled = !hudEnabled;
        ModConfig.save();
    }


    public static boolean isFiftyFiftyModeEnabled() {
        return fiftyFiftyModeEnabled;
    }

    public static void toggleFiftyFiftyMode() { setFiftyFiftyModeEnabled(!fiftyFiftyModeEnabled); }

    public static void setFiftyFiftyModeEnabled(boolean value) {
        fiftyFiftyModeEnabled = value;
        ModConfig.save();
    }

    
    public static boolean isDropperSideLockEnabled() {
        return simulationEnabled;
    }

    public static WinningSide getWinningSide() { return winningSide; }

    public static void cycleWinningSide() {
        winningSide = winningSide == WinningSide.RIGHT ? WinningSide.LEFT : WinningSide.RIGHT;
        ModConfig.save();
    }

    public static int getLastLeft() {
        return lastLeft;
    }

    public static void setLastLeft(int value) {
        lastLeft = value;
    }

    public static int getLastActualRight() {
        return lastActualRight;
    }

    public static void setLastActualRight(int value) {
        lastActualRight = value;
    }

    public static int getLastDisplayedRight() {
        return lastDisplayedRight;
    }

    public static void setLastDisplayedRight(int value) {
        lastDisplayedRight = value;
    }

    public static ItemStack getLastActualRightStack() {
        return lastActualRightStack.copy();
    }

    public static void setLastActualRightStack(ItemStack stack) {
        lastActualRightStack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static void clearRound() {
        lastLeft = -1;
        lastActualRight = -1;
        lastDisplayedRight = -1;
        lastActualRightStack = ItemStack.EMPTY;
    }
}
