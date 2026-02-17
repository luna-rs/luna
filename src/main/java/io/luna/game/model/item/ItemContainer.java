package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import com.google.common.math.IntMath;
import com.google.common.primitives.Ints;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetItemsMessageWriter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
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
import static java.util.Objects.requireNonNull;

/**
 * A fixed-capacity container of {@link Item}s used by gameplay systems and UI overlays.
 * <p>
 * This container enforces a {@link StackPolicy} that determines how item stacking behaves:
 * <ul>
 *   <li>{@link StackPolicy#STANDARD}: only items whose {@link ItemDefinition#isStackable()} is true will stack</li>
 *   <li>{@link StackPolicy#ALWAYS}: all items stack regardless of definition</li>
 *   <li>{@link StackPolicy#NEVER}: no items stack (each unit consumes one slot)</li>
 * </ul>
 * <p>
 * The container maintains a cached {@code size} (non-null slot count) so {@link #computeRemainingSize()} is O(1). All
 * modifications to the backing array MUST go through {@link #set(int, Item)} so that size bookkeeping and event
 * dispatch remain consistent.
 * <p>
 * Event callbacks are delivered to {@link ItemContainerListener}s when:
 * <ul>
 *   <li>a single slot changes (single update)</li>
 *   <li>a batch of changes occurs between {@link #startBulkUpdate()} and {@link #finishBulkUpdate()} (bulk update)</li>
 *   <li>bulk update completes (bulk update completed)</li>
 *   <li>capacity/space constraints prevent an add (capacity exceeded)</li>
 *   <li>the container is loaded and first initialized (init)</li>
 * </ul>
 * <p>
 * Widgets:
 * <ul>
 *   <li>{@link #updatePrimaryWidget(Player)} writes this container to {@link #primaryWidget}</li>
 *   <li>{@link #updateSecondaryWidget(Player)} writes this container to {@link #secondaryWidget}, if present.</li>
 * </ul>
 *
 * @author lare96
 */
public class ItemContainer implements Iterable<Item> {

    /**
     * Iterator implementation that returns {@link #get(int)} values for indices in range.
     * <p>
     * This iterator is "fail-safe" in the sense that it does not detect concurrent modification and will reflect
     * whatever the container currently holds for each index when {@link #next()} is called.
     * <p>
     * {@link #remove()} removes the last returned element by setting the backing slot to null. It may only be called
     * once per {@link #next()}.
     */
    private final class ItemContainerIterator implements Iterator<Item> {

        /**
         * The next index to return from {@link #next()}.
         */
        private int index;

        /**
         * The last index that was returned by {@link #next()}, or -1 if none / already removed.
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
     * Policies determining when items should stack in this container.
     * <p>
     * Note: even if an item is stackable under the policy, the container treats {@link DynamicItem} types (items with
     * attributes/variable state) as <b>non-stackable</b> for add/remove operations, because equality and stacking
     * semantics depend on more than the item id.
     */
    public enum StackPolicy {

        /**
         * Stack only items whose {@link ItemDefinition#isStackable()} is true.
         */
        STANDARD,

        /**
         * Stack all items regardless of {@link ItemDefinition#isStackable()}.
         */
        ALWAYS,

        /**
         * Never stack items (each unit takes a distinct slot).
         */
        NEVER
    }

    /**
     * The maximum number of slots in this container.
     */
    private final int capacity;

    /**
     * The stacking rules for this container.
     */
    private final StackPolicy policy;

    /**
     * The backing item array. Slots may be null.
     * <p>
     * All mutations must be performed via {@link #set(int, Item)} to keep {@link #size} correct and to fire listener
     * events.
     */
    private final Item[] items;

    /**
     * The cached count of non-null slots.
     */
    private int size;

    /**
     * The widget id used by {@link #updatePrimaryWidget(Player)} for UI refresh.
     */
    private final int primaryWidget;

