package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.protection.ClientSpoofer;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientConfigurationNetworkAddon;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;












@Mixin(AbstractChanneledNetworkAddon.class)
public class AbstractChanneledNetworkAddonMixin {

    @Inject(method = "handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Z",
            at = @At("HEAD"), cancellable = true)
    private void opsec$dropInboundModChannel(CustomPacketPayload payload, CallbackInfoReturnable<Boolean> cir) {
        
        Object self = this;
        if (!(self instanceof ClientConfigurationNetworkAddon) && !(self instanceof ClientPlayNetworkAddon)) {
            return;
        }

                Identifier id = payload.type().id();
        
        
        if (ClientSpoofer.shouldDropInboundChannel(id)) {
            cir.setReturnValue(false);
        }
    }
}
