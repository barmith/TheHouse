package com.thehouse.paperutility;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PaperUtilityClient implements ClientModInitializer {
    public static final String MOD_ID = "paperutility";

    private static KeyBinding openMenuKey;
    private static KeyBinding toggleSimulationKey;
    private static KeyBinding cycleSideKey;
    private static KeyBinding toggleBlackjackKey;
    private static KeyBinding blackjackHostTurnKey;
    private static KeyBinding toggleArrowKey;
    private static ClientWorld lastWorld;

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    public Identifier getFabricId() { return Identifier.of(MOD_ID, "browser_item_icons"); }
                    public void reload(net.minecraft.resource.ResourceManager manager) { BrowserItemIcons.clear(); }
                });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BrowserItemIcons.clear());
        ModConfig.load();
        registerKeys();
        com.thehouse.paperutility.render.RenderModules.registerKeys();

        HudElementRegistry.addLast(
                Identifier.of(MOD_ID, "status"),
                (context, tickCounter) -> PaperUtilityHud.render(context)
        );

        ClientTickEvents.END_CLIENT_TICK.register(PaperUtilityClient::onClientTick);
    }

    private static void registerKeys() {
        KeyBinding.Category category = KeyBinding.Category.create(
                Identifier.of(MOD_ID, "general")
        );

        openMenuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.menu",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_INSERT,
                        category
                )
        );

        toggleSimulationKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.toggle",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_R,
                        category
                )
        );

        cycleSideKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.side",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_U,
                        category
                )
        );

        toggleBlackjackKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.blackjack_rigging",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_B,
                        category
                )
        );

        blackjackHostTurnKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.blackjack_host_turn",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_H,
                        category
                )
        );

        toggleArrowKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.paperutility.arrow_toggle",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        category
                )
        );
    }

    private static void onClientTick(MinecraftClient client) {
        ArrowBrowserGrid.openExternalUiOnce();
        
        if (client.world != lastWorld) {
            com.thehouse.paperutility.render.RenderModules.worldChanged();
            PaperSimulationTracker.clearState();
            FiftyFiftyTracker.clearState();
            ArrowTracker.clearTransient();
            
            
            
            BlackjackState.resetHand();
            lastWorld = client.world;
        }

        
        
        BlackjackDropperVisualMask.tickRelearn(client);

        PaperSimulationTracker.tick(client);
        FiftyFiftyTracker.tick(client);
        
        
        
        BlackjackTracker.tick(client);
        ArrowTracker.tick(client);
        ArrowInventoryMask.tick(client);
        com.thehouse.paperutility.render.RenderModules.tick(client);

        while (openMenuKey.wasPressed()) {
            ArrowBrowserGrid.openBrowser();
        }

        while (toggleSimulationKey.wasPressed()) {
            ModState.toggleSimulation();
            if (!ModState.isSimulationEnabled()) {
                PaperSimulationTracker.restoreAll(client);
                FiftyFiftyTracker.restoreAll(client);
            }
        }

        while (cycleSideKey.wasPressed()) {
            
            
            PaperSimulationTracker.restoreAll(client);
            FiftyFiftyTracker.restoreAll(client);
            ModState.cycleWinningSide();
            ModState.clearRound();
        }

        while (toggleBlackjackKey.wasPressed()) {
            BlackjackTracker.restoreAll(client);
            BlackjackState.toggleEnabled();
        }

        while (blackjackHostTurnKey.wasPressed()) {
            if (!BlackjackState.isEnabled()) {
                continue;
            }
            BlackjackState.advanceHostTurnOrReset();
        }

        while (toggleArrowKey.wasPressed()) {
            ArrowState.toggleEnabled();
            if (!ArrowState.isEnabled()) ArrowTracker.clearTransient();
        }
    }
}
