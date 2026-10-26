package aurick.opsec.mod.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


















public class MeteorMixinCanceller implements MixinCanceller {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("OpSec");
    private static final String METEOR_MIXIN = "meteordevelopment.meteorclient.mixin.AbstractSignEditScreenMixin";
    
    private static final boolean meteorFixEnabled;
    private static final boolean meteorPresent;
    
    
    private static final boolean appliedMeteorFix;
    
    static {
        
        meteorPresent = FabricLoader.getInstance().isModLoaded("meteor-client");
        
        
        meteorFixEnabled = readMeteorFixSetting();
        
        
        appliedMeteorFix = meteorPresent && meteorFixEnabled;
        
        if (meteorPresent && meteorFixEnabled) {
            LOGGER.info("[OpSec] Meteor Client detected - Meteor Fix enabled, will disable Meteor's broken key resolution protection");
        } else if (meteorPresent) {
            LOGGER.warn("[OpSec] Meteor Client detected - Meteor Fix is DISABLED. Meteor's translation protection may expose your mods to servers!");
        }
    }
    
    



    public static boolean wasAppliedAtStartup() {
        return appliedMeteorFix;
    }
    
    



    public static boolean needsRestart(boolean currentSetting) {
        if (!meteorPresent) {
            return false; 
        }
        return currentSetting != appliedMeteorFix;
    }
    
    







    private static boolean readMeteorFixSetting() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("opsec.json");
        
        if (!Files.exists(configPath)) {
            
            return true;
        }
        
        try {
            String content = Files.readString(configPath);
            if (content == null || content.trim().isEmpty()) {
                return true;
            }
            
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            
            
            if (json.has("settings")) {
                JsonObject settings = json.getAsJsonObject("settings");
                
                
                if (settings.has("translationProtection") && !settings.get("translationProtection").getAsBoolean()) {
                    LOGGER.info("[OpSec] Key resolution protection is disabled, Meteor Fix will not apply");
                    return false;
                }
                
                
                if (settings.has("meteorFix")) {
                    return settings.get("meteorFix").getAsBoolean();
                }
            }
            
            
            return true;
            
        } catch (IOException | com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LOGGER.warn("[OpSec] Could not read config for Meteor Fix setting, defaulting to enabled: {}", e.getMessage());
            return true;
        }
    }
    
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
                
        
        
        
        if (meteorPresent && meteorFixEnabled && METEOR_MIXIN.equals(mixinClassName)) {
            LOGGER.debug("[OpSec] Cancelling Meteor mixin: {}", mixinClassName);
            return true;
        }
        return false;
            }
}

