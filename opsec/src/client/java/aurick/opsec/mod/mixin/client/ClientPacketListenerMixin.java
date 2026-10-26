package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.protection.PackStripHandler;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;











@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Unique
    private static volatile ScheduledExecutorService opsec$scheduler;

    @Unique
    private static volatile ScheduledFuture<?> opsec$pendingTask;

    @Unique
    private static synchronized ScheduledExecutorService opsec$getScheduler() {
        if (opsec$scheduler == null || opsec$scheduler.isShutdown()) {
            opsec$scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSec-Scheduler");
            t.setDaemon(true);
            return t;
        });
        }
        return opsec$scheduler;
    }

    @Unique
    private static synchronized void opsec$shutdownScheduler() {
        if (opsec$pendingTask != null) {
            opsec$pendingTask.cancel(false);
            opsec$pendingTask = null;
        }
        if (opsec$scheduler != null && !opsec$scheduler.isShutdown()) {
            opsec$scheduler.shutdownNow();
            opsec$scheduler = null;
        }
    }

    




    @WrapOperation(
        method = "handleBundlePacket",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void opsec$wrapBundleSubPacketHandle(
            Packet<T> instance, T listener, Operation<Void> original) {
        TranslationProtectionHandler.clearDedup();
        PacketContext.setPacketName(instance);
        original.call(instance, listener);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener)(Object)this;
        String serverAddress = listener.getConnection().getRemoteAddress().toString();

        if (serverAddress.startsWith("/")) {
            serverAddress = serverAddress.substring(1);
        }
        int colonIndex = serverAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            serverAddress = serverAddress.substring(0, colonIndex);
        }

        OpsecConfig.getInstance().setCurrentServer(serverAddress);

        
        opsec$pendingTask = opsec$getScheduler().schedule(() -> {
            Minecraft.getInstance().execute(PrivacyLogger::showPortScanSummary);
        }, 2, TimeUnit.SECONDS);
    }

    
    



    @WrapOperation(
            method = "sendChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/SignedMessageChain$Encoder;pack(Lnet/minecraft/network/chat/SignedMessageBody;)Lnet/minecraft/network/chat/MessageSignature;")
    )
    private MessageSignature opsec$skipSigningEncoder(SignedMessageChain.Encoder encoder, SignedMessageBody body, Operation<MessageSignature> original) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] Skipping message signing (chain preserved)");
            return null;
        }
        return original.call(encoder, body);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        opsec$shutdownScheduler();
        PrivacyLogger.resetPortScanTracking();
        PrivacyLogger.clearCooldowns();
        OpsecConfig.getInstance().setCurrentServer(null);
        PackStripHandler.clearAll();
        PackStripOverlay.clearQueue();
    }
}
