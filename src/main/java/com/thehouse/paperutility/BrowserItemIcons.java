package com.thehouse.paperutility;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;


public final class BrowserItemIcons {
    private static final Logger LOG = Logger.getLogger("TheHouseItemIcons");
    private static final ConcurrentHashMap<Identifier, CompletableFuture<byte[]>> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Identifier> QUEUE = new ConcurrentLinkedQueue<>();
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TheHouse-IconEncoder");
        thread.setDaemon(true);
        return thread;
    });
    private static GuiRenderState state;
    private static GuiRenderer renderer;
    private static Framebuffer framebuffer;
    private static boolean rendering;

    private BrowserItemIcons() {}

    public static byte[] get(String rawId) {
        try {
            Identifier id = Identifier.of(rawId);
            if (!Registries.ITEM.containsId(id)) return null;
            CompletableFuture<byte[]> created = new CompletableFuture<>();
            CompletableFuture<byte[]> future = CACHE.putIfAbsent(id, created);
            if (future == null) {
                future = created;
                QUEUE.add(id);
            }
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception error) { return null; }
    }

    public static boolean owns(Object candidate) { return candidate == renderer; }
    public static Framebuffer target() { return framebuffer; }

    
    public static void pump(GpuBufferSlice fog) {
        if (rendering || QUEUE.isEmpty()) return;
        Identifier id = QUEUE.poll();
        if (id == null) return;
        CompletableFuture<byte[]> result = CACHE.get(id);
        if (result == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        rendering = true;
        try {
            if (renderer == null) {
                state = new GuiRenderState();
                renderer = new GuiRenderer(state, client.getBufferBuilders().getEntityVertexConsumers(),
                        client.gameRenderer.getEntityRenderCommandQueue(),
                        client.gameRenderer.getEntityRenderDispatcher(), List.of());
                framebuffer = new SimpleFramebuffer("TheHouse item icon", 32, 32, true);
            }
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);
            DrawContext context = new DrawContext(client, state, 0, 0);
            context.drawItem(Registries.ITEM.get(id).getDefaultStack(), 0, 0);
            renderer.incrementFrame();
            renderer.render(fog);
            
            
            ScreenshotRecorder.takeScreenshot(framebuffer, image -> {
                try (image) {
                    int[] pixels = image.copyPixelsArgb();
                    int width = image.getWidth(), height = image.getHeight();
                    ENCODER.execute(() -> {
                        try {
                            BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            png.setRGB(0, 0, width, height, pixels, 0, width);
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            ImageIO.write(png, "png", bytes);
                            result.complete(bytes.toByteArray());
                        } catch (Exception error) { fail(id, result, error); }
                    });
                } catch (Exception error) { fail(id, result, error); }
            });
        } catch (Exception error) {
            if (state != null) state.clear();
            fail(id, result, error);
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            rendering = false;
        }
    }

    private static void fail(Identifier id, CompletableFuture<byte[]> result, Exception error) {
        LOG.warning("Item icon failed for " + id + ": " + error.getClass().getSimpleName());
        result.completeExceptionally(error);
        CACHE.remove(id, result);
    }

    public static void clear() {
        CACHE.values().forEach(future -> future.cancel(false));
        CACHE.clear();
        QUEUE.clear();
        if (renderer != null) renderer.close();
        if (framebuffer != null) framebuffer.delete();
        renderer = null;
        framebuffer = null;
        state = null;
    }
}
