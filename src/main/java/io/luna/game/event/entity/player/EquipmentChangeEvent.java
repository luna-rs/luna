package io.luna.game.event.entity.player;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

import java.util.OptionalInt;

/**
 * An event sent when the equipment of a player changes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentChangeEvent extends PlayerEvent {

    /**
     * The index.
     */
    private final int index;

    /**
     * The old item on the index (removed item).
     */
    private final Item oldItem;

    /**
     * The new item on the index (added item).
     */
    private final Item newItem;

    /**
     * Creates a new {@link EquipmentChangeEvent}.
     *
     * @param index The index.
     * @param oldItem The old item on the index.
     * @param newItem The new item on the index.
     */
    public EquipmentChangeEvent(Player player, int index, Item oldItem, Item newItem) {
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
        return oldItem;
    }

    /**
     * @return The old item identifier on the index.
     */
    public OptionalInt getOldId() {
        return oldItem != null ? OptionalInt.of(oldItem.getId()) : OptionalInt.empty();
    }

    /**
     * @return The new item on the index.
     */
    public Item getNewItem() {
        return newItem;
    }

    /**
     * @return The new item identifier on the index.
     */
    public OptionalInt getNewId() {
        return newItem != null ? OptionalInt.of(newItem.getId()) : OptionalInt.empty();
    }
}
