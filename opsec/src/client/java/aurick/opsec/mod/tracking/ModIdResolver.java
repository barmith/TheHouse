package aurick.opsec.mod.tracking;

import aurick.opsec.mod.Opsec;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;








public class ModIdResolver {

    
    private static volatile Map<Path, String> modPathMap;

    private ModIdResolver() {}

    




    private static Map<Path, String> getModPathMap() {
        if (modPathMap == null) {
            synchronized (ModIdResolver.class) {
                if (modPathMap == null) {
                    Map<Path, String> map = new HashMap<>();
                    for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                        String modId = mod.getMetadata().getId();
                        
                        if ("opsec".equals(modId) || "minecraft".equals(modId)
                                || "java".equals(modId) || "fabricloader".equals(modId)) continue;

                        try {
                            for (Path path : mod.getOrigin().getPaths()) {
                                map.put(path.toAbsolutePath().normalize(), modId);
                            }
                        } catch (Exception e) {
                            
                            Opsec.LOGGER.debug("[ModIdResolver] Could not get paths for mod {}: {}", modId, e.getMessage());
                        }
                    }
                    modPathMap = Map.copyOf(map);
                }
            }
        }
        return modPathMap;
    }

    


    public static String getModIdFromClass(Class<?> clazz) {
        if (clazz == null) return null;

        Path path = getCodeSourcePath(clazz);
        if (path == null) return null;

        return getModPathMap().get(path);
    }

    



    public static String getModIdFromStacktrace() {
        Map<Path, String> pathMap = getModPathMap();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stackTrace) {
            try {
                Class<?> clazz = Class.forName(element.getClassName());

                Path path = getCodeSourcePath(clazz);
                if (path == null) continue;

                String modId = pathMap.get(path);
                if (modId != null) {
                    return modId;
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                
            }
        }

        return null;
    }

    


    private static Path getCodeSourcePath(Class<?> clazz) {
        try {
            if (clazz.getProtectionDomain() == null) return null;
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) return null;

            return Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | SecurityException e) {
            Opsec.LOGGER.debug("[ModIdResolver] Failed to get code source for {}: {}",
                clazz.getName(), e.getMessage());
            return null;
        }
    }
}
