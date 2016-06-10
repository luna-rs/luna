package io.luna.game.model.item;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mobile.Player;

/**
 * An {@link ItemContainer} implementation that manages equipment for a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Equipment extends ItemContainer {

    /**
     * An {@link ItemContainerAdapter} implementation that listens for changes to equipment.
     */
    private static final class EquipmentListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link EquipmentListener}.
         *
         * @param player The {@link Player} this instance is dedicated to.
         */
        public EquipmentListener(Player player) {
            super(player);
        }

        @Override
        public int getWidgetId() {
            return EQUIPMENT_DISPLAY_ID;
        }

        @Override
        public String getCapacityExceededMsg() {
            throw new IllegalStateException(ERROR_MSG);
        }
    }

    /**
     * The head equipment index identifier.
     */
    public static final int HEAD = 0;

    /**
     * The cape equipment index identifier.
     */
    public static final int CAPE = 1;

    /**
     * The amulet equipment index identifier.
     */
    public static final int AMULET = 2;

    /**
     * The weapon equipment index identifier.
     */
    public static final int WEAPON = 3;

    /**
     * The chest equipment index identifier.
     */
    public static final int CHEST = 4;

    /**
     * The shield equipment index identifier.
     */
    public static final int SHIELD = 5;

    /**
     * The legs equipment index identifier.
     */
    public static final int LEGS = 7;

    /**
     * The hands equipment index identifier.
     */
    public static final int HANDS = 9;

    /**
     * The feet equipment index identifier.
     */
    public static final int FEET = 10;

    /**
     * The ring equipment index identifier.
     */
    public static final int RING = 12;

    /**
     * The ammunition equipment index identifier.
     */
    public static final int AMMUNITION = 13;

    /**
     * The size of all equipment instances.
     */
    public static final int SIZE = 11;

    /**
     * The error message printed when certain functions from the superclass are utilized.
     */
    private static final String ERROR_MSG = "Please use { equipment.set(index, Item) } instead";

    /**
     * An {@link ImmutableSet} containing equipment indexes that don't require appearance updates.
     */
    private static final ImmutableSet<Integer> NO_APPEARANCE = ImmutableSet.of(RING, AMMUNITION);

    /**
     * The equipment item display widget identifier.
     */
    private static final int EQUIPMENT_DISPLAY_ID = 1688;

    /**
     * The {@link Player} this instance is dedicated to.
     */
    private final Player player;

    /**
     * Creates a new {@link Equipment}.
     *
     * @param player The {@link Player} this instance is dedicated to.
     */
    public Equipment(Player player) {
        super(SIZE, StackPolicy.STANDARD);
        this.player = player;

        addListener(new EquipmentListener(player));
    }

    @Override
    public boolean add(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public boolean remove(Item item, int preferredIndex) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * Equips an {@link Item} from the underlying player's {@link Inventory}.
     *
     * @param inventoryIndex The {@code Inventory} index to equip the {@code Item} from.
     * @return {@code true} if the item was equipped, {@code false} otherwise.
     */
    public boolean equip(int inventoryIndex) {
        Inventory inventory = player.getInventory();
        Item equipItem = inventory.get(inventoryIndex);

        // TODO: Equipment definitions have to be done before this can be completed
        return true;
    }

    /**
     * Unequips an {@link Item} from the underlying player's {@code Equipment}.
     *
     * @param equipmentIndex The {@code Equipment} index to unequip the {@code Item} from.
     * @return {@code true} if the item was unequipped, {@code false} otherwise.
     */
    public boolean unequip(int equipmentIndex) {
        Item equipItem = get(equipmentIndex);

        // TODO: Equipment definitions have to be done before this can be completed
        return true;
    }

    /**
     * Forces a refresh of {@code Equipment} items to the {@code EQUIPMENT_DISPLAY_ID} widget.
     */
    private void forceRefresh() {
        player.queue(constructRefresh(EQUIPMENT_DISPLAY_ID));
    }
}
