package com.thehouse.paperutility.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;


public final class FreecamInteraction {
    private FreecamInteraction() {}

    public static void updateTarget(MinecraftClient client, float tickProgress) {
        if (!FreeCamera.active()) return;
        BlockHitResult target = target(client, tickProgress);
        client.crosshairTarget = target != null ? target : miss(tickProgress);
        client.targetedEntity = null;
    }

    public static boolean prepare(MinecraftClient client) {
        
        BlockHitResult target = target(client, 1.0f);
        client.crosshairTarget = target != null ? target : miss(1.0f);
        client.targetedEntity = null;
        return target != null;
    }

    private static BlockHitResult target(MinecraftClient client, float tickProgress) {
        if (!FreeCamera.active() || !RenderModules.flag("freecamInteract")
                || client.interactionManager == null || client.currentScreen != null
                || client.getCameraEntity() != client.player || client.player.isSpectator()
                || client.player.isRiding() || client.player.isSleeping()) return null;

        double reach = client.player.getBlockInteractionRange();
        if (!Double.isFinite(reach) || reach <= 0.0) return null;
        Vec3d from = FreeCamera.position(1.0f);
        Vec3d direction = Vec3d.fromPolar(FreeCamera.pitch(), FreeCamera.yaw());
        
        
        
        double cameraRayLength = Math.max(reach, 128.0);
        BlockHitResult cameraHit = client.world.raycast(new RaycastContext(
                from, from.add(direction.multiply(cameraRayLength)), RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, client.player));
        if (cameraHit == null || cameraHit.getType() != HitResult.Type.BLOCK
                || cameraHit.isInsideBlock() || cameraHit.isAgainstWorldBorder())
            return null;
        
        
        
        BlockPos pos = cameraHit.getBlockPos();
        Vec3d eye = client.player.getEyePos();
        double nearestX = MathHelper.clamp(eye.x, pos.getX(), pos.getX() + 1.0);
        double nearestY = MathHelper.clamp(eye.y, pos.getY(), pos.getY() + 1.0);
        double nearestZ = MathHelper.clamp(eye.z, pos.getZ(), pos.getZ() + 1.0);
        return new Vec3d(nearestX, nearestY, nearestZ).squaredDistanceTo(eye) <= reach * reach
                ? cameraHit : null;
    }

    static boolean matches(BlockHitResult playerHit, BlockHitResult cameraHit, Vec3d playerEye, double reach) {
        return playerHit != null && cameraHit != null && Double.isFinite(reach) && reach > 0
                && playerHit.getType() == HitResult.Type.BLOCK && cameraHit.getType() == HitResult.Type.BLOCK
                && !playerHit.isInsideBlock() && !cameraHit.isInsideBlock()
                && !playerHit.isAgainstWorldBorder() && !cameraHit.isAgainstWorldBorder()
                && playerHit.getPos().squaredDistanceTo(playerEye) < reach * reach
                && playerHit.getBlockPos().equals(cameraHit.getBlockPos())
                && playerHit.getSide() == cameraHit.getSide();
    }

    private static BlockHitResult miss(float tickProgress) {
        Vec3d from = FreeCamera.position(tickProgress);
        return BlockHitResult.createMissed(from, Direction.UP, BlockPos.ofFloored(from));
    }
}
