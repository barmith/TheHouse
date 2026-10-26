package aurick.opsec.mod.mixin.client;

import net.minecraft.client.resources.server.DownloadedPackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;




@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {

    



    @Invoker("cleanupAfterDisconnect")
    void opsec$invokeCleanupAfterDisconnect();
}
