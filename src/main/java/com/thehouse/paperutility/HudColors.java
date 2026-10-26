package com.thehouse.paperutility;





public final class HudColors {
    private static HudColor paperRight = HudColor.BLUE;
    private static HudColor paperLeft = HudColor.RED;
    private static HudColor blackjackViewer = HudColor.GREEN;
    private static HudColor blackjackHost = HudColor.ORANGE;
    private static HudColor blackjackLocked = HudColor.WHITE;
    private static HudColor blackjackFailure = HudColor.RED;

    private HudColors() {}

    static void applyPersisted(
            HudColor paperRightValue,
            HudColor paperLeftValue,
            HudColor blackjackViewerValue,
            HudColor blackjackHostValue,
            HudColor blackjackLockedValue,
            HudColor blackjackFailureValue) {
        paperRight = safe(paperRightValue, HudColor.BLUE);
        paperLeft = safe(paperLeftValue, HudColor.RED);
        blackjackViewer = safe(blackjackViewerValue, HudColor.GREEN);
        blackjackHost = safe(blackjackHostValue, HudColor.ORANGE);
        blackjackLocked = safe(blackjackLockedValue, HudColor.WHITE);
        blackjackFailure = safe(blackjackFailureValue, HudColor.RED);
    }

    private static HudColor safe(HudColor value, HudColor fallback) {
        return value == null ? fallback : value;
    }

    public static HudColor paperRight() { return paperRight; }
    public static HudColor paperLeft() { return paperLeft; }
    public static HudColor blackjackViewer() { return blackjackViewer; }
    public static HudColor blackjackHost() { return blackjackHost; }
    public static HudColor blackjackLocked() { return blackjackLocked; }
    public static HudColor blackjackFailure() { return blackjackFailure; }

    public static void cyclePaperRight(int direction) {
        paperRight = step(paperRight, direction);
        ModConfig.save();
    }

    public static void cyclePaperLeft(int direction) {
        paperLeft = step(paperLeft, direction);
        ModConfig.save();
    }

    public static void cycleBlackjackViewer(int direction) {
        blackjackViewer = step(blackjackViewer, direction);
        ModConfig.save();
    }

    public static void cycleBlackjackHost(int direction) {
        blackjackHost = step(blackjackHost, direction);
        ModConfig.save();
    }

    public static void cycleBlackjackLocked(int direction) {
        blackjackLocked = step(blackjackLocked, direction);
        ModConfig.save();
    }

    public static void cycleBlackjackFailure(int direction) {
        blackjackFailure = step(blackjackFailure, direction);
        ModConfig.save();
    }

    public static void resetPaperDefaults() {
        paperRight = HudColor.BLUE;
        paperLeft = HudColor.RED;
        ModConfig.save();
    }

    public static void resetBlackjackDefaults() {
        blackjackViewer = HudColor.GREEN;
        blackjackHost = HudColor.ORANGE;
        blackjackLocked = HudColor.WHITE;
        blackjackFailure = HudColor.RED;
        ModConfig.save();
    }

    private static HudColor step(HudColor color, int direction) {
        return direction < 0 ? color.previous() : color.next();
    }
}
