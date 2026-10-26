package aurick.opsec.mod.util;

import java.net.InetAddress;
import java.net.UnknownHostException;











public class LocalAddressUtil {

    



    public static volatile String serverAddress = null;

    
















    public static boolean isLocalAddress(String host) throws UnknownHostException {
        if (host == null) return false;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }
}
