package aurick.opsec.mod.protection;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.resources.Identifier;






public class ClientSpoofer {

    




    public static boolean isVanillaMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isVanillaMode();
    }

    




    public static boolean isFabricMode() {
        OpsecConfig config = OpsecConfig.getInstance();
        return config.shouldSpoofChannels() && config.getSettings().isFabricMode();
    }

    






        public static boolean shouldDropInboundChannel(Identifier channel) {
            if ("minecraft".equals(channel.getNamespace())) return false;
        if (isVanillaMode()) return true;
        return isFabricMode() && !ModRegistry.isWhitelistedChannel(channel);
    }
}
