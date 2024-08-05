package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An item-click based event. Not intended for interception.
 *
 * @author lare96
 */
public class ItemClickEvent extends PlayerEvent implements ControllableEvent {

    /**
     * An event sent when a player clicks an item's first index.
     *
     * @author lare96
     */
    public static final class ItemFirstClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFirstClickEvent}.
         */
        public ItemFirstClickEvent(Player player, int itemId, int slot, int interfaceId) {
            super(player, itemId, slot, interfaceId);
        }
    }

    /**
     * An event sent when a player clicks an item's second index.
     *
     * @author lare96
     */
    public static final class ItemSecondClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemSecondClickEvent}.
         */
        public ItemSecondClickEvent(Player player, int itemId, int slot, int interfaceId) {
            super(player, itemId, slot, interfaceId);
        }
    }

    /**
     * An event sent when a player clicks an item's third index.
     *
     * @author lare96
     */
    public static final class ItemThirdClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemThirdClickEvent}.
         */
        public ItemThirdClickEvent(Player player, int itemId, int slot, int interfaceId) {
            super(player, itemId, slot, interfaceId);
        }
    }

    /**
     * An event sent when a player clicks an item's fourth index.
     *
     * @author lare96
     */
    public static final class ItemFourthClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFourthClickEvent}.
         */
        public ItemFourthClickEvent(Player player, int id, int index, int interfaceId) {
            super(player, id, index, interfaceId);
        }
    }

    /**
     * An event sent when a player clicks an item's fifth index.
     *
     * @author lare96
     */
    public static final class ItemFifthClickEvent extends ItemClickEvent {

        /**
         * Creates a new {@link ItemFifthClickEvent}.
         */
        public ItemFifthClickEvent(Player player, int id, int index, int interfaceId) {
            super(player, id, index, interfaceId);
        }
    }

    /**
     * The identifier of the item clicked.
     */
    private final int id;

    /**
     * The index of the item clicked.
     */
    private final int index;

    /**
     * The identifier of the interface the item was clicked on.
     */
    private final int interfaceId;

    /**
     * Creates a new {@link ItemClickEvent}.
     *
     * @param player The player.
     * @param id The identifier of the item clicked.
     * @param index The index of the item clicked.
     * @param interfaceId The identifier of the interface the item was clicked on.
     */
    private ItemClickEvent(Player player, int id, int index, int interfaceId) {
        super(player);
        this.id = id;
        this.index = index;
        this.interfaceId = interfaceId;
    }

    /**
     * @return The identifier of the item clicked.
     */
    public final int getId() {
        return id;
    }

    /**
     * @return The index of the item clicked.
     */
    public final int getIndex() {
        return index;
    }

    /**
     * @return The identifier of the interface the item was clicked on.
     */
    public final int getInterfaceId() {
        return interfaceId;
    }
}