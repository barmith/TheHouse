package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.RenderModules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class RenderEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void thehouse$range(Entity entity, Frustum frustum, double x, double y, double z,
                                CallbackInfoReturnable<Boolean> cir) {
        if ((RenderModules.outlined(entity) || RenderModules.named(entity)) && frustum.isVisible(entity.getBoundingBox()))
            cir.setReturnValue(true);
    }

    
    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private void thehouse$decorate(Entity entity, float delta, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (RenderModules.outlined(entity)) state.outlineColor = RenderModules.outlineColor();
        if (!RenderModules.named(entity)) return;
        PlayerEntity player = (PlayerEntity) entity;
        MutableText label = player.getDisplayName().copy();
        if (RenderModules.flag("nameHealth")) label.append(Text.literal("  " +
                Math.round(player.getHealth() + player.getAbsorptionAmount()) + " HP").formatted(Formatting.GREEN));
        if (RenderModules.flag("nameDistance")) {
            double distance = Math.sqrt(entity.squaredDistanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos()));
            label.append(Text.literal("  " + Math.round(distance) + "m").formatted(Formatting.GRAY));
        }
        state.displayName = label;
        state.nameLabelPos = new Vec3d(0, entity.getHeight() + 0.5, 0);
        state.sneaking = false;
    }
}
