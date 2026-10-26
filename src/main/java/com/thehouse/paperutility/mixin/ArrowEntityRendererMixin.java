package com.thehouse.paperutility.mixin;

import com.thehouse.paperutility.ArrowTracker;
import net.minecraft.client.render.entity.ArrowEntityRenderer;
import net.minecraft.client.render.entity.state.ArrowEntityRenderState;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ArrowEntityRenderer.class)
public abstract class ArrowEntityRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/projectile/ArrowEntity;Lnet/minecraft/client/render/entity/state/ArrowEntityRenderState;F)V", at=@At("TAIL"))
    private void thehouse$arrowVisual(ArrowEntity arrow, ArrowEntityRenderState state, float tickDelta, CallbackInfo ci) {
        
        
        
        
        if(ArrowTracker.shouldHideTerminalMaskedArrow(arrow) || ArrowTracker.shouldSuppressImpactedArrow(arrow)) {
            state.y = -4096.0;
            return;
        }
        Vec3d real=new Vec3d(state.x,state.y,state.z);
        Vec3d synthetic=ArrowTracker.syntheticRenderPosition(arrow,real);
        if(synthetic==null)return;
        
        
        state.x=synthetic.x;state.y=synthetic.y;state.z=synthetic.z;
    }
}
