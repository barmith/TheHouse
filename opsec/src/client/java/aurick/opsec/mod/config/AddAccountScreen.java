package aurick.opsec.mod.config;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.SessionAccount;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;




public class AddAccountScreen extends Screen {
    
    private final Screen parent;
    private EditBox tokenInput;
    private EditBox refreshTokenInput;
    private Button addButton;
    private Button cancelButton;
    private Component statusMessage;
    private boolean isValidating = false;
    private StringWidget titleLabel;
    private StringWidget statusLabel;
    
    public AddAccountScreen(Screen parent) {
        super(OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_SESSION_TITLE));
        this.parent = parent;
        this.statusMessage = Component.literal("");
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        
        int titleWidth = this.font.width(this.title);
        this.titleLabel = new StringWidget(centerX - titleWidth / 2, centerY - 75, titleWidth, 20, this.title, this.font);
        this.addRenderableWidget(this.titleLabel);
        
        
        this.tokenInput = new EditBox(
                this.font,
                centerX - 150,
                centerY - 50,
                300,
                20,
                OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_SESSION_LABEL)
        );
        this.tokenInput.setMaxLength(2000);
        this.tokenInput.setHint(OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_SESSION_HINT));
        this.addRenderableWidget(this.tokenInput);

        
        this.refreshTokenInput = new EditBox(
                this.font,
                centerX - 150,
                centerY - 20,
                300,
                20,
                OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_REFRESH_LABEL)
        );
        this.refreshTokenInput.setMaxLength(2000);
        this.refreshTokenInput.setHint(OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_REFRESH_HINT));
        this.addRenderableWidget(this.refreshTokenInput);
        
        
        this.statusLabel = new StringWidget(centerX - 150, centerY + 45, 300, 20, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);
        
        
        this.addButton = Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_ADD_BUTTON), button -> {
            addAccount();
        }).bounds(centerX - 105, centerY + 15, 100, 20).build();
        this.addRenderableWidget(this.addButton);

        
        this.cancelButton = Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_SCREEN_CANCEL_BUTTON), button -> {
            this.onClose();
        }).bounds(centerX + 5, centerY + 15, 100, 20).build();
        this.addRenderableWidget(this.cancelButton);
        
        
        this.setInitialFocus(this.tokenInput);
    }
    
    private void addAccount() {
        String token = tokenInput.getValue().trim();
        String refreshToken = refreshTokenInput.getValue().trim();
        
        if (token.isEmpty()) {
            statusMessage = OpsecLang.component(OpsecStrings.ACCOUNT_ERROR_EMPTY_TOKEN);
            if (statusLabel != null) {
                statusLabel.setMessage(statusMessage);
            }
            return;
        }

        if (isValidating) {
            return;
        }

        isValidating = true;
        addButton.active = false;
        statusMessage = OpsecLang.component(OpsecStrings.ACCOUNT_STATUS_VALIDATING);
        if (statusLabel != null) {
            statusLabel.setMessage(statusMessage);
        }
        
        
        CompletableFuture.runAsync(() -> {
            SessionAccount account = refreshToken.isEmpty() 
                    ? new SessionAccount(token)
                    : new SessionAccount(token, refreshToken);
            boolean valid = account.fetchInfo();
            
            
            Minecraft.getInstance().execute(() -> {
                isValidating = false;
                addButton.active = true;
                
                if (valid) {
                    
                    AccountManager.getInstance().add(account);
                    
                    
                    if (account.login()) {
                        AccountManager.getInstance().setActiveAccountUuid(account.getUuid());
                    }
                    
                    String successMsg = OpsecLang.tr(OpsecStrings.ACCOUNT_SUCCESS_ADDED, account.getUsername());
                    if (account.hasRefreshToken()) {
                        successMsg += OpsecLang.tr(OpsecStrings.ACCOUNT_SUCCESS_REFRESH_SUFFIX);
                    }
                    statusMessage = Component.literal(successMsg);
                    if (statusLabel != null) {
                        statusLabel.setMessage(statusMessage);
                    }

                    
                    Minecraft.getInstance().execute(() -> {
                        this.onClose();
                    });
                } else {
                    statusMessage = OpsecLang.component(OpsecStrings.ACCOUNT_ERROR_INVALID_TOKEN);
                    if (statusLabel != null) {
                        statusLabel.setMessage(statusMessage);
                    }
                }
            });
        });
    }
    
        @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        
        if (statusLabel != null && statusMessage != null && !statusMessage.getString().isEmpty()) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int statusWidth = this.font.width(statusMessage);
            statusLabel.setX(centerX - statusWidth / 2);
            statusLabel.setY(centerY + 45);
            statusLabel.setWidth(statusWidth);
        }
    }
        
    @Override
    public void onClose() {
        
        Screen grandParent = null;
        if (parent instanceof OpsecConfigScreen configScreen) {
            grandParent = configScreen.getParent();
        }
        
                this.minecraft.setScreen(new OpsecConfigScreen(grandParent, 3, 0));
            }
    
    
    }
