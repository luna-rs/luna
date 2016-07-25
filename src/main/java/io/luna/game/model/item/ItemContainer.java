package io.luna.game.model.item;

import com.google.common.collect.Iterables;
import io.luna.game.model.def.ItemDefinition;
import io.luna.net.msg.out.WidgetItemGroupMessageWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.item.ItemContainer.StackPolicy.ALWAYS;
import static io.luna.game.model.item.ItemContainer.StackPolicy.STANDARD;

/**
 * An abstraction model representing a group of {@link Item}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ItemContainer implements Iterable<Item> {

    // TODO: Unit tests for various functions

    /**
     * An {@link Iterator} implementation for this container.
     */
    private static final class ItemContainerIterator implements Iterator<Item> {

        /**
         * The container instance to iterate over.
         */
        private final ItemContainer container;

        /**
         * The current index being iterated over.
         */
        private int index;

        /**
         * The last index that was iterated over.
         */
        private int lastIndex = -1;

        /**
         * Creates a new {@link ItemContainerIterator}.
         *
         * @param container The container instance to iterate over.
         */
        public ItemContainerIterator(ItemContainer container) {
            this.container = container;
        }

        @Override
        public boolean hasNext() {
            return (index + 1) <= container.capacity;
        }

        @Override
        public Item next() {
            checkState(index < container.capacity, "no more elements left to iterate");

            lastIndex = index;
            index++;
            return container.items[lastIndex];
        }

        @Override
        public void remove() {
            checkState(lastIndex != -1, "can only be called once after 'next'");

            Item oldItem = container.items[lastIndex];

            container.items[lastIndex] = null;
            container.size--;

            container.fireItemUpdatedEvent(oldItem, null, lastIndex);

            index = lastIndex;
            lastIndex = -1;
        }
    }

    /**
     * An enumerated type defining policies for stackable {@link Item}s.
     */
    public enum StackPolicy {

        /**
         * The {@code STANDARD} policy, items are only stacked if they are defined as stackable in their {@link
         * ItemDefinition} table.
         */
        STANDARD,

        /**
         * The {@code ALWAYS} policy, items are always stacked regardless of their {@link ItemDefinition} table.
         */
        ALWAYS,

        /**
         * The {@code NEVER} policy, items are never stacked regardless of their {@link ItemDefinition} table.
         */
        NEVER
    }

    /**
     * An {@link ArrayList} of {@link ItemContainerListener}s listening for various events.
     */
    private final List<ItemContainerListener> listeners = new ArrayList<>();

    /**
     * The capacity of this container.
     */
    private final int capacity;

    /**
     * The policy of this container.
     */
    private final StackPolicy policy;

    /**
     * The {@link Item}s within this container.
     */
    private final Item[] items;

    /**
     * The amount of non-{@code null} values within this container.
     */
    private int size;

    /**
     * If events are currently being fired.
     */
    private boolean firingEvents = true;

    /**
     * Creates a new {@link ItemContainer}.
     *
     * @param capacity The capacity of this container.
     * @param policy The {@link Item}s within this container.
     */
    public ItemContainer(int capacity, StackPolicy policy) {
        this.capacity = capacity;
        this.policy = policy;
        items = new Item[capacity];
    }

    /**
     * Iterates through all of the {@link Item}s within this container and performs {@code action} on them, skipping empty
     * indexes ({@code null} values) as they are encountered.
     */
    @Override
    public final void forEach(Consumer<? super Item> action) {
        Objects.requireNonNull(action);
        for (Item item : items) {
            if (item == null) {
                continue;
            }
            action.accept(item);
        }
    }

    @Override
    public final Spliterator<Item> spliterator() {
        return Spliterators.spliterator(items, Spliterator.ORDERED);
    }

    @Override
    public final Iterator<Item> iterator() {
        return new ItemContainerIterator(this);
    }

    /**
     * @return A stream associated with the elements in this container, built using the {@code spliterator()} implementation.
     */
    public final Stream<Item> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Attempts to add {@code item} into this container, preferably at {@code preferredIndex}.
     *
     * @param item The {@link Item} to add.
     * @param preferredIndex The preferable index to add {@code item} to.
     * @return {@code true} if the {@code Item} was added, {@code false} if there was not enough space left.
     */
    public boolean add(Item item, int preferredIndex) {
        checkArgument(preferredIndex >= -1, "invalid index identifier");

        ItemDefinition def = item.getItemDef();
        boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

        if (stackable) {
            preferredIndex = computeIndexForId(item.getId());
        } else if (preferredIndex != -1) {
            preferredIndex = items[preferredIndex] != null ? -1 : preferredIndex;
        }
        preferredIndex = preferredIndex == -1 ? computeFreeIndex() : preferredIndex;

        if (preferredIndex == -1) { // Not enough space in container.
            fireCapacityExceededEvent();
            return false;
        }

        if (stackable) {
            Item current = items[preferredIndex];
            items[preferredIndex] = (current == null) ? item : current.createAndIncrement(item.getAmount());
            size++;

            fireItemUpdatedEvent(current, items[preferredIndex], preferredIndex);
        } else {
            int remaining = computeRemainingSize();
            int until = (remaining > item.getAmount()) ? item.getAmount() : remaining;

            for (int index = 0; index < until; index++) {
                preferredIndex = (items[preferredIndex] == null) ? preferredIndex : computeFreeIndex();

                Item newItem = new Item(item.getId());
                items[preferredIndex] = newItem;
                size++;

                fireItemUpdatedEvent(null, newItem, preferredIndex++);
            }
        }
        return true;
    }

    /**
     * Attempts to add {@code item} into this container.
     *
     * @param item The {@link Item} to add.
     * @return {@code true} the {@code Item} was added, {@code false} if there was not enough space left.
     */
    public boolean add(Item item) {
        return add(item, -1);
    }

    /**
     * Attempts to add {@code items} in bulk into this container.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(Collection<? extends Item> items) {
        if (items.size() == 1) { // Bulk operation on singleton list? No thanks..
            Optional<? extends Item> item = items.stream().
                filter(Objects::nonNull).
                findFirst();
            return item.isPresent() && add(item.get());
        }

        firingEvents = false;

        boolean added = false;
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (add(item)) {
                    added = true;
                }
            }
        } finally {
            firingEvents = true;
        }
        fireBulkItemsUpdatedEvent();
        return added;
    }

    /**
     * Attempts to add {@code items} in bulk into this container.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(Item... items) {
        return addAll(Arrays.asList(items));
    }

    /**
     * Attempts to add {@code items} in bulk into this container.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(ItemContainer items) {
        return addAll(items.items);
    }

    /**
     * Attempts to remove {@code item} from this container, preferably from {@code preferredIndex}.
     *
     * @param item The {@link Item} to remove.
     * @param preferredIndex The preferable index to remove {@code item} from.
     * @return {@code true} if the {@code Item} was removed, {@code false} if it isn't present in this container.
     */
    public boolean remove(Item item, int preferredIndex) {
        checkArgument(preferredIndex >= -1, "invalid index identifier");

        ItemDefinition def = item.getItemDef();
        boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

        if (stackable) {
            preferredIndex = computeIndexForId(item.getId());
        } else {
            preferredIndex = preferredIndex == -1 ? computeIndexForId(item.getId()) : preferredIndex;

            if (preferredIndex != -1 && items[preferredIndex] == null) {
                preferredIndex = -1;
            }
        }

        if (preferredIndex == -1) { // Item isn't present within this container.
            return false;
        }

        if (stackable) {
            Item current = items[preferredIndex];
            if (current.getAmount() > item.getAmount()) {
                items[preferredIndex] = current.createAndDecrement(item.getAmount());
            } else {
                items[preferredIndex] = null;
                size--;
            }

            fireItemUpdatedEvent(current, items[preferredIndex], preferredIndex);
        } else {
            int until = computeAmountForId(item.getId());
            until = (item.getAmount() > until) ? until : item.getAmount();

            for (int index = 0; index < until; index++) {
                preferredIndex =
                    (items[preferredIndex] != null && items[preferredIndex].getId() == item.getId()) ? preferredIndex :
                        computeIndexForId(item.getId());

                Item oldItem = items[preferredIndex];
                items[preferredIndex] = null;
                size--;

                fireItemUpdatedEvent(oldItem, null, preferredIndex++);
            }
        }
        return true;
    }

    /**
     * Attempts to remove {@code item} from this container.
     *
     * @param item The {@link Item} to remove.
     * @return {@code true} if the {@code Item} was removed, {@code false} if it isn't present in this container.
     */
    public boolean remove(Item item) {
        return remove(item, -1);
    }

    /**
     * Attempts to remove {@code items} in bulk from this container.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(Collection<? extends Item> items) {
        if (items.size() == 1) { // Bulk operation on singleton list? No thanks..
            Optional<? extends Item> item = items.stream().
                filter(Objects::nonNull).
                findFirst();
            return item.isPresent() && remove(item.get());
        }

        firingEvents = false;
        boolean removed = false;
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (remove(item)) {
                    removed = true;
                }
            }
        } finally {
            firingEvents = true;
        }
        fireBulkItemsUpdatedEvent();
        return removed;
    }

    /**
     * Attempts to remove {@code items} in bulk from this container.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(Item... items) {
        return removeAll(Arrays.asList(items));
    }

    /**
     * Attempts to remove {@code items} in bulk from this container.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(ItemContainer items) {
        return removeAll(items.items);
    }

    /**
     * Computes the next free ({@code null}) index in this container.
     *
     * @return The free index, {@code -1} if no free indexes could be found.
     */
    public final int computeFreeIndex() {
        for (int index = 0; index < capacity; index++) {
            if (items[index] == null) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Computes the first index found that {@code id} is in.
     *
     * @param id The identifier to compute for.
     * @return The first index found, {@code -1} if no {@link Item} with {@code id} is in this container.
     */
    public final int computeIndexForId(int id) {
        for (int index = 0; index < capacity; index++) {
            Optional<Item> optional = Optional.ofNullable(items[index]).
                filter(item -> item.getId() == id);
            if (optional.isPresent()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Computes the total quantity of the {@link Item}s in this container with {@code id}.
     *
     * @param id The identifier of the {@code Item} to determine the total quantity of.
     * @return The total quantity.
     */
    public final int computeAmountForId(int id) {
        int amount = 0;
        for (Item item : items) {
            if (item == null || item.getId() != id) {
                continue;
            }
            amount += item.getAmount();
        }
        return amount;
    }

    /**
     * Computes the identifier of the {@link Item} on {@code index}.
     *
     * @param index The index to compute the identifier for.
     * @return The identifier wrapped in an optional.
     */
    public final Optional<Integer> computeIdForIndex(int index) {
        return retrieve(index).map(Item::getId);
    }

    /**
     * Replaces the first occurrence of the {@link Item} having the identifier {@code oldId} with {@code newId}.
     *
     * @param oldId The old identifier to replace.
     * @param newId The new identifier to replace.
     * @return {@code true} if the replace operation was successful, {@code false otherwise}.
     */
    public final boolean replace(int oldId, int newId) {
        int index = computeIndexForId(oldId);
        if (index == -1) {
            return false;
        }

        Item oldItem = items[index];
        Item newItem = oldItem.createWithId(newId);

        return remove(oldItem, index) && add(newItem, index);
    }

    /**
     * Replaces all occurrences of {@link Item}s having the identifier {@code oldId} with {@code newId}.
     *
     * @param oldId The old identifier to replace.
     * @param newId The new identifier to replace.
     * @return {@code true} if the replace operation was successful at least once, {@code false otherwise}.
     */
    public final boolean replaceAll(int oldId, int newId) {
        boolean replaced = false;

        firingEvents = false;
        try {
            while (replace(oldId, newId)) {
                replaced = true;
            }
        } finally {
            firingEvents = true;
        }
        fireBulkItemsUpdatedEvent();
        return replaced;
    }

    /**
     * Computes the amount of indexes required to hold {@code items} in this container.
     *
     * @param items The items to compute the index count for.
     * @return The index count.
     */
    public final int computeIndexCount(Item... items) {
        int indexCount = 0;
        for (Item item : items) {
            ItemDefinition def = item.getItemDef();
            boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

            if (stackable) {
                int index = computeIndexForId(item.getId());
                if (index == -1) {
                    indexCount++;
                    continue;
                }

                Item existing = items[index];
                if ((existing.getAmount() + item.getAmount()) <= 0) {
                    indexCount++;
                    continue;
                }
            }
            indexCount += item.getAmount();
        }
        return indexCount;
    }

    /**
     * Determines if this container has the capacity for {@code item}.
     *
     * @param item The {@link Item} to determine this for.
     * @return {@code true} if {@code item} can be added, {@code false} otherwise.
     */
    public final boolean hasCapacityFor(Item item) {
        int indexCount = computeIndexCount(item);
        return computeRemainingSize() >= indexCount;
    }

    /**
     * Determines if this container contains {@code id}.
     *
     * @param id The identifier to check this container for.
     * @return {@code true} if this container has {@code id}, {@code false} otherwise.
     */
    public final boolean contains(int id) {
        return computeIndexForId(id) != -1;
    }

    /**
     * Determines if this container contains all {@code identifiers}.
     *
     * @param identifiers The identifiers to check this container for.
     * @return {@code true} if this container has all {@code identifiers}, {@code false} otherwise.
     */
    public final boolean containsAll(int... identifiers) {
        return Arrays.stream(identifiers).allMatch(this::contains);
    }

    /**
     * Determines if this container contains any {@code identifiers}.
     *
     * @param identifiers The identifiers to check this container for.
     * @return {@code true} if this container has any {@code identifiers}, {@code false} otherwise.
     */
    public final boolean containsAny(int... identifiers) {
        return Arrays.stream(identifiers).anyMatch(this::contains);
    }

    /**
     * Determines if this container contains {@code item}.
     *
     * @param item The {@link Item} to check this container for.
     * @return {@code true} if this container has {@code item}, {@code false} otherwise.
     */
    public final boolean contains(Item item) {
        return stream().filter(Objects::nonNull)
            .anyMatch(it -> it.getId() == item.getId() && it.getAmount() >= item.getAmount());
    }

    /**
     * Determines if this container contains all {@code items}.
     *
     * @param items The {@link Item}s to check this container for.
     * @return {@code true} if this container has all {@code items}, {@code false} otherwise.
     */
    public final boolean containsAll(Item... items) {
        return containsAll(Arrays.asList(items));
    }

    /**
     * Determines if this container contains all {@code items}.
     *
     * @param items The {@link Item}s to check this container for.
     * @return {@code true} if this container has all {@code items}, {@code false} otherwise.
     */
    public final boolean containsAll(Collection<Item> items) {
        return items.stream().filter(Objects::nonNull).allMatch(this::contains);
    }

    /**
     * Determines if this container contains any {@code items}.
     *
     * @param items The {@link Item}s to check this container for.
     * @return {@code true} if this container has all {@code items}, {@code false} otherwise.
     */
    public final boolean containsAny(Item... items) {
        return containsAny(Arrays.asList(items));
    }

    /**
     * Determines if this container contains any {@code items}.
     *
     * @param items The {@link Item}s to check this container for.
     * @return {@code true} if this container has all {@code items}, {@code false} otherwise.
     */
    public final boolean containsAny(Collection<Item> items) {
        return items.stream().filter(Objects::nonNull).anyMatch(this::contains);
    }

    /**
     * Constructs a {@link WidgetItemGroupMessageWriter} that when encoded and sent to the client will display the items in
     * this container on {@code widget}.
     *
     * @param widget The widget to send the {@code Item}s on.
     * @return The constructed {@code SendWidgetItemGroupMessage}.
     */
    public final WidgetItemGroupMessageWriter constructRefresh(int widget) {
        return new WidgetItemGroupMessageWriter(widget, items);
    }

    /**
     * Swaps the {@link Item}s on {@code oldIndex} and {@code newIndex}.
     *
     * @param insert If the {@code Item} should be inserted.
     * @param oldIndex The old index.
     * @param newIndex The new index.
     */
    public final void swap(boolean insert, int oldIndex, int newIndex) {
        checkArgument(oldIndex >= 0 && oldIndex < capacity, "oldIndex out of range");
        checkArgument(newIndex >= 0 && oldIndex < capacity, "newIndex out of range");

        if (insert) {
            if (newIndex > oldIndex) {
                for (int index = oldIndex; index < newIndex; index++) {
                    swap(index, index + 1);
                }
            } else if (oldIndex > newIndex) {
                for (int index = oldIndex; index > newIndex; index--) {
                    swap(index, index - 1);
                }
            }
        } else {
            Item itemOld = items[oldIndex];
            Item itemNew = items[newIndex];

            items[oldIndex] = itemNew;
            items[newIndex] = itemOld;

            fireItemUpdatedEvent(itemOld, items[oldIndex], oldIndex);
            fireItemUpdatedEvent(itemNew, items[newIndex], newIndex);
        }
    }

    /**
     * Swaps the {@link Item}s on {@code oldIndex} and {@code newIndex}.
     *
     * @param oldIndex The old index.
     * @param newIndex The new index.
     */
    public final void swap(int oldIndex, int newIndex) {
        swap(false, oldIndex, newIndex);
    }

    /**
     * Shifts {@link Item}s to the left to fill any empty ({@code null}) indexes.
     */
    public final void shift() {
        Item[] newItems = new Item[capacity];
        int newIndex = 0;

        for (Item item : items) {
            if (item == null) {
                continue;
            }
            newItems[newIndex++] = item;
        }

        setItems(newItems);
    }

    /**
     * Sets the container of items to {@code items}. The container will not hold any references to the array.
     *
     * @param newItems The new array of items, the capacity of this must be equal to or lesser than the container.
     */
    public final void setItems(Item[] newItems) {
        checkArgument(newItems.length <= capacity, "newItems.length must be <= capacity");
        System.arraycopy(newItems, 0, items, 0, capacity);
    }

    /**
     * Sets the container of items to {@code newItems}. The only difference between this and {@code setItems(Item[])} is that
     * this method takes a wrapper class named {@link IndexedItem} that holds the index of items.
     *
     * @param newItems The new array of items.
     */
    public final void setIndexedItems(IndexedItem[] newItems) {
        Arrays.fill(items, null);
        size = 0;
        for (IndexedItem item : newItems) {
            items[item.getIndex()] = new Item(item.getId(), item.getAmount());
            size++;
        }
    }

    /**
     * Returns a <strong>shallow copy</strong> of the backing array. Changes made to the returned array will not be reflected
     * on the backing array.
     *
     * @return A shallow copy of the backing array.
     */
    public final Item[] toArray() {
        return Arrays.copyOf(items, items.length);
    }

    /**
     * Returns an array of {@link IndexedItem}s describing the contents of the backing array. Changes made to the returned
     * array will, of course, not be reflected on the backing array.
     *
     * @return An array of {@code IndexedItem}s describing the contents of the backing array.
     */
    public final IndexedItem[] toIndexedArray() {
        List<IndexedItem> indexedItems = new LinkedList<>();
        for (int index = 0; index < capacity; index++) {
            Item item = items[index];
            if (item == null) {
                continue;
            }
            indexedItems.add(new IndexedItem(item, index));
        }
        return Iterables.toArray(indexedItems, IndexedItem.class);
    }

    /**
     * Sets the {@code index} to {@code item}.
     *
     * @param index The index to set.
     * @param item The {@link Item} to set on the index.
     */
    public final void set(int index, Item item) {
        boolean indexFree = items[index] == null;
        boolean removingItem = item == null;

        if (indexFree && !removingItem) {
            size++;
        } else if (!indexFree && removingItem) {
            size--;
        }

        Item oldItem = items[index];
        items[index] = item;

        fireItemUpdatedEvent(oldItem, items[index], index);
    }

    /**
     * Retrieves the item located on {@code index}.
     *
     * @param index the index to get the item on.
     * @return the item on the index, or {@code null} if no item exists on the index.
     */
    public final Optional<Item> retrieve(int index) {
        if (index == -1 || index >= items.length)
            return Optional.empty();
        return Optional.ofNullable(items[index]);
    }

    /**
     * Gets the {@link Item} located on {@code index}.
     *
     * @param index The index to get the {@code Item} on.
     * @return The {@code Item} instance, {@code null} if the index is empty.
     */
    public final Item get(int index) {
        return retrieve(index).orElse(null);
    }

    /**
     * Returns {@code true} if {@code index} is occupied (non-{@code null}).
     */
    public final boolean indexOccupied(int index) {
        return retrieve(index).isPresent();
    }

    /**
     * Returns {@code true} if all {@code indexes} are occupied (non-{@code null}).
     */
    public final boolean allIndexesOccupied(int... indexes) {
        for (int index : indexes) {
            if (!indexOccupied(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code index} is not occupied ({@code null}).
     */
    public final boolean indexFree(int index) {
        return !indexOccupied(index);
    }

    /**
     * Returns {@code true} if all {@code indexes} are free ({@code null}).
     */
    public final boolean allIndexesFree(int... indexes) {
        for (int index : indexes) {
            if (!indexFree(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes all of the items from this container.
     */
    public final void clear() {
        Arrays.fill(items, null);
        size = 0;

        fireBulkItemsUpdatedEvent();
    }

    /**
     * Adds an {@link ItemContainerListener} to this container.
     *
     * @param listener The listener to add to this container.
     * @return {@code true} if the listener was added, {@code false} otherwise.
     */
    public final boolean addListener(ItemContainerListener listener) {
        return listeners.add(listener);
    }

    /**
     * Removes an {@link ItemContainerListener} from this container.
     *
     * @param listener The listener to remove from this container.
     * @return {@code true} if the listener was removed, {@code false} otherwise.
     */
    public final boolean removeListener(ItemContainerListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Fires the {@code ItemContainerListener.itemUpdated(ItemContainer, int)} event.
     */
    public final void fireItemUpdatedEvent(Item oldItem, Item newItem, int index) {
        if (firingEvents) {
            listeners
                .forEach(evt -> evt.itemUpdated(this, Optional.ofNullable(oldItem), Optional.ofNullable(newItem), index));
        }
    }

    /**
     * Fires the {@code ItemContainerListener.bulkItemsUpdated(ItemContainer)} event.
     */
    public final void fireBulkItemsUpdatedEvent() {
        if (firingEvents) {
            listeners.forEach(evt -> evt.bulkItemsUpdated(this));
        }
    }

    /**
     * Fires the {@code ItemContainerListener.capacityExceeded(ItemContainer)} event.
     */
    public final void fireCapacityExceededEvent() {
        if (firingEvents) {
            listeners.forEach(evt -> evt.capacityExceeded(this));
        }
    }

    /**
     * @return {@code true} if events are currnetly being fired, {@code false otherwise}.
     */
    public boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Sets the value for {@link #firingEvents}.
     */
    public void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }

    /**
     * @return The amount of remaining free indexes.
     */
    public final int computeRemainingSize() {
        return capacity - size;
    }

    /**
     * @return The amount of used indexes.
     */
    public final int getSize() {
        return size;
    }

    /**
     * @return The total amount of used and free indexes.
     */
    public final int getCapacity() {
        return capacity;
    }

    /**
     * @return The policy this container follows.
     */
    public final StackPolicy getPolicy() {
        return policy;
    }
}
