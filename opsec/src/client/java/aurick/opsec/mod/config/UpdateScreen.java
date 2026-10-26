package aurick.opsec.mod.config;

import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.util.Util;








public class UpdateScreen extends Screen {

    private final Screen parent;

    public UpdateScreen(Screen parent) {
        super(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_TITLE)));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        
        addCenteredStringWidget(this.title, centerX, centerY - 50);
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_MESSAGE)), centerX, centerY - 28);
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_CURRENT, UpdateChecker.getCurrentVersion())), centerX, centerY - 14);
        addCenteredStringWidget(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_LATEST, UpdateChecker.getLatestVersion())), centerX, centerY);

        
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonSpacing = 24;
        int firstButtonY = centerY + 15;

        
        this.addRenderableWidget(Button.builder(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_DOWNLOAD)), button -> {
            Util.getPlatform().openUri(UpdateChecker.getReleaseUrl());
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY, buttonWidth, buttonHeight).build());

        
        this.addRenderableWidget(Button.builder(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_SKIP)), button -> {
            OpsecConfig.getInstance().getSettings().setSkippedUpdateVersion(UpdateChecker.getLatestVersion());
            OpsecConfig.getInstance().save();
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY + buttonSpacing, buttonWidth, buttonHeight).build());

        
        this.addRenderableWidget(Button.builder(Component.literal(OpsecLang.tr(OpsecStrings.UPDATE_CANCEL)), button -> {
            this.onClose();
        }).bounds(centerX - buttonWidth / 2, firstButtonY + buttonSpacing * 2, buttonWidth, buttonHeight).build());
    }

    private void addCenteredStringWidget(Component text, int centerX, int y) {
        int textWidth = this.font.width(text);
        this.addRenderableWidget(new StringWidget(centerX - textWidth / 2, y, textWidth, 10, text, this.font));
    }

    
    @Override
    public void onClose() {
                this.minecraft.setScreen(this.parent);
            }
}
