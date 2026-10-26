package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.protection.OpsecFromPacketAccess;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import aurick.opsec.mod.protection.TranslationProtectionHandler.InterceptionType;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;












@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin implements OpsecFromPacketAccess {

    @Shadow @Final private String key;
    @Shadow @Final private String fallback;

    @Unique
    private boolean opsec$fromPacket = false;

    @Unique
    private boolean opsec$reported = false;

    @Unique
    private boolean opsec$silent = false;

    @Override
    public void opsec$setFromPacket() {
        this.opsec$fromPacket = true;
    }

    @Override
    public void opsec$setSilent() {
        this.opsec$silent = true;
    }

    
    @Unique
    private static final String OPSEC_ALLOW_ORIGINAL = "\0__opsec_allow__";

    



    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String opsec$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
        String result = opsec$handleTranslationLookup(id, id);
        if (result == OPSEC_ALLOW_ORIGINAL) {
            return original.call(instance, id);
        }
        return result;
    }

    



    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    )
    private String opsec$wrapGetOrDefaultWithFallback(Language instance, String keyArg, String defaultValue, Operation<String> original) {
        String result = opsec$handleTranslationLookup(keyArg, defaultValue);
        if (result == OPSEC_ALLOW_ORIGINAL) {
            return original.call(instance, keyArg, defaultValue);
        }
        return result;
    }

    








    @Unique
    private String opsec$handleTranslationLookup(String translationKey, String defaultValue) {
        
        if (!this.opsec$fromPacket || Minecraft.getInstance().hasSingleplayerServer()) {
            return OPSEC_ALLOW_ORIGINAL;
        }

        
        TranslationProtectionHandler.notifyExploitDetected();

        if (ModRegistry.isVanillaTranslationKey(translationKey)) {
            opsec$reportPassthrough(translationKey, defaultValue);
            return OPSEC_ALLOW_ORIGINAL;
        }

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        
        if (!config.isTranslationProtectionEnabled()) {
            String realValue = opsec$getRealTranslation(translationKey, defaultValue);
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
            return OPSEC_ALLOW_ORIGINAL;
        }

        
        if (settings.isVanillaMode()) {
            String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
            opsec$logBlocked(translationKey, blockedValue);
            return blockedValue;
        }

        
        if (settings.isFabricMode()) {
            if (ModRegistry.isWhitelistedTranslationKey(translationKey)) {
                opsec$reportPassthrough(translationKey, defaultValue);
                return OPSEC_ALLOW_ORIGINAL;
            }
            String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
            opsec$logBlocked(translationKey, blockedValue);
            return blockedValue;
        }

        
        if (ModRegistry.isWhitelistedTranslationKey(translationKey)) {
            opsec$reportPassthrough(translationKey, defaultValue);
            return OPSEC_ALLOW_ORIGINAL;
        }
        String blockedValue = opsec$getBlockedValue(translationKey, defaultValue);
        opsec$logBlocked(translationKey, blockedValue);
        return blockedValue;
    }

    



    @Unique
    private void opsec$logBlocked(String translationKey, String defaultValue) {
        if (opsec$silent || opsec$reported) return;
        opsec$reported = true;
        String originalValue = opsec$getRealTranslation(translationKey, defaultValue);

        if (!originalValue.equals(defaultValue)) {
            TranslationProtectionHandler.sendDetail(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
        } else {
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
        }
        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, originalValue, defaultValue);
    }

    
    @Unique
    private String opsec$getBlockedValue(String translationKey, String defaultValue) {
        String packValue = ModRegistry.getServerPackTranslation(translationKey);
        return packValue != null ? packValue : defaultValue;
    }

    
    @Unique
    private void opsec$reportPassthrough(String translationKey, String defaultValue) {
        if (!OpsecConfig.getInstance().isDebugAlerts() && !OpsecConfig.getInstance().isLogDetections()) return;
        String realValue = opsec$getRealTranslation(translationKey, defaultValue);
        TranslationProtectionHandler.sendDetailDebug(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
        TranslationProtectionHandler.logDetection(InterceptionType.TRANSLATION, translationKey, realValue, realValue);
    }

    



    @Unique
    private String opsec$getRealTranslation(String translationKey, String defaultValue) {
        try {
            Language lang = Language.getInstance();
            if (lang instanceof ClientLanguageAccessor accessor) {
                Map<String, String> storage = accessor.opsec$getStorage();
                String value = storage.get(translationKey);
                return value != null ? value : defaultValue;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to get real translation for key '{}': {}",
                    translationKey, e.getMessage());
        }
        return defaultValue;
    }
}