    /**
     * The optional widget id used by {@link #updateSecondaryWidget(Player)} for UI refresh.
     */
    private OptionalInt secondaryWidget = OptionalInt.empty();

    /**
     * The registered listeners. Stored as an immutable snapshot to simplify iteration and avoid accidental mutation.
     */
    private ImmutableList<ItemContainerListener> listeners = ImmutableList.of();

    /**
     * If false, no listener callbacks will be fired.
     */
    private boolean firingEvents = true;

    /**
     * The depth counter of active bulk updates.
     *
     * <p>Bulk updates may be nested. A "bulk update completed" event is fired when the counter returns to 0.
     */
    private int bulkUpdates;

    /**
     * Whether {@link #initialize()} has been called and initialization events have been fired.
     */
    private boolean initialized;

    /**
     * Creates a new {@link ItemContainer}.
     *
     * @param capacity The number of slots in this container (fixed).
     * @param policy The stacking policy for this container.
     * @param primaryWidget The primary widget id to refresh with {@link #updatePrimaryWidget(Player)}.
     */
    public ItemContainer(int capacity, StackPolicy policy, int primaryWidget) {
        this.capacity = capacity;
        this.policy = policy;
        this.primaryWidget = primaryWidget;
        items = new Item[capacity];
    }

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
        return Spliterators.spliterator(items, Spliterator.ORDERED | Spliterator.SIZED);
    }

    @Override
    public final Iterator<Item> iterator() {
        return new ItemContainerIterator();
    }

