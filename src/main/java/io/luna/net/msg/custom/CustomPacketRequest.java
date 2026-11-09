package io.luna.net.msg.custom;

import java.util.List;

/**
 * An immutable custom request carried inside the native command packet.
 *
 * @param id The independent custom packet identifier.
 * @param payload The flat JSON values, with all integer values represented as {@link Long}s.
 * @author lare96
 */
public record CustomPacketRequest(int id, List<Object> payload) {

    public CustomPacketRequest {
        if (id < 0 || payload.size() > CustomPacketCodec.MAX_VALUES) {
            throw new IllegalArgumentException("Invalid custom packet id or payload size.");
        }
        payload = List.copyOf(payload);
        for (Object value : payload) {
            if (!(value instanceof String || value instanceof Boolean || value instanceof Long)) {
                throw new IllegalArgumentException("Unsupported custom packet value.");
            }
        }
    }
}
