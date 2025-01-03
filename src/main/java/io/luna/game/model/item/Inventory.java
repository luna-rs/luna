package io.luna.game.model.item;

import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import world.player.Messages;

/**
 * An item container model representing a player's inventory.
 *
 * @author lare96 
 */
public final class Inventory extends ItemContainer {

    /**
     * The message sent when the inventory is full.
     */
    public static final String INVENTORY_FULL_MESSAGE = Messages.INVENTORY_FULL.getText();

    /**
     * Creates a new {@link Inventory}.
     *
     * @param player The player.
     */
    public Inventory(Player player) {
        super(28, StackPolicy.STANDARD, 3214);

        setListeners(new PlayerRefreshListener(player, INVENTORY_FULL_MESSAGE),
                new WeightListener(player));
    }
}
