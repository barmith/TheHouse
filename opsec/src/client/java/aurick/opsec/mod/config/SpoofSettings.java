package aurick.opsec.mod.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

import static aurick.opsec.mod.config.OpsecConstants.Brands.*;




public class SpoofSettings {
    
    










    public enum SigningMode {
        
        SIGN,
        
        OFF
    }

    



    public enum WhitelistMode {
        
        OFF,
        
        AUTO,
        
        CUSTOM
    }

    







    public enum StripMode {
        

        MANUAL,
        
        ASK,
        
        ALWAYS_ON
    }
    
    
    
    
    
    
    
    private boolean spoofAsVanilla = false;

    
    private boolean isolatePackCache = true;
    private boolean blockLocalPackUrls = true;
    
    private boolean stripModShaders = true;

    
    private boolean translationProtection = true;
    private boolean fakeDefaultKeybinds = true;  
    private boolean meteorFix = true;  

    
    
    
    
    private StripMode packStripMode = StripMode.MANUAL;
    
    
    private boolean showAlerts = true;
    private boolean showToasts = true;
    private boolean logDetections = true;
    private boolean debugAlerts = false;

    
    private boolean debugCommand = false;

    
    private SigningMode signingMode = SigningMode.SIGN;
    
    
    private boolean disableTelemetry = true;
    
    
    private int buttonX = -1;
    private int buttonY = -1;

    
    private String skippedUpdateVersion = "";
    private boolean tamperWarningDismissed = false;

    
    private boolean alertHintShown = false;
    
    
    private WhitelistMode whitelistMode = WhitelistMode.AUTO;
    
    
    
    
    private WhitelistMode previousWhitelistMode = null;
    private Set<String> whitelistedMods = new HashSet<>();

    public SpoofSettings() {}

    public boolean isSpoofAsVanilla() { return spoofAsVanilla; }
    public void setSpoofAsVanilla(boolean spoofAsVanilla) {
        if (this.spoofAsVanilla == spoofAsVanilla) return;
        if (spoofAsVanilla) {
            
            
            if (this.whitelistMode != WhitelistMode.OFF) {
                this.previousWhitelistMode = this.whitelistMode;
            }
            this.whitelistMode = WhitelistMode.OFF;
        } else {
            
            
            
            WhitelistMode restored = this.previousWhitelistMode;
            if (restored == null || restored == WhitelistMode.OFF) {
                restored = WhitelistMode.AUTO;
            }
            this.whitelistMode = restored;
            this.previousWhitelistMode = null;
        }
        this.spoofAsVanilla = spoofAsVanilla;
    }

    public boolean isIsolatePackCache() { return isolatePackCache; }
    public void setIsolatePackCache(boolean isolatePackCache) { this.isolatePackCache = isolatePackCache; }
    
    public boolean isBlockLocalPackUrls() { return blockLocalPackUrls; }
    public void setBlockLocalPackUrls(boolean blockLocalPackUrls) { this.blockLocalPackUrls = blockLocalPackUrls; }

    public boolean isStripModShaders() { return stripModShaders; }
    public void setStripModShaders(boolean stripModShaders) { this.stripModShaders = stripModShaders; }

    public boolean isTranslationProtectionEnabled() { return translationProtection; }
    public void setTranslationProtection(boolean enabled) { this.translationProtection = enabled; }
    
    public boolean isFakeDefaultKeybinds() { return fakeDefaultKeybinds; }
    public void setFakeDefaultKeybinds(boolean enabled) { this.fakeDefaultKeybinds = enabled; }
    
    public boolean isMeteorFix() { return meteorFix; }
    public void setMeteorFix(boolean enabled) { this.meteorFix = enabled; }

    public StripMode getPackStripMode() { return packStripMode; }
    public void setPackStripMode(StripMode mode) { this.packStripMode = mode != null ? mode : StripMode.MANUAL; }
    
    public boolean isShowAlerts() { return showAlerts; }
    public void setShowAlerts(boolean showAlerts) { this.showAlerts = showAlerts; }
    
    public boolean isShowToasts() { return showToasts; }
    public void setShowToasts(boolean showToasts) { this.showToasts = showToasts; }
    
    public boolean isLogDetections() { return logDetections; }
    public void setLogDetections(boolean logDetections) { this.logDetections = logDetections; }

    public boolean isDebugAlerts() { return debugAlerts; }
    public void setDebugAlerts(boolean debugAlerts) { this.debugAlerts = debugAlerts; }

    public boolean isDebugCommand() { return debugCommand; }
    public void setDebugCommand(boolean debugCommand) { this.debugCommand = debugCommand; }

    
    public SigningMode getSigningMode() { return signingMode; }
    public void setSigningMode(SigningMode mode) { this.signingMode = mode; }

    public boolean shouldNotSign() {
        return signingMode == SigningMode.OFF;
    }

    public boolean isDisableTelemetry() { return disableTelemetry; }
    public void setDisableTelemetry(boolean disableTelemetry) { this.disableTelemetry = disableTelemetry; }
    
