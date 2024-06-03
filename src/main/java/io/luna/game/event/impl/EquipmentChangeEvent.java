package io.luna.game.event.impl;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An event sent when the equipment of a player changes.
 *
 * @author lare96
 */
public final class EquipmentChangeEvent extends PlayerEvent {

    /**
     * The index.
     */
    private final int index;

    /**
     * The old item on the index (removed item).
     */
    private final Optional<Item> oldItem;

    /**
     * The new item on the index (added item).
     */
    private final Optional<Item> newItem;

    /**
     * Creates a new {@link EquipmentChangeEvent}.
     *
     * @param player The player.
     * @param index The index.
     * @param oldItem The old item on the index.
     * @param newItem The new item on the index.
     */
    public EquipmentChangeEvent(Player player, int index, Optional<Item> oldItem, Optional<Item> newItem) {
        super(player);
        this.index = index;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    /**
     * @return The index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The old item on the index.
     */
    public Item getOldItem() {
        return oldItem.orElse(null);
    }

    /**
     * @return The old item identifier on the index.
     */
    public OptionalInt getOldId() {
        return oldItem.map(item -> OptionalInt.of(item.getId())).orElseGet(OptionalInt::empty);
    }

    /**
     * @return The new item on the index.
     */
    public Item getNewItem() {
        return newItem.orElse(null);
    }

    /**
     * @return The new item identifier on the index.
     */
    public OptionalInt getNewId() {
        return newItem.map(item -> OptionalInt.of(item.getId())).orElseGet(OptionalInt::empty);
    }
}
