package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.UUID;













@Mixin(DownloadQueue.class)
public class DownloadQueueMixin {
    @Shadow
    @Final
    private Path cacheDir;

    @SuppressWarnings("UnresolvedMixinReference")
    @ModifyExpressionValue(
        method = "method_55485",
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"),
        require = 0)
    private Path opsec$isolatePackPath(Path original, @Local(argsOnly = true) UUID packId) {
        if (!OpsecConfig.getInstance().shouldIsolatePackCache()) {
            return original;
        }

        
        if (!original.getParent().equals(cacheDir)) {
            return original;
        }

        UUID accountId = Minecraft.getInstance().getUser().getProfileId();
        if (accountId == null) {
            Opsec.LOGGER.warn("[OpSec] Failed to isolate resource pack cache - account UUID is null");
            return original;
        }

        Path isolatedPath = cacheDir.resolve(accountId.toString()).resolve(packId.toString());
        Opsec.LOGGER.debug("[OpSec] Isolating resource pack cache for account {} -> {}", accountId, isolatedPath);
        return isolatedPath;
    }
}
