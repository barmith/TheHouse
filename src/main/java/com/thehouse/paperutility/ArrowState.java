package com.thehouse.paperutility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;


public final class ArrowState {
    private static boolean enabled;
    private static boolean hudEnabled = true;
    private static BlockPos boardCenter;
    private static String lastTarget = "NO TARGET";
    private static boolean maskingEnabled = false;
    private static boolean desiredHostWin = true;
    private static int maskTargetX = 0;
    private static int maskTargetZ = 1;
    private static volatile boolean hasTrueLanding;
    private static volatile int trueLandingX;
    private static volatile int trueLandingZ;
    private static volatile boolean trackingArrow;

    private ArrowState() {}

    public static void applyPersisted(boolean enabledValue, boolean hudValue, BlockPos center, boolean masking, int targetX, int targetZ, boolean hostWin) {
        enabled = enabledValue;
        hudEnabled = hudValue;
        boardCenter = center;
        maskingEnabled = enabledValue;
        
        desiredHostWin = true;
        maskTargetX = Math.max(-3, Math.min(3, targetX));
        maskTargetZ = Math.max(-3, Math.min(3, targetZ));
        if (!isSelectableCell(maskTargetX, maskTargetZ)) {
            maskTargetX = 0;
            maskTargetZ = 1;
        }
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isHudEnabled() { return hudEnabled; }
    public static BlockPos getBoardCenter() { return boardCenter; }
    public static boolean isBoardLearned() { return boardCenter != null; }
    public static String getLastTarget() { return lastTarget; }
    public static boolean isMaskingEnabled() { return maskingEnabled; }
    public static boolean isDesiredHostWin() { return true; }
    public static int getMaskTargetX() { return maskTargetX; }
    public static int getMaskTargetZ() { return maskTargetZ; }
    public static boolean hasTrueLanding() { return hasTrueLanding; }
    public static int getTrueLandingX() { return trueLandingX; }
    public static int getTrueLandingZ() { return trueLandingZ; }
    public static boolean isTrackingArrow() { return trackingArrow; }

    public static synchronized boolean setMaskTarget(int x, int z) {
        if (!isSelectableCell(x, z)) return false;
        maskTargetX = x;
        maskTargetZ = z;
        
        
        ModConfig.save();
        return true;
    }

    
    public static void toggleDesiredOutcome() { desiredHostWin = true; }

    
    public static boolean isHostWinCell(int x, int z) {
        if (!isSelectableCell(x, z)) return false;
        return ((x + z) & 1) != 0;
    }

    public static boolean matchesDesiredOutcome(int x, int z) {
        return isSelectableCell(x, z) && isHostWinCell(x, z);
    }

    
    static void setAutoMaskTarget(int x, int z) {
        if (!isSelectableCell(x, z)) return;
        maskTargetX = x;
        maskTargetZ = z;
    }
    
    public static boolean isSelectableCell(int x, int z) {
        if (x < -3 || x > 3 || z < -3 || z > 3) return false;
        if (Math.abs(x) == 3 && Math.abs(z) == 3) return false;
        return !(x == 0 && z == 0);
    }

    public static void toggleEnabled() { setEnabled(!enabled); }

    public static void setEnabled(boolean value) {
        enabled = value;
        maskingEnabled = value;
        ArrowTracker.clearTransient();
        ModConfig.save();
    }

    public static void toggleHud() {
        hudEnabled = !hudEnabled;
        ModConfig.save();
    }

    
    public static boolean learnFromCrosshair(MinecraftClient client) {
        if (client == null || !(client.crosshairTarget instanceof BlockHitResult hit)) return false;
        boardCenter = hit.getBlockPos().toImmutable();
        lastTarget = "CENTER LEARNED";
        ArrowTracker.clearTransient();
        ModConfig.save();
        return true;
    }

    static void setDetectedLandingCell(int cellX, int cellZ,
                                       double embeddedX, double embeddedY, double embeddedZ,
                                       String method) {
        trueLandingX = cellX; trueLandingZ = cellZ; hasTrueLanding = true; trackingArrow = false;
        lastTarget = "TARGET " + signed(cellX) + "," + signed(cellZ);
    }

    static void setNoGridLanding(double embeddedX, double embeddedY, double embeddedZ, String method) {
        hasTrueLanding = false;
        trackingArrow = false;
        lastTarget = "NO GRID RESULT";
    }

    static void setTracking() { lastTarget = "TRACKING"; trackingArrow = true; }

    static void setTrackingCount(int count) {
        trackingArrow = count > 0;
        if (count > 0) lastTarget = count == 1 ? "TRACKING" : "TRACKING " + count + " ARROWS";
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }
}
