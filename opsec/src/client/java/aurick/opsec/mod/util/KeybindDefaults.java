package aurick.opsec.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;












public final class KeybindDefaults {

    private KeybindDefaults() {}

    private static final Map<String, InputConstants.Key> defaultKeys = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    




    public static void initialize() {
        if (initialized) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;

            KeyMapping[] keyMappings = mc.options.keyMappings;
            if (keyMappings == null) return;

            int vanillaCount = 0;
            for (KeyMapping mapping : keyMappings) {
                if (mapping == null) continue;

                String keyName = mapping.getName();

                
                
                if (ModRegistry.isVanillaTranslationKey(keyName)) {
                    InputConstants.Key defaultKey = mapping.getDefaultKey();
                    if (defaultKey != null) {
                        defaultKeys.put(keyName, defaultKey);
                        vanillaCount++;
                    }
                }
            }

            initialized = true;
            Opsec.LOGGER.debug("[OpSec] Loaded {} vanilla keybind defaults from translation whitelist", vanillaCount);

        } catch (RuntimeException e) {
            Opsec.LOGGER.error("[OpSec] Failed to initialize keybind defaults: {}", e.getMessage());
        }
    }

    




    public static String getDefault(String key) {
        if (!initialized) {
            initialize();
        }
        InputConstants.Key defaultKey = defaultKeys.get(key);
        return defaultKey == null ? null : defaultKey.getDisplayName().getString();
    }

    


    public static boolean hasDefault(String key) {
        if (!initialized) {
            initialize();
        }
        return defaultKeys.containsKey(key);
    }

    


    public static boolean isVanillaKeybind(String keyName) {
        return hasDefault(keyName);
    }

    public static int size() {
        if (!initialized) initialize();
        return defaultKeys.size();
    }

    







    public static synchronized void reset() {
        defaultKeys.clear();
        initialized = false;
        Opsec.LOGGER.debug("[OpSec] KeybindDefaults cache reset");
    }

}
