package aurick.opsec.mod.command;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.tracking.ModRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;





public class OpsecCommand {
    
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(OpsecCommand::registerCommands);
    }
    
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, 
                                          CommandBuildContext context) {
        dispatcher.register(
                        ClientCommandManager.literal("opsec")
                            .requires(source -> OpsecConfig.getInstance().getSettings().isDebugCommand())
                .executes(OpsecCommand::showHelp)
                .then(
                                        ClientCommandManager.literal("info")
                                        .then(
                                                ClientCommandManager.argument("modName", StringArgumentType.greedyString())
                                                .suggests(OpsecCommand::suggestModNames)
                        .executes(ctx -> showModInfo(ctx, StringArgumentType.getString(ctx, "modName"))))
                    .executes(ctx -> showOverview(ctx)))
                .then(
                                        ClientCommandManager.literal("channels")
                                        .executes(OpsecCommand::showAllChannels))
        );
    }
    
    


    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_HELP_HEADER)));
        source.sendFeedback(Component.empty());
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_HELP_INFO)));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_HELP_CHANNELS)));
        
        return 1;
    }
    
    





    private static CompletableFuture<Suggestions> suggestModNames(
            CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {

        Set<String> modNames = new LinkedHashSet<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
            if (ModRegistry.hasTrackableContentIncludingJij(modId)) {
                modNames.add(modId);
            }
        }

        return SharedSuggestionProvider.suggest(modNames, builder);
    }
    
    


    private static int showOverview(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();

        
        
        List<MutableComponent> entries = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
            if (mod.getContainingMod().isPresent()) continue; 
            ModRegistry.ContentCounts counts = ModRegistry.aggregateContent(modId);
            if (counts.isEmpty()) continue;
            entries.add(modEntry(mod.getMetadata().getName(), counts));
        }

        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_HEADER)));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_MODS, entries.size())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_VANILLA_KEYS, ModRegistry.getVanillaKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_SERVER_KEYS, ModRegistry.getServerPackKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYS, ModRegistry.getTranslationKeyCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KEYBINDS, ModRegistry.getKeybindCount())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_SHADERS, ModRegistry.getShaderCount())));
        
        if (ModRegistry.isKnownPacksHookPresent()) {
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_TOTAL_KNOWN_PACKS, ModRegistry.getKnownPackCount())));
        }

        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_MODS_HEADER)));

        if (entries.isEmpty()) {
            source.sendFeedback(warning(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_NO_MODS)));
        } else {
            for (MutableComponent entry : entries) {
                source.sendFeedback(entry);
            }
        }

        source.sendFeedback(Component.empty());
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_OVERVIEW_USE_INFO)));
        
        return 1;
    }
    
    


    private static int showModInfo(CommandContext<FabricClientCommandSource> ctx, String modName) {
        FabricClientCommandSource source = ctx.getSource();

        Resolved resolved = findMod(modName);
        if (resolved == null) {
            source.sendFeedback(error(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NOT_FOUND, modName)));
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_USE_LIST)));
            return 0;
        }

        String modId = resolved.modId();
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_INFO_HEADER, resolved.displayName())));
        source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_INFO_ID, modId)));

        
        renderContentSection(source, OpsecStrings.COMMAND_INFO_TRANSLATION_KEYS,
            ModRegistry.aggregateAllTranslationKeys(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_KEYBINDS,
            ModRegistry.aggregateAllKeybinds(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_CHANNELS,
            ModRegistry.aggregateAllChannelIds(modId), 20);
        renderContentSection(source, OpsecStrings.COMMAND_INFO_SHADERS,
            ModRegistry.aggregateAllShaderPaths(modId), 20);

        
        if (ModRegistry.isKnownPacksHookPresent()) {
            List<String> knownPacks = ModRegistry.aggregateAllKnownPackStrings(modId);
            source.sendFeedback(Component.empty());
            source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_KNOWN_PACKS, knownPacks.size())));
            if (knownPacks.isEmpty()) {
                source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
            } else {
                for (String pack : knownPacks) {
                    source.sendFeedback(listItem(pack));
                }
            }
        }

        
        List<? extends ModContainer> trackedContained = ModRegistry.getContainedMods(modId).stream()
            .filter(child -> ModRegistry.getModInfo(child.getMetadata().getId()) != null)
            .toList();
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(OpsecStrings.COMMAND_INFO_JIJ, trackedContained.size())));
        if (trackedContained.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
        } else {
            for (ModContainer child : trackedContained) {
                source.sendFeedback(listItem(child.getMetadata().getId()));
            }
        }

        
        source.sendFeedback(Component.empty());
        OpsecConfig opsecConfig = OpsecConfig.getInstance();
        SpoofSettings opsecSettings = opsecConfig.getSettings();
        SpoofSettings.WhitelistMode whitelistMode = opsecSettings.getWhitelistMode();
        ModRegistry.ModInfo info = ModRegistry.getModInfo(modId);
        boolean hasChannels = info != null && info.hasChannels();

        String statusText;
        boolean isAllowed;

        boolean directlyWhitelisted = switch (whitelistMode) {
            case AUTO -> hasChannels;
            case CUSTOM -> opsecSettings.isModWhitelisted(modId);
            default -> false;
        };

        if (directlyWhitelisted) {
            isAllowed = true;
            statusText = OpsecLang.tr(whitelistMode == SpoofSettings.WhitelistMode.AUTO
                ? OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_AUTO
                : OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_CUSTOM);
        } else if (ModRegistry.isInDependencyClosure(modId)) {
            isAllowed = true;
            statusText = OpsecLang.tr(OpsecStrings.COMMAND_INFO_STATUS_ALLOWED_DEP,
                ModRegistry.resolveRequiringModName(modId));
        } else {
            isAllowed = false;
            statusText = OpsecLang.tr(switch (whitelistMode) {
                case AUTO -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_AUTO;
                case CUSTOM -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_CUSTOM;
                default -> OpsecStrings.COMMAND_INFO_STATUS_BLOCKED_OFF;
            });
        }
        source.sendFeedback(isAllowed ? success(statusText) : warning(statusText));
        
        return 1;
    }
    
    
    


    private static int showAllChannels(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        
        source.sendFeedback(header(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_HEADER)));
        
        int totalChannels = 0;
        for (ModRegistry.ModInfo info : ModRegistry.getAllMods()) {
                        Set<Identifier> channels = info.getChannels();
                        if (!channels.isEmpty()) {
                source.sendFeedback(subheader(info.getDisplayName() + " (" + info.getModId() + "):"));
                                for (Identifier channel : channels) {
                                    boolean whitelisted = ModRegistry.isWhitelistedChannel(channel);
                    if (whitelisted) {
                        source.sendFeedback(Component.literal("  ✓ " + channel.toString())
                            .withStyle(ChatFormatting.GREEN));
                    } else {
                        source.sendFeedback(Component.literal("  ✗ " + channel.toString())
                            .withStyle(ChatFormatting.RED));
                    }
                    totalChannels++;
                }
            }
        }
        
        if (totalChannels == 0) {
            source.sendFeedback(warning(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_NONE)));
        } else {
            source.sendFeedback(Component.empty());
            source.sendFeedback(info(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_TOTAL, totalChannels)));
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_CHANNELS_LEGEND)));
        }
        
        return 1;
    }
    
    
    
    private record Resolved(String modId, String displayName) {}

    






    private static Resolved findMod(String query) {
        if (query == null || query.isEmpty()) return null;

        
        ModRegistry.ModInfo info = ModRegistry.getModInfo(query);
        if (info != null) return new Resolved(info.getModId(), info.getDisplayName());
        var container = FabricLoader.getInstance().getModContainer(query);
        if (container.isPresent()) {
            
            
            
            var meta = container.get().getMetadata();
            String realId = meta.getId();
            if (ModRegistry.hasTrackableContentIncludingJij(realId)) {
                return new Resolved(realId, meta.getName());
            }
        }

        
        for (ModRegistry.ModInfo mod : ModRegistry.getAllMods()) {
            if (mod.getModId().equalsIgnoreCase(query) ||
                mod.getDisplayName().equalsIgnoreCase(query)) {
                return new Resolved(mod.getModId(), mod.getDisplayName());
            }
        }
        
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (mod.getContainingMod().isPresent()) continue;
            if (ModRegistry.PLATFORM_MODS.contains(id)) continue;
            if (!ModRegistry.hasTrackableContentIncludingJij(id)) continue;
            String name = mod.getMetadata().getName();
            if (id.equalsIgnoreCase(query) || name.equalsIgnoreCase(query)) {
                return new Resolved(id, name);
            }
        }

        return null;
    }
    
    
    private static void renderContentSection(
        FabricClientCommandSource source,
        String headerKey,
        List<String> items,
        int limit
    ) {
        source.sendFeedback(Component.empty());
        source.sendFeedback(subheader(OpsecLang.tr(headerKey, items.size())));
        if (items.isEmpty()) {
            source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_NONE)));
            return;
        }
        int shown = 0;
        for (String item : items) {
            if (limit >= 0 && shown >= limit) {
                source.sendFeedback(dim(OpsecLang.tr(OpsecStrings.COMMAND_INFO_MORE, items.size() - shown)));
                break;
            }
            source.sendFeedback(listItem(item));
            shown++;
        }
    }

    

    private static MutableComponent header(String text) {
        return Component.literal("[OpSec] ")
                .withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    private static MutableComponent subheader(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent success(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent warning(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent error(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent dim(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent listItem(String text) {
        return Component.literal("  - ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent modEntry(String displayName, ModRegistry.ContentCounts counts) {
        MutableComponent component = Component.literal(displayName).withStyle(ChatFormatting.WHITE);

        List<String> details = new ArrayList<>();
        if (counts.translationKeys() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYS, counts.translationKeys()));
        }
        if (counts.keybinds() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KEYBINDS, counts.keybinds()));
        }
        if (counts.channels() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_CHANNELS, counts.channels()));
        }
        if (counts.knownPacks() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_KNOWN_PACKS, counts.knownPacks()));
        }
        if (counts.shaders() > 0) {
            details.add(OpsecLang.tr(OpsecStrings.COMMAND_MODENTRY_SHADERS, counts.shaders()));
        }

        if (!details.isEmpty()) {
            component.append(Component.literal(" (" + String.join(", ", details) + ")")
                    .withStyle(ChatFormatting.GRAY));
        }

        return component;
    }
}
