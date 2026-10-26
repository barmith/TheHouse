package aurick.opsec.mod.protection;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.resources.Identifier;


import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;







public final class LangOnlyPackResources implements PackResources {

    private final PackResources delegate;
    private final UUID packId;

    public LangOnlyPackResources(PackResources delegate, UUID packId) {
        this.delegate = delegate;
        this.packId = packId;
    }

    
    
    private boolean filterActive() {
        return !PackStripHandler.isLoadForReal(packId);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return delegate.getRootResource(paths);
    }

        @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (filterActive() && !isLangResource(type, location.getPath())) return null;
        if (shouldStripShader(type, location.getNamespace(), location.getPath())) return null;
        return delegate.getResource(type, location);
    }
    
    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (filterActive() && !isLangPath(type, path)) return;
        if (stripsShaderNamespace(type, namespace)) {
            
            delegate.listResources(type, namespace, path, (loc, supplier) -> {
                if (!loc.getPath().startsWith("shaders/")) output.accept(loc, supplier);
            });
            return;
        }
        delegate.listResources(type, namespace, path, output);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

        @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return delegate.getMetadataSection(type);
    }
    
        @Override
    public PackLocationInfo location() {
        return delegate.location();
    }
    
    @Override
    public String packId() {
        return delegate.packId();
    }

        @Override
    public Optional<net.minecraft.server.packs.repository.KnownPack> knownPackInfo() {
        return delegate.knownPackInfo();
    }
    
    @Override
    public void close() {
        delegate.close();
    }

    private static boolean isLangResource(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
                && path.startsWith("lang/")
                && path.endsWith(".json");
    }

    private static boolean isLangPath(PackType type, String path) {
        return type == PackType.CLIENT_RESOURCES
                && (path.equals("lang") || path.startsWith("lang/"));
    }

    
    
    private boolean shouldStripShader(PackType type, String namespace, String path) {
        if (!path.startsWith("shaders/")) return false;
        if (!stripsShaderNamespace(type, namespace)) return false;
        ShaderStripTracker.onStripped(namespace, path);
        return true;
    }

    private boolean stripsShaderNamespace(PackType type, String namespace) {
        return type == PackType.CLIENT_RESOURCES
                && !"minecraft".equals(namespace)
                && OpsecConfig.getInstance().shouldStripModShaders()
                && !ModRegistry.isServerPackShaderAllowed(namespace);
    }
}
