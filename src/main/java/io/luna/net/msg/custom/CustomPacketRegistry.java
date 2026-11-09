package io.luna.net.msg.custom;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An explicit immutable registry of assigned custom identifiers and their handlers.
 *
 * @author lare96
 */
public final class CustomPacketRegistry {

    /**
     * The production registry. Existing assignments remain stable when more handlers are added.
     */
    public static final CustomPacketRegistry INSTANCE = new CustomPacketRegistry(new ItemExaminePriceHandler());

    private final Map<Integer, CustomPacketHandler> handlers;

    /**
     * Creates a registry and rejects duplicate assignments during initialization.
     *
     * @param registrations The explicitly assigned handlers.
     */
    public CustomPacketRegistry(CustomPacketHandler... registrations) {
        Map<Integer, CustomPacketHandler> assigned = new HashMap<>();
        for (CustomPacketHandler handler : registrations) {
            Objects.requireNonNull(handler);
            if (handler.id() < 0 || assigned.putIfAbsent(handler.id(), handler) != null) {
                throw new IllegalArgumentException("Invalid or duplicate custom packet id " + handler.id());
            }
        }
        handlers = Map.copyOf(assigned);
    }

    /**
     * Applies the same codec and handler schema for preliminary classification and final decoding.
     *
     * @param msg The command frame.
     * @return A supported, schema-valid request without consuming bytes or executing its handler.
     */
    public Optional<CustomPacketRequest> decode(GameMessage msg) {
        return CustomPacketCodec.decode(msg).filter(request -> {
            CustomPacketHandler handler = handlers.get(request.id());
            return handler != null && handler.validate(request.payload());
        });
    }

    /**
     * @param request The schema-valid request.
     * @return Whether the assigned handler explicitly permits passive handling.
     */
    public boolean isPassive(CustomPacketRequest request) {
        CustomPacketHandler handler = handlers.get(request.id());
        return handler != null && handler.validate(request.payload()) && handler.isPassive();
    }

    /**
     * Dispatches a supported request from the normal reader handling phase.
     *
     * @param player The requesting player.
     * @param request The decoded request.
     */
    public void dispatch(Player player, CustomPacketRequest request) {
        CustomPacketHandler handler = handlers.get(request.id());
        if (handler != null && handler.validate(request.payload())) {
            handler.handle(player, request);
        }
    }
}
