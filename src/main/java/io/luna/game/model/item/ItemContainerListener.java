package io.luna.game.model.item;

import java.util.Optional;

/**
 * A model representing a listener within an item container. One should aim to extend the adapter instead to
 * reduce boilerplate.
 *
 * @author lare96 <http://github.org/lare96>
 */
public interface ItemContainerListener {

    /**
     * Invoked after an item on one index is updated.
     *
     * @param items The container that fired the event.
     * @param oldItem The old item.
     * @param newItem The new item.
     * @param index The updated index.
     */
    default void onSingleUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
    }

    /**
     * Invoked after an item on one index is updated during a bulk operation.
     *
     * @param items The container that fired the event.
     * @param oldItem The old item.
     * @param newItem The new item.
     * @param index The updated index.
     */
    default void onBulkUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {

    }

    /**
     * Invoked after a series of bulk update invocations.
     *
     * @param items The container that fired the event.
     */
    default void onBulkUpdateCompleted(ItemContainer items) {
    }

    /**
     * Invoked when the capacity is exceeded.
     *
     * @param items The container that fired the event.
     */
    default void onCapacityExceeded(ItemContainer items) {
    }
}
