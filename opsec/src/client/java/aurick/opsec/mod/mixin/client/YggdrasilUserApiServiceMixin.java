package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;










@Mixin(value = YggdrasilUserApiService.class, remap = false)
public class YggdrasilUserApiServiceMixin {
    
    


    @Inject(method = "newTelemetrySession", at = @At("HEAD"), cancellable = true)
    private void opsec$disableTelemetrySession(Executor executor, CallbackInfoReturnable<TelemetrySession> info) {
        if (OpsecConfig.getInstance().shouldDisableTelemetry()) {
            Opsec.LOGGER.debug("[OpSec] Returning disabled TelemetrySession");
            info.setReturnValue(TelemetrySession.DISABLED);
        }
    }
}

