package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.ClientSpoofer;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;















@Mixin(PayloadTypeRegistryImpl.class)
public class PayloadTypeRegistryImplMixin {

    @Inject(method =
                "get(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$TypeAndCodec;"
                , at = @At("HEAD"), cancellable = true)
    private void opsec$gateInboundChannel(
                        Identifier id,
                        CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> cir) {
        
        
        
        
        
        
        
        if (!PacketContext.isDecodingPayload()) return;

        
        PayloadTypeRegistryImpl<?> self = (PayloadTypeRegistryImpl<?>) (Object) this;
                if (self.getSide() != PacketFlow.CLIENTBOUND) return;
        
        
        if (ClientSpoofer.shouldDropInboundChannel(id)) {
            cir.setReturnValue(null);
        }
    }
}
