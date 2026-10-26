package com.thehouse.paperutility.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


public final class FreeCamera {
    private static ClientWorld world;
    private static net.minecraft.client.network.ClientPlayerEntity owner;
    private static Vec3d previous = Vec3d.ZERO, position = Vec3d.ZERO;
    private static float yaw, pitch;
    private static boolean cullingOverridden, previousCulling;
    private FreeCamera() {}

    public static void start(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        world = client.world;
        owner = client.player;
        position = previous = client.gameRenderer.getCamera().getCameraPos();
        yaw = client.gameRenderer.getCamera().getYaw();
        pitch = client.gameRenderer.getCamera().getPitch();
        if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
        updateCulling();
    }
    public static void stop() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean sameWorld = world != null && world == client.world;
        world = null;
        owner = null;
        updateCulling();
        if (sameWorld) {
            if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            client.gameRenderer.updateCrosshairTarget(1.0f);
        }
    }
    public static void updateCulling() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        boolean changed = false;
        if (active()) {
            if (!cullingOverridden) {
                previousCulling = client.chunkCullingEnabled;
                cullingOverridden = true;
                changed = true;
            }
            if (client.chunkCullingEnabled) {
                client.chunkCullingEnabled = false;
                changed = true;
            }
        } else if (cullingOverridden) {
            client.chunkCullingEnabled = previousCulling;
            cullingOverridden = false;
            changed = true;
        }
        
        if (changed && client.world != null) client.worldRenderer.scheduleTerrainUpdate();
    }
    public static boolean active() {
        MinecraftClient client = MinecraftClient.getInstance();
        return world != null && world == client.world && client.player != null && client.player == owner && client.player.isAlive() && RenderModules.enabled("freecam");
    }
    public static void look(double x, double y) {
        yaw += (float) (x * 0.15);
        pitch = MathHelper.clamp(pitch + (float) (y * 0.15), -90, 90);
    }
    public static void move(PlayerInput input) {
        previous = position;
        if (MinecraftClient.getInstance().currentScreen != null) return;
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double sideways = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        double vertical = (input.jump() ? 1 : 0) - (input.sneak() ? 1 : 0);
        double speed = Math.max(0.0, RenderModules.number("freecamSpeed"));
        double horizontal = speed * (input.sprint() ? 1.0 : 0.5);
        double verticalSpeed = speed * (input.sprint() ? 1.0 : 0.5);
        double angle = Math.toRadians(yaw);
        double dx = sideways * Math.cos(angle) - forward * Math.sin(angle);
        double dz = forward * Math.cos(angle) + sideways * Math.sin(angle);
        if (forward != 0.0 && sideways != 0.0) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            dx *= diagonal;
            dz *= diagonal;
        }
        if (dx != 0.0 || dz != 0.0 || vertical != 0.0)
            position = position.add(dx * horizontal, vertical * verticalSpeed, dz * horizontal);
    }
    public static Vec3d position(float tickProgress) { return previous.lerp(position, tickProgress); }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
}
