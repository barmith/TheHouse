package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.tracking.ModIdResolver;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;





@Mixin(value = KeyBindingRegistryImpl.class, remap = false)
public class KeyBindingRegistryImplMixin {
    
    @Unique
    private static boolean opsec$loggedOnce = false;
    
    


        @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "registerKeyBinding", at = @At("RETURN"))
        private static void opsec$onKeybindRegister(KeyMapping keyBinding, CallbackInfoReturnable<KeyMapping> cir) {
        if (keyBinding == null) return;
        
        
        String modId = ModIdResolver.getModIdFromStacktrace();
        
        if (modId != null) {
            ModRegistry.recordKeybind(modId, keyBinding.getName());
            
            if (!opsec$loggedOnce) {
                opsec$loggedOnce = true;
                Opsec.LOGGER.debug("[OpSec] Mod keybind tracking active");
            }
        } else {
            Opsec.LOGGER.debug("[OpSec] Could not determine mod for keybind: {}", keyBinding.getName());
        }
    }
}