    /**
     * Creates a sequential, null-free stream of items in this container.
     * <p>
     * The stream is backed by {@link #spliterator()} and filters out null slots.
     *
     * @return A stream of non-null items.
     */
    public final Stream<Item> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * Attempts to add {@code item}, preferring to place it at {@code preferredIndex} when possible.
     * <p>
     * Index selection rules:
     * <ul>
     *   <li>If the item will stack (per policy/definition) and is NOT dynamic, it will be placed on the existing
     *       stack slot for that id if present.</li>
     *   <li>If {@code preferredIndex != -1} and the slot is free, it may be used for non-stackable items.</li>
     *   <li>Otherwise the next free slot is chosen.</li>
     * </ul>
     *
     * <p>Capacity behavior:
     * <ul>
     *   <li>If no slot is available, {@link #onCapacityExceeded()} is fired and false is returned.</li>
     *   <li>If stacking would overflow the integer amount (past max int), capacity exceeded is fired and false is returned.</li>
     * </ul>
     *
     * @param preferredIndex A preferred target slot, or -1 to select automatically.
     * @param item The item to add.
     * @return {@code true} if the underlying container changed.
     */
    public boolean add(int preferredIndex, Item item) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        // Determine best index to place on.
        boolean stackable = stackable(item) && !item.isDynamic();
        int addIndex = preferredIndex;
        if (stackable) {
            addIndex = computeIndexForId(item.getId());
        } else if (addIndex != -1) {
            addIndex = occupied(addIndex) ? -1 : addIndex;
        }
        if (addIndex == -1) {
            addIndex = getNextFreeIndex();
        }

        // Not enough space.
        if (addIndex == -1) {
            onCapacityExceeded();
            return false;
        }

        if (stackable) {
            // Add stackable item.
            Item current = get(addIndex);
            if (!occupied(addIndex)) {
                set(addIndex, item);
            } else if ((item.getAmount() + current.getAmount()) < 0) {
                // Overflow guard (amount wrapped to negative).
                onCapacityExceeded();
                return false;
            } else {
                set(addIndex, current.addAmount(item.getAmount()));
            }
        } else {
            // Add non-stackable item: each unit consumes a slot.
            int remaining = computeRemainingSize();
            int until = Math.min(remaining, item.getAmount());

            startBulkUpdate();
            try {
                for (int added = 0; added < until; added++) {
                    if (occupied(addIndex)) {
                        addIndex = getNextFreeIndex();
                        if (addIndex == -1) {
                            throw new IllegalStateException("'size' field and container actual size mismatch.");
                        }
                    }
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
     * Convenience overload that adds exactly 1 of {@code id}.
     *
     * @param id The item id to add.
     * @return {@code true} if successful.
     */
    public boolean add(int id) {
        return add(new Item(id));
    }

    /**
     * Attempts to add all items in {@code items}.
     * <p>
     * Null entries are skipped. Operation is wrapped in a bulk update for reduced event spam.
     *
     * @param items The items to add.
     * @return {@code true} if at least one item was added.
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
            }
        } finally {
            finishBulkUpdate();
        }
        return added;
    }

    /**
     * Attempts to remove {@code item}, preferring {@code preferredIndex} where appropriate.
     * <p>
     * Removal rules:
     * <ul>
     *   <li>Dynamic items are matched by {@link Item#equals(Object)} against existing slots.</li>
     *   <li>Non-dynamic items remove from the preferred index if it matches the id, otherwise remove from the first
     *       matching id index.</li>
     *   <li>Stackables reduce amount or clear the slot when depleted.</li>
     *   <li>Non-stackables clear up to {@code item.getAmount()} matching slots.</li>
     * </ul>
     *
     * @param preferredIndex Preferred slot to remove from, or -1 to select automatically.
     * @param item The item to remove.
     * @return {@code true} if any removal occurred; false if nothing matched.
     */
    public boolean remove(int preferredIndex, Item item) {
        checkArgument(preferredIndex >= -1, "Preferred index must be above or equal to -1.");

        int removeIndex = -1;
        if (item.isDynamic()) {
            for (int index = 0; index < capacity; index++) {
                Item nextItem = get(index);
                if (item.equals(nextItem)) {
                    removeIndex = index;
                    break;
                }
            }
        } else if (preferredIndex == -1 || !occupied(preferredIndex)) {
            removeIndex = computeIndexForId(item.getId());
        } else if (item.getId() == get(preferredIndex).getId()) {
            removeIndex = preferredIndex;
        }

        if (removeIndex == -1) {
            return false;
        }

        if (item.isDynamic()) {
            set(removeIndex, null);
        } else if (stackable(item)) {
            Item current = get(removeIndex);
            if (item.contains(current)) {
                set(removeIndex, null);
            } else {
                set(removeIndex, current.addAmount(-item.getAmount()));
            }
        } else {
            int until = computeAmountForId(item.getId());
            until = Math.min(item.getAmount(), until);

            startBulkUpdate();
            try {
                for (int removed = 0; removed < until; removed++) {
                    boolean isItemPresent = computeIdForIndex(removeIndex) == item.getId();
                    removeIndex = isItemPresent ? removeIndex : computeIndexForId(item.getId());

                    if (removeIndex == -1) {
                        return true;
                    }

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
     * Convenience overload that removes exactly 1 of {@code id}.
     *
     * @param id The item id to remove.
     * @return {@code true} if successful.
     */
    public boolean remove(int id) {
        return remove(-1, new Item(id));
    }

    /**
     * Attempts to remove all items in {@code items}.
     * <p>
     * Null entries are skipped. Operation is wrapped in a bulk update for reduced event spam.
     *
     * @param items The items to remove.
     * @return {@code true} if at least one item was removed.
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
            }
        } finally {
            finishBulkUpdate();
        }
        return removed;
    }

    /**
     * Removes all {@code removeItems} then adds all {@code addItems} in one bulk update scope.
     * <p>
     * This is useful for transactional inventory adjustments (e.g., crafting, trading, exchanges).
     *
     * @param addItems The items to add after removals.
     * @param removeItems The items to remove first.
     * @return {@code true} if at least one change occurred.
     */
    public boolean updateAll(Iterable<? extends Item> addItems, Iterable<? extends Item> removeItems) {
        boolean changed = false;
        startBulkUpdate();
        try {
            for (Item item : removeItems) {
                if (item == null) {
                    continue;
                }
                if (remove(item)) {
                    changed = true;
                }
            }

            for (Item item : addItems) {
                if (item == null) {
                    continue;
                }
                if (add(item)) {
                    changed = true;
                }
            }
        } finally {
            finishBulkUpdate();
        }
        return changed;
    }

    /**
     * Replaces the first occurrence of {@code oldId} with {@code newId}.
     * <p>
     * Only intended for non-stackable ids (enforced). For stackable items, replacement semantics are ambiguous because
     * one slot can represent many units.
     *
     * @param oldId The id to replace.
     * @param newId The replacement id.
     * @return {@code true} if an occurrence was found and replaced.
     */
    public final boolean replace(int oldId, int newId) {
        checkArgument(!stackable(oldId) && !stackable(newId), "Cannot replace stackable items.");

        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item != null && item.getId() == oldId) {
                set(index, item.withId(newId));
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces up to {@code amount} occurrences of {@code oldId} with {@code newId}.
     * <p>
     * Runs within a bulk update scope. The return value is the total amount replaced, which for non-stackables
     * typically increments by 1 per slot.
     *
     * @param oldId The id to replace.
     * @param newId The replacement id.
     * @param amount Maximum number of occurrences to replace.
     * @return The total amount of items replaced.
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
                    set(index, item.withId(newId));
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
     * @param oldId The id to replace.
     * @param newId The replacement id.
     * @return The total amount replaced.
     */
    public final int replaceAll(int oldId, int newId) {
        return replace(oldId, newId, Integer.MAX_VALUE);
    }

    /**
     * Finds the next free (null) slot index.
     *
     * @return The first free index, or -1 if none exist.
     */
    public final int getNextFreeIndex() {
        for (int index = 0; index < capacity; index++) {
            if (!occupied(index)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Finds the first index containing an item with {@code id}.
     *
     * @param id The item id to search for.
     * @return The first matching index, or -1 if not found.
     */
    public final int computeIndexForId(int id) {
        for (int index = 0; index < capacity; index++) {
            if (computeIdForIndex(index) == id) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Computes the total quantity of {@code id} present in this container.
     * <p>
     * For stackable ids, this returns the amount stored in the single stack slot. For non-stackable ids, this sums
     * across all matching slots.
     *
     * @param id The item id to sum.
     * @return The total quantity present.
     */
    public final int computeAmountForId(int id) {
        boolean isStackable = stackable(id);
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
     * Computes the item id stored at {@code index}.
     *
     * @param index The slot index.
     * @return The id at this slot, or -1 if empty.
     */
    public final int computeIdForIndex(int index) {
        Item item = get(index);
        return item != null ? item.getId() : -1;
    }

    /**
     * Computes the item amount stored at {@code index}.
     *
     * @param index The slot index.
     * @return The amount at this slot, or 0 if empty.
     */
    public final int computeAmountForIndex(int index) {
        Item item = get(index);
        return item != null ? item.getAmount() : 0;
    }

    /**
     * Computes remaining free slots in O(1) time using {@link #size}.
     *
     * @return The free slot count.
     */
    public final int computeRemainingSize() {
        return capacity - size;
    }

    /**
     * @return {@code true} if this container has no free slots.
     */
    public final boolean isFull() {
        return computeRemainingSize() == 0;
    }

    /**
     * Performs an indexed iteration over the entire capacity and calls {@code action} for each slot.
     * <p>
     * Note: this method wraps iteration in a bulk update scope.
     *
     * @param action The action callback (index, item-or-null).
     */
    public void forIndexedItems(BiConsumer<Integer, Item> action) {
        startBulkUpdate();
        try {
            for (int index = 0; index < capacity; index++) {
                action.accept(index, get(index));
            }
        } finally {
            finishBulkUpdate();
        }
    }

    /**
     * Checks whether slot {@code index} contains an item with {@code id}.
     *
     * @param index The slot index.
     * @param id The item id to compare.
     * @return {@code true} if {@code id} is contained.
     */
    public boolean contains(int index, int id) {
        int lookupId = computeIdForIndex(index);
        return lookupId != -1 && lookupId == id;
    }

    /**
     * Checks whether slot {@code index} contains an item that satisfies {@link Item#contains(Item)} semantics.
     *
     * @param index The slot index.
     * @param item The requested item (id + amount).
     * @return {@code true} if the slot item exists and satisfies {@code item.contains(existing)}.
     */
    public boolean contains(int index, Item item) {
        Item existing = get(index);
        return existing != null && item.contains(existing);
    }

    /**
     * Checks whether any slot contains {@code id}.
     *
     * @param id The item id to search for.
     * @return {@code true} if present.
     */
    public final boolean contains(int id) {
        return computeIndexForId(id) != -1;
    }

    /**
     * Checks whether the provided ids are present at least once.
     *
     * @param ids The item ids.
     * @return {@code true} if all ids exist in this container, or true if none provided.
     */
    public final boolean containsAll(int... ids) {
        if (ids.length == 0) {
            return true;
        }
        return containsAllIds(Ints.asList(ids));
    }

    /**
     * Checks whether all ids in {@code ids} exist in this container at least once.
     *
     * @param ids The item ids.
     * @return {@code true} if all exist.
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
     * Checks whether any of the provided ids are present.
     *
     * @param ids The item ids.
     * @return {@code true} if any exist, or true if none provided.
     */
    public final boolean containsAny(int... ids) {
        if (ids.length == 0) {
            return true;
        }
        return containsAnyIds(Ints.asList(ids));
    }

    /**
     * Checks whether any id in {@code ids} exists in this container.
     *
     * @param ids The item ids.
     * @return {@code true} if any exist.
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
     * Checks whether the container contains at least {@code item.getAmount()} of {@code item.getId()}.
     *
     * @param item The item requirement.
     * @return {@code true} if quantity requirement is met.
     */
    public final boolean contains(Item item) {
        return computeAmountForId(item.getId()) >= item.getAmount();
    }

    /**
     * Checks whether the container satisfies all provided item requirements.
     *
     * @param items the item requirements.
     * @return {@code true} if all are satisfied.
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
     * Checks whether the container satisfies any of the provided item requirements.
     *
     * @param items The item requirements.
     * @return {@code true} if any are satisfied.
     */
    public final boolean containsAny(Item... items) {
        return containsAny(Arrays.asList(items));
    }

    /**
     * Checks whether the container satisfies any of the provided item requirements.
     *
     * @param items The item requirements.
     * @return {@code true} if any are satisfied.
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
     * Determines whether there is enough remaining capacity to add {@code item} in its current form.
     * <p>
     * This uses {@link #computeSpaceFor(Item)} to account for stacking rules and overflow checks.
     *
     * @param item The item to test.
     * @return {@code true} if the container can accommodate the item.
     */
    public final boolean hasSpaceFor(Item item) {
        return computeSpaceFor(item) <= computeRemainingSize();
    }

    /**
     * Determines whether there is enough remaining capacity to add all {@code items}.
     * <p>
     * Null entries are ignored. Uses saturated addition to avoid integer overflow while computing total space.
     *
     * @param items The items to test.
     * @return {@code true} if all items can fit.
     */
    public final boolean hasSpaceForAll(Iterable<? extends Item> items) {
        int count = 0;
        for (Item item : items) {
            if (item == null) {
                continue;
            }
            count = IntMath.saturatedAdd(count, computeSpaceFor(item));
            if (count > computeRemainingSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes how many slots are required to add {@code item} given stacking rules and dynamic item handling.
     * <p>
     * Rules:
     * <ul>
     *   <li>If item will stack and is not dynamic:
     *     <ul>
     *       <li>requires 1 slot if no stack exists yet</li>
     *       <li>requires 0 slots if stack exists and amount won't overflow</li>
     *       <li>returns {@link Integer#MAX_VALUE} if stacking would overflow</li>
     *     </ul>
     *   </li>
     *   <li>Otherwise, requires {@code item.getAmount()} slots (non-stackables).</li>
     * </ul>
     *
     * @param item The item to compute for.
     * @return The required slots (or MAX_VALUE if impossible due to overflow).
     */
    public final int computeSpaceFor(Item item) {
        if (stackable(item) && !item.isDynamic()) {
            int index = computeIndexForId(item.getId());
            if (index == -1) {
                return 1;
            } else if (get(index).getAmount() + item.getAmount() < 0) {
                return Integer.MAX_VALUE;
            } else {
                return 0;
            }
        }
        return item.getAmount();
    }

    /**
     * Computes the total number of slots required to add all {@code items}.
     * <p>
     * Null entries are ignored. Uses saturated addition to avoid integer overflow.
     *
     * @param items The items to compute for.
     * @return The required slots.
     */
    public final int computeSpaceForAll(Iterable<? extends Item> items) {
        int count = 0;
        for (Item item : items) {
            if (item == null) {
                continue;
            }
            count = IntMath.saturatedAdd(count, computeSpaceFor(item));
        }
        return count;
    }

    /**
     * Determines whether {@code item} will stack in this container, based on policy and item id definition.
     *
     * @param item The item to check.
     * @return {@code true} if items of this id stack under current policy.
     */
    public final boolean stackable(Item item) {
        return stackable(item.getId());
    }

    /**
     * Determines whether items with {@code id} will stack in this container, based on {@link #policy}.
     *
     * @param id The item id to check.
     * @return {@code true} if stackable.
     */
    public final boolean stackable(int id) {
        return (policy == StackPolicy.STANDARD && ItemDefinition.ALL.retrieve(id).isStackable())
                || policy == StackPolicy.ALWAYS;
    }

    /**
     * Writes this container's contents to the primary widget for {@code player}.
     *
     * @param player The player receiving the widget refresh.
     */
    public final void updatePrimaryWidget(Player player) {
        player.queue(new WidgetItemsMessageWriter(primaryWidget, items));
    }

    /**
     * Writes this container's contents to the secondary widget for {@code player}, if configured.
     *
     * @param player The player receiving the widget refresh.
     */
    public final void updateSecondaryWidget(Player player) {
        if (secondaryWidget.isPresent()) {
            player.queue(new WidgetItemsMessageWriter(secondaryWidget.getAsInt(), items));
        }
    }

    /**
     * Swaps the items at {@code firstIndex} and {@code secondIndex}.
     * <p>
     * Runs inside a bulk update scope so listeners treat this as a batch change.
     *
     * @param firstIndex The first slot index.
     * @param secondIndex The second slot index.
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
     * Inserts the item at {@code oldIndex} into {@code newIndex}, shifting intermediate items.
     * <p>
     * If {@code newIndex > oldIndex}, items are shifted left-to-right. If {@code newIndex < oldIndex}, items are
     * shifted right-to-left.
     *
     * @param oldIndex The original slot index.
     * @param newIndex The destination slot index.
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
     * Performs a raw swap between two indices.
     * <p>
     * This method validates indices and then uses {@link #set(int, Item)} to ensure size tracking and events work.
     *
     * @param firstIndex The source index.
     * @param secondIndex The target index.
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
     * Loads this container from a list of indexed items, replacing all existing contents.
     * <p>
     * This method:
     * <ol>
     *   <li>Clears the container</li>
     *   <li>Sets {@link #items} slots directly from {@code setItems}</li>
     *   <li>Recomputes {@link #size}</li>
     *   <li>Fires {@link #initialize()} once</li>
     * </ol>
     *
     * @param setItems The items to load, each containing a slot index.
     */
    public final void load(List<IndexedItem> setItems) {
        clear();
        for (IndexedItem item : setItems) {
            items[item.getIndex()] = item.toItem();
            size++;
        }
        initialize();
    }

    /**
     * @return A shallow copy of the backing array (may include null slots).
     */
    public final Item[] toArray() {
        return Arrays.copyOf(items, items.length);
    }

    /**
     * Converts the container to a list of {@link IndexedItem} entries.
     * <p>
     * Only non-null slots are included.
     *
     * @return The list of indexed items with capacity {@link #size()}.
     */
    public final List<IndexedItem> toList() {
        var list = new ArrayList<IndexedItem>(size);
        for (int index = 0; index < capacity; index++) {
            Item item = get(index);
            if (item == null) {
                continue;
            }
            list.add(item.withIndex(index));
        }
        return list;
    }

    /**
     * The sets slot {@code index} to {@code item}.
     * <p>
     * This is the ONLY safe way to mutate the container because it:
     * <ul>
     *   <li>Maintains {@link #size} (non-null slot count).</li>
     *   <li>Fires listener callbacks via {@link #onItemsChanged(int, Item, Item)}.</li>
     * </ul>
     *
     * @param index The slot index.
     * @param item The new item value, or null to clear.
     */
    public final void set(int index, Item item) {
        checkArgument(index >= 0 && index < capacity, "Index out of bounds!");
        boolean removingItem = item == null;

        if (!occupied(index) && !removingItem) {
            size++;
        } else if (occupied(index) && removingItem) {
            size--;
        }

        Item oldItem = get(index);
        items[index] = item;

        onItemsChanged(index, oldItem, item);
    }

    /**
     * Gets the item at {@code index}.
     *
     * @param index The slot index.
     * @return The item at that slot, or null if empty.
     */
    public final Item get(int index) {
        checkArgument(index >= 0 && index < capacity, "Index out of bounds!");
        return items[index];
    }

    /**
     * Checks whether slot {@code index} is occupied (non-null).
     *
     * @param index The slot index.
     * @return {@code true} if occupied.
     */
    public final boolean occupied(int index) {
        checkArgument(index >= 0 && index < capacity, "Index out of bounds!");
        return get(index) != null;
    }

    /**
     * Shifts all items left, removing gaps (null slots) between items.
     * <p>
     * This is typically used for banking.
     */
    public void clearSpaces() {
        if (size > 0) {
            Queue<Integer> indexes = new ArrayDeque<>();
            int shiftAmount = size;

            startBulkUpdate();
            try {
                for (int index = 0; index < capacity; index++) {
                    if (shiftAmount == 0) {
                        break;
                    } else if (occupied(index)) {
                        Integer newIndex = indexes.poll();
                        if (newIndex != null) {
                            set(newIndex, get(index));
                            set(index, null);
                            indexes.add(index);
                        }
                        shiftAmount--;
                    } else {
                        indexes.add(index);
                    }
                }
            } finally {
                finishBulkUpdate();
            }
        }
    }

    /**
     * Clears all slots in this container (sets all indices to null).
     * <p>
     * Runs in a bulk update scope and uses {@link #set(int, Item)} so {@link #size} becomes 0 and listeners are
     * informed appropriately.
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
     * Replaces the listener set for this container.
     * <p>
     * The given array is snapshot-copied into an immutable list.
     *
     * @param newListeners The new listeners to use.
     */
    public final void setListeners(ItemContainerListener... newListeners) {
        listeners = ImmutableList.copyOf(newListeners);
    }

    /**
     * Dispatches item change events to listeners if events are enabled and the slot value actually changed.
     * <p>
     * If {@link #isBulkUpdating()} is true, listeners receive {@code onBulkUpdate}; otherwise they receive
     * {@code onSingleUpdate}.
     *
     * @param index The slot index changed.
     * @param oldItem The previous value (may be null).
     * @param newItem The new value (may be null).
     */
    private void onItemsChanged(int index, Item oldItem, Item newItem) {
        if (firingEvents && !Objects.equals(oldItem, newItem)) {
            for (ItemContainerListener listener : listeners) {
                if (isBulkUpdating()) {
                    listener.onBulkUpdate(index, this, oldItem, newItem);
                } else {
                    listener.onSingleUpdate(index, this, oldItem, newItem);
                }
            }
        }
    }

    /**
     * Fires a capacity exceeded event to all listeners (if events are enabled).
     * <p>
     * This is invoked when an add operation cannot proceed due to lack of slots, or when a stackable add would
     * overflow the integer amount.
     */
    public final void onCapacityExceeded() {
        if (firingEvents) {
            listeners.forEach(listener -> listener.onCapacityExceeded(this));
        }
    }

    /**
     * Fires a one-time initialization event to listeners.
     * <p>
     * This should be called after persistent loading (see {@link #load(List)}). It will only fire once.
     */
    public final void initialize() {
        if (firingEvents && !initialized) {
            initialized = true;
            listeners.forEach(listener -> listener.onInit(this));
        }
    }

    /**
     * @return The fixed container capacity (slot count).
     */
    public final int capacity() {
        return capacity;
    }

    /**
     * @return The cached number of occupied (non-null) slots.
     */
    public final int size() {
        return size;
    }

    /**
     * @return The stack policy used by this container.
     */
    public final StackPolicy getPolicy() {
        return policy;
    }

    /**
     * @return The primary widget id used for UI refresh.
     */
    public final int getPrimaryWidget() {
        return primaryWidget;
    }

    /**
     * Removes the secondary widget id so {@link #updateSecondaryWidget(Player)} does nothing.
     */
    public final void clearSecondaryWidget() {
        secondaryWidget = OptionalInt.empty();
    }

    /**
     * Sets the secondary widget id.
     *
     * @param id The widget id to use.
     */
    public final void setSecondaryWidget(int id) {
        secondaryWidget = OptionalInt.of(id);
    }

    /**
     * @return The secondary widget id if present.
     */
    public final OptionalInt getSecondaryWidget() {
        return secondaryWidget;
    }

    /**
     * @return {@code true} if listener events are currently enabled.
     */
    public final boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Enables listener event dispatch.
     */
    public final void startEvents() {
        firingEvents = true;
    }

    /**
     * Disables listener event dispatch.
     */
    public final void stopEvents() {
        firingEvents = false;
    }

    /**
     * Begins (or nests) a bulk update operation.
     * <p>
     * While bulk updating, {@link #onItemsChanged(int, Item, Item)} emits bulk events instead of single events.
     * When the bulk update counter returns to 0, {@link ItemContainerListener#onBulkUpdateCompleted(ItemContainer)}
     * is fired.
     */
    public final void startBulkUpdate() {
        bulkUpdates++;
    }

    /**
     * Completes a bulk update operation.
     * <p>
     * Must be balanced with {@link #startBulkUpdate()}. When the internal counter reaches 0, bulk update completion
     * is fired (if events are enabled).
     *
     * @throws IllegalStateException If called when no bulk update is active.
     */
    public final void finishBulkUpdate() {
        checkState(isBulkUpdating(), "startBulkUpdate must be called at least once before finishBulkUpdate");
        if (--bulkUpdates == 0) {
            if (firingEvents) {
                listeners.forEach(listener -> listener.onBulkUpdateCompleted(this));
            }
        }
    }

    /**
     * @return {@code true} if currently inside a bulk update scope.
     */
    public final boolean isBulkUpdating() {
        return bulkUpdates > 0;
    }

    /**
     * @return {@code true} if {@link #initialize()} has been fired.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
