package io.luna.game.event.impl;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} implementation that allows a player to arrange an {@link Item} on an {@link ItemContainer}.
 *
 * @author lare96
 */
public final class ArrangeItemEvent extends PlayerEvent {

    /**
     * Move from index.
     */
    private final int fromIndex;

    /**
     * Move to index.
     */
    private final int toIndex;

    /**
     * The widget ID.
     */
    private final int widgetId;

    /**
     * The insertion mode.
     */
    private final int insertionMode;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     * @param fromIndex Move from index.
     * @param toIndex Move to index.
     * @param widgetId The widget ID.
     * @param insertionMode The insertion mode.
     */
    public ArrangeItemEvent(Player plr, int fromIndex, int toIndex, int widgetId, int insertionMode) {
        super(plr);
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.widgetId = widgetId;
        this.insertionMode = insertionMode;
    }

    /**
     * @return Move from index.
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * @return Move to index.
     */
    public int getToIndex() {
        return toIndex;
    }

    /**
     * @return The insertion mode.
     */
    public int getInsertionMode() {
        return insertionMode;
    }

    /**
     * @return The widget ID.
     */
    public int getWidgetId() {
        return widgetId;
    }
}
