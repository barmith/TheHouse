package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConstants;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.util.KeybindDefaults;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.resources.Identifier;


import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;






public class ModRegistry {

    
    private static final Map<String, ModInfo> registry =
        new ConcurrentHashMap<>();

    






    private static volatile Set<String> vanillaTranslationKeys =
        ConcurrentHashMap.newKeySet();

    
    private static volatile Map<String, String> serverPackTranslations =
        new ConcurrentHashMap<>();

    
    private static final Map<String, Set<String>> namespaceToModIds =
        new ConcurrentHashMap<>();

    
    private static volatile Map<String, String> translationKeyToModId =
        new ConcurrentHashMap<>();

    
    private static final Map<String, String> keybindToModId =
        new ConcurrentHashMap<>();

    
        private static final Map<Identifier, String> channelToModId = new ConcurrentHashMap<>();
    
    
    private static volatile Set<String> dependencyClosure =
        ConcurrentHashMap.newKeySet();

    
    private static volatile Map<String, String> implicitProvider =
        new ConcurrentHashMap<>();

    
    public static final Set<String> PLATFORM_MODS =
        Set.of("minecraft", "java", "fabricloader");

    
    private static final String VANILLA_PACK_NAMESPACE = "minecraft";
    
    private static final String FABRIC_PACK_NAMESPACE = "vanilla";

    
    private static final int MIN_MOD_ID_LEN_FOR_PATH_ATTRIBUTION = 3;

    private ModRegistry() {}

    


    public static class ModInfo {

        private final String modId;
        private final String displayName;
        private final Set<String> translationKeys =
            ConcurrentHashMap.newKeySet();
        private final Set<String> keybinds = ConcurrentHashMap.newKeySet();
        
        private final Set<String> shaders = ConcurrentHashMap.newKeySet();
                private final Set<Identifier> channels = ConcurrentHashMap.newKeySet();
        
        public ModInfo(String modId, String displayName) {
            this.modId = modId;
            this.displayName = displayName;
        }

        public String getModId() {
            return modId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Set<String> getTranslationKeys() {
            return Collections.unmodifiableSet(translationKeys);
        }

        public Set<String> getKeybinds() {
            return Collections.unmodifiableSet(keybinds);
        }

                public Set<Identifier> getChannels() { return Collections.unmodifiableSet(channels); }
        
        public boolean hasTranslationKeys() {
            return !translationKeys.isEmpty();
        }

        public boolean hasKeybinds() {
            return !keybinds.isEmpty();
        }

        public boolean hasChannels() {
            return !channels.isEmpty();
        }

        public boolean hasShaders() {
            return !shaders.isEmpty();
        }

        public Set<String> getShaders() {
            return Collections.unmodifiableSet(shaders);
        }

        


        public boolean hasTrackableContent() {
            return hasTranslationKeys() || hasChannels() || hasKeybinds() || hasShaders();
        }
    }

    

    


    public static ModInfo getOrCreateModInfo(String modId) {
        if (modId == null) return null;

        return registry.computeIfAbsent(modId, id -> {
            String displayName = resolveDisplayName(id);
            return new ModInfo(id, displayName);
        });
    }

    


    public static ModInfo getModInfo(String modId) {
        return modId == null ? null : registry.get(modId);
    }

    


    public static Collection<ModInfo> getAllMods() {
        return Collections.unmodifiableCollection(registry.values());
    }

    


    private static String resolveDisplayName(String modId) {
        Optional<ModContainer> container =
            FabricLoader.getInstance().getModContainer(modId);
        return container.map(c -> c.getMetadata().getName()).orElse(modId);
    }

    

    





    private static final class TranslationRebuildStaging {
        final Set<String> vanilla = ConcurrentHashMap.newKeySet();
        final Map<String, String> serverPackTranslations = new ConcurrentHashMap<>();
        final Map<String, String> translationKeyToModId = new ConcurrentHashMap<>();
    }

    





    private static final ThreadLocal<TranslationRebuildStaging> rebuildStaging = new ThreadLocal<>();

    









    public static void beginTranslationRebuild() {
        rebuildStaging.remove();
        rebuildStaging.set(new TranslationRebuildStaging());
        
        
        
        for (ModInfo info : registry.values()) {
            info.translationKeys.clear();
        }
    }

    



