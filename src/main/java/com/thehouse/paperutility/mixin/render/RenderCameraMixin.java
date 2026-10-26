package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.FreeCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class RenderCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void thehouse$camera(World world, Entity focus, boolean thirdPerson, boolean inverse, float delta, CallbackInfo ci) {
        if (!FreeCamera.active()) return;
        setPos(FreeCamera.position(delta));
        setRotation(FreeCamera.yaw(), FreeCamera.pitch());
        this.thirdPerson = true;
    }
}
