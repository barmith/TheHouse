package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.tracking.ModRegistry;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;

import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;






















@Mixin(value = ResourceLoaderImpl.class, remap = false)

public class ResourceLoaderBuiltinPackMixin {

    
    
    
    
    
    
    
    
    
        @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "registerBuiltinPack", at = @At("RETURN"))
    private static void opsec$onRegisterBuiltinPack(
            Identifier id, String subPath, ModContainer container,
            Component displayName, PackActivationType activationType,
            CallbackInfoReturnable<Boolean> cir,
            @Local(name = "dataPack") ModNioPackResources dataPack) {
        
        
        if (dataPack == null) return;
        ModRegistry.recordBuiltinPack(container.getMetadata().getId(), id.toString());
    }
    }
