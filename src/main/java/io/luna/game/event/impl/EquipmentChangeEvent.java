package io.luna.game.event.impl;

import io.luna.game.model.Locatable;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;

/**
 * An event sent when the equipment of a player changes.
 *
 * @author lare96
 */
public final class EquipmentChangeEvent extends PlayerEvent implements InjectableEvent {

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
     * @param player The player.
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

    @Override
    public Locatable contextLocatable(Bot bot) {
        return plr;
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
     * @return The new item on the index.
     */
    public Item getNewItem() {
        return newItem;
    }
}
