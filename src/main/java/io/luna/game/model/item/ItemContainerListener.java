package io.luna.game.model.item;

/**
 * A listener interface for reacting to {@link ItemContainer} lifecycle events and item mutations.
 * <p>
 * Containers invoke these callbacks to notify listeners of changes. Updates are separated into:
 * <ul>
 *     <li><b>Single updates</b>: standalone slot changes performed outside of a bulk operation.</li>
 *     <li><b>Bulk updates</b>: slot changes performed as part of a larger transaction, followed by a completion event.</li>
 * </ul>
 *
 * @author lare96
 */
public interface ItemContainerListener {

    /**
     * Invoked when a standalone item update occurs (not part of a bulk update).
     *
     * @param index The updated slot index.
     * @param items The container that fired the event.
     * @param oldItem The previous slot value.
     * @param newItem The new slot value.
     */
    default void onSingleUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
    }

    /**
     * Invoked when an item update occurs during a bulk update operation.
     * <p>
     * Implementations should typically defer expensive work until {@link #onBulkUpdateCompleted(ItemContainer)}.
     *
     * @param index The updated slot index.
     * @param items The container that fired the event.
     * @param oldItem The previous slot value.
     * @param newItem The new slot value.
     */
    default void onBulkUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
    }

    /**
     * Invoked after a bulk update operation has completed.
     * <p>
     * This is typically used as a "flush" event to apply work that was accumulated during bulk updates.
     *
     * @param items The container that fired the event.
     */
    default void onBulkUpdateCompleted(ItemContainer items) {
    }

    /**
     * Invoked when a capacity constraint is exceeded for the container.
     * <p>
     * This callback is typically fired when an item cannot be added due to container size restrictions.
     *
     * @param items The container that fired the event.
     */
    default void onCapacityExceeded(ItemContainer items) {
    }

    /**
     * Invoked when the container becomes initialized and is ready for normal use.
     * <p>
     * This is commonly used to snapshot initial container state or perform one-time setup logic.
     *
     * @param items The container that fired the event.
     */
    default void onInit(ItemContainer items) {
    }
}
