package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import com.google.common.math.IntMath;
import com.google.common.primitives.Ints;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.AbstractInterface;
import io.luna.net.msg.out.WidgetItemsMessageWriter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.item.ItemContainer.StackPolicy.ALWAYS;
import static io.luna.game.model.item.ItemContainer.StackPolicy.STANDARD;
import static io.luna.util.OptionalUtils.mapToInt;
import static io.luna.util.OptionalUtils.matches;
import static java.util.Objects.requireNonNull;

/**
 * A model representing a traversable group of items that adhere to a strict set of rules. These rules dictate how
 * items are stored and displayed on {@link AbstractInterface} types.
 *
 * @author lare96
 */
public class ItemContainer implements Iterable<Item> {

    // TODO delete/reorganize functions more

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
            return index + 1 <= capacity;
        }

        @Override
        public Item next() {
            if (index >= capacity) {
                throw new NoSuchElementException("No more elements left to iterate.");
            }
            lastIndex = index++;
            return get(lastIndex);
        }

        @Override
        public void remove() {
            checkState(lastIndex != -1, "Can only be called once after next().");

            set(lastIndex, null);
            lastIndex = -1;
        }
    }

    /**
     * An enumerated type whose elements represent policies for when to stack items.
     */
    public enum StackPolicy {

        /**
         * Stack only stackable items.
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
     * The capacity.
     */
    private final int capacity;

    /**
     * The stack policy.
     */
    private final StackPolicy policy;

    /**
     * The items.
     */
    private final Item[] items;

    /**
     * The size.
     */
    private int size;

    /**
     * The primary refresh widget.
     */
    private final int primaryRefreshId;

    /**
     * The secondary refresh widget.
     */
    private OptionalInt secondaryRefreshId = OptionalInt.empty();

    /**
     * An immutable list of listeners.
     */
    private ImmutableList<ItemContainerListener> listeners = ImmutableList.of();

    /**
     * If events are being fired.
     */
    private boolean firingEvents = true;

    /**
     * If a bulk operation is in progress.
     */
    private boolean inBulkUpdate;

    /**
     * If this container has been initialized.
     */
    private boolean initialized;

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
     * @param preferredIndex The preferred index to add the item on.
     * @param item The item to add.
     * @return {@code true} if successful.
     */
    public boolean add(int preferredIndex, Item item) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        // Determine best index to place on.
        int addIndex = preferredIndex;
        if (isStackable(item)) {
            addIndex = computeIndexForId(item.getId()).orElse(-1);
        } else if (addIndex != -1) {
            addIndex = occupied(addIndex) ? -1 : addIndex;
        }
        if (addIndex == -1) {
            addIndex = nextFreeIndex().orElse(-1);
        }

        // Not enough space.
        if (addIndex == -1) {
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
                set(addIndex, current.addAmount(amount));
            }
        } else {
            // Add non-stackable item.
            int remaining = computeRemainingSize();
            int until = Math.min(remaining, item.getAmount());

            startBulkUpdate();
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
                finishBulkUpdate();
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
        return add(-1, item);
    }

    /**
     * Forwards to {@link #add(Item)} with the amount as {@code 1}.
     *
     * @param id The ID to add 1 of.
     * @return {@code true} if successful.
     */
    public boolean add(int id) {
        return add(new Item(id));
    }

    /**
     * Attempts to add {@code items} to the closest available spots.
     *
     * @param items The items to add.
     * @return {@code true} if at least one was added.
     */
    public boolean addAll(Iterable<? extends Item> items) {
        boolean added = false;
        startBulkUpdate();
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (add(item)) {
                    added = true;
                }
                // Bulk operation gets set to false in 'add'.
                startBulkUpdate();
            }
        } finally {
            finishBulkUpdate();
        }
        return added;
    }

    /**
     * Attempts to remove {@code item} from {@code preferredIndex}.
     *
     * @param preferredIndex The preferred index to remove the item from.
     * @param item The item to remove.
     * @return {@code true} if successful.
     */
    public boolean remove(int preferredIndex, Item item) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        // Determine best index to remove from.
        int removeIndex = -1;
        if (preferredIndex == -1 || !occupied(preferredIndex)) {
            removeIndex = computeIndexForId(item.getId()).orElse(-1);
        } else if (item.getId() == get(preferredIndex).getId()) {
            removeIndex = preferredIndex;
        }

        // Item doesn't exist.
        if (removeIndex == -1) {
            return false;
        }

        if (isStackable(item)) {
            // Remove stackable item.
            Item current = get(removeIndex);
            if (item.contains(current)) {
                // Remove item.
                set(removeIndex, null);
            } else {
                // Change item amount.
                int amount = item.getAmount();
                set(removeIndex, current.addAmount(-amount));
            }
        } else {
            // Remove non-stackable item.
            int until = computeAmountForId(item.getId());
            until = Math.min(item.getAmount(), until);

            startBulkUpdate();
            try {
                for (int removed = 0; removed < until; removed++) {
                    // Calculate next free index if needed.
                    boolean isItemPresent = computeIdForIndex(removeIndex).orElse(-1) == item.getId();
                    removeIndex = isItemPresent ? removeIndex : computeIndexForId(item.getId()).orElse(-1);

                    // Can't remove anymore.
                    if (removeIndex == -1) {
                        return true;
                    }

                    // Remove item.
                    set(removeIndex++, null);
                }
            } finally {
                finishBulkUpdate();
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
        return remove(-1, item);
    }

    /**
     * Forwards to {@link #remove(Item)} with the amount as {@code 1}.
     *
     * @param id The ID to remove 1 of.
     * @return {@code true} if successful.
     */
    public boolean remove(int id) {
        return remove(-1, new Item(id));
    }

    /**
     * Attempts to remove {@code items} from the closest available spots.
     *
     * @param items The items to remove.
     * @return {@code true} if at least one was removed.
     */
    public boolean removeAll(Iterable<? extends Item> items) {
        boolean removed = false;
        startBulkUpdate();
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (remove(item)) {
                    removed = true;
                }
                // Bulk operation gets set to false in 'remove'.
                startBulkUpdate();
            }
        } finally {
            finishBulkUpdate();
        }
        return removed;
    }

    /**
     * Attempts to remove {@code removeItems} and add {@code addItems}.
     *
     * @param addItems The items to add.
     * @param removeItems The items to remove.
     * @return {@code true} if at least one was added or removed.
     */
    public boolean updateAll(Iterable<? extends Item> addItems, Iterable<? extends Item> removeItems) {
        boolean changed = false;
        startBulkUpdate();
        try {
            for (Item item : removeItems) {
                if (item == null) {
                    continue;
                }
                // Bulk operation gets set to false in 'remove'.
                startBulkUpdate();
                if (remove(item)) {
                    changed = true;
                }
            }

            for (Item item : addItems) {
                if (item == null) {
                    continue;
                }
                // Bulk operation gets set to false in 'add'.
                startBulkUpdate();
                if (add(item)) {
                    changed = true;
                }
            }
            startBulkUpdate();
        } finally {
            finishBulkUpdate();
        }
        return changed;
    }

    /**
     * Replaces the first occurrence of {@code oldId} with {@code newId}.
     *
     * @param oldId The old item identifier.
     * @param newId The new item identifier.
     * @return {@code true} if successful.
     */
    public final boolean replace(int oldId, int newId) {
        checkArgument(!isStackable(oldId) && !isStackable(newId), "Cannot replace stackable items.");

        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item != null && item.getId() == oldId) {
                Item newItem = item.withId(newId);
                set(index, newItem);
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces {@code amount} occurrences of {@code oldId} with {@code newId}.
     *
     * @param oldId The old item identifier.
     * @param newId The new item identifier.
     * @param amount The amount of occurrences to replace.
     * @return the total amount of items replaced.
     */
    public final int replace(int oldId, int newId, int amount) {
        int count = 0;
        startBulkUpdate();
        try {
            for (int index = 0; index < capacity; index++) {
                if (count >= amount) {
                    break;
                }
                Item item = get(index);
                if (item != null && item.getId() == oldId) {
                    Item newItem = item.withId(newId);
                    set(index, newItem);
                    count += item.getAmount();
                }
            }
        } finally {
            finishBulkUpdate();
        }
        return count;
    }

    /**
     * Replaces all occurrences of {@code oldId} with {@code newId}.
     *
     * @param oldId The old item identifier.
     * @param newId The new item identifier.
     * @return the total amount of items replaced.
     */
    public final int replaceAll(int oldId, int newId) {
        return replace(oldId, newId, Integer.MAX_VALUE);
    }

    /**
     * Computes the next free index.
     *
     * @return The next free index, wrapped in an optional.
     */
    public final OptionalInt nextFreeIndex() {
        for (int index = 0; index < capacity; index++) {
            if (!occupied(index)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Computes the next index that {@code id} is found in.
     *
     * @param id The identifier to search for.
     * @return The index of {@code id}, wrapped in an optional.
     */
    public final OptionalInt computeIndexForId(int id) {
        for (int index = 0; index < capacity; index++) {
            if (matches(computeIdForIndex(index), id)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Computes the total quantity of items with {@code id}.
     *
     * @param id The identifier to get the amount of.
     * @return The total amount of items with {@code id}.
     */
    public final int computeAmountForId(int id) {
        boolean isStackable = isStackable(id);
        int currentAmount = 0;
        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item != null && item.getId() == id) {
                if (isStackable) {
                    return item.getAmount();
                } else {
                    currentAmount += item.getAmount();
                }
            }
        }
        return currentAmount;
    }

    /**
     * Computes the identifier at {@code index}.
     *
     * @param index The index to compute the identifier of.
     * @return The identifier at {@code index}, wrapped in an optional.
     */
    public final OptionalInt computeIdForIndex(int index) {
        return mapToInt(nonNullGet(index), Item::getId);
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
     * Determines if this container is full.
     *
     * @return {@code true} if this container is full.
     */
    public final boolean isFull() {
        return computeRemainingSize() == 0;
    }

    /**
     * Performs an indexed loop of the container and executes an action containing the index and item as arguments.
     *
     * @param action The action to execute.
     */
    public void forIndexedItems(BiConsumer<Integer, Item> action) {
        boolean bulkUpdateInProgress = inBulkUpdate;
        if (!bulkUpdateInProgress) {
            startBulkUpdate();
        }
        try {
            for (int index = 0; index < capacity; index++) {
                action.accept(index, get(index));
            }
        } finally {
            if (!bulkUpdateInProgress) {
                finishBulkUpdate();
            }
        }
    }

    /**
     * Determines if {@code id} exists on {@code index}.
     *
     * @param index The index.
     * @param id The identifier.
     * @return {@code true} if present within this container.
     */
    public boolean contains(int index, int id) {
        OptionalInt lookupId = computeIdForIndex(index);
        if (lookupId.isEmpty()) {
            return false;
        }
        return lookupId.getAsInt() == id;
    }

    /**
     * Determines if {@code item} exists on {@code index}.
     *
     * @param index The index.
     * @param item The item.
     * @return {@code true} if present within this container.
     */
    public boolean contains(int index, Item item) {
        return Optional.ofNullable(get(index)).map(it -> it.contains(item)).isPresent();
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
        if (ids.length == 0) {
            return true;
        }
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
        if (ids.length == 0) {
            return true;
        }
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
        return computeAmountForId(item.getId()) >= item.getAmount();
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
     * Determines if there is enough space for {@code item} to be added.
     *
     * @param item The items.
     * @return {@code true} if there's enough space for {@code item}.
     */
    public final boolean hasSpaceFor(Item item) {
        return computeSpaceFor(item) <= computeRemainingSize();
    }

    /**
     * Determines if there is enough space for {@code items} to be added.
     *
     * @param items The items.
     * @return {@code true} if there's enough space for {@code items}.
     */
    public final boolean hasSpaceForAll(Iterable<? extends Item> items) {
        int count = 0;
        for (Item item : items) {
            count = IntMath.saturatedAdd(count, computeSpaceFor(item));

            if (count > computeRemainingSize()) {
                // Can't fit, no point in checking other items.
                return false;
            }
        }
        return true;
    }

    /**
     * Computes the amount of space required to hold {@code items}.
     *
     * @param items The items.
     * @return The amount of space required.
     */
    public final int computeSpaceForAll(Iterable<? extends Item> items) {
        int count = 0;
        for (Item item : items) {
            count = IntMath.saturatedAdd(count, computeSpaceFor(item));
        }
        return count;
    }

    /**
     * Computes the amount of space required to hold {@code item}.
     *
     * @param item The item.
     * @return The amount of space required.
     */
    public final int computeSpaceFor(Item item) {
        if (isStackable(item)) {
            // See if there's an index for the item.
            int index = computeIndexForId(item.getId()).orElse(-1);
            if (index == -1) {
                // There isn't, we require a space.
                return 1;
            } else if (get(index).getAmount() + item.getAmount() < 0) {
                // There is, and trying to add onto it will result in an overflow.
                return Integer.MAX_VALUE;
            } else {
                // There is, no space needed.
                return 0;
            }
        }

        // Non-stackable items are equal to the amount.
        return item.getAmount();
    }


    /**
     * Determines if {@code item} will stack when in this container.
     *
     * @param item The item to determine for.
     * @return {@code true} if this item will stack.
     */
    public final boolean isStackable(Item item) {
        return isStackable(item.getId());
    }

    /**
     * Determines if {@code id} will stack when in this container.
     *
     * @param id The identifier to determine for.
     * @return {@code true} if items with the identifier will stack.
     */
    public final boolean isStackable(int id) {
        return policy == STANDARD && ItemDefinition.ALL.retrieve(id).isStackable() || policy == ALWAYS;
    }

    /**
     * Refreshes this container onto the primary widget.
     *
     * @param player The player to refresh for.
     */
    public final void refreshPrimary(Player player) {
        player.queue(new WidgetItemsMessageWriter(primaryRefreshId, items));
    }

    /**
     * Refreshes this container onto the secondary widget, if one is present.
     *
     * @param player The player to refresh for.
     */
    public final void refreshSecondary(Player player) {
        if (secondaryRefreshId.isPresent()) {
            player.queue(new WidgetItemsMessageWriter(secondaryRefreshId.getAsInt(), items));
        }
    }

    /**
     * Swaps the items on {@code firstIndex} and {@code secondIndex}.
     *
     * @param firstIndex The first index.
     * @param secondIndex The second index.
     */
    public final void swap(int firstIndex, int secondIndex) {
        startBulkUpdate();
        try {
            swapOperation(firstIndex, secondIndex);
        } finally {
            finishBulkUpdate();
        }
    }

    /**
     * Inserts the item on {@code oldIndex} to {@code newIndex}. Will shift items to the left or right to
     * accommodate the insertion.
     *
     * @param oldIndex The old index.
     * @param newIndex The insertion index.
     */
    public final void insert(int oldIndex, int newIndex) {
        startBulkUpdate();
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
            finishBulkUpdate();
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
     * Sets the backing array to {@code newItems} using a deep copy. Fires initialization events.
     *
     * @param setItems The items to set.
     */
    public final void init(List<IndexedItem> setItems) {
        checkState(size == 0 && !initialized, "Containers can only be initialized once.");

        for (IndexedItem item : setItems) {
            items[item.getIndex()] = item.toItem();
            size++;
        }
        fireInitEvent();
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
     * Creates and returns the backing array as a list of indexed items.
     *
     * @return The backing list, as indexed items.
     */
    public final List<IndexedItem> toList() {
        var list = new ArrayList<IndexedItem>(size);
        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item == null) {
                continue;
            }
            list.add(new IndexedItem(index, item));
        }
        return list;
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

        fireUpdateEvent(index, oldItem, item);
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
     * Shifts all items to the left, clearing all {@code null} elements in between {@code non-null} elements.
     */
    public void clearSpaces() {
        if (size > 0) {
            // Create queue of pending indexes and cache this container's size.
            Queue<Integer> indexes = new ArrayDeque<>();
            int shiftAmount = size;

            startBulkUpdate();
            try {
                for (int index = 0; index < capacity; index++) {
                    if (shiftAmount == 0) {
                        // No more items left to shift.
                        break;
                    } else if (occupied(index)) {
                        // Item is present on this index.
                        Integer newIndex = indexes.poll();
                        if (newIndex != null) {
                            // Shift it to the left, if needed.
                            set(newIndex, get(index));
                            set(index, null);
                            indexes.add(index);
                        }
                        // We've encountered an item, decrement counter.
                        shiftAmount--;
                    } else {
                        // No item on this index, add it to pending queue.
                        indexes.add(index);
                    }
                }
            } finally {
                finishBulkUpdate();
            }
        }
    }

    /**
     * Removes all items from this container.
     */
    public final void clear() {
        startBulkUpdate();
        try {
            for (int index = 0; index < capacity; index++) {
                set(index, null);
            }
        } finally {
            finishBulkUpdate();
        }
    }

    /**
     * Sets the backing immutable list of listeners.
     *
     * @param newListeners The new listeners.
     */
    public final void setListeners(ItemContainerListener... newListeners) {
        listeners = ImmutableList.copyOf(newListeners);
    }

    /**
     * Fires a single or bulk update event.
     *
     * @param index The index.
     * @param oldItem The old item.
     * @param newItem The new item.
     */
    public final void fireUpdateEvent(int index, Item oldItem, Item newItem) {
        if (firingEvents && !Objects.equals(oldItem, newItem)) {
            Optional<Item> oldOptional = Optional.ofNullable(oldItem);
            Optional<Item> newOptional = Optional.ofNullable(newItem);

            for (ItemContainerListener listener : listeners) {
                if (inBulkUpdate) {
                    listener.onBulkUpdate(index, this, oldOptional, newOptional);
                } else {
                    listener.onSingleUpdate(index, this, oldOptional, newOptional);
                }
            }
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
     * Fires an initialization event.
     */
    public final void fireInitEvent() {
        if (firingEvents && !initialized) {
            initialized = true;
            listeners.forEach(listener -> listener.onInit(this));
        }
    }

    /**
     * @return The capacity.
     */
    public final int capacity() {
        return capacity;
    }

    /**
     * @return The size.
     */
    public final int size() {
        return size;
    }

    /**
     * @return The stack policy.
     */
    public final StackPolicy getPolicy() {
        return policy;
    }

    /**
     * @return The primary refresh widget.
     */
    public final int getPrimaryRefresh() {
        return primaryRefreshId;
    }

    /**
     * Resets the secondary refresh widget.
     */
    public final void resetSecondaryRefresh() {
        secondaryRefreshId = OptionalInt.empty();
    }

    /**
     * Sets the secondary refresh widget.
     *
     * @param secondaryRefresh The new widget.
     */
    public final void setSecondaryRefresh(int secondaryRefresh) {
        secondaryRefreshId = OptionalInt.of(secondaryRefresh);
    }

    /**
     * @return The secondary refresh widget.
     */
    public final OptionalInt getSecondaryRefresh() {
        return secondaryRefreshId;
    }

    /**
     * @return {@code true} if events are being fired.
     */
    public final boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Signals this container to start firing events.
     */
    public final void enableEvents() {
        firingEvents = true;
    }

    /**
     * Signals this container to stop firing events.
     */
    public final void disableEvents() {
        firingEvents = false;
    }

    /**
     * @return {@code true} if a bulk update operation is in progress.
     */
    public final boolean isInBulkUpdate() {
        return inBulkUpdate;
    }

    /**
     * Starts a bulk update operation.
     */
    public final void startBulkUpdate() {
        inBulkUpdate = true;
    }

    /**
     * Finishes a bulk update operations.
     */
    public final void finishBulkUpdate() {
        inBulkUpdate = false;
        if (firingEvents) {
            listeners.forEach(listener -> listener.onBulkUpdateCompleted(this));
        }
    }

    /**
     * @return {@code true} if this container has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
