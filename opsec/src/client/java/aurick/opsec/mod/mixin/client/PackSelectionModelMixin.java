package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.protection.PackStripHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;























@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {

    @Shadow @Final private List<Pack> selected;
    @Shadow @Final private List<Pack> unselected;

    @Unique
    private boolean opsec$stripStateChanged;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void opsec$rearrangeOnOpen(CallbackInfo ci) {
        if (!OpsecConfig.getInstance().shouldStripPack()) return;

        List<Pack> toMove = new ArrayList<>();
        Iterator<Pack> it = selected.iterator();
        while (it.hasNext()) {
            Pack pack = it.next();
            if (pack.getPackSource() != PackSource.SERVER) continue;
            Optional<UUID> maybeId = PackStripHandler.packIdToUuid(pack.getId());
            if (maybeId.isEmpty()) continue;
            UUID id = maybeId.get();
            
            if (!PackStripHandler.isWrapped(id)) continue;
            
            if (PackStripHandler.isLoadForReal(id)) continue;
            toMove.add(pack);
            it.remove();
        }
        for (Pack pack : toMove) {
            unselected.add(pack);
        }
    }

    @Inject(method = "commit", at = @At("HEAD"))
    private void opsec$applyStripState(CallbackInfo ci) {
        opsec$stripStateChanged = false;
        if (!OpsecConfig.getInstance().shouldStripPack()) return;

        for (Pack pack : selected) opsec$applyPackState(pack, true);
        for (Pack pack : unselected) opsec$applyPackState(pack, false);
    }

    
    
    
    @Inject(method = "commit", at = @At("RETURN"))
    private void opsec$reloadOnStripChange(CallbackInfo ci) {
        if (!opsec$stripStateChanged) return;
        Minecraft.getInstance().reloadResourcePacks();
    }

    private void opsec$applyPackState(Pack pack, boolean visuallySelected) {
        if (pack.getPackSource() != PackSource.SERVER) return;
        Optional<UUID> maybeId = PackStripHandler.packIdToUuid(pack.getId());
        if (maybeId.isEmpty()) return;
        UUID id = maybeId.get();

        if (PackStripHandler.isWrapped(id)) {
            
            
            
            boolean prev = PackStripHandler.isLoadForReal(id);
            if (prev == visuallySelected) return;
            if (visuallySelected) PackStripHandler.markLoadForReal(id);
            else PackStripHandler.clearLoadForReal(id);
            opsec$stripStateChanged = true;
        } else {
            
            
            boolean wasOptOut = PackStripHandler.isUserUnselectedOptional(id);
            boolean shouldOptOut = !visuallySelected;
            if (wasOptOut == shouldOptOut) return;
            if (shouldOptOut) PackStripHandler.markUserUnselectedOptional(id);
            else PackStripHandler.clearUserUnselectedOptional(id);
            opsec$stripStateChanged = true;
        }
    }
}
