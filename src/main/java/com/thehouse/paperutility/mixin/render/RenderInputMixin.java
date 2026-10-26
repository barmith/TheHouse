package com.thehouse.paperutility.mixin.render;

import com.thehouse.paperutility.render.FreeCamera;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class RenderInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void thehouse$input(CallbackInfo ci) {
        if (!FreeCamera.active()) return;
        FreeCamera.move(playerInput);
        playerInput = PlayerInput.DEFAULT;
        movementVector = Vec2f.ZERO;
    }
}
