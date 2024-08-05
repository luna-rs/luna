package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An event sent when a player drops an item from the inventory.
 *
 * @author lare96
 */
public final class DropItemEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The item identifier.
     */
    private final int itemId;

    /**
     * The widget identifier.
     */
    private final int widgetId;

    /**
     * The index of the item.
     */
    private final int index;

    /**
     * Creates a new {@link DropItemEvent}.
     *
     * @param player The player.
     * @param itemId The item identifier.
     * @param widgetId The widget identifier.
     * @param index The index of the item.
     */
    public DropItemEvent(Player player, int itemId, int widgetId, int index) {
        super(player);
        this.itemId = itemId;
        this.widgetId = widgetId;
        this.index = index;
    }

    /**
     * @return The item identifier.
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * @return The widget identifier.
     */
    public int getWidgetId() {
        return widgetId;
    }

    /**
     * @return The index of the item.
     */
    public int getIndex() {
        return index;
    }
}