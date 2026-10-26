package com.thehouse.paperutility.render;

import com.google.gson.JsonObject;
import com.thehouse.paperutility.ModConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;


public final class RenderModules {
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        for (String module : new String[]{"freecam", "fullbright", "norender", "esp", "zoom", "nametags"})
            DEFAULTS.put(module, "false");
        DEFAULTS.put("freecamSpeed", "0.5");
        DEFAULTS.put("freecamInteract", "false");
        DEFAULTS.put("zoomFactor", "4.0");
        DEFAULTS.put("zoomSmooth", "true");
        DEFAULTS.put("espPlayers", "true");
        DEFAULTS.put("espMobs", "false");
        DEFAULTS.put("espItems", "false");
        DEFAULTS.put("espRange", "128.0");
        DEFAULTS.put("espColor", "#55ccff");
        DEFAULTS.put("nameHealth", "true");
        DEFAULTS.put("nameDistance", "true");
        DEFAULTS.put("nameRange", "96.0");
        DEFAULTS.put("noFire", "true");
        DEFAULTS.put("noWater", "true");
        DEFAULTS.put("noWall", "true");
        DEFAULTS.put("noPortal", "true");
        DEFAULTS.put("noVignette", "true");
        DEFAULTS.put("noWeather", "true");
        DEFAULTS.put("noBossbar", "false");
        DEFAULTS.put("noScoreboard", "false");
    }
    private static volatile Map<String, String> settings = Map.copyOf(DEFAULTS);
    private static final Map<String, KeyBinding> keys = new LinkedHashMap<>();
    private static KeyBinding zoomKey;
    private static volatile boolean zoomHeld;
    private static double currentZoom = 1;
    private static long lastFrame;
    private static boolean zoomScrollDirty;
    private static long lastZoomScroll;
    private static int espColor = 0xff55ccff;

    private RenderModules() {}

    public static boolean enabled(String module) {
        return flag(module);
    }
    public static boolean flag(String key) { return Boolean.parseBoolean(settings.get(key)); }
    public static double number(String key) { return Double.parseDouble(settings.get(key)); }
    public static boolean omit(String option) { return enabled("norender") && flag(option); }
    public static boolean bright() { return enabled("fullbright"); }
    public static int outlineColor() { return espColor; }
    public static boolean zooming() { return flag("zoom") || zoomHeld; }
    public static double zoomScale() { return currentZoom; }

    
    public static boolean scrollZoom(double amount) {
        if (!zooming()) return false;
        if (Double.isFinite(amount) && amount != 0) {
            double next = Math.max(1.0, Math.min(20.0, number("zoomFactor") + amount * 0.5));
            if (next != number("zoomFactor")) {
                putValidated("zoomFactor", Double.toString(next));
                zoomScrollDirty = true;
                lastZoomScroll = System.nanoTime();
            }
        }
        return true;
    }

    public static void load(Properties properties) {
        settings = Map.copyOf(DEFAULTS);
        espColor = 0xff55ccff;
        for (String key : DEFAULTS.keySet()) {
            
            if (key.equals("freecam") || key.equals("zoom")) continue;
            String value = properties.getProperty("render." + key);
            if (value != null) putValidated(key, value);
        }
    }
    public static void store(Properties properties) {
        zoomScrollDirty = false;
        settings.forEach((key, value) -> properties.setProperty("render." + key,
                key.equals("freecam") || key.equals("zoom") ? "false" : value));
    }

    
    public static String set(String key, String value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (key.equals("freecam") && value.equals("true") &&
                (client.world == null || client.player == null || !client.player.isAlive()))
            return "Join a world before enabling Freecam.";
        String error = putValidated(key, value);
        if (error != null) return error;
        if (key.equals("freecam")) {
            if (enabled("freecam")) FreeCamera.start(client); else FreeCamera.stop();
        }
        if (key.equals("freecamInteract") && FreeCamera.active()) {
            if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            client.gameRenderer.updateCrosshairTarget(1.0f);
        }
        ModConfig.save();
        return null;
    }

    private static String putValidated(String key, String raw) {
        if (!DEFAULTS.containsKey(key) || raw == null) return "Unknown render setting.";
        String value = raw.trim();
        try {
            switch (key) {
                case "freecamSpeed", "zoomFactor", "espRange", "nameRange" -> {
                    double n = Double.parseDouble(value);
                    double min = key.equals("freecamSpeed") ? 0.1 : key.equals("zoomFactor") ? 1 : 8;
                    double max = key.equals("freecamSpeed") ? 5 : key.equals("zoomFactor") ? 20 : 256;
                    if (!Double.isFinite(n) || n < min || n > max) return "Value must be between " + min + " and " + max + ".";
                    value = Double.toString(n);
                }
                case "espColor" -> {
                    if (!value.matches("#[0-9a-fA-F]{6}")) return "Choose a valid outline color.";
                }
                default -> {
                    if (!value.equals("true") && !value.equals("false")) return "Expected ON or OFF.";
                }
            }
        } catch (NumberFormatException e) { return "Enter a valid number."; }
        Map<String, String> next = new LinkedHashMap<>(settings);
        next.put(key, value);
        settings = Map.copyOf(next);
        if (key.equals("espColor")) espColor = 0xff000000 | Integer.parseInt(value.substring(1), 16);
        return null;
    }


    public static void registerKeys() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("paperutility", "render"));
        for (String module : new String[]{"freecam", "fullbright", "norender", "esp", "nametags"}) {
            keys.put(module, KeyBindingHelper.registerKeyBinding(new KeyBinding("key.paperutility.render." + module,
                    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)));
        }
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.paperutility.render.zoom",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
    }
    public static void tick(MinecraftClient client) {
        if (zoomScrollDirty && System.nanoTime() - lastZoomScroll >= 250_000_000L) ModConfig.save();
        keys.forEach((module, key) -> {
            while (key.wasPressed()) if (client.currentScreen == null) set(module, Boolean.toString(!flag(module)));
        });
        zoomHeld = client.world != null && client.currentScreen == null && zoomKey != null && zoomKey.isPressed();
        if (zoomKey != null) while (zoomKey.wasPressed()) { }
        if (flag("freecam") && !FreeCamera.active()) {
            FreeCamera.stop();
            putValidated("freecam", "false");
        }
    }
    public static void worldChanged() {
        if (zoomScrollDirty) ModConfig.save();
        FreeCamera.stop();
        putValidated("freecam", "false");
        putValidated("zoom", "false");
        zoomHeld = false;
        currentZoom = 1;
        lastFrame = 0;
    }
    public static void frame() {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 1.0 / 60 : Math.min(0.1, (now - lastFrame) / 1.0e9);
        lastFrame = now;
        double target = zooming() ? number("zoomFactor") : 1;
        currentZoom = flag("zoomSmooth") ? currentZoom + (target - currentZoom) * (1 - Math.exp(-18 * dt)) : target;
        if (Math.abs(currentZoom - target) < 0.001) currentZoom = target;
    }
    public static boolean outlined(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled("esp") || entity == client.player || client.player == null || !entity.isAlive()) return false;
        if (entity.squaredDistanceTo(client.gameRenderer.getCamera().getCameraPos()) > Math.pow(number("espRange"), 2)) return false;
        return entity instanceof PlayerEntity ? flag("espPlayers") :
                entity instanceof LivingEntity ? flag("espMobs") : entity instanceof ItemEntity && flag("espItems");
    }
    public static boolean named(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return enabled("nametags") && entity instanceof PlayerEntity && entity != client.player && entity.isAlive()
                && entity.squaredDistanceTo(client.gameRenderer.getCamera().getCameraPos()) <= Math.pow(number("nameRange"), 2);
    }
    public static String json() {
        JsonObject result = new JsonObject();
        settings.forEach((key, value) -> result.addProperty(key, value));
        result.addProperty("zoomActive", zooming());
        return result.toString();
    }
}
