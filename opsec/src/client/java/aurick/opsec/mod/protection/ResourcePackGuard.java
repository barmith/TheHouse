package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.mixin.client.DownloadedPackSourceAccessor;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.DownloadedPackSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;




public class ResourcePackGuard {

    




    public static void clearAllCaches() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        int totalDeleted = 0;
        
        
        totalDeleted += clearCacheFolder(gameDir, "downloads");
        
        
        totalDeleted += clearCacheFolder(gameDir, "server-resource-packs");
        
        
        boolean memoryCleared = clearDownloadQueueState();

        Opsec.LOGGER.info("[OpSec] Clear pack cache: {} files removed, memoryCleared={}", totalDeleted, memoryCleared);
    }
    
    




    private static boolean clearDownloadQueueState() {
                try {
            Minecraft mc = Minecraft.getInstance();
            DownloadedPackSource packSource = ((MinecraftAccessor) mc).opsec$getDownloadedPackSource();

            if (packSource != null) {
                ((DownloadedPackSourceAccessor) packSource).opsec$invokeCleanupAfterDisconnect();
                Opsec.LOGGER.debug("[OpSec] Cleared download queue state via cleanupAfterDisconnect");
                return true;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not clear download queue state: {}", e.getMessage());
        }
        return false;
            }
    
    



    private static int clearCacheFolder(Path gameDir, String folderName) {
        Path cachePath = gameDir.resolve(folderName).normalize();

        if (!cachePath.startsWith(gameDir)) {
            Opsec.LOGGER.error("[OpSec] Security: {} path outside game directory", folderName);
            return 0;
        }

        if (!Files.exists(cachePath)) {
            return 0;
        }

        int deleted = 0;
        try {
            for (Path p : Files.walk(cachePath).sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(cachePath)) continue;
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    Opsec.LOGGER.warn("[OpSec] Failed to delete cache entry {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to walk cache folder {}: {}", folderName, e.getMessage());
        }
        
        return deleted;
    }
}
