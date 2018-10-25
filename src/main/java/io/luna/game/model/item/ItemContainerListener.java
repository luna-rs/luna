package io.luna.game.model.item;

import java.util.Optional;

/**
 * A model representing a listener for an item container.
 *
 * @author lare96 <http://github.org/lare96>
 */
public interface ItemContainerListener {

    /**
     * Invoked when a standalone item has been updated.
     *
     * @param index The updated index.
     * @param items The container that fired the event.
     * @param oldItem The old item.
     * @param newItem The new item.
     */
    default void onSingleUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
    }

    /**
     * Invoked when an item has been updated during a bulk update operation.
     *
     * @param index The updated index.
     * @param items The container that fired the event.
     * @param oldItem The old item.
     * @param newItem The new item.
     */
    default void onBulkUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
    }

    /**
     * Invoked when a bulk update operation has completed.
     *
     * @param items The container that fired the event.
     */
    default void onBulkUpdateCompleted(ItemContainer items) {
    }

    /**
     * Invoked when the capacity of the container has been exceeded.
     *
     * @param items The container that fired the event.
     */
    default void onCapacityExceeded(ItemContainer items) {
    }

    /**
     * Invoked when the container is initialized.
     *
     * @param items The container that fired the event.
     */
    default void onInit(ItemContainer items) {

    }
}