    public static void commitTranslationRebuild() {
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s == null) return;
        
        
        vanillaTranslationKeys = s.vanilla;
        serverPackTranslations = s.serverPackTranslations;
        translationKeyToModId = s.translationKeyToModId;
        rebuildStaging.remove();
        Opsec.LOGGER.debug(
            "[ModRegistry] Committed translation rebuild: {} vanilla, {} server pack",
            s.vanilla.size(), s.serverPackTranslations.size()
        );
    }

    



    public static void abortTranslationRebuild() {
        rebuildStaging.remove();
    }

    


    public static void recordTranslationKey(String modId, String key) {
        if (modId == null || key == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.translationKeys.add(key);
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.translationKeyToModId.put(key, modId);
        } else {
            translationKeyToModId.put(key, modId);
        }
    }

    


    public static void recordVanillaTranslationKey(String key) {
        if (key == null) return;

        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.vanilla.add(key);
        } else {
            vanillaTranslationKeys.add(key);
        }
    }

    


    public static void removeVanillaTranslationKey(String key) {
        if (key == null) return;
        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.vanilla.remove(key);
            s.translationKeyToModId.remove(key);
        } else {
            vanillaTranslationKeys.remove(key);
            translationKeyToModId.remove(key);
        }
    }

    
    public static void recordServerPackTranslation(String key, String value) {
        if (key == null || value == null) return;

        TranslationRebuildStaging s = rebuildStaging.get();
        if (s != null) {
            s.serverPackTranslations.put(key, value);
        } else {
            serverPackTranslations.put(key, value);
        }
    }

    


    public static boolean isVanillaTranslationKey(String key) {
        return key != null && vanillaTranslationKeys.contains(key);
    }

    


    public static String getModForTranslationKey(String key) {
        if (key == null) return null;
        return translationKeyToModId.get(key);
    }

    

    
    public static void rebuildDependencyClosure() {
        Set<String> seeds = collectWhitelistSeeds();
        Set<String> closure = ConcurrentHashMap.newKeySet();
        Map<String, String> providers = new ConcurrentHashMap<>();
        for (String seed : seeds) {
            walkDependencies(seed, seed, closure, providers);
            walkUpToJijHost(seed, closure, providers);
        }
        dependencyClosure = closure;
        implicitProvider = providers;
        Opsec.LOGGER.debug(
            "[ModRegistry] Dep closure rebuilt: {} seeds -> {} mods",
            seeds.size(), closure.size()
        );
    }

    private static Set<String> collectWhitelistSeeds() {
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();
        Set<String> seeds = new HashSet<>();
        switch (settings.getWhitelistMode()) {
            case AUTO -> {
                for (ModInfo info : registry.values()) {
                    if (info.hasChannels()) seeds.add(info.getModId());
                }
            }
            case CUSTOM -> seeds.addAll(settings.getWhitelistedMods());
            default -> {}
        }
        return seeds;
    }

    
    public static boolean isInDependencyClosure(String modId) {
        return modId != null && dependencyClosure.contains(modId);
    }

    
    public static String getRequiringMod(String modId) {
        return modId == null ? null : implicitProvider.get(modId);
    }

    public static String getModDisplayName(String modId) {
        if (modId == null) return null;
        return FabricLoader.getInstance().getModContainer(modId)
            .map(c -> c.getMetadata().getName()).orElse(modId);
    }

    
    public static String resolveRequiringModName(String modId) {
        String requiringId = getRequiringMod(modId);
        return requiringId != null
            ? getModDisplayName(requiringId)
            : OpsecLang.tr(OpsecStrings.WHITELIST_REQUIRING_FALLBACK);
    }

    public static Collection<? extends ModContainer> getContainedMods(String modId) {
        if (modId == null) return List.of();
        return FabricLoader.getInstance().getModContainer(modId)
            .map(ModContainer::getContainedMods)
            .orElseGet(List::of);
    }

    
    public record ContentCounts(int translationKeys, int keybinds, int channels, int knownPacks, int shaders) {
        public boolean isEmpty() {
            return translationKeys == 0 && keybinds == 0 && channels == 0 && knownPacks == 0 && shaders == 0;
        }
    }

    public static ContentCounts aggregateContent(String modId) {
        if (modId == null) return new ContentCounts(0, 0, 0, 0, 0);
        int tk = 0, kb = 0, ch = 0, sh = 0;
        ModInfo info = registry.get(modId);
        if (info != null) {
            tk = info.getTranslationKeys().size();
            kb = info.getKeybinds().size();
            ch = info.getChannels().size();
            sh = info.getShaders().size();
        }
        int kp = getKnownPackStrings(modId).size();
        for (ModContainer child : getContainedMods(modId)) {
            ContentCounts sub = aggregateContent(child.getMetadata().getId());
            tk += sub.translationKeys();
            kb += sub.keybinds();
            ch += sub.channels();
            kp += sub.knownPacks();
            sh += sub.shaders();
        }
        return new ContentCounts(tk, kb, ch, kp, sh);
    }

    
    public static boolean hasTrackableContentIncludingJij(String modId) {
        return !aggregateContent(modId).isEmpty();
    }

    
    public static List<String> aggregateAllTranslationKeys(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getTranslationKeys()));
        return out;
    }

    public static List<String> aggregateAllKeybinds(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getKeybinds()));
        return out;
    }

    public static List<String> aggregateAllChannelIds(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> info.getChannels().forEach(c -> out.add(c.toString())));
        return out;
    }

    public static List<String> aggregateAllShaderPaths(String modId) {
        List<String> out = new java.util.ArrayList<>();
        aggregateRecursive(modId, info -> out.addAll(info.getShaders()));
        return out;
    }

    







    public static List<String> aggregateAllKnownPackStrings(String modId) {
        if (modId == null || !KNOWN_PACKS_HOOK_PRESENT) return List.of();
        List<String> out = new java.util.ArrayList<>();
        aggregateKnownPacksRecursive(modId, out);
        return out;
    }

    private static void aggregateKnownPacksRecursive(String modId, List<String> out) {
        out.addAll(getKnownPackStrings(modId));
        for (ModContainer child : getContainedMods(modId)) {
            aggregateKnownPacksRecursive(child.getMetadata().getId(), out);
        }
    }

    private static void aggregateRecursive(String modId, java.util.function.Consumer<ModInfo> sink) {
        if (modId == null) return;
        ModInfo info = registry.get(modId);
        if (info != null) sink.accept(info);
        for (ModContainer child : getContainedMods(modId)) {
            aggregateRecursive(child.getMetadata().getId(), sink);
        }
    }

    
    private static final Map<String, Set<String>> builtinPackIds = new ConcurrentHashMap<>();

    
    private static final Map<String, String> knownPackToModId = new ConcurrentHashMap<>();

    







    private static final boolean KNOWN_PACKS_HOOK_PRESENT = probeKnownPacksHook();

    private static boolean probeKnownPacksHook() {
        try {
            Class.forName("net.fabricmc.fabric.impl.resource.pack.ModPackResourcesUtil",
                    false, ModRegistry.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static boolean isKnownPacksHookPresent() {
        return KNOWN_PACKS_HOOK_PRESENT;
    }

    



    public static void recordBuiltinPack(String registeringModId, String packIdToString) {
        if (registeringModId == null || packIdToString == null) return;
        builtinPackIds.computeIfAbsent(registeringModId, k -> ConcurrentHashMap.newKeySet())
                .add(packIdToString);
        
        FabricLoader.getInstance().getModContainer(registeringModId).ifPresent(c -> {
            String version = c.getMetadata().getVersion().getFriendlyString();
            knownPackToModId.put(formatKnownPackTriple(packIdToString, version), registeringModId);
        });
    }

    




    public static List<String> getKnownPackStrings(String modId) {
        if (modId == null) return List.of();
        if (!KNOWN_PACKS_HOOK_PRESENT) return List.of();
        Optional<ModContainer> opt = FabricLoader.getInstance().getModContainer(modId);
        if (opt.isEmpty()) return List.of();
        ModContainer container = opt.get();
        if ("builtin".equals(container.getMetadata().getType())) return List.of();

        List<String> out = new java.util.ArrayList<>();
        collectKnownPackTriples(container, out::add);
        return out;
    }

    
    private static void collectKnownPackTriples(ModContainer container, java.util.function.Consumer<String> sink) {
        String modId = container.getMetadata().getId();
        String version = container.getMetadata().getVersion().getFriendlyString();

        if (hasServerDataContent(container)) {
            sink.accept(formatKnownPackTriple(modId, version));
        }

        Set<String> builtins = builtinPackIds.get(modId);
        if (builtins != null) {
            for (String packId : builtins) {
                sink.accept(formatKnownPackTriple(packId, version));
            }
        }
    }

    private static String formatKnownPackTriple(String id, String version) {
        return FABRIC_PACK_NAMESPACE + ":" + id + ":" + version;
    }

    
    public static void indexKnownPackOwners() {
        if (!KNOWN_PACKS_HOOK_PRESENT) return;
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if ("builtin".equals(container.getMetadata().getType())) continue;
            String modId = container.getMetadata().getId();
            collectKnownPackTriples(container, triple -> knownPackToModId.put(triple, modId));
        }
        Opsec.LOGGER.debug("[OpSec] Indexed {} known-pack triples across all mods", knownPackToModId.size());
    }

    public static String resolveModForKnownPack(String namespace, String id, String version) {
        if (!FABRIC_PACK_NAMESPACE.equals(namespace)) return null;
        return knownPackToModId.get(formatKnownPackTriple(id, version));
    }

    






    
    private static final Map<String, Boolean> serverDataContentCache = new ConcurrentHashMap<>();

    





    private static boolean hasServerDataContent(ModContainer container) {
        return serverDataContentCache.computeIfAbsent(
                container.getMetadata().getId(),
                id -> computeServerDataContent(container));
    }

    private static boolean computeServerDataContent(ModContainer container) {
        for (java.nio.file.Path root : container.getRootPaths()) {
            java.nio.file.Path dataDir = root.resolve("data");
            if (!java.nio.file.Files.isDirectory(dataDir)) continue;
            try (var stream = java.nio.file.Files.list(dataDir)) {
                if (stream.anyMatch(java.nio.file.Files::isDirectory)) return true;
            } catch (java.io.IOException ignored) {
                
            }
        }
        return false;
    }

    private static void walkDependencies(String seedRoot, String modId, Set<String> closure, Map<String, String> providers) {
        if (modId == null || PLATFORM_MODS.contains(modId)) return;
        Optional<ModContainer> container =
            FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) return;
        String canonicalId = container.get().getMetadata().getId();
        if (PLATFORM_MODS.contains(canonicalId)) return;
        if (!closure.add(canonicalId)) return;
        
        for (String alias : container.get().getMetadata().getProvides()) {
            closure.add(alias);
            if (!alias.equals(seedRoot)) providers.putIfAbsent(alias, seedRoot);
        }
        if (!canonicalId.equals(seedRoot)) providers.putIfAbsent(canonicalId, seedRoot);

        for (ModContainer contained : container.get().getContainedMods()) {
            walkDependencies(seedRoot, contained.getMetadata().getId(), closure, providers);
        }
        for (ModDependency dep : container.get().getMetadata().getDependencies()) {
            if (dep.getKind() != ModDependency.Kind.DEPENDS) continue;
            walkDependencies(seedRoot, dep.getModId(), closure, providers);
        }
    }

    
    private static void walkUpToJijHost(String originalSeed, Set<String> closure, Map<String, String> providers) {
        Optional<ModContainer> current = FabricLoader.getInstance().getModContainer(originalSeed);
        while (current.isPresent()) {
            Optional<ModContainer> host = current.get().getContainingMod();
            if (host.isEmpty()) break;
            String hostId = host.get().getMetadata().getId();
            
            providers.putIfAbsent(hostId, originalSeed);
            walkDependencies(hostId, hostId, closure, providers);
            current = host;
        }
    }

    

    
    private static boolean isModEffectivelyWhitelisted(
        String modId,
        SpoofSettings settings
    ) {
        if (modId == null) return false;
        if (settings.getWhitelistMode() == SpoofSettings.WhitelistMode.AUTO) {
            ModInfo info = getModInfo(modId);
            if (info != null && info.hasChannels()) return true;
        } else if (settings.isModWhitelisted(modId)) {
            return true;
        }
        return dependencyClosure.contains(modId);
    }

    
    public static boolean isWhitelistedKnownPack(String namespace, String id, String version) {
        if (VANILLA_PACK_NAMESPACE.equals(namespace)) return true;
        String modId = resolveModForKnownPack(namespace, id, version);
        if (modId == null) return false;
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return isModEffectivelyWhitelisted(modId, settings);
    }

    
    private static final Map<String, String> shaderOwnerByNamespace = new ConcurrentHashMap<>();

    




    public static String resolveShaderOwnerMod(String namespace) {
        if (namespace == null) return null;
        String owner = shaderOwnerByNamespace.computeIfAbsent(namespace, ns -> {
            String rel = "assets/" + ns + "/shaders";
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                if (PLATFORM_MODS.contains(id)) continue;
                for (java.nio.file.Path root : mod.getRootPaths()) {
                    if (java.nio.file.Files.exists(root.resolve(rel))) return id;
                }
            }
            return "";
        });
        return owner.isEmpty() ? null : owner;
    }

    
    public static void recordShader(String modId, String namespace, String shaderPath) {
        if (modId == null || namespace == null || shaderPath == null) return;
        getOrCreateModInfo(modId).shaders.add(namespace + ":" + shaderPath);
        shaderOwnerByNamespace.putIfAbsent(namespace, modId);
    }

    




    public static boolean isServerPackShaderAllowed(String namespace) {
        if (namespace == null) return true;
        if (VANILLA_PACK_NAMESPACE.equals(namespace)) return true;

        String modId = resolveShaderOwnerMod(namespace);
        if (modId == null) return true; 

        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isWhitelistEnabled()) return false; 
        return isModEffectivelyWhitelisted(modId, settings);
    }

    

    







    public static boolean isWhitelistedKey(String key, String source) {
        if (key == null) return false;

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        
        if (!settings.isWhitelistEnabled()) {
            Opsec.LOGGER.debug(
                "[Whitelist] {} '{}' - whitelist NOT enabled",
                source,
                key
            );
            return false;
        }

        
        
        String modId = getModForKeybind(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug(
                "[Whitelist] ALLOWED {} '{}' via keybind tracking (mod: {})",
                source,
                key,
                modId
            );
            return true;
        }

        
        modId = getModForTranslationKey(key);
        if (modId != null && isModEffectivelyWhitelisted(modId, settings)) {
            Opsec.LOGGER.debug(
                "[Whitelist] ALLOWED {} '{}' via translation tracking (mod: {})",
                source,
                key,
                modId
            );
            return true;
        }

        Opsec.LOGGER.debug(
            "[Whitelist] BLOCKED {} '{}' - modId: '{}', not whitelisted",
            source,
            key,
            modId
        );
        return false;
    }

    



    public static boolean isWhitelistedTranslationKey(String key) {
        return isWhitelistedKey(key, "translation");
    }

    



    public static boolean isWhitelistedKeybind(String keybindName) {
        return isWhitelistedKey(keybindName, "keybind");
    }

    


    public static boolean isServerPackTranslationKey(String key) {
        return key != null && serverPackTranslations.containsKey(key);
    }

    public static String getServerPackTranslation(String key) {
        return key == null ? null : serverPackTranslations.get(key);
    }

    




    public static void clearTranslationKeys() {
        for (ModInfo info : registry.values()) {
            info.translationKeys.clear();
        }
        vanillaTranslationKeys.clear();
        serverPackTranslations.clear();
        translationKeyToModId.clear();
        Opsec.LOGGER.debug(
            "[ModRegistry] Cleared translation key cache (including server pack keys)"
        );
    }

    

    


    public static void recordKeybind(String modId, String keybindName) {
        if (modId == null || keybindName == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.keybinds.add(keybindName);
        keybindToModId.put(keybindName, modId);

        Opsec.LOGGER.debug(
            "[ModRegistry] Recorded keybind '{}' from mod '{}'",
            keybindName,
            modId
        );
    }

    


    public static String getModForKeybind(String keybindName) {
        if (keybindName == null) return null;
        return keybindToModId.get(keybindName);
    }

    

    







    public static Set<String> resolveModIdsForNamespace(String namespace) {
        if (namespace == null) return Set.of();

        
        var container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            return Set.of(container.get().getMetadata().getId());
        }

        
        Set<String> mapped = namespaceToModIds.get(namespace);
        if (mapped != null && !mapped.isEmpty()) {
            return Collections.unmodifiableSet(mapped);
        }

        return Set.of();
    }

    






    public static String resolveOwningModForNamespace(String namespace) {
        if (namespace == null) return null;
        Set<String> resolved = resolveModIdsForNamespace(namespace);
        if (!resolved.isEmpty()) return resolved.iterator().next();
        return namespace;
    }

    
    public static boolean isVanillaMinecraftChannel(String path) {
        return path != null && OpsecConstants.Channels.VANILLA_PATHS.contains(path);
    }

    




    public static String resolveOwningModForChannel(String namespace, String path) {
        if (namespace == null) return null;
        if (!"minecraft".equals(namespace)) {
            return resolveOwningModForNamespace(namespace);
        }
        if (isVanillaMinecraftChannel(path)) return null;
        return resolveModForMinecraftChannelPath(path);
    }

    




    public static String resolveModForMinecraftChannelPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String best = null;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (id.length() < MIN_MOD_ID_LEN_FOR_PATH_ATTRIBUTION) continue;
            
            boolean match = path.startsWith(id)
                && (path.length() == id.length() || "_/.-".indexOf(path.charAt(id.length())) >= 0);
            if (match && (best == null || id.length() > best.length())) {
                best = id;
            }
        }
        return best;
    }

    








    public static void inferJijNamespaceAliases() {
        for (ModContainer top : FabricLoader.getInstance().getAllMods()) {
            if (top.getContainingMod().isPresent()) continue;
            String topId = top.getMetadata().getId();
            if (PLATFORM_MODS.contains(topId)) continue;

            Map<String, Integer> prefixCounts = new java.util.HashMap<>();
            for (ModContainer child : top.getContainedMods()) {
                String childId = child.getMetadata().getId();
                int dash = childId.indexOf('-');
                if (dash > 0) {
                    prefixCounts.merge(childId.substring(0, dash), 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : prefixCounts.entrySet()) {
                String prefix = e.getKey();
                
                
                
                if (e.getValue() >= MIN_CHILDREN_FOR_PREFIX_ALIAS
                        && topId.startsWith(prefix)
                        && !FabricLoader.getInstance().getModContainer(prefix).isPresent()) {
                    recordNamespaceMapping(prefix, topId);
                }
            }
        }
    }

    private static final int MIN_CHILDREN_FOR_PREFIX_ALIAS = 2;

    






    public static void recordNamespaceMapping(String namespace, String modId) {
        if (
            namespace == null || modId == null || namespace.equals(modId)
        ) return;

        namespaceToModIds
            .computeIfAbsent(namespace, k -> ConcurrentHashMap.newKeySet())
            .add(modId);
        Opsec.LOGGER.debug(
            "[ModRegistry] Mapped namespace '{}' to mod '{}'",
            namespace,
            modId
        );
    }

    

    


        public static void recordChannel(String modId, Identifier channel) {
            if (modId == null || channel == null) return;

        ModInfo info = getOrCreateModInfo(modId);
        info.channels.add(channel);
        channelToModId.put(channel, modId);

        Opsec.LOGGER.debug(
            "[ModRegistry] Recorded channel '{}' from mod '{}'",
            channel,
            modId
        );
    }

    




        public static boolean isWhitelistedChannel(Identifier channel) {
            if (channel == null) return false;

        String namespace = channel.getNamespace();
        String path = channel.getPath();

        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getSettings();

        
        
        if ("minecraft".equals(namespace)) {
            if (isVanillaMinecraftChannel(path)) return true;
            if (!settings.isWhitelistEnabled()) return false;
            String owner = channelToModId.get(channel);
            if (owner == null) owner = resolveModForMinecraftChannelPath(path);
            return owner != null && isModEffectivelyWhitelisted(owner, settings);
        }

        if (!settings.isWhitelistEnabled()) {
            return false;
        }

        String channelOwner = channelToModId.get(channel);
        if (channelOwner != null && isModEffectivelyWhitelisted(channelOwner, settings)) {
            return true;
        }

        if (isModEffectivelyWhitelisted(namespace, settings)) {
            return true;
        }

        
        Set<String> resolvedModIds = resolveModIdsForNamespace(namespace);
        for (String resolvedModId : resolvedModIds) {
            if (isModEffectivelyWhitelisted(resolvedModId, settings)) {
                return true;
            }
        }

        return false;
    }

    

    public static int getVanillaKeyCount() {
        return vanillaTranslationKeys.size();
    }

    public static int getServerPackKeyCount() {
        return serverPackTranslations.size();
    }

    public static int getTranslationKeyCount() {
        java.util.HashSet<String> all = new java.util.HashSet<>(
            vanillaTranslationKeys
        );
        all.addAll(serverPackTranslations.keySet());
        for (ModInfo info : registry.values()) {
            all.addAll(info.translationKeys);
        }
        return all.size();
    }

    public static int getKeybindCount() {
        int count = KeybindDefaults.size();
        for (ModInfo info : registry.values()) count += info.keybinds.size();
        return count;
    }

    public static int getKnownPackCount() {
        return knownPackToModId.size();
    }

    public static int getShaderCount() {
        int count = 0;
        for (ModInfo info : registry.values()) count += info.shaders.size();
        return count;
    }
}
