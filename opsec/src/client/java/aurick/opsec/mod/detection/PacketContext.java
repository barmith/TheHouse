package aurick.opsec.mod.detection;

import net.minecraft.network.protocol.Packet;


















public class PacketContext {
    private static final ThreadLocal<Boolean> PROCESSING_PACKET =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> DECODING_PAYLOAD =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<String> PACKET_NAME =
        ThreadLocal.withInitial(() -> "unknown");

    private PacketContext() {}

    public static boolean isProcessingPacket() {
        return PROCESSING_PACKET.get();
    }

    public static void setProcessingPacket(boolean value) {
        PROCESSING_PACKET.set(value);
    }

    
    public static boolean isDecodingPayload() {
        return DECODING_PAYLOAD.get();
    }

    public static void setDecodingPayload(boolean value) {
        DECODING_PAYLOAD.set(value);
    }

    public static String getPacketName() {
        return PACKET_NAME.get();
    }

    




    public static void setPacketName(Object packet) {
        if (packet instanceof Packet<?> p) {
            try {
                                PACKET_NAME.set(p.type().id().toString());
                            } catch (Exception e) {
                PACKET_NAME.set("unknown");
            }
        }
    }
}
