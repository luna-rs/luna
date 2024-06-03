package io.luna.game.event.impl;

import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllableEvent;

/**
 * An event sent when a player picks up an item.
 *
 * @author lare96
 */
public final class PickupItemEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The ground item.
     */
    private final GroundItem item;

    /**
     * Creates a new {@link PickupItemEvent}.
     *
     * @param item The ground item.
     */
    public PickupItemEvent(Player player, GroundItem item) {
        super(player);
        this.item = item;
    }

    /**
     * @return The ground item.
     */
    public GroundItem getItem() {
        return item;
    }
}
