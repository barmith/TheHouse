package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;















public class TranslationProtectionHandler {

    
    public enum InterceptionType {
        TRANSLATION("Translation"),
        KEYBIND("Keybind");

        private final String displayName;

        InterceptionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    
    private record AlertDedupeKey(InterceptionType type, String keyName) {}

    
    private record LogDedupeKey(
        InterceptionType type,
        String packetName,
        String keyName,
        String originalValue,
        String spoofedValue
    ) {}

    
    private static final Set<AlertDedupeKey> alertedKeys =
        ConcurrentHashMap.newKeySet();
    private static final Set<LogDedupeKey> loggedKeys =
        ConcurrentHashMap.newKeySet();

    
    private static final int MAX_DEDUPE_ENTRIES = 500;

    






    private static final int MAX_ALERT_VALUE_LEN = 256;

    private static volatile long lastHeaderTime = 0;
    private static volatile boolean headerPending = false;

    private static final long HEADER_COOLDOWN_MS = 5000; 

    private TranslationProtectionHandler() {}

    






    public static void notifyExploitDetected() {
        if (!shouldProcess()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastHeaderTime < HEADER_COOLDOWN_MS) {
            return;
        }

        
        headerPending = true;
    }

    



    private static void emitHeader() {
        String source = PacketContext.getPacketName();

        
        if (OpsecConfig.getInstance().shouldShowAlerts()) {
            Minecraft mc = Minecraft.getInstance();
            Runnable sendAlert = () -> {
                if (mc.player != null) {
                    String alertText = OpsecLang.tr(
                        OpsecStrings.ALERT_KEYRESOLUTION
                    );
                                        mc.player.displayClientMessage(
                        Component.literal("[OpSec] ")
                            .withStyle(ChatFormatting.DARK_PURPLE)
                            .append(
                                Component.literal(alertText).withStyle(
                                    ChatFormatting.RED
                                )
                            ),
                        false
                    );
                                    }
            };
            if (mc.isSameThread()) {
                sendAlert.run();
            } else {
                mc.execute(sendAlert);
            }
        }

        
        if (OpsecConfig.getInstance().shouldShowToasts()) {
            PrivacyLogger.showToastRaw(
                Component.literal(
                    OpsecLang.tr(OpsecStrings.TOAST_KEYRESOLUTION)
                ).withStyle(ChatFormatting.RED),
                null
            );
        }

        if (OpsecConfig.getInstance().isLogDetections()) {
            Opsec.logInfoAsync(
                "[OpSec] Key resolution exploit detected via {}",
                source
            );
        }

        
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isAlertHintShown()) {
            settings.setAlertHintShown(true);
            CompletableFuture.runAsync(() -> OpsecConfig.getInstance().save());
            CompletableFuture.delayedExecutor(
                2,
                java.util.concurrent.TimeUnit.SECONDS
            ).execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        String hintText = OpsecLang.tr(
                            OpsecStrings.HINT_ALERTS_CAN_BE_DISABLED
                        );
                                                mc.player.displayClientMessage(
                            Component.literal(hintText).withStyle(
                                ChatFormatting.AQUA
                            ),
                            false
                        );
                                            }
                });
            });
        }
    }

    










    public static void sendDetail(
        InterceptionType type,
        String keyName,
        String originalValue,
        String spoofedValue
    ) {
        if (!OpsecConfig.getInstance().shouldShowAlerts()) {
            return;
        }

        
        if (alertedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            alertedKeys.clear();
        }

        
        if (!alertedKeys.add(new AlertDedupeKey(type, keyName))) {
            return;
        }

        
        if (headerPending) {
            headerPending = false;
            lastHeaderTime = System.currentTimeMillis();
            emitHeader();
        }

        
        String detailText = "[" + truncateForAlert(keyName) + "] '"
            + truncateForAlert(originalValue) + "'→'"
            + truncateForAlert(spoofedValue) + "'";

        
        
        if (OpsecConfig.getInstance().isDebugAlerts()) {
            String packetName = PacketContext.getPacketName();
            MutableComponent detail = Component.literal(
                "[" + type.getDisplayName() + ":" + packetName + "] "
            )
                .withStyle(ChatFormatting.DARK_PURPLE)
                .append(
                    Component.literal(detailText).withStyle(ChatFormatting.DARK_GRAY)
                );
            PrivacyLogger.sendKeybindDetail(detail);
        } else {
            PrivacyLogger.sendKeybindDetail(detailText);
        }
    }

    









    public static void sendDetailDebug(
        InterceptionType type,
        String keyName,
        String originalValue,
        String spoofedValue
    ) {
        if (!OpsecConfig.getInstance().isDebugAlerts()) return;
        sendDetail(type, keyName, originalValue, spoofedValue);
    }

    








    public static void logDetection(
        InterceptionType type,
        String keyName,
        String originalValue,
        String spoofedValue
    ) {
        if (!OpsecConfig.getInstance().isLogDetections()) {
            return;
        }

        String packetName = PacketContext.getPacketName();

        
        if (loggedKeys.size() >= MAX_DEDUPE_ENTRIES) {
            loggedKeys.clear();
        }

        
        if (
            !loggedKeys.add(
                new LogDedupeKey(
                    type,
                    packetName,
                    keyName,
                    originalValue,
                    spoofedValue
                )
            )
        ) {
            return;
        }

        Opsec.logInfoAsync(
            "[{}:{}] '{}' '{}' -> '{}'",
            type.getDisplayName(),
            packetName,
            keyName,
            originalValue,
            spoofedValue
        );
    }

    





    private static String truncateForAlert(String value) {
        if (value == null || value.length() <= MAX_ALERT_VALUE_LEN) {
            return value;
        }
        return value.substring(0, MAX_ALERT_VALUE_LEN) + "…(" + value.length() + " chars)";
    }

    



    private static boolean shouldProcess() {
        return (
            OpsecConfig.getInstance().shouldShowAlerts() ||
            OpsecConfig.getInstance().isLogDetections()
        );
    }

    




    public static void clearDedup() {
        alertedKeys.clear();
        loggedKeys.clear();
        headerPending = false;
    }

    


    public static void clearCache() {
        alertedKeys.clear();
        loggedKeys.clear();
        lastHeaderTime = 0;
        headerPending = false;
    }
}
