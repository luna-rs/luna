package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An event sent when a player clicks an item in the inventory to equip it.
 *
 * @author lare96
 */
public final class EquipItemEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The clicked index.
     */
    private final int index;

    /**
     * The item identifier clicked.
     */
    private final int itemId;

    /**
     * The interface containing the clicked index.
     */
    private final int interfaceId;

    /**
     * Creates a new {@link EquipItemEvent}.
     *
     * @param player      The player.
     * @param index       The clicked index.
     * @param itemId      The item identifier clicked.
     * @param interfaceId The interface containing the clicked index.
     */
    public EquipItemEvent(Player player, int index, int itemId, int interfaceId) {
        super(player);
        this.index = index;
        this.itemId = itemId;
        this.interfaceId = interfaceId;
    }

    /**
     * @return The clicked index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The item identifier clicked.
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * @return The interface containing the clicked index.
     */
    public int getInterfaceId() {
        return interfaceId;
    }
}