package aurick.opsec.mod.protection;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public final class PackStripHandler {
    private PackStripHandler() {}

    private static final Set<UUID> loadForReal = ConcurrentHashMap.newKeySet();
    
    private static final Set<UUID> requiredPacks = ConcurrentHashMap.newKeySet();
    
    
    private static final Set<UUID> wrappedPacks = ConcurrentHashMap.newKeySet();
    
    
    
    private static final Set<UUID> userUnselectedOptional = ConcurrentHashMap.newKeySet();
    
    private static final AtomicBoolean overlayShownThisSession = new AtomicBoolean(false);

    public static void onPackPush(UUID id, String url, boolean required) {
        OpsecConfig config = OpsecConfig.getInstance();
        boolean stripPack = config.shouldStripPack();          
        boolean stripShaders = config.shouldStripModShaders();  

        if (!stripPack && !stripShaders) return; 

        SpoofSettings.StripMode mode = config.getPackStripMode();

        
        
        if (!stripPack || mode == SpoofSettings.StripMode.MANUAL) {
            loadForReal.add(id);
        } else {
            loadForReal.remove(id);
        }

        if (required) {
            requiredPacks.add(id);
        } else {
            requiredPacks.remove(id);
        }

        
        
        boolean shouldWrap = required || mode != SpoofSettings.StripMode.MANUAL || stripShaders;
        if (shouldWrap) {
            wrappedPacks.add(id);
        } else {
            wrappedPacks.remove(id);
        }

        
        
        userUnselectedOptional.remove(id);

        
        if (stripPack && required && mode == SpoofSettings.StripMode.ASK
                && overlayShownThisSession.compareAndSet(false, true)) {
            if (!isHttpUrl(url)) return; 
            PackStripOverlay.enqueue(id, required);
        }
    }

    private static boolean isHttpUrl(String url) {
        try {
                        Util.parseAndValidateUntrustedUri(url);
                        return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void onPop(Optional<UUID> maybeId) {
        if (maybeId == null || maybeId.isEmpty()) {
            loadForReal.clear();
            requiredPacks.clear();
            wrappedPacks.clear();
            userUnselectedOptional.clear();
            ShaderStripTracker.clear();
            return;
        }
        UUID id = maybeId.get();
        loadForReal.remove(id);
        requiredPacks.remove(id);
        wrappedPacks.remove(id);
        userUnselectedOptional.remove(id);
        
        ShaderStripTracker.clear();
    }

    public static void clearAll() {
        loadForReal.clear();
        requiredPacks.clear();
        wrappedPacks.clear();
        userUnselectedOptional.clear();
        overlayShownThisSession.set(false);
        ShaderStripTracker.clear();
    }

    public static void markLoadForReal(UUID id) {
        if (id != null) loadForReal.add(id);
    }

    public static void clearLoadForReal(UUID id) {
        if (id != null) loadForReal.remove(id);
    }

    public static boolean isLoadForReal(UUID id) {
        return id != null && loadForReal.contains(id);
    }

    public static boolean isRequired(UUID id) {
        return id != null && requiredPacks.contains(id);
    }

    
    public static boolean isWrapped(UUID id) {
        return id != null && wrappedPacks.contains(id);
    }

    public static void markUserUnselectedOptional(UUID id) {
        if (id != null) userUnselectedOptional.add(id);
    }

    public static void clearUserUnselectedOptional(UUID id) {
        if (id != null) userUnselectedOptional.remove(id);
    }

    public static boolean isUserUnselectedOptional(UUID id) {
        return id != null && userUnselectedOptional.contains(id);
    }

    
    
    public static final String SERVER_PACK_PREFIX = "server/";
    public static final String LEGACY_SERVER_PACK_ID = "server";
    public static final UUID LEGACY_SERVER_PACK_UUID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    
    public static Optional<UUID> packIdToUuid(String packId) {
        if (packId == null) return Optional.empty();
        if (LEGACY_SERVER_PACK_ID.equals(packId)) return Optional.of(LEGACY_SERVER_PACK_UUID);
        if (!packId.startsWith(SERVER_PACK_PREFIX)) return Optional.empty();
        int lastSlash = packId.lastIndexOf('/');
        if (lastSlash < SERVER_PACK_PREFIX.length() - 1) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(packId.substring(lastSlash + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
