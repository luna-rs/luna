package io.luna.game.model.item;

import java.util.function.Consumer;

/**
 * An {@link ItemContainerListener} that tracks items added to and removed from an {@link ItemContainer}.
 * <p>
 * When an item changes, the previous item is removed from tracking before the replacement is added. Subclasses
 * determine how these changes are handled by implementing {@link #add(Item)} and {@link #remove(Item)}.
 *
 * @author lare96
 */
public abstract class ItemTracker implements ItemContainerListener {

    /**
     * Creates an {@link ItemTracker} that delegates item additions and removals to the supplied consumers.
     *
     * @param add The consumer invoked when an item is added.
     * @param remove The consumer invoked when an item is removed.
     * @return The newly created item tracker.
     */
    public static ItemTracker create(Consumer<Item> add, Consumer<Item> remove) {
        return new ItemTracker() {
            @Override
            public void add(Item newItem) {
                add.accept(newItem);
            }

            @Override
            public void remove(Item oldItem) {
                remove.accept(oldItem);
            }
        };
    }

    /**
     * Initializes tracking for all existing non-null items in the container.
     *
     * @param items The container being initialized.
     */
    @Override
    public final void onInit(ItemContainer items) {
        for (Item item : items) {
            if (item != null) {
                add(item);
            }
        }
    }

    /**
     * Updates tracking when a single item slot changes.
     *
     * @param index The index of the updated slot.
     * @param items The container containing the updated slot.
     * @param oldItem The item previously occupying the slot, or {@code null} if empty.
     * @param newItem The item now occupying the slot, or {@code null} if empty.
     */
    @Override
    public final void onSingleUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        update(oldItem, newItem);
    }

    /**
     * Updates tracking for an item slot reported during a bulk container update.
     *
     * @param index The index of the updated slot.
     * @param items The container containing the updated slot.
     * @param oldItem The item previously occupying the slot, or {@code null} if empty.
     * @param newItem The item now occupying the slot, or {@code null} if empty.
     */
    @Override
    public final void onBulkUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        update(oldItem, newItem);
    }

    /**
     * Handles an item being added to the tracked container.
     *
     * @param newItem The item being added.
     */
    public abstract void add(Item newItem);

    /**
     * Handles an item being removed from the tracked container.
     *
     * @param oldItem The item being removed.
     */
    public abstract void remove(Item oldItem);

    /**
     * Updates tracking by removing the previous item and adding its replacement.
     * <p>
     * Null items are ignored. When both items are non-null, removal is processed before addition.
     *
     * @param oldItem The previous item, or {@code null} if the slot was empty.
     * @param newItem The replacement item, or {@code null} if the slot is now empty.
     */
    private void update(Item oldItem, Item newItem) {
        if (oldItem != null) {
            remove(oldItem);
        }
        if (newItem != null) {
            add(newItem);
        }
    }
}