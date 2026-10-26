package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.UUID;














@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @WrapOperation(
        method = "rebuildSelected",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/Pack;isRequired()Z"))
    private boolean opsec$allowUnselectOptionalServerPack(Pack pack, Operation<Boolean> original) {
        boolean vanilla = original.call(pack);
        if (!vanilla) return false;
        if (pack.getPackSource() != PackSource.SERVER) return true;
        if (!OpsecConfig.getInstance().shouldStripPack()) return true;
        Optional<UUID> id = PackStripHandler.packIdToUuid(pack.getId());
        if (id.isEmpty()) return true;
        
        
        
        
        if (PackStripHandler.isUserUnselectedOptional(id.get())) return false;
        return true;
    }
}