    public int[] getButtonPosition() {
        if (buttonX < 0 || buttonY < 0) return null;
        return new int[] { buttonX, buttonY };
    }
    
    public void setButtonPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }
    
    
    public boolean isAlertHintShown() { return alertHintShown; }
    public void setAlertHintShown(boolean shown) { this.alertHintShown = shown; }

    
    public WhitelistMode getWhitelistMode() { return whitelistMode; }
    public void setWhitelistMode(WhitelistMode mode) { this.whitelistMode = mode; }

    
    public boolean isWhitelistEnabled() { return whitelistMode != WhitelistMode.OFF; }

    public Set<String> getWhitelistedMods() { return whitelistedMods; }
    public void setWhitelistedMods(Set<String> mods) { this.whitelistedMods = mods != null ? mods : new HashSet<>(); }

    
    public boolean isModWhitelisted(String modId) {
        return whitelistMode == WhitelistMode.CUSTOM && modId != null && whitelistedMods.contains(modId);
    }

    
    public String getSkippedUpdateVersion() { return skippedUpdateVersion; }
    public void setSkippedUpdateVersion(String version) { this.skippedUpdateVersion = version != null ? version : ""; }
    public boolean isVersionSkipped(String version) { return version != null && version.equals(skippedUpdateVersion); }

    public boolean isTamperWarningDismissed() { return tamperWarningDismissed; }
    public void setTamperWarningDismissed(boolean dismissed) { this.tamperWarningDismissed = dismissed; }

    public String getEffectiveBrand() {
        return spoofAsVanilla ? VANILLA : FABRIC;
    }

    public boolean isVanillaMode() {
        return spoofAsVanilla;
    }

    public boolean isFabricMode() {
        return !spoofAsVanilla;
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("spoofAsVanilla", spoofAsVanilla);
        json.addProperty("isolatePackCache", isolatePackCache);
        json.addProperty("blockLocalPackUrls", blockLocalPackUrls);
        json.addProperty("stripModShaders", stripModShaders);
        json.addProperty("translationProtection", translationProtection);
        json.addProperty("fakeDefaultKeybinds", fakeDefaultKeybinds);
        json.addProperty("meteorFix", meteorFix);
        json.addProperty("packStripMode", packStripMode.name());
        json.addProperty("showAlerts", showAlerts);
        json.addProperty("showToasts", showToasts);
        json.addProperty("logDetections", logDetections);
        json.addProperty("debugAlerts", debugAlerts);
        json.addProperty("debugCommand", debugCommand);
        json.addProperty("signingMode", signingMode.name());
        json.addProperty("disableTelemetry", disableTelemetry);
        json.addProperty("buttonX", buttonX);
        json.addProperty("buttonY", buttonY);
        json.addProperty("skippedUpdateVersion", skippedUpdateVersion);
        json.addProperty("tamperWarningDismissed", tamperWarningDismissed);
        json.addProperty("alertHintShown", alertHintShown);

        
        json.addProperty("whitelistMode", whitelistMode.name());
        if (previousWhitelistMode != null) {
            json.addProperty("previousWhitelistMode", previousWhitelistMode.name());
        }
        JsonArray modsArray = new JsonArray();
        for (String modId : whitelistedMods) {
            modsArray.add(modId);
        }
        json.add("whitelistedMods", modsArray);

        return json;
    }
    
    public static SpoofSettings fromJson(JsonObject json) {
        SpoofSettings s = new SpoofSettings();
        if (json.has("spoofAsVanilla")) {
            s.spoofAsVanilla = json.get("spoofAsVanilla").getAsBoolean();
        } else if (json.has("spoofBrand")) {
            
            
            boolean sb = json.get("spoofBrand").getAsBoolean();
            String cb = json.has("customBrand") ? json.get("customBrand").getAsString() : FABRIC;
            s.spoofAsVanilla = sb && VANILLA.equalsIgnoreCase(cb);
        }
        if (json.has("isolatePackCache")) s.isolatePackCache = json.get("isolatePackCache").getAsBoolean();
        if (json.has("blockLocalPackUrls")) s.blockLocalPackUrls = json.get("blockLocalPackUrls").getAsBoolean();
        if (json.has("stripModShaders")) s.stripModShaders = json.get("stripModShaders").getAsBoolean();
        
        if (json.has("spoofLocalPackUrls")) s.blockLocalPackUrls = json.get("spoofLocalPackUrls").getAsBoolean();
        if (json.has("translationProtection")) s.translationProtection = json.get("translationProtection").getAsBoolean();
        
        if (json.has("blockTranslationExploit")) s.translationProtection = json.get("blockTranslationExploit").getAsBoolean();
        if (json.has("fakeDefaultKeybinds")) s.fakeDefaultKeybinds = json.get("fakeDefaultKeybinds").getAsBoolean();
        if (json.has("meteorFix")) s.meteorFix = json.get("meteorFix").getAsBoolean();
        
        if (json.has("packStripMode")) {
            String raw = json.get("packStripMode").getAsString();
            
            s.packStripMode = switch (raw) {
                case "OFF" -> StripMode.MANUAL;
                case "ON"  -> StripMode.ALWAYS_ON;
                default    -> {
                    try {
                        yield StripMode.valueOf(raw);
                    } catch (IllegalArgumentException e) {
                        yield StripMode.MANUAL;
                    }
                }
            };
        } else if (json.has("fakeAcceptResourcePack")) {
            boolean legacyEnabled = json.get("fakeAcceptResourcePack").getAsBoolean();
            boolean legacyOverlay = !json.has("showPackAcceptOverlay")
                    || json.get("showPackAcceptOverlay").getAsBoolean();
            s.packStripMode = !legacyEnabled ? StripMode.MANUAL
                    : (legacyOverlay ? StripMode.ASK : StripMode.ALWAYS_ON);
        }
        if (json.has("showAlerts")) s.showAlerts = json.get("showAlerts").getAsBoolean();
        if (json.has("showToasts")) s.showToasts = json.get("showToasts").getAsBoolean();
        if (json.has("logDetections")) s.logDetections = json.get("logDetections").getAsBoolean();
        if (json.has("debugAlerts")) s.debugAlerts = json.get("debugAlerts").getAsBoolean();
        if (json.has("debugCommand")) s.debugCommand = json.get("debugCommand").getAsBoolean();
        if (json.has("signingMode")) {
            String mode = json.get("signingMode").getAsString();
            
            
            
            
            if ("NO_KEY".equals(mode) || "NO_SIGN".equals(mode) || "OFF".equals(mode)) {
                s.signingMode = SigningMode.OFF;
            } else {
                s.signingMode = SigningMode.SIGN;
            }
        }
        if (json.has("disableTelemetry")) s.disableTelemetry = json.get("disableTelemetry").getAsBoolean();
        if (json.has("buttonX")) s.buttonX = json.get("buttonX").getAsInt();
        if (json.has("buttonY")) s.buttonY = json.get("buttonY").getAsInt();
        if (json.has("skippedUpdateVersion")) s.skippedUpdateVersion = json.get("skippedUpdateVersion").getAsString();
        if (json.has("tamperWarningDismissed")) s.tamperWarningDismissed = json.get("tamperWarningDismissed").getAsBoolean();
        if (json.has("alertHintShown")) s.alertHintShown = json.get("alertHintShown").getAsBoolean();

        
        if (json.has("whitelistMode")) {
            String modeStr = json.get("whitelistMode").getAsString();
            if ("ON".equals(modeStr)) modeStr = "CUSTOM";
            try {
                s.whitelistMode = WhitelistMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                s.whitelistMode = WhitelistMode.OFF;
            }
        } else if (json.has("whitelistEnabled")) {
            s.whitelistMode = json.get("whitelistEnabled").getAsBoolean() ? WhitelistMode.CUSTOM : WhitelistMode.OFF;
        }
        if (json.has("previousWhitelistMode")) {
            String prevStr = json.get("previousWhitelistMode").getAsString();
            try {
                WhitelistMode prev = WhitelistMode.valueOf(prevStr);
                
                if (prev != WhitelistMode.OFF) {
                    s.previousWhitelistMode = prev;
                }
            } catch (IllegalArgumentException ignored) {
                
            }
        }
        if (json.has("whitelistedMods")) {
            JsonArray modsArray = json.getAsJsonArray("whitelistedMods");
            s.whitelistedMods = new HashSet<>();
            for (int i = 0; i < modsArray.size(); i++) {
                s.whitelistedMods.add(modsArray.get(i).getAsString());
            }
        }

        return s;
    }
    
    public void copyFrom(SpoofSettings other) {
        this.spoofAsVanilla = other.spoofAsVanilla;
        this.isolatePackCache = other.isolatePackCache;
        this.blockLocalPackUrls = other.blockLocalPackUrls;
        this.stripModShaders = other.stripModShaders;
        this.translationProtection = other.translationProtection;
        this.fakeDefaultKeybinds = other.fakeDefaultKeybinds;
        this.meteorFix = other.meteorFix;
        this.packStripMode = other.packStripMode;
        this.showAlerts = other.showAlerts;
        this.showToasts = other.showToasts;
        this.logDetections = other.logDetections;
        this.debugAlerts = other.debugAlerts;
        this.debugCommand = other.debugCommand;
        this.signingMode = other.signingMode;
        this.disableTelemetry = other.disableTelemetry;
        this.buttonX = other.buttonX;
        this.buttonY = other.buttonY;
        this.skippedUpdateVersion = other.skippedUpdateVersion;
        this.tamperWarningDismissed = other.tamperWarningDismissed;
        this.alertHintShown = other.alertHintShown;
        this.whitelistMode = other.whitelistMode;
        this.previousWhitelistMode = other.previousWhitelistMode;
        this.whitelistedMods = new HashSet<>(other.whitelistedMods);
    }
}
