package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;






public class OpsecConfig {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("opsec.json");

    public static final boolean EXPLOIT_PREVENTER_LOADED =
        FabricLoader.getInstance().isModLoaded("exploitpreventer");

    
    
    
    
    public static final boolean NO_CHAT_REPORTS_LOADED =
        FabricLoader.getInstance().isModLoaded("nochatreports");
    public static final boolean NO_PRYING_EYES_LOADED =
        FabricLoader.getInstance().isModLoaded("nopryingeyes");

    
    public static final boolean CHAT_SIGNING_MANAGED_EXTERNALLY =
        NO_CHAT_REPORTS_LOADED || NO_PRYING_EYES_LOADED;

    
    public static final boolean TELEMETRY_MANAGED_EXTERNALLY =
        NO_CHAT_REPORTS_LOADED || NO_PRYING_EYES_LOADED;

    
    
        public static final boolean MC_VERSION_HAS_MULTI_PACK = true;
    
    
        public static final boolean MC_VERSION_HAS_BLOCK_LOCAL_URLS = true;
    
    private static volatile OpsecConfig INSTANCE;
    private static final Object LOCK = new Object();

    private SpoofSettings settings = new SpoofSettings();
    private volatile String currentServer = null;

    private OpsecConfig() {
        load();
        if (EXPLOIT_PREVENTER_LOADED) {
            Opsec.LOGGER.info(
                "[OpSec] ExploitPreventer detected - compatibility mode active."
            );
        }
        if (NO_CHAT_REPORTS_LOADED) {
            Opsec.LOGGER.info(
                "[OpSec] No Chat Reports detected - deferring chat signing and telemetry to it."
            );
        }
        if (NO_PRYING_EYES_LOADED) {
            Opsec.LOGGER.info(
                "[OpSec] No Prying Eyes detected - deferring chat signing and telemetry to it."
            );
        }
    }

    
    public static String chatSigningManagerName() {
        if (NO_CHAT_REPORTS_LOADED) return "No Chat Reports";
        if (NO_PRYING_EYES_LOADED) return "No Prying Eyes";
        return null;
    }

    
    public static String telemetryManagerName() {
        if (NO_CHAT_REPORTS_LOADED) return "No Chat Reports";
        if (NO_PRYING_EYES_LOADED) return "No Prying Eyes";
        return null;
    }

    public static OpsecConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new OpsecConfig();
                }
            }
        }
        return INSTANCE;
    }

    
    public void load() {
        settings = SpoofSettings.fromJson(JsonParser.parseString("{\"spoofAsVanilla\":false,\"isolatePackCache\":true,\"blockLocalPackUrls\":true,\"stripModShaders\":true,\"translationProtection\":true,\"fakeDefaultKeybinds\":true,\"meteorFix\":true,\"packStripMode\":\"ALWAYS_ON\",\"showAlerts\":false,\"showToasts\":false,\"logDetections\":false,\"debugAlerts\":false,\"debugCommand\":false,\"signingMode\":\"SIGN\",\"disableTelemetry\":true,\"buttonX\":-1,\"buttonY\":-1,\"skippedUpdateVersion\":\"\",\"tamperWarningDismissed\":false,\"alertHintShown\":true,\"whitelistMode\":\"AUTO\",\"whitelistedMods\":[]}").getAsJsonObject());
    }

    






    private boolean validateAndCorrectSettings(SpoofSettings settings) {
        if (settings == null) {
            Opsec.LOGGER.error("[OpSec] Validation failed: settings is null");
            return false;
        }

        boolean modified = false;

        if (settings.getSigningMode() == null) {
            Opsec.LOGGER.warn(
                "[OpSec] Invalid signing mode, resetting to SIGN"
            );
            settings.setSigningMode(SpoofSettings.SigningMode.SIGN);
            modified = true;
        }

        if (settings.getWhitelistMode() == null) {
            Opsec.LOGGER.warn(
                "[OpSec] Invalid whitelist mode, resetting to AUTO"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.AUTO);
            modified = true;
        }

        
        
        
        
        if (settings.isSpoofAsVanilla() && settings.getWhitelistMode() != SpoofSettings.WhitelistMode.OFF) {
            Opsec.LOGGER.warn(
                "[OpSec] active whitelist incompatible with spoofAsVanilla; forcing whitelist OFF (block all)"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.OFF);
            modified = true;
        }

        
        
        
        if (!settings.isSpoofAsVanilla() && settings.getWhitelistMode() == SpoofSettings.WhitelistMode.OFF) {
            Opsec.LOGGER.info(
                "[OpSec] Migrating legacy whitelist OFF (block all) to AUTO — option is no longer user-selectable"
            );
            settings.setWhitelistMode(SpoofSettings.WhitelistMode.AUTO);
            modified = true;
        }

        if (modified) {
            save();
        }

        return true;
    }

    
    public void save() {
        if (INSTANCE != null) {
            ModRegistry.rebuildDependencyClosure();
        }
    }

    public SpoofSettings getSettings() {
        return settings;
    }

    public void setCurrentServer(String server) {
        this.currentServer = normalizeAddress(server);
    }

    public String getCurrentServer() {
        return currentServer;
    }

    
    



    public boolean shouldSpoofBrand() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isSpoofAsVanilla();
    }

    





    public boolean shouldSpoofChannels() {
        return !EXPLOIT_PREVENTER_LOADED;
    }

    
    public boolean shouldSpoofKnownPacks() {
        return !EXPLOIT_PREVENTER_LOADED;
    }

    public String getEffectiveBrand() {
        return settings.getEffectiveBrand();
    }

    
    public boolean shouldIsolatePackCache() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isIsolatePackCache();
    }

    public boolean shouldBlockLocalPackUrls() {
        return !EXPLOIT_PREVENTER_LOADED && settings.isBlockLocalPackUrls();
    }

    
    public boolean shouldStripModShaders() {
        return settings.isStripModShaders();
    }

    
    public boolean shouldWrapServerPacks() {
        return shouldStripPack() || shouldStripModShaders();
    }

    
    public boolean isTranslationProtectionEnabled() {
        return (
            !EXPLOIT_PREVENTER_LOADED &&
            settings.isTranslationProtectionEnabled()
        );
    }

    public boolean isMeteorFix() {
        return settings.isMeteorFix();
    }

    
    public SpoofSettings.StripMode getPackStripMode() {
        return settings.getPackStripMode();
    }

    public boolean shouldStripPack() {
        return !EXPLOIT_PREVENTER_LOADED;
    }

    
    public boolean shouldShowPackOverlay() {
        return (
            !EXPLOIT_PREVENTER_LOADED &&
            settings.getPackStripMode() == SpoofSettings.StripMode.ASK
        );
    }

    
    public boolean shouldShowAlerts() {
        return settings.isShowAlerts();
    }

    public boolean shouldShowToasts() {
        return settings.isShowToasts();
    }

    public boolean isLogDetections() {
        return settings.isLogDetections();
    }

    public boolean isDebugAlerts() {
        return settings.isDebugAlerts();
    }

    
    public SpoofSettings.SigningMode getSigningMode() {
        return settings.getSigningMode();
    }

    public boolean shouldNotSign() {
        return !CHAT_SIGNING_MANAGED_EXTERNALLY && settings.shouldNotSign();
    }

    
    public boolean shouldDisableTelemetry() {
        return !TELEMETRY_MANAGED_EXTERNALLY && settings.isDisableTelemetry();
    }

    




    public static String normalizeAddress(String address) {
        if (address == null) return "unknown";
        String normalized = address.toLowerCase().trim();
        if (normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized;
    }
}
