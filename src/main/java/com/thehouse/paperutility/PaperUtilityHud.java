package com.thehouse.paperutility;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;


public final class PaperUtilityHud {
    private PaperUtilityHud() {}

    public static void render(DrawContext context) {
        
        if (ModState.isHudEnabled() && ModState.isSimulationEnabled()) {
            final int x = 2;
            final int y = 2;
            final int color = ModState.getWinningSide() == WinningSide.RIGHT
                    ? HudColors.paperRight().hudArgb()
                    : HudColors.paperLeft().hudArgb();
            context.fill(x, y, x + 1, y + 1, color);
        }

        
        
        
        
        if (BlackjackState.isHudEnabled()
                && (BlackjackState.isEnabled()
                    || BlackjackDropperVisualMask.isRelearnArmed()
                    || BlackjackDropperVisualMask.isRelearnFeedbackActive())) {
            final int x = 12;
            final int y = 2;
            final int viewerColor = HudColors.blackjackViewer().hudArgb();
            final int hostColor = HudColors.blackjackHost().hudArgb();
            final int lockedColor = HudColors.blackjackLocked().hudArgb();
            final int failureColor = HudColors.blackjackFailure().hudArgb();

            if (BlackjackDropperVisualMask.isRelearnFeedbackActive()) {
                final int color = BlackjackDropperVisualMask.wasLastRelearnSuccessful()
                        ? viewerColor
                        : failureColor;
                context.fill(x, y, x + 1, y + 1, color);
            } else if (BlackjackDropperVisualMask.isRelearnArmed()) {
                if (BlackjackDropperVisualMask.isRelearnFlashVisible()) {
                    context.fill(x, y, x + 1, y + 1, lockedColor);
                }
            } else {
                final int color = BlackjackState.isMaskLocked()
                        ? lockedColor
                        : (BlackjackState.isHostTurn() ? hostColor : viewerColor);
                context.fill(x, y, x + 1, y + 1, color);
            }
        }

    }
}
