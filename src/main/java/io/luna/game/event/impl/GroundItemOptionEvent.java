package io.luna.game.event.impl;

import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;

/**
 * An event sent when a player picks the first or second ground option of an item that has its own ground options,
 * such as the Telekinetic Theatre statue's "Observe" and "Reset". The player is not walked to the item first.
 *
 * @author TheLining
 */
public final class GroundItemOptionEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The ground item that was clicked.
     */
    private final GroundItem groundItem;

    /**
     * The ground option picked, 0 for the first and 1 for the second.
     */
    private final int option;

    /**
     * Creates a new {@link GroundItemOptionEvent}.
     *
     * @param plr The player.
     * @param groundItem The ground item that was clicked.
     * @param option The ground option picked, 0 or 1.
     */
    public GroundItemOptionEvent(Player plr, GroundItem groundItem, int option) {
        super(plr);
        this.groundItem = groundItem;
        this.option = option;
    }

    /**
     * @return The ground item that was clicked.
     */
    public GroundItem getGroundItem() {
        return groundItem;
    }

    /**
     * @return The ground option picked, 0 or 1.
     */
    public int getOption() {
        return option;
    }
}
