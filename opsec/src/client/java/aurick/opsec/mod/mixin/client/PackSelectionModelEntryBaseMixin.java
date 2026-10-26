package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;













@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class PackSelectionModelEntryBaseMixin {

    @Shadow @Final private Pack pack;

    private boolean opsec$shouldUnlock() {
        return pack.getPackSource() == PackSource.SERVER
            && OpsecConfig.getInstance().shouldStripPack();
    }

    @Inject(method = "isFixedPosition", at = @At("HEAD"), cancellable = true)
    private void opsec$unlockFixedPosition(CallbackInfoReturnable<Boolean> cir) {
        if (opsec$shouldUnlock()) cir.setReturnValue(false);
    }

    @Inject(method = "isRequired", at = @At("HEAD"), cancellable = true)
    private void opsec$unlockRequired(CallbackInfoReturnable<Boolean> cir) {
        if (opsec$shouldUnlock()) cir.setReturnValue(false);
    }
}
