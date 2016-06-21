package io.luna.game.model.item;

import java.util.Optional;

/**
 * A listener that is fired by {@link ItemContainer}. One should aim to extend {@link ItemContainerAdapter} for generic use
 * cases rather than implement this directly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public interface ItemContainerListener {

    /**
     * Fired when an {@link Item} is added, removed, or replaced.
     *
     * @param container The {@link ItemContainer} firing the event.
     * @param index The index the update is occurring on.
     */
    default void itemUpdated(ItemContainer container, Optional<Item> oldItem, Optional<Item> newItem, int index) {
    }

    /**
     * Fired when an {@link Item}s are added, removed, or replaced in bulk. This is to prevent firing multiple {@code
     * itemUpdated(ItemContainer, int)} events for a single operation.
     *
     * @param container The {@link ItemContainer} firing the event.
     */
    default void bulkItemsUpdated(ItemContainer container) {
    }

    /**
     * Fired when the capacity of {@code container} is exceeded.
     *
     * @param container The {@link ItemContainer} firing the event.
     */
    default void capacityExceeded(ItemContainer container) {
    }
}
