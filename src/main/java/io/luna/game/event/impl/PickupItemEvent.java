package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;

/**
 * An event sent when a player picks up an item.
 *
 * @author lare96
 */
public final class PickupItemEvent extends PlayerEvent implements ControllableEvent, InteractableEvent {

    /**
     * The ground item.
     */
    private final GroundItem targetItem;

    /**
     * Creates a new {@link PickupItemEvent}.
     *
     * @param targetItem The ground item.
     */
    public PickupItemEvent(Player player, GroundItem targetItem) {
        super(player);
        this.targetItem = targetItem;
    }

    @Override
    public Entity target() {
        return targetItem;
    }

    /**
     * @return The ground item.
     */
    public GroundItem getTargetItem() {
        return targetItem;
    }
}
