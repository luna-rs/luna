package io.luna.game.model.item;

import game.player.Messages;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;

/**
 * An {@link ItemContainer} implementation representing a player's inventory.
 * <p>
 * Inventories have a fixed capacity of 28 slots and use {@link StackPolicy#STANDARD} stacking rules (only items
 * marked stackable in {@link ItemDefinition} will stack).
 * <p>
 * This container is bound to the standard inventory widget (id {@code 3214}) and installs listeners that:
 * <ul>
 *   <li>Refresh the owning player's inventory widget when items change</li>
 *   <li>Notify the player when capacity is exceeded ("inventory is full")</li>
 *   <li>Recalculate and/or update player weight when inventory contents change</li>
 * </ul>
 * <p>
 * All inventory mutations should be performed through inherited {@link ItemContainer} methods (e.g.,
 * {@link #add(Item)}, {@link #remove(Item)}, {@link #clear()}, etc.) so that size bookkeeping and listeners remain correct.
 *
 * @author lare96
 */
public final class Inventory extends ItemContainer {

    /**
     * The message sent to the player when an add operation cannot proceed due to full inventory.
     */
    public static final String INVENTORY_FULL_MESSAGE = Messages.INVENTORY_FULL.getText();

    /**
     * Creates a new {@link Inventory} for {@code player}.
     * <p>
     * Registers listeners that automatically keep the inventory widget in sync and respond to capacity/weight changes.
     *
     * @param player The owning player.
     */
    public Inventory(Player player) {
        super(28, StackPolicy.STANDARD, 3214);
        setListeners(new PlayerRefreshListener(player, INVENTORY_FULL_MESSAGE), new WeightListener(player));
    }
}
