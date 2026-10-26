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
import aurick.opsec.mod.util.KeybindDefaults;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;





















@Mixin(KeybindContents.class)
public class KeybindContentsMixin implements OpsecFromPacketAccess {

    @Shadow @Final
    private String name;

    @Unique
    private boolean opsec$fromPacket = false;

    @Unique
    private Object opsec$cachedBlocked = null;

    @Unique
    private boolean opsec$reported = false;

    @Override
    public void opsec$setFromPacket() {
        this.opsec$fromPacket = true;
    }

    




    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object opsec$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!this.opsec$fromPacket || Minecraft.getInstance().hasSingleplayerServer()) {
            return original.call(supplier);
        }
        if (opsec$cachedBlocked != null) {
            return opsec$cachedBlocked;
        }

        TranslationProtectionHandler.notifyExploitDetected();

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        if (ModRegistry.isWhitelistedKeybind(name)) {
            if (OpsecConfig.getInstance().isDebugAlerts() || OpsecConfig.getInstance().isLogDetections()) {
                String displayValue = opsec$readKeybindDisplay();
                TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, displayValue, displayValue);
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, displayValue, displayValue);
            }
            return original.call(supplier);
        }

        
        if (!config.isTranslationProtectionEnabled()) {
            Object originalResult = original.call(supplier);
            String originalValue = originalResult instanceof Component c ? c.getString()
                : originalResult != null ? originalResult.toString() : name;
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
            TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
            return originalResult;
        }

        
        if (KeybindDefaults.hasDefault(name)) {
            if (!settings.isFakeDefaultKeybinds()) {
                Object originalResult = original.call(supplier);
                String originalValue = originalResult instanceof Component c ? c.getString()
                    : originalResult != null ? originalResult.toString() : name;
                TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, name, originalValue, originalValue);
                TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, name, originalValue, originalValue);
                return originalResult;
            }
            String spoofedValue = KeybindDefaults.getDefault(name);
            opsec$logBlocked(name, spoofedValue);
            Component literal = Component.literal(spoofedValue);
            opsec$cachedBlocked = literal;
            return literal;
        }

        
        
        
        
        
        String realValue = opsec$readKeybindDisplay();
        String packValue = ModRegistry.getServerPackTranslation(name);
        String spoofed = packValue != null ? packValue : name;
        opsec$reportBlocked(name, realValue, spoofed);
        Component replacement = Component.translatable(name);
        if (replacement.getContents() instanceof OpsecFromPacketAccess access) {
            access.opsec$setFromPacket();
            access.opsec$setSilent();
        }
        opsec$cachedBlocked = replacement;
        return replacement;
    }

    @Unique
    private void opsec$logBlocked(String keybindName, String spoofedValue) {
        opsec$reportBlocked(keybindName, opsec$readKeybindDisplay(), spoofedValue);
    }

    @Unique
    private void opsec$reportBlocked(String keybindName, String realValue, String spoofedValue) {
        if (opsec$reported) return;
        opsec$reported = true;
        if (!realValue.equals(spoofedValue)) {
            TranslationProtectionHandler.sendDetail(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
        } else {
            TranslationProtectionHandler.sendDetailDebug(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
        }
        TranslationProtectionHandler.logDetection(InterceptionType.KEYBIND, keybindName, realValue, spoofedValue);
    }

    




    @Unique
    private String opsec$readKeybindDisplay() {
        try {
            Component display = KeyMapping.createNameSupplier(name).get();
            if (display != null) {
                return display.getString();
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to read keybind '{}': {}", name, e.getMessage());
        }
        return name;
    }
}
