package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} sent when a player clicks one of the options of an item on the ground.
 *
 * @author lare96
 */
public class GroundItemClickEvent extends PlayerEvent implements ControllableEvent, InteractableEvent {

    /**
     * An event sent when a player picks up an item.
     *
     * @author lare96
     */
    public static final class PickupItemEvent extends GroundItemClickEvent {

        /**
         * Creates a new {@link PickupItemEvent}.
         *
         * @param plr The player.
         * @param groundItem The ground item that was clicked.
         */
        public PickupItemEvent(Player plr, GroundItem groundItem) {
            super(plr, groundItem);
        }
    }

    /**
     * An event sent when a player clicks the second option of an item on the ground.
     *
     * @author lare96
     */
    public static final class GroundItemSecondClickEvent extends GroundItemClickEvent {

        /**
         * Creates a new {@link GroundItemSecondClickEvent}.
         *
         * @param plr The player.
         * @param groundItem The ground item that was clicked.
         */
        public GroundItemSecondClickEvent(Player plr, GroundItem groundItem) {
            super(plr, groundItem);
        }
    }

    /**
     * The ground item that was clicked.
     */
    private final GroundItem groundItem;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     */
    public GroundItemClickEvent(Player plr, GroundItem groundItem) {
        super(plr);
        this.groundItem = groundItem;
    }

    @Override
    public Entity target() {
        return groundItem;
    }

    /**
     * @return The ground item that was clicked.
     */
    public GroundItem getGroundItem() {
        return groundItem;
    }
}
