package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.protection.PackStripHandler;
import aurick.opsec.mod.tracking.ModIdResolver;
import aurick.opsec.mod.tracking.ModRegistry;
import aurick.opsec.mod.util.KeybindDefaults;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;











@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    
    @Unique
    private static boolean opsec$loggedOnce = false;
    
    







    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void opsec$onLoadStart(ResourceManager resourceManager, List<String> filenames,
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        ModRegistry.beginTranslationRebuild();
        
        
        OpsecLang.reload(filenames);
        Opsec.LOGGER.debug("[OpSec] ClientLanguageMixin: Starting language load");
        opsec$loggedOnce = false;
    }

    




    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void opsec$onLoadComplete(ResourceManager resourceManager, List<String> filenames,
            boolean defaultRightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
        ModRegistry.commitTranslationRebuild();
        KeybindDefaults.reset();
        if (!opsec$loggedOnce) {
            opsec$loggedOnce = true;
            Opsec.LOGGER.debug("[OpSec] Translation key tracking: {} vanilla, {} server pack, {} total",
                ModRegistry.getVanillaKeyCount(),
                ModRegistry.getServerPackKeyCount(),
                ModRegistry.getTranslationKeyCount());
        }
    }
    
    



    @WrapOperation(
        method = "appendFrom",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"))
    private static void opsec$trackTranslationKeys(
            InputStream stream, 
            BiConsumer<String, String> output, 
            Operation<Void> original,
            @Local Resource resource) {
        
        PackResources pack = resource.source();
        
        
        if (pack instanceof VanillaPackResources) {
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordVanillaTranslationKey(key);
                output.accept(key, value);
            });
            return;
        }
        
        
        
        
        String modId = opsec$getModIdFromPack(pack);

        if (modId != null) {
            Set<String> modKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordTranslationKey(modId, key);
                modKeys.add(key);
                output.accept(key, value);
            });
            
            Opsec.LOGGER.debug("[OpSec] Tracked {} translation keys from mod '{}'", modKeys.size(), modId);
            return;
        }
        
        
        
        String serverPackId = pack.packId();
        if (opsec$isServerPackId(serverPackId)) {
            Set<String> serverKeys = new HashSet<>();
            original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                ModRegistry.recordServerPackTranslation(key, value);
                serverKeys.add(key);
                output.accept(key, value);
            });

            if (!serverKeys.isEmpty()) {
                Opsec.LOGGER.debug("[OpSec] Whitelisted {} server pack translation keys", serverKeys.size());
            }
            return;
        }
        
        
        if (pack instanceof PathPackResources) {
            
            String packId = pack.packId();
            if (packId != null && !packId.isEmpty() && !packId.equals("vanilla")
                    && !packId.startsWith("file/") && !opsec$isServerPackId(packId)) {
                
                String extractedModId = packId.replace("fabric/", "").replace("mod/", "");
                if (!extractedModId.isEmpty()) {
                    Set<String> modKeys = new HashSet<>();
                    final String finalModId = extractedModId;
                    original.call(stream, (BiConsumer<String, String>) (key, value) -> {
                        ModRegistry.recordTranslationKey(finalModId, key);
                        modKeys.add(key);
                        output.accept(key, value);
                    });
                    Opsec.LOGGER.debug("[OpSec] Tracked {} translation keys from pack '{}' (mod: {})", 
                        modKeys.size(), packId, extractedModId);
                    return;
                }
            }
            
            original.call(stream, output);
            return;
        }
        
        
        Opsec.LOGGER.debug("[OpSec] Unknown pack type: {} - passing through without tracking", 
            pack.getClass().getName());
        original.call(stream, output);
    }
    
    
    


    @Unique
    private static String opsec$getModIdFromPack(PackResources pack) {
        if (pack == null) return null;

        
        try {
            var method = pack.getClass().getMethod("getFabricModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {
            
        }

        
        try {
            var method = pack.getClass().getMethod("getModMetadata");
            var metadata = method.invoke(pack);
            if (metadata != null) {
                var getIdMethod = metadata.getClass().getMethod("getId");
                String id = (String) getIdMethod.invoke(metadata);
                if (id != null) return id;
            }
        } catch (Exception e) {
            
        }

        
        String packId = pack.packId();
        if (packId != null && !packId.isEmpty() && !packId.equals("vanilla")
                && !packId.startsWith("file/") && !opsec$isServerPackId(packId)) {
            if (FabricLoader.getInstance().getModContainer(packId).isPresent()) {
                return packId;
            }
        }

        
        return ModIdResolver.getModIdFromClass(pack.getClass());
    }

    
    @Unique
    private static boolean opsec$isServerPackId(String packId) {
        if (packId == null) return false;
        return PackStripHandler.LEGACY_SERVER_PACK_ID.equals(packId)
                || packId.startsWith(PackStripHandler.SERVER_PACK_PREFIX);
    }
}
