package io.luna.game.model.item;

import io.luna.game.model.mobile.Player;

/**
 * An item container model representing a player's inventory.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Inventory extends ItemContainer {

    /**
     * An adapter listening for inventory changes.
     */
    private final class InventoryListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link InventoryListener}.
         */
        public InventoryListener() {
            super(player);
        }

        @Override
        public int getWidgetId() {
            return INVENTORY_DISPLAY_ID;
        }

        @Override
        public String getCapacityExceededMsg() {
            return "You do not have enough space in your inventory.";
        }
    }

    /**
     * The inventory item display.
     */
    public static final int INVENTORY_DISPLAY_ID = 3214;

    /**
     * The player.
     */
    private final Player player;

    /**
     * Creates a new {@link Inventory}.
     *
     * @param player The player.
     */
    public Inventory(Player player) {
        super(28, StackPolicy.STANDARD);
        this.player = player;

        addListener(new InventoryListener());
        addListener(new ItemWeightListener(player));
    }
}
