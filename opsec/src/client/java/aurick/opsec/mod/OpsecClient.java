package aurick.opsec.mod;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.command.OpsecCommand;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.JarIntegrityChecker;
import aurick.opsec.mod.config.UpdateChecker;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.protection.ShaderStripTracker;
import aurick.opsec.mod.tracking.ModRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;





public class OpsecClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		
		Opsec.LOGGER.info("{} v{} - Privacy protection for Minecraft", Opsec.MOD_NAME, Opsec.getVersion());
		Opsec.LOGGER.info("Protecting against: TrackPack, Key Resolution Exploit, Client Fingerprinting");
		
		OpsecConfig.getInstance();
		OpsecCommand.register();
		AccountManager.getInstance(); 

		
		
		Opsec.LOGGER.info("OpSec is managed by the TheHouse bundled build");

		
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			ModRegistry.inferJijNamespaceAliases();  
			scanRegisteredChannels();
			
			scanModsForLanguageFiles();
			scanModsForShaders();
			ModRegistry.indexKnownPackOwners();
			
			ModRegistry.rebuildDependencyClosure();
		});

		ClientTickEvents.END_CLIENT_TICK.register(PackStripOverlay::tryShowNext);
		ClientTickEvents.END_CLIENT_TICK.register(client -> ShaderStripTracker.flushPending());

		Opsec.LOGGER.info("OpSec client protection initialized");
	}
	
	




	private void scanRegisteredChannels() {
		int channelCount = 0;

		channelCount += scanChannelSource(ClientPlayNetworking::getGlobalReceivers, "play channels");
				channelCount += scanChannelSource(ClientConfigurationNetworking::getGlobalReceivers, "config channels");
				channelCount += scanChannelSource(ClientPlayNetworking::getReceived, "play received channels");
		channelCount += scanChannelSource(ClientPlayNetworking::getSendable, "play sendable channels");
				channelCount += scanChannelSource(ClientConfigurationNetworking::getReceived, "config received channels");
		channelCount += scanChannelSource(ClientConfigurationNetworking::getSendable, "config sendable channels");
		
		Opsec.LOGGER.debug("[OpSec] Scanned {} mod channels at startup", channelCount);
	}

		private int scanChannelSource(Supplier<Set<Identifier>> source, String label) {
			try {
			int count = 0;
			for (var channel : source.get()) {
				
				String owner = ModRegistry.resolveOwningModForChannel(channel.getNamespace(), channel.getPath());
				if (owner == null) continue;
				ModRegistry.recordChannel(owner, channel);
				count++;
			}
			return count;
		} catch (Exception e) {
			Opsec.LOGGER.debug("[OpSec] Could not scan {}: {}", label, e.getMessage());
			return 0;
		}
	}
	
	




	private void scanModsForLanguageFiles() {
		int modsWithLang = 0;
		int modsAdded = 0;
		
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modId = mod.getMetadata().getId();

			if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
			
			if (mod.getContainingMod().isPresent()) continue;
			
			
			ModRegistry.ModInfo existingInfo = ModRegistry.getModInfo(modId);
			if (existingInfo != null && existingInfo.hasTranslationKeys()) {
				modsWithLang++;
				continue;
			}
			
			
			boolean found = false;
			for (Path rootPath : mod.getRootPaths()) {
				Path langFile = rootPath.resolve("assets/" + modId + "/lang/en_us.json");
				if (Files.exists(langFile)) {
					found = true;
					
					try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(langFile))) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						int keyCount = 0;
						for (String key : json.keySet()) {
							ModRegistry.recordTranslationKey(modId, key);
							keyCount++;
						}
						if (keyCount > 0) {
							Opsec.LOGGER.debug("[OpSec] Fallback: Registered {} translation keys for mod '{}'", keyCount, modId);
							modsWithLang++;
							modsAdded++;
						}
					} catch (Exception e) {
						Opsec.LOGGER.debug("[OpSec] Could not read language file for {}: {}", modId, e.getMessage());
					}
					break; 
				}
			}
			
			
			if (!found && existingInfo != null && existingInfo.hasChannels()) {
				modsWithLang++;
			}
		}
		
		Opsec.LOGGER.debug("[OpSec] Fallback scan added {} mods with translation keys", modsAdded);
		Opsec.LOGGER.debug("[ModRegistry] Total: {} whitelistable mods, {} translation keys, {} keybinds",
			modsWithLang, ModRegistry.getTranslationKeyCount(), ModRegistry.getKeybindCount());
	}

	
	private void scanModsForShaders() {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modId = mod.getMetadata().getId();
			if (ModRegistry.PLATFORM_MODS.contains(modId)) continue;
			if (mod.getContainingMod().isPresent()) continue; 

			for (Path rootPath : mod.getRootPaths()) {
				Path assets = rootPath.resolve("assets");
				if (!Files.isDirectory(assets)) continue;
				try (var namespaces = Files.list(assets)) {
					namespaces.filter(Files::isDirectory).forEach(nsDir -> {
						String ns = nsDir.getFileName().toString().replace("/", "");
						if ("minecraft".equals(ns)) return; 
						Path shadersDir = nsDir.resolve("shaders");
						if (!Files.isDirectory(shadersDir)) return;
						try (var files = Files.walk(shadersDir)) {
							files.filter(Files::isRegularFile).forEach(f ->
								ModRegistry.recordShader(modId, ns,
									"shaders/" + shadersDir.relativize(f).toString().replace('\\', '/')));
						} catch (Exception e) {
							Opsec.LOGGER.debug("[OpSec] shader walk failed for {} ({}): {}", modId, ns, e.getMessage());
						}
					});
				} catch (Exception e) {
					Opsec.LOGGER.debug("[OpSec] shader scan failed for {}: {}", modId, e.getMessage());
				}
			}
		}
	}
}
