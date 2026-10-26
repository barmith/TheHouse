package aurick.opsec.mod.config;

import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.util.Util;









public class TamperWarningScreen extends Screen {

    private final Screen parent;

    public TamperWarningScreen(Screen parent) {
        super(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_TITLE)));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        
        int textLineHeight = 14;
        int textGap = 6;         
        int hashGap = 8;         
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonSpacing = 24;
        int textToButtonGap = 14;

        
        int textHeight = textLineHeight * 8 + textGap + hashGap;
        int totalHeight = textHeight + textToButtonGap + buttonSpacing + buttonHeight;
        int startY = (this.height - totalHeight) / 2;

        
        int y = startY;
        addCenteredStringWidget(this.title, centerX, y);
        y += textLineHeight;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_WARNING)), centerX, y);
        y += textLineHeight;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_MISMATCH)), centerX, y);
        y += textLineHeight;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_MALICIOUS)), centerX, y);
        y += textLineHeight + textGap;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_COMPROMISED)), centerX, y);
        y += textLineHeight;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_ACTION)), centerX, y);
        y += textLineHeight + hashGap;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_EXPECTED, truncate(JarIntegrityChecker.getExpectedDigest(), 16))), centerX, y);
        y += textLineHeight;
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_ACTUAL, truncate(JarIntegrityChecker.getActualDigest(), 16))), centerX, y);
        y += textLineHeight + textToButtonGap;

        
        this.addRenderableWidget(Button.builder(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_DOWNLOAD)), button -> {
            Util.getPlatform().openUri(UpdateChecker.getReleaseUrl());
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal(OpsecLang.tr(OpsecStrings.TAMPER_DISMISS_PERMANENT)), button -> {
            OpsecConfig.getInstance().getSettings().setTamperWarningDismissed(true);
            OpsecConfig.getInstance().save();
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, y + buttonSpacing, buttonWidth, buttonHeight).build());
    }

    private void addCenteredStringWidget(Component text, int centerX, int y) {
        int textWidth = this.font.width(text);
        this.addRenderableWidget(new StringWidget(centerX - textWidth / 2, y, textWidth, 10, text, this.font));
    }

    


    private static String truncate(String s, int len) {
        if (s == null) {
            return "unknown";
        }
        return s.substring(0, Math.min(s.length(), len));
    }

    
    @Override
    public void onClose() {
                this.minecraft.setScreen(this.parent);
            }
}
