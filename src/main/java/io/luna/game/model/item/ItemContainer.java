package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetItemGroupMessageWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.item.ItemContainer.StackPolicy.ALWAYS;
import static io.luna.game.model.item.ItemContainer.StackPolicy.STANDARD;
import static java.util.Objects.requireNonNull;

/**
 * A model representing a group of items.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ItemContainer implements Iterable<Item> {

    public void setSecondaryRefresh(int i) {
        secondaryRefreshId = OptionalInt.of(i);
    }

    public void resetSecondaryRefresh() {
        secondaryRefreshId = OptionalInt.empty();
    }

    /**
     * A fail-safe iterator for items within this container.
     */
    private final class ItemContainerIterator implements Iterator<Item> {

        /**
         * The current index.
         */
        private int index;

        /**
         * The last index.
         */
        private int lastIndex = -1;

        @Override
        public boolean hasNext() {
            return (index + 1) <= capacity;
        }

        @Override
        public Item next() {
            checkState(index < capacity, "no more elements left to iterate");

            lastIndex = index;
            index++;
            return get(lastIndex);
        }

        @Override
        public void remove() {
            checkState(lastIndex != -1, "can only be called once after 'next()'");

            set(lastIndex, null);

            index = lastIndex;
            lastIndex = -1;
        }
    }

    /**
     * An enum representing policies for when to stack items.
     */
    public enum StackPolicy {

        /**
         * Stack stackable items.
         */
        STANDARD,

        /**
         * Stack all items.
         */
        ALWAYS,

        /**
         * Stack no items.
         */
        NEVER
    }

    /**
     * A list of listeners.
     */
    private ImmutableList<ItemContainerListener> listeners = ImmutableList.of();

    /**
     * The capacity.
     */
    final int capacity;

    /**
     * The stack policy.
     */
    final StackPolicy policy;

    /**
     * The items.
     */
    Item[] items;

    /**
     * The size.
     */
    int size;

    final int primaryRefreshId;
    OptionalInt secondaryRefreshId = OptionalInt.empty();

    /**
     * If events are being fired.
     */
    boolean firingEvents = true;

    /**
     * If a bulk operation is in progress.
     */
    boolean bulkOperation;

    /**
     * Creates a new {@link ItemContainer}.
     *
     * @param capacity The capacity.
     * @param policy The stack policy.
     */
    public ItemContainer(int capacity, StackPolicy policy, int primaryRefreshId) {
        this.capacity = capacity;
        this.policy = policy;
        this.primaryRefreshId = primaryRefreshId;
        items = new Item[capacity];
    }

    /**
     * This implementation will skip {@code null} values.
     */
    @Override
    public final void forEach(Consumer<? super Item> action) {
        requireNonNull(action);
        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item == null) {
                continue;
            }
            action.accept(item);
        }
    }

    @Override
    public final Spliterator<Item> spliterator() {
        return Spliterators.spliterator(items, Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED);
    }

    @Override
    public final Iterator<Item> iterator() {
        return new ItemContainerIterator();
    }

    /**
     * Creates and returns a sequential stream constructed using {@code spliterator()}. This stream
     * is null-free.
     *
     * @return A null free stream of these items.
     */
    public final Stream<Item> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * Attempts to add {@code item} on {@code preferredIndex}.
     *
     * @param item The item to add.
     * @param preferredIndex The preferred index to add the item on.
     * @return {@code true} if successful.
     */
    public boolean add(Item item, int preferredIndex) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        // Determine best index to place on.
        int addIndex = computeAddIndex(item, preferredIndex);
        if (addIndex == -1) {
            // Not enough space.
            fireCapacityExceededEvent();
            return false;
        }

        if (isStackable(item)) {
            // Add stackable item.
            Item current = get(addIndex);
            if (!occupied(addIndex)) {
                // Space is empty, set item here.
                set(addIndex, item);
            } else if ((item.getAmount() + current.getAmount()) < 0) {
                // Item amount exceeds 2147M.
                fireCapacityExceededEvent();
                return false;
            } else {
                // Increase amount on current spot.
                int amount = item.getAmount();
                set(addIndex, current.changeAmount(amount));
            }
        } else {
            // Add non-stackable item.
            int remaining = computeRemainingSize();
            int until = remaining > item.getAmount() ? item.getAmount() : remaining;

            bulkOperation = true;
            try {
                for (int added = 0; added < until; added++) {
                    if (occupied(addIndex)) {
                        // Calculate next free index if needed.
                        addIndex = nextFreeIndex().orElseThrow(() ->
                                new IllegalStateException("The 'size' field is inaccurate."));
                    }

                    // Set non-stackable item.
                    set(addIndex++, item.withAmount(1));
                }
            } finally {
                bulkOperation = false;
                fireUpdateCompletedEvent();
            }
        }
        return true;
    }

    /**
     * Attempts to add {@code item} to the closest available spot.
     *
     * @param item The item to add.
     * @return {@code true} if successful.
     */
    public boolean add(Item item) {
        return add(item, -1);
    }

    /**
     * Attempts to add {@code items} to the closest available spots.
     *
     * @param items The items to add.
     * @return {@code true} if at least one was added.
     */
    public boolean addAll(Iterable<? extends Item> items) {
        boolean added = false;
        bulkOperation = true;
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (add(item)) {
                    added = true;
                }
                // Bulk operation gets set to false in add.
                bulkOperation = true;
            }
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
        return added;
    }

    /**
     * Attempts to add {@code items} to the closest available spots.
     *
     * @param items The items to add.
     * @return {@code true} if at least one was added.
     */
    public boolean addAll(Item... items) {
        return addAll(Arrays.asList(items));
    }

    /**
     * Attempts to remove {@code item} from {@code preferredIndex}.
     *
     * @param item The item to remove.
     * @param preferredIndex The preferred index to remove the item from.
     * @return {@code true} if successful.
     */
    public boolean remove(Item item, int preferredIndex) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        // Determine best index to remove from.
        int removeIndex = computeRemoveIndex(item, preferredIndex);
        if (removeIndex == -1) {
            // Item doesn't exist.
            return false;
        }

        if (isStackable(item)) {
            // Remove stackable item.
            Item current = get(removeIndex);
            if (item.encompasses(current)) {
                // Remove item.
                set(removeIndex, null);
            } else {
                // Change item amount.
                int amount = item.getAmount();
                set(removeIndex, current.changeAmount(-amount));
            }
        } else {
            // Remove non-stackable item.
            int until = computeAmountForId(item.getId());
            until = item.getAmount() > until ? until : item.getAmount();

            bulkOperation = true;
            try {
                for (int removed = 0; removed < until; removed++) {

                    // Calculate next free index if needed.
                    boolean isItemPresent = getIdForIndex(removeIndex).
                            filter(id -> id == item.getId()).isPresent();
                    removeIndex = isItemPresent ? removeIndex : computeIndexForId(removeIndex).orElse(-1);

                    // Can't remove anymore.
                    if (removeIndex == -1) {
                        return true;
                    }

                    // Remove item.
                    set(removeIndex++, null);
                }
            } finally {
                bulkOperation = false;
                fireUpdateCompletedEvent();
            }
        }
        return true;
    }

    /**
     * Attempts to remove {@code item} from the closest available spot.
     *
     * @param item The item to remove.
     * @return {@code true} if successful.
     */
    public boolean remove(Item item) {
        return remove(item, -1);
    }

    /**
     * Attempts to remove {@code items} from the closest available spots.
     *
     * @param items The items to remove.
     * @return {@code true} if at least one was removed.
     */
    public boolean removeAll(Iterable<? extends Item> items) {
        boolean removed = false;
        bulkOperation = true;
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (remove(item)) {
                    removed = true;
                }
                // Bulk operation gets set to false in remove.
                bulkOperation = true;
            }
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
        return removed;
    }

    /**
     * Attempts to remove {@code items} from the closest available spots.
     *
     * @param items The items to remove.
     * @return {@code true} if at least one was removed.
     */
    public boolean removeAll(Item... items) {
        return removeAll(Arrays.asList(items));
    }

    private int computeAddIndex(Item item, int preferredIndex) {
        int index = preferredIndex;
        if (isStackable(item)) {
            index = computeIndexForId(item.getId()).orElse(-1);
        } else if (index != -1) {
            index = occupied(index) ? -1 : index;
        }

        if (index == -1) {
            return nextFreeIndex().orElse(-1);
        }
        return index;
    }

    private int computeRemoveIndex(Item item, int index) {
        if (index == -1 || !occupied(index) ||
                get(index).getId() != item.getId() || isStackable(item)) {
            return computeIndexForId(item.getId()).orElse(-1);
        }
        return -1;
    }

    /**
     * Computes the next free index.
     *
     * @return The next free index, wrapped in an optional.
     */
    public final Optional<Integer> nextFreeIndex() {
        for (int index = 0; index < capacity; index++) {
            if (!occupied(index)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    /**
     * Computes the next index that {@code id} is found in.
     *
     * @param id The identifier to search for.
     * @return The index of {@code id}, wrapped in an optional.
     */
    public final Optional<Integer> computeIndexForId(int id) {
        for (int index = 0; index < capacity; index++) {
            if (nonNullGet(index).filter(it -> it.getId() == id).isPresent()) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    /**
     * Computes the total quantity of items with {@code id}.
     *
     * @param id The identifier to get the amount of.
     * @return The total amount of items with {@code id}.
     */
    public final int computeAmountForId(int id) {
        // TODO optimize for stackable items.
        int amount = 0;
        for (Item item : items) {
            if (item != null && item.getId() == id) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    /**
     * Computes the identifier at {@code index}.
     *
     * @param index The index to compute the identifier of.
     * @return The identifier at {@code index}, wrapped in an optional.
     */
    public final Optional<Integer> getIdForIndex(int index) {
        return nonNullGet(index).map(Item::getId);
    }

    /**
     * Computes the amount at {@code index}.
     *
     * @param index The index to compute the amount of.
     * @return The amount at {@code index}.
     */
    public final int computeAmountForIndex(int index) {
        return nonNullGet(index).map(Item::getAmount).orElse(0);
    }

    /**
     * Computes the remaining amount of free indexes.
     *
     * @return The free indexes.
     */
    public final int computeRemainingSize() {
        return capacity - size;
    }

    /**
     * Replaces the first occurrence of {@code oldId} with {@code newId}.
     *
     * @param oldId The old item identifier.
     * @param newId The new item identifier.
     * @return {@code true} if successful.
     */
    public final boolean replace(int oldId, int newId) {
        Optional<Integer> index = computeIndexForId(oldId);
        if (index.isPresent()) {
            Item oldItem = get(index.get());
            Item newItem = new Item(newId);
            checkState(!isStackable(oldItem) && !isStackable(newItem), "Stackable items cannot be replaced.");

            set(index.get(), new Item(newId));
            return true;
        }
        return false;
    }

    /**
     * Replaces all occurrences of {@code oldId} with {@code newId}. Returns {@code true} if at least one
     * was replaced.
     *
     * @param oldId The old item identifier.
     * @param newId The new item identifier.
     * @return {@code true}  if at least one item was replaced.
     */
    public final boolean replaceAll(int oldId, int newId) {
        boolean replaced = false;
        bulkOperation = true;
        try {
            while (replace(oldId, newId)) {
                replaced = true;
            }
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
        return replaced;
    }

    /**
     * Determines if there is enough space for {@code items} to be added.
     *
     * @param items The items.
     * @return {@code true} if there's enough space for {@code items}.
     */
    public final boolean hasSpaceFor(Item... items) {
        int count = 0;
        for (Item item : items) {
            if (isStackable(item)) {
                // See if there's an index for the item.
                int index = computeIndexForId(item.getId()).orElse(-1);
                if (index == -1) {
                    // There isn't, we require a space.
                    count++;
                } else if (get(index).getAmount() + item.getAmount() < 0) {
                    // There is, and trying to add onto it will result in an overflow.
                    return false;
                }
            } else {
                // Non-stackable items are equal to the amount.
                count += item.getAmount();
            }

            // Can't fit, no point in checking other items.
            if (count > computeRemainingSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if an item with {@code id} is present.
     *
     * @param id The identifier to look for.
     * @return {@code true} if {@code id} is present in this container.
     */
    public final boolean contains(int id) {
        return computeIndexForId(id).isPresent();
    }

    /**
     * Determines if all items with {@code ids} are present.
     *
     * @param ids The identifiers to look for.
     * @return {@code true} if {@code ids} are all present in this container.
     */
    public final boolean containsAll(int... ids) {
        return containsAllIds(Ints.asList(ids));
    }

    /**
     * Determines if all items with {@code ids} are present.
     *
     * @param ids The identifiers to look for.
     * @return {@code true} if {@code ids} are all present in this container.
     */
    public final boolean containsAllIds(Iterable<? extends Integer> ids) {
        for (int id : ids) {
            if (!contains(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if all items with {@code ids} are present.
     *
     * @param ids The identifiers to look for.
     * @return {@code true} if {@code ids} are all present in this container.
     */
    public final boolean containsAny(int... ids) {
        return containsAnyIds(Ints.asList(ids));
    }

    /**
     * Determines if any items with {@code ids} are present.
     *
     * @param ids The identifiers to look for.
     * @return {@code true} if any of {@code ids} are present in this container.
     */
    public final boolean containsAnyIds(Iterable<? extends Integer> ids) {
        for (int id : ids) {
            if (contains(id)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Determines if {@code item} is present.
     *
     * @param item The item to look for.
     * @return {@code true} if {@code item} is present in this container.
     */
    public final boolean contains(Item item) {
        return stream().anyMatch(it -> it.encompasses(item));
    }

    /**
     * Determines if all of {@code items} are present.
     *
     * @param items The items to look for.
     * @return {@code true} if {@code items} are all present in this container.
     */
    public final boolean containsAll(Item... items) {
        return containsAll(Arrays.asList(items));
    }

    /**
     * Determines if all of {@code items} are present.
     *
     * @param items The items to look for.
     * @return {@code true} if {@code items} are all present in this container.
     */
    public final boolean containsAll(Iterable<? extends Item> items) {
        for (Item item : items) {
            if (!contains(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if any of {@code items} are present.
     *
     * @param items The items to look for.
     * @return {@code true} if any {@code items} are present in this container.
     */
    public final boolean containsAny(Item... items) {
        return containsAny(Arrays.asList(items));
    }

    /**
     * Determines if any of {@code items} are present.
     *
     * @param items The items to look for.
     * @return {@code true} if any {@code items} are present in this container.
     */
    public final boolean containsAny(Iterable<? extends Item> items) {
        for (Item item : items) {
            if (contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if {@code item} will stack when added.
     *
     * @param item The item to determine for.
     * @return {@code true} if this item will stack.
     */
    public final boolean isStackable(Item item) {
        return policy == STANDARD && item.getItemDef().isStackable() || policy == ALWAYS;
    }

    public final void refresh(Player player) {
        refreshPrimary(player);
        refreshSecondary(player);
    }

    /**
     * Creates and returns a message that will display these items on {@code widget}.
     *
     * @return A message that will display these items.
     */
    public final void refreshPrimary(Player player) {
        player.queue(new WidgetItemGroupMessageWriter(primaryRefreshId, items));
    }

    public final void refreshSecondary(Player player) {
        if (secondaryRefreshId.isPresent()) {
            player.queue(new WidgetItemGroupMessageWriter(secondaryRefreshId.getAsInt(), items));
        }
    }

    /**
     * Swaps the items on {@code firstIndex} and {@code secondIndex}.
     *
     * @param firstIndex The first index's item.
     * @param secondIndex The second index's item.
     */
    public final void swap(int firstIndex, int secondIndex) {
        bulkOperation = true;
        try {
            swapOperation(firstIndex, secondIndex);
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
    }

    /**
     * Inserts the item on {@code oldIndex} on {@code newIndex}. Will shift items to the left or right to
     * accommodate the insertion.
     *
     * @param oldIndex The old index.
     * @param newIndex The insertion index.
     */
    public final void insert(int oldIndex, int newIndex) {
        bulkOperation = true;
        try {
            if (newIndex > oldIndex) {
                for (int index = oldIndex; index < newIndex; index++) {
                    swapOperation(index, index + 1);
                }
            } else if (oldIndex > newIndex) {
                for (int index = oldIndex; index > newIndex; index--) {
                    swapOperation(index, index - 1);
                }
            }
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
    }

    /**
     * Swaps the items on {@code firstIndex} and {@code secondIndex}.
     *
     * @param firstIndex The initial index.
     * @param secondIndex The new index.
     */
    private void swapOperation(int firstIndex, int secondIndex) {
        checkArgument(firstIndex >= 0 && firstIndex < capacity,
                "firstIndex must be above or equal to 0 and below the capacity.");
        checkArgument(secondIndex >= 0 && secondIndex < capacity,
                "secondIndex must be above or equal to 0 and below the capacity.");

        Item oldItem = get(firstIndex);
        Item newItem = get(secondIndex);

        set(firstIndex, newItem);
        set(secondIndex, oldItem);
    }

    /**
     * Sets the backing array to {@code newItems} (deep copy). Only used during Player login.
     *
     * @param newItems The new items.
     */
    public final void setItems(IndexedItem[] newItems) {
        size = 0;
        for (IndexedItem item : newItems) {
            items[item.getIndex()] = item.toItem();
            size++;
        }
    }

    /**
     * Creates and returns a shallow copy of the array of items.
     *
     * @return A shallow copy of the backing array.
     */
    public final Item[] toArray() {
        return Arrays.copyOf(items, items.length);
    }

    /**
     * Creates and returns the backing array as an array of indexed items.
     *
     * @return The backing array, as indexed items.
     */
    public final IndexedItem[] toIndexedArray() {
        List<IndexedItem> indexedItems = new ArrayList<>(size);
        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item == null) {
                continue;
            }
            IndexedItem indexedItem = new IndexedItem(index, item.getId(), item.getAmount());
            indexedItems.add(indexedItem);
        }
        return Iterables.toArray(indexedItems, IndexedItem.class);
    }

    /**
     * Sets {@code index} to {@code item}.
     *
     * @param index The index to set on.
     * @param item The item to set.
     */
    public final void set(int index, Item item) {
        boolean removingItem = item == null;

        if (!occupied(index) && !removingItem) {
            size++;
        } else if (occupied(index) && removingItem) {
            size--;
        }

        Item oldItem = get(index);
        items[index] = item;

        fireUpdateEvent(oldItem, item, index);
    }

    /**
     * Retrieves the item at {@code index}, wrapping the result in an optional.
     *
     * @param index The index to retrieve.
     * @return The item, wrapped in an optional.
     */
    public final Optional<Item> nonNullGet(int index) {
        return Optional.ofNullable(get(index));
    }

    /**
     * Gets the item at {@code index}. Might be {@code null}.
     *
     * @return The item on index, possibly {@code null}.
     */
    public final Item get(int index) {
        return items[index];
    }

    /**
     * Determines if {@code index} is occupied.
     *
     * @param index The index to check.
     * @return {@code true} if {@code index} is occupied.
     */
    public final boolean occupied(int index) {
        return nonNullGet(index).isPresent();
    }

    /**
     * Determines if {@code indexes} are all occupied.
     *
     * @param indexes The indexes to check.
     * @return {@code true} if {@code indexes} are all occupied.
     */
    public final boolean allOccupied(int... indexes) {
        for (int index : indexes) {
            if (!occupied(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if any {@code indexes} are occupied.
     *
     * @param indexes The indexes to check.
     * @return {@code true} if any {@code indexes} are occupied.
     */
    public final boolean anyOccupied(int... indexes) {
        for (int index : indexes) {
            if (occupied(index)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes all items from this container.
     */
    public final void clear() {
        bulkOperation = true;
        try {
            for (int index = 0; index < capacity; index++) {
                set(index, null);
            }
        } finally {
            bulkOperation = false;
            fireUpdateCompletedEvent();
        }
    }

    /**
     * Adds a listener.
     */
    public final void setListeners(ItemContainerListener... newListeners) {
        listeners = ImmutableList.copyOf(newListeners);
    }

    /**
     * Fires a single or bulk update event.
     *
     * @param oldItem The old item.
     * @param newItem The new item.
     * @param index The index.
     */
    public final void fireUpdateEvent(Item oldItem, Item newItem, int index) {
        Optional<Item> oldOptional = Optional.ofNullable(oldItem);
        Optional<Item> newOptional = Optional.ofNullable(newItem);

        if (firingEvents && !oldOptional.equals(newOptional)) {
            for (ItemContainerListener listener : listeners) {
                if (bulkOperation) {
                    listener.onBulkUpdate(index, oldOptional, newOptional, this);
                } else {
                    listener.onSingleUpdate(index, this, oldOptional, newOptional);
                }
            }
        }
    }

    /**
     * Fires a bulk update completion event.
     */
    public final void fireUpdateCompletedEvent() {
        if (firingEvents) {
            listeners.forEach(listener -> listener.onBulkUpdateCompleted(this));
        }
    }

    /**
     * Fires a capacity exceeded event.
     */
    public final void fireCapacityExceededEvent() {
        if (firingEvents) {
            listeners.forEach(listener -> listener.onCapacityExceeded(this));
        }
    }

    /**
     * @return {@code true} if events are being fired.
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
     * @return The size.
     */
    public final int getSize() {
        return size;
    }

    /**
     * @return The capacity.
     */
    public final int getCapacity() {
        return capacity;
    }

    /**
     * @return The stack policy.
     */
    public final StackPolicy getPolicy() {
        return policy;
    }
}
