package io.luna.game.model.item;

import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;

/**
 * An item container model representing a player's inventory.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Inventory extends ItemContainer {

    /**
     * Creates a new {@link Inventory}.
     *
     * @param player The player.
     */
    public Inventory(Player player) {
        super(28, StackPolicy.STANDARD, 3214);

        setListeners(new PlayerRefreshListener(player, "You do not have enough space in your inventory."),
                new WeightListener(player));
    }
}
