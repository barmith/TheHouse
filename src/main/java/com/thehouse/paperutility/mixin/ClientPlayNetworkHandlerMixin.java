package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.ArrowTracker;
import com.thehouse.paperutility.PaperSimulationTracker;
import com.thehouse.paperutility.BlackjackTracker;
import com.thehouse.paperutility.FiftyFiftyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void thehouse$observeSpawnedItem(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        thehouse$observe(packet.getEntityId());
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("HEAD"))
    private void thehouse$snapshotArrowFrames(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.isOnThread() && client.world!=null
                && client.world.getEntityById(packet.id()) instanceof ItemFrameEntity)
            ArrowTracker.observeWorldUpdate(client);
    }

    @Inject(method = "onEntityTrackerUpdate", at = @At("TAIL"))
    private void thehouse$observeTrackedItem(EntityTrackerUpdateS2CPacket packet, CallbackInfo ci) {
        thehouse$observe(packet.id());
    }

    private static void thehouse$observe(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        Entity entity = client.world.getEntityById(entityId);
        if(entity instanceof ArrowEntity || entity instanceof ItemFrameEntity || entity instanceof ItemEntity)
            ArrowTracker.observeWorldUpdate(client);
        if (entity instanceof ItemEntity itemEntity) {
            PaperSimulationTracker.observeEntityImmediately(client, itemEntity);
            FiftyFiftyTracker.observeItemImmediately(client, itemEntity);
            BlackjackTracker.observeItemImmediately(client, itemEntity);
        }
    }
}
