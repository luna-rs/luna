package io.luna.game.model.item;

import io.luna.game.model.mobile.Player;

/**
 * An {@link ItemContainer} implementation that manages the inventory for a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Inventory extends ItemContainer {

    /**
     * An {@link ItemContainerAdapter} implementation that listens for changes to the inventory.
     */
    private static final class InventoryListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link InventoryListener}.
         *
         * @param player The {@link Player} this instance is dedicated to.
         */
        public InventoryListener(Player player) {
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
     * The inventory item display widget identifier.
     */
    public static final int INVENTORY_DISPLAY_ID = 3214;

    /**
     * Creates a new {@link Inventory}.
     *
     * @param player The {@link Player} this instance is dedicated to.
     */
    public Inventory(Player player) {
        super(28, StackPolicy.STANDARD);

        addListener(new InventoryListener(player));
    }
}
