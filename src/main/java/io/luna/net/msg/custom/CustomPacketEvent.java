package io.luna.net.msg.custom;

import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.event.impl.PlayerEvent;
import io.luna.game.model.mob.Player;

import java.util.Objects;

/**
 * A custom request dispatched through the normal game-thread reader and controller lifecycle.
 *
 * @author lare96
 */
public final class CustomPacketEvent extends PlayerEvent implements ControllableEvent {

    private final CustomPacketRequest request;

    /**
     * @param player The requesting player.
     * @param request The immutable request.
     */
    public CustomPacketEvent(Player player, CustomPacketRequest request) {
        super(player);
        this.request = Objects.requireNonNull(request);
    }

    /**
     * @return The immutable custom request.
     */
    public CustomPacketRequest getRequest() {
        return request;
    }
}
