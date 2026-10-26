package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.Account;
import aurick.opsec.mod.accounts.SessionAccount;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.mixin.MeteorMixinCanceller;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class OpsecConfigScreen extends Screen {
    private static final Function<Boolean, Component> COLORED_BOOL_TO_TEXT = b -> 
        Boolean.TRUE.equals(b) 
            ? Component.literal("\u00A7aON") 
            : Component.literal("\u00A7cOFF");
    
    
    private static volatile String loggingInAccountUuid = null;
    
    private static volatile boolean isLoggingOut = false;
    
    private static volatile int animationTicks = 0;
    
    
    
    
        private static <T> CycleButton.Builder<T> cycleBuilder(Function<T, Component> valueToText, List<T> values, T initialValue) {
        return CycleButton.<T>builder(valueToText, initialValue).withValues(values);
    }
        
    private final Screen parent;
    private final OpsecConfig config;
    
    public Screen getParent() {
        return parent;
    }
    
    private TabNavigationBar tabWidget;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    
    private Button doneButton;
    private Button resetButton;
    private StringWidget versionLabel;
    private boolean versionOutdated;
    private int currentTab = 0;
    private double scrollOffset = 0;
    private List<Tab> tabs;

    
    public OpsecConfigScreen(Screen parent) {
        this(parent, 0, 0);
    }

    public OpsecConfigScreen(Screen parent, int initialTab) {
        this(parent, initialTab, 0);
    }

    public OpsecConfigScreen(Screen parent, int initialTab, double scrollOffset) {
        super(OpsecLang.component(OpsecStrings.CONFIG_TITLE));
        this.parent = parent;
        this.config = OpsecConfig.getInstance();
        this.currentTab = initialTab;
        this.scrollOffset = scrollOffset;
    }
    
    @Override
    protected void init() {
        
        SpoofSettings settings = config.getSettings();
        
        this.tabs = List.of(
            createProtectionTab(settings),
            createWhitelistTab(settings),
            createMiscTab(settings),
            createAccountsTab()
        );
        
                this.tabWidget = TabNavigationBar.builder(this.tabManager, this.width)
                        .addTabs(this.tabs.toArray(new Tab[0]))
                .build();
        this.addRenderableWidget(this.tabWidget);
        this.tabWidget.selectTab(this.currentTab, false);
        
        
        
        boolean isAccountsTab = this.currentTab == 3;
        this.resetButton = Button.builder(Component.translatable("controls.reset"), button -> {
            SpoofSettings defaults = new SpoofSettings();
            settings.copyFrom(defaults);
            config.save();
            UpdateChecker.resetShown();
            JarIntegrityChecker.resetShown();
            refreshScreen();
        }).width(150)
          .tooltip(Tooltip.create(OpsecLang.component(isAccountsTab
                  ? OpsecStrings.BUTTON_RESET_DISABLED_TOOLTIP
                  : OpsecStrings.BUTTON_RESET_TOOLTIP)))
          .build();
        
        
        this.resetButton.active = !isAccountsTab;
        
        this.doneButton = Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(150)
                .build();
        
                LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
                footer.addChild(this.resetButton);
        footer.addChild(this.doneButton);
        
        this.layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            this.addRenderableWidget(widget);
        });

        this.repositionElements();

        
        String currentVersion = "v" + UpdateChecker.getCurrentVersion();
        this.versionOutdated = UpdateChecker.getLatestVersion() != null
                && !UpdateChecker.getLatestVersion().equals(UpdateChecker.getCurrentVersion());
        String color = versionOutdated ? "\u00A7c" : "\u00A7a";
        Component versionText = Component.literal(color + currentVersion);
        int textWidth = this.font.width(currentVersion);
        int labelHeight = 10;
        int footerY = this.height - 36;
        int labelY = footerY + (36 - labelHeight) / 2;

        this.versionLabel = new StringWidget(textWidth, labelHeight, versionText, this.font);
        this.versionLabel.setX(6);
        this.versionLabel.setY(labelY + 2);
        if (versionOutdated) {
            this.versionLabel.setTooltip(Tooltip.create(OpsecLang.component(
                    OpsecStrings.VERSION_TOOLTIP_OUTDATED, UpdateChecker.getLatestVersion())));
        } else {
            this.versionLabel.setTooltip(Tooltip.create(OpsecLang.component(OpsecStrings.VERSION_TOOLTIP_UPTODATE)));
        }
        this.addRenderableWidget(this.versionLabel);
    }
    
    private Tab createProtectionTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_CLIENT_BRAND)));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isSpoofAsVanilla())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA),
                        (button, value) -> {
                            
                            
                            settings.setSpoofAsVanilla(value);
                            config.save();
                            refreshScreen();
                    }));
        }

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_RESOURCE_PACK)));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            if (OpsecConfig.MC_VERSION_HAS_MULTI_PACK) {
                widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_ISOLATE_PACK_CACHE)));
            }
            if (OpsecConfig.MC_VERSION_HAS_BLOCK_LOCAL_URLS) {
                widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_BLOCK_LOCAL_PACK_URLS)));
            }
        } else {
            if (OpsecConfig.MC_VERSION_HAS_MULTI_PACK) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isIsolatePackCache())
                        .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_ISOLATE_PACK_CACHE)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_ISOLATE_PACK_CACHE),
                        (button, value) -> { settings.setIsolatePackCache(value); config.save(); }));
            }

            if (OpsecConfig.MC_VERSION_HAS_BLOCK_LOCAL_URLS) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isBlockLocalPackUrls())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_BLOCK_LOCAL_PACK_URLS)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_BLOCK_LOCAL_PACK_URLS),
                        (button, value) -> { settings.setBlockLocalPackUrls(value); config.save(); }));
            }

            
            widgets.add(cycleBuilder(
                    (SpoofSettings.StripMode m) -> switch (m) {
                        case MANUAL    -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_MANUAL);
                        case ASK       -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ASK);
                        case ALWAYS_ON -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON);
                    },
                    List.of(SpoofSettings.StripMode.values()),
                    settings.getPackStripMode())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(switch (v) {
                    case MANUAL    -> OpsecStrings.PACKSTRIP_MODE_MANUAL_TOOLTIP;
                    case ASK       -> OpsecStrings.PACKSTRIP_MODE_ASK_TOOLTIP;
                    case ALWAYS_ON -> OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON_TOOLTIP;
                })))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_PACK_STRIP_MODE),
                    (button, value) -> {
                        settings.setPackStripMode(value);
                        config.save();
                    }));
        }

        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isStripModShaders())
            .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_STRIP_MOD_SHADERS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_STRIP_MOD_SHADERS),
                (button, value) -> { settings.setStripModShaders(value); config.save(); }));

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.OPTION_CLEAR_CACHE), button -> {
                ResourcePackGuard.clearAllCaches();
            }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_CLEAR_CACHE)))
          .build());

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_KEY_RESOLUTION)));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            widgets.add(createEPManagedToggle(OpsecLang.component(OpsecStrings.OPTION_KEY_RESOLUTION_SPOOFING)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isTranslationProtectionEnabled())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_KEY_RESOLUTION_SPOOFING)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_KEY_RESOLUTION_SPOOFING),
                        (button, value) -> {
                            settings.setTranslationProtection(value);
                            config.save();
                            refreshScreen();
                    }));

            
            if (settings.isTranslationProtectionEnabled()) {
                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isFakeDefaultKeybinds())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_FAKE_DEFAULT_KEYBINDS)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_FAKE_DEFAULT_KEYBINDS),
                            (button, value) -> {
                                settings.setFakeDefaultKeybinds(value);
                                config.save();
                        }));

                                widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isMeteorFix())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_METEOR_FIX)))
                        .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_METEOR_FIX),
                            (button, value) -> {
                                settings.setMeteorFix(value);
                                config.save();
                                refreshScreen();
                        }));

                
                if (MeteorMixinCanceller.needsRestart(settings.isMeteorFix())) {
                    widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_RESTART_WARNING)));
                }
                            }
        }

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_PRIVACY)));

        
        
        String lastManagedHeader = null;

        
        if (OpsecConfig.CHAT_SIGNING_MANAGED_EXTERNALLY) {
            String manager = OpsecConfig.chatSigningManagerName();
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.COMPAT_MANAGED_HEADER, manager)));
            lastManagedHeader = manager;
            widgets.add(createManagedToggle(OpsecLang.component(OpsecStrings.OPTION_CHAT_SIGNING),
                    OpsecLang.component(OpsecStrings.COMPAT_MANAGED_TOOLTIP, manager)));
        } else {
            widgets.add(cycleBuilder(SigningModeDisplay::getDisplayName, List.of(SpoofSettings.SigningMode.values()), settings.getSigningMode())
                    .withTooltip(v -> Tooltip.create(SigningModeDisplay.getTooltip(v)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_CHAT_SIGNING),
                    (button, value) -> { settings.setSigningMode(value); config.save(); }));
        }

        
        if (OpsecConfig.TELEMETRY_MANAGED_EXTERNALLY) {
            String manager = OpsecConfig.telemetryManagerName();
            
            if (!manager.equals(lastManagedHeader)) {
                widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.COMPAT_MANAGED_HEADER, manager)));
            }
            widgets.add(createManagedToggle(OpsecLang.component(OpsecStrings.OPTION_DISABLE_TELEMETRY),
                    OpsecLang.component(OpsecStrings.COMPAT_MANAGED_TOOLTIP, manager)));
        } else {
            widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDisableTelemetry())
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_DISABLE_TELEMETRY)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_DISABLE_TELEMETRY),
                    (button, value) -> { settings.setDisableTelemetry(value); config.save(); }));
        }

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_PROTECTION), widgets);
    }
    
    private Tab createMiscTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();
        
        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_ALERTS)));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowAlerts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_ALERTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SHOW_ALERTS),
                (button, value) -> { settings.setShowAlerts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isShowToasts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_TOASTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_SHOW_TOASTS),
                (button, value) -> { settings.setShowToasts(value); config.save(); }));
        
        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isLogDetections())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_LOG_DETECTIONS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_LOG_DETECTIONS),
                (button, value) -> { settings.setLogDetections(value); config.save(); }));

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_DEBUG)));

        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDebugAlerts())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_DEBUG_ALERTS)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_DEBUG_ALERTS),
                (button, value) -> { settings.setDebugAlerts(value); config.save(); }));

        widgets.add(cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), settings.isDebugCommand())
                .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_DEBUG_COMMAND)))
                .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_DEBUG_COMMAND),
                (button, value) -> { settings.setDebugCommand(value); config.save(); }));

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_MISC), widgets);
    }
    
    private Tab createAccountsTab() {
        List<AbstractWidget> widgets = new ArrayList<>();
        AccountManager accountManager = AccountManager.getInstance();
        
        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_ACCOUNTS)));
        
        
        String currentUser = Minecraft.getInstance().getUser().getName();
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.ACCOUNT_CURRENT, currentUser)));
        
        
        if (!accountManager.getAccounts().isEmpty()) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_SAVED_ACCOUNTS)));
            
            for (Account account : accountManager.getAccounts()) {
                String displayName = account.getUsername() + (account.isCracked() ? " \u00A77(offline)" : "");
                
                boolean isLoggedIn = currentUser.equals(account.getUsername());
                boolean isValid = account.isValid();
                
                
                
                boolean isLoggingIn = account.getUuid() != null && account.getUuid().equals(loggingInAccountUuid);
                
                
                String buttonText;
                if (isLoggingIn) {
                    
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "\u00A7e" + displayName + " \u00A77(logging in" + dots + ")";
                } else if (isLoggedIn && isLoggingOut) {
                    
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "\u00A7e" + displayName + " \u00A77(logging out" + dots + ")";
                } else if (!isValid) {
                    buttonText = "\u00A7c" + displayName + " \u00A78(invalid)";
                } else if (isLoggedIn) {
                    buttonText = "\u00A7a" + displayName;
                } else {
                    buttonText = displayName;
                }
                
                String refreshInfo = (account instanceof SessionAccount sa && sa.hasRefreshToken()) ? "\n\u00A72Auto-refresh enabled" : "";
                String tooltip;
                if (!isValid) {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "\u00A7c" + errorMsg + "\n\u00A77Click to try login anyway" + refreshInfo;
                    } else {
                        tooltip = "\u00A7cToken expired or invalid\n\u00A77Click to try login anyway" + refreshInfo;
                    }
                } else if (isLoggedIn) {
                    tooltip = "Currently logged in\nClick to logout to original account" + refreshInfo;
                } else {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "\u00A7cLast error: " + errorMsg + "\n\u00A77Click to login as " + displayName + refreshInfo;
                    } else {
                        tooltip = "Click to login as " + displayName + refreshInfo;
                    }
                }
                
                widgets.add(new AccountRowWidget(
                        buttonText,
                        tooltip,
                        isLoggedIn,
                        
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return; 
                            }
                            if (isLoggedIn) {
                                
                                isLoggingOut = true;
                                Opsec.LOGGER.info("[OpSec] Logging out of account: {}", account.getUsername());
                                
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        refreshScreen();
                                    });
                                });
                            } else {
                                
                                loggingInAccountUuid = account.getUuid();
                                Opsec.LOGGER.info("[OpSec] Logging into account: {}", account.getUsername());
                                
                                CompletableFuture.runAsync(() -> {
                                    boolean success = account.login();
                                    if (success) {
                                        accountManager.setActiveAccountUuid(account.getUuid());
                                    }
                                }).whenComplete((result, error) -> {
                                    
                                    Minecraft.getInstance().execute(() -> {
                                        loggingInAccountUuid = null;
                                        refreshScreen();
                                    });
                                });
                            }
                        },
                        
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return; 
                            }
                            
                            if (isLoggedIn) {
                                isLoggingOut = true;
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        accountManager.remove(account);
                                        refreshScreen();
                                    });
                                });
                            } else {
                                accountManager.remove(account);
                                refreshScreen();
                            }
                        }
                ));
            }
            
            
            Button refreshButton = Button.builder(OpsecLang.component(
                    accountManager.isRefreshing() ? OpsecStrings.BUTTON_REFRESHING : OpsecStrings.BUTTON_REFRESH_ALL
            ), button -> {
                if (accountManager.isRefreshing()) {
                    return; 
                }
                button.setMessage(OpsecLang.component(OpsecStrings.BUTTON_REFRESHING));
                button.active = false;
                accountManager.refreshAllAccounts((valid, invalid) -> {
                    refreshScreen();
                });
            }).size(230, 20)              .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REFRESH_TOOLTIP)))

              .build();
            
            
            if (accountManager.isRefreshing()) {
                refreshButton.active = false;
            }
            
            widgets.add(refreshButton);
        }
        
        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_ADD_ACCOUNT)));
        
        
        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION), button -> {
                        this.minecraft.setScreen(new AddAccountScreen(this));
                    }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION_TOOLTIP)))
          .build());

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE), button -> {
                        this.minecraft.setScreen(new AddCrackedAccountScreen(this));
                    }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE_TOOLTIP)))
          .build());
        
        
        widgets.add(new ImportExportRowWidget(
                
                () -> {
                    openImportDialog();
                },
                
                () -> {
                    openExportDialog();
                }
        ));
        
        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_ACCOUNTS), widgets);
    }
    
    private void openImportDialog() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Accounts",
                    "",
                    filters,
                    "JSON Files (*.json)",
                    false
                );
                
                if (result == null) return;
                
                File file = new File(result);
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                int imported = AccountManager.getInstance().importFromJson(content);
                
                
                Minecraft.getInstance().execute(() -> {
                    if (imported > 0) {
                        Opsec.LOGGER.info("[OpSec] Imported {} accounts from {}", imported, file.getName());
                    }
                    refreshScreen();
                });
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to import accounts: {}", e.getMessage());
            }
        }, "OpSec-Import-Thread").start();
    }
    
    private void openExportDialog() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Accounts",
                    "opsec-accounts-export.json",
                    filters,
                    "JSON Files (*.json)"
                );
                
                if (result == null) return;
                
                File file = new File(result);
                if (!file.getName().toLowerCase().endsWith(".json")) {
                    file = new File(file.getParentFile(), file.getName() + ".json");
                }
                
                String json = AccountManager.getInstance().exportToJson();
                Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    
                Opsec.LOGGER.info("[OpSec] Exported accounts to {}", file.getPath());
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to export accounts: {}", e.getMessage());
            }
        }, "OpSec-Export-Thread").start();
    }
    
    
    private static class AccountRowWidget extends AbstractWidget {
        private final Button accountButton;
        private final Button removeButton;
        private final Runnable onAccountClick;
        private final Runnable onRemoveClick;
                private boolean wasMouseDown = false;
                
        public AccountRowWidget(String accountName, String tooltip, boolean isActive, Runnable onAccountClick, Runnable onRemoveClick) {
            super(0, 0, 230, 20, Component.empty());
            this.onAccountClick = onAccountClick;
            this.onRemoveClick = onRemoveClick;
            
            this.accountButton = Button.builder(Component.literal(accountName), btn -> onAccountClick.run())
                    .size(150, 20)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build();
            
            this.removeButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_REMOVE), btn -> onRemoveClick.run())
                    .size(55, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REMOVE_TOOLTIP)))
                    .build();
        }
        
        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }
        
        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }
        
        private void updateButtonPositions() {
            
            int totalWidth = 150 + 5 + 55; 
            int startX = getX() + (getWidth() - totalWidth) / 2;
            accountButton.setX(startX);
            accountButton.setY(getY());
            removeButton.setX(startX + 150 + 5);
            removeButton.setY(getY());
        }
        
                @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);

            
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (!isMouseDown && wasMouseDown) {
                
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                }
            }
            wasMouseDown = isMouseDown;
        }
                
        
                
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            accountButton.updateNarration(output);
        }
    }
    
    
    private static class ImportExportRowWidget extends AbstractWidget {
        private final Button importButton;
        private final Button exportButton;
        private final Runnable onImportAction;
        private final Runnable onExportAction;
                private boolean wasMouseDown = false;
                
        public ImportExportRowWidget(Runnable onImport, Runnable onExport) {
            super(0, 0, 230, 20, Component.empty());
            this.onImportAction = onImport;
            this.onExportAction = onExport;
            
            this.importButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_IMPORT), btn -> onImport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_IMPORT_TOOLTIP)))
                    .build();
            
            this.exportButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_EXPORT), btn -> onExport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_EXPORT_TOOLTIP)))
                    .build();
        }
        
        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }
        
        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }
        
        private void updateButtonPositions() {
            int totalWidth = 100 + 5 + 100;
            int startX = getX() + (getWidth() - totalWidth) / 2;
            importButton.setX(startX);
            importButton.setY(getY());
            exportButton.setX(startX + 100 + 5);
            exportButton.setY(getY());
        }
        
                @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);

            
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (!isMouseDown && wasMouseDown) {
                
                if (importButton.isMouseOver(mouseX, mouseY)) {
                    onImportAction.run();
                } else if (exportButton.isMouseOver(mouseX, mouseY)) {
                    onExportAction.run();
                }
            }
            wasMouseDown = isMouseDown;
                }
        
        
                
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            importButton.updateNarration(output);
        }
    }
    
    
    private static class ToggleAllRowWidget extends AbstractWidget {
        private final Button enableAllButton;
        private final Button disableAllButton;
        private final Runnable onEnableAll;
        private final Runnable onDisableAll;
                private boolean wasMouseDown = false;
        
        public ToggleAllRowWidget(Runnable onEnableAll, Runnable onDisableAll) {
            super(0, 0, 230, 20, Component.empty());
            this.onEnableAll = onEnableAll;
            this.onDisableAll = onDisableAll;

            this.enableAllButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_ENABLE_ALL), btn -> onEnableAll.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_ENABLE_ALL_TOOLTIP)))
                    .build();

            this.disableAllButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_DISABLE_ALL), btn -> onDisableAll.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_DISABLE_ALL_TOOLTIP)))
                    .build();
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }

        private void updateButtonPositions() {
            int totalWidth = 100 + 5 + 100;
            int startX = getX() + (getWidth() - totalWidth) / 2;
            enableAllButton.setX(startX);
            enableAllButton.setY(getY());
            disableAllButton.setX(startX + 100 + 5);
            disableAllButton.setY(getY());
        }

                @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            enableAllButton.render(graphics, mouseX, mouseY, partialTick);
            disableAllButton.render(graphics, mouseX, mouseY, partialTick);

            
            long windowHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (!isMouseDown && wasMouseDown) {
                
                if (enableAllButton.isMouseOver(mouseX, mouseY)) {
                    enableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onEnableAll.run();
                } else if (disableAllButton.isMouseOver(mouseX, mouseY)) {
                    disableAllButton.playDownSound(Minecraft.getInstance().getSoundManager());
                    onDisableAll.run();
                }
            }
            wasMouseDown = isMouseDown;
        }
        
        
        
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            enableAllButton.updateNarration(output);
        }
    }

    private Tab createWhitelistTab(SpoofSettings settings) {
        List<AbstractWidget> widgets = new ArrayList<>();

        
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_MOD_WHITELIST)));

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.EP_MANAGED_HEADER)));
            CycleButton<SpoofSettings.WhitelistMode> modeButton = cycleBuilder(WhitelistModeDisplay::getDisplayName, List.of(SpoofSettings.WhitelistMode.values()), SpoofSettings.WhitelistMode.OFF)
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.EP_MANAGED_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE), (b, v) -> {});
            modeButton.active = false;
            widgets.add(modeButton);
        } else if (settings.isSpoofAsVanilla()) {
            
            
            
            CycleButton<SpoofSettings.WhitelistMode> lockedModeButton = cycleBuilder(WhitelistModeDisplay::getDisplayName, List.of(SpoofSettings.WhitelistMode.values()), SpoofSettings.WhitelistMode.OFF)
                    .withTooltip(v -> Tooltip.create(OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE_LOCKED_TOOLTIP)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE), (b, v) -> {});
            lockedModeButton.active = false;
            widgets.add(lockedModeButton);
        } else {
            
            
            
            widgets.add(cycleBuilder(
                        WhitelistModeDisplay::getDisplayName,
                        List.of(SpoofSettings.WhitelistMode.AUTO, SpoofSettings.WhitelistMode.CUSTOM),
                        settings.getWhitelistMode() == SpoofSettings.WhitelistMode.OFF
                            ? SpoofSettings.WhitelistMode.AUTO
                            : settings.getWhitelistMode())
                    .withTooltip(v -> Tooltip.create(WhitelistModeDisplay.getTooltip(v)))
                    .create(0, 0, 230, 20, OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE),
                        (button, value) -> {
                            settings.setWhitelistMode(value);
                            config.save();
                            refreshScreen();
                    }));

            if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.AUTO) {
                List<ModContainer> whitelistableMods = getWhitelistableMods();
                for (ModContainer mod : whitelistableMods) {
                    String modId = mod.getMetadata().getId();
                    String modName = mod.getMetadata().getName();
                    ModRegistry.ModInfo info = ModRegistry.getModInfo(modId);
                    boolean hasChannels = info != null && info.hasChannels();
                    if (hasChannels) {
                        widgets.add(createSectionHeader("\u00A7a" + modName + OpsecLang.tr(OpsecStrings.WHITELIST_SUFFIX_CHANNELS, info.getChannels().size())));
                    } else if (ModRegistry.isInDependencyClosure(modId)) {
                        widgets.add(createSectionHeader("\u00A7a" + modName + OpsecLang.tr(OpsecStrings.WHITELIST_SUFFIX_REQUIRED)));
                    }
                }
            } else if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.CUSTOM) {
                
                widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_INSTALLED_MODS)));

                List<ModContainer> whitelistableMods = getWhitelistableMods();
                widgets.add(new ToggleAllRowWidget(
                    () -> {
                        for (ModContainer m : whitelistableMods) {
                            settings.getWhitelistedMods().add(m.getMetadata().getId());
                        }
                        config.save();
                        refreshScreen();
                    },
                    () -> {
                        settings.getWhitelistedMods().clear();
                        config.save();
                        refreshScreen();
                    }
                ));

                for (ModContainer mod : whitelistableMods) {
                    String modId = mod.getMetadata().getId();
                    String modName = mod.getMetadata().getName();

                    boolean isExplicit = settings.getWhitelistedMods().contains(modId);
                    
                    boolean isImplicit = !isExplicit && ModRegistry.isInDependencyClosure(modId);
                    final String finalModId = modId;

                    Component tooltip = isImplicit
                        ? OpsecLang.component(OpsecStrings.WHITELIST_TOOLTIP_REQUIRED_BY,
                            finalModId, ModRegistry.resolveRequiringModName(modId))
                        : Component.literal(finalModId);

                    CycleButton<Boolean> modButton = cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), isExplicit || isImplicit)
                        .withTooltip(v -> Tooltip.create(tooltip))
                        .create(0, 0, 230, 20, Component.literal(modName),
                            (button, value) -> {
                                if (value) {
                                    settings.getWhitelistedMods().add(finalModId);
                                } else {
                                    settings.getWhitelistedMods().remove(finalModId);
                                }
                                config.save();
                                refreshScreen();
                        });
                    if (isImplicit) modButton.active = false;
                    widgets.add(modButton);
                }
            }
        }

        return new WidgetTab(OpsecLang.component(OpsecStrings.TAB_WHITELIST), widgets);
    }
    
    
    private static List<ModContainer> getWhitelistableMods() {
        return FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> !ModRegistry.PLATFORM_MODS.contains(mod.getMetadata().getId())
                    && mod.getContainingMod().isEmpty())
            .sorted(Comparator.comparing(mod -> mod.getMetadata().getName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private CycleButton<Boolean> createEPManagedToggle(Component label) {
        return createManagedToggle(label, OpsecLang.component(OpsecStrings.EP_MANAGED_TOOLTIP));
    }

    
    private CycleButton<Boolean> createManagedToggle(Component label, Component tooltip) {
        CycleButton<Boolean> button = cycleBuilder(COLORED_BOOL_TO_TEXT, List.of(Boolean.TRUE, Boolean.FALSE), false)
                .withTooltip(v -> Tooltip.create(tooltip))
                .create(0, 0, 230, 20, label, (b, v) -> {});
        button.active = false;
        return button;
    }

    private StringWidget createSectionHeader(String text) {
                Component component = Component.literal(text);
        
        int textWidth = Minecraft.getInstance().font.width(component);
        return new StringWidget(textWidth, 20, component, Minecraft.getInstance().font);
            }
    
    private int getCurrentTabIndex() {
        if (this.tabs != null && this.tabManager.getCurrentTab() != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (this.tabs.get(i) == this.tabManager.getCurrentTab()) {
                    return i;
                }
            }
        }
        return this.currentTab;
    }
    
    private double getCurrentScrollOffset() {
        Tab currentTab = this.tabManager.getCurrentTab();
        if (currentTab instanceof WidgetTab widgetTab) {
            return widgetTab.getScrollAmount();
        }
        return 0;
    }

    private void refreshScreen() {
        int tabIndex = getCurrentTabIndex();
        double scroll = getCurrentScrollOffset();
                this.minecraft.setScreen(new OpsecConfigScreen(this.parent, tabIndex, scroll));
            }

    
    
    @Override
    protected void repositionElements() {
        if (this.tabWidget != null) {
                        this.tabWidget.setWidth(this.width);
            this.tabWidget.arrangeElements();
                        int tabBottom = this.tabWidget.getRectangle().bottom();
            ScreenRectangle screenRect = new ScreenRectangle(0, tabBottom, this.width, this.height - 36 - tabBottom);
            this.tabManager.setTabArea(screenRect);
            this.layout.setHeaderHeight(tabBottom);
            this.layout.arrangeElements();
                    }
        
        if (this.versionLabel != null) {
            int labelHeight = 10;
            int footerY = this.height - 36;
            int labelY = footerY + (36 - labelHeight) / 2;
            this.versionLabel.setX(6);
            this.versionLabel.setY(labelY + 2);
        }
    }

    @Override
    public void tick() {
        super.tick();
        
        if (this.resetButton != null) {
            int currentTabIndex = getCurrentTabIndex();
            boolean isAccountsTab = currentTabIndex == 3;
            this.resetButton.active = !isAccountsTab;
        }
        
        
        if (loggingInAccountUuid != null || isLoggingOut) {
            animationTicks++;
            
            if (animationTicks % 5 == 0) {
                refreshScreen();
            }
        } else {
            animationTicks = 0;
        }
                pollVersionLabelClick();
    }

    
    private boolean isOverVersionLabel(double mouseX, double mouseY) {
        if (!versionOutdated || versionLabel == null) return false;
        return mouseX >= versionLabel.getX() && mouseX < versionLabel.getX() + versionLabel.getWidth()
                && mouseY >= versionLabel.getY() && mouseY < versionLabel.getY() + versionLabel.getHeight();
    }

    private boolean versionLabelMouseWasDown = false;

    private void pollVersionLabelClick() {
        if (!versionOutdated || versionLabel == null) return;
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        boolean isDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!isDown && versionLabelMouseWasDown) {
            double[] mx = new double[1], my = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mx, my);
            double scale = this.minecraft.getWindow().getGuiScale();
            double guiX = mx[0] / scale;
            double guiY = my[0] / scale;
            if (isOverVersionLabel(guiX, guiY)) {
                openReleaseUrl();
            }
        }
        versionLabelMouseWasDown = isDown;
    }

    private void openReleaseUrl() {
        try {
                        net.minecraft.util.Util.getPlatform().openUri(UpdateChecker.getReleaseUrl());
                    } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] Failed to open release URL: {}", e.getMessage());
        }
    }

    @Override
    public void onClose() {
        config.save();
                this.minecraft.setScreen(parent);
            }
    
        
    private class WidgetTab implements Tab {
        private final Component title;
        private final ScrollableWidgetList scrollableList;

        public WidgetTab(Component title, List<AbstractWidget> widgets) {
            this.title = title;
            
            
            this.scrollableList = new ScrollableWidgetList(Minecraft.getInstance(), 300, 200, 0, widgets);
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> consumer) {
            consumer.accept(scrollableList);
        }

        @Override
        public void doLayout(ScreenRectangle rectangle) {
            
            scrollableList.setSize(rectangle.width(), rectangle.height());
            scrollableList.setPosition(rectangle.left(), rectangle.top());
            
            scrollableList.setScrollAmount(scrollOffset);
        }
        
        @Override
        public Component getTabExtraNarration() {
            return Component.empty();
        }
        
        public double getScrollAmount() {
            return scrollableList.currentScrollAmount();
        }

            }
    
    
    private static class ScrollableWidgetList extends ContainerObjectSelectionList<ScrollableWidgetList.WidgetEntry> {
        public ScrollableWidgetList(Minecraft minecraft, int width, int height, int top, List<AbstractWidget> widgets) {
                        super(minecraft, width, height, top, 25);
                        this.centerListVertically = false;
            
            for (AbstractWidget widget : widgets) {
                                this.addEntry(new WidgetEntry(widget, this));
                            }
        }

        
        public double currentScrollAmount() {
                        return this.scrollAmount();
                    }

        @Override
        public int getRowWidth() {
            return 220;
        }

                @Override
        protected int scrollBarX() {
            return this.getX() + this.getWidth() / 2 + 124;
        }
                

        public static class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetEntry> {
            private final AbstractWidget widget;

                        public WidgetEntry(AbstractWidget widget, ScrollableWidgetList parentList) {
                this.widget = widget;
            }
            
                        @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int centerX = (screenWidth - widget.getWidth()) / 2;
                widget.setX(centerX);
                widget.setY(this.getContentY());
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            
            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(widget);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(widget);
            }

                    }
    }
    
    


    private static class WhitelistModeDisplay {
        public static Component getDisplayName(SpoofSettings.WhitelistMode mode) {
            return switch (mode) {
                case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL);
                case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO);
                case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM);
            };
        }

        public static Component getTooltip(SpoofSettings.WhitelistMode mode) {
            return switch (mode) {
                case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL_TOOLTIP);
                case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO_TOOLTIP);
                case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM_TOOLTIP);
            };
        }
    }

    



    private static class SigningModeDisplay {
        public static Component getDisplayName(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> Component.literal("\u00A7aON");  
                case OFF  -> Component.literal("\u00A7eOFF"); 
            };
        }

        public static Component getTooltip(SpoofSettings.SigningMode mode) {
            return switch (mode) {
                case SIGN -> OpsecLang.component(OpsecStrings.CHATSIGNING_SIGN_TOOLTIP);
                case OFF  -> OpsecLang.component(OpsecStrings.CHATSIGNING_OFF_TOOLTIP);
            };
        }
    }
}
