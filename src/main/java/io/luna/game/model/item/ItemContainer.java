package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
 * An abstraction model that represents a collection of items.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ItemContainer implements Iterable<Item> {

    /**
     * An {@link Iterator} that will iterate over elements in {@code ItemCollection}s.
     *
     * @author lare96 <http://github.com/lare96>
     */
    private static final class ItemCollectionIterator implements Iterator<Item> {

        /**
         * The item collection to iterate over.
         */
        private final ItemContainer collection;

        /**
         * The current index being iterated over.
         */
        private int index;

        /**
         * The last index that was iterated over.
         */
        private int lastIndex = -1;

        /**
         * Creates a new {@link ItemCollectionIterator}.
         *
         * @param collection The collection to iterate over.
         */
        public ItemCollectionIterator(ItemContainer collection) {
            this.collection = collection;
        }

        @Override
        public boolean hasNext() {
            return (index + 1) <= collection.getCapacity();
        }

        @Override
        public Item next() {
            checkState(index < collection.capacity, "no more elements left to iterate");

            lastIndex = index;
            index++;
            return collection.items[lastIndex];
        }

        @Override
        public void remove() {
            checkState(lastIndex != -1, "can only be called once after \"next\"");

            collection.items[lastIndex] = null;
            collection.size--;
            // fire event change here
            index = lastIndex;
            lastIndex = -1;
        }
    }

    /**
     * An enumerated type that defines the stackable item policy of {@code ItemCollection}s.
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
     * A list of {@link ItemCollectionListener}s listening for events.
     */
    private final List<ItemCollectionListener> listeners = new ArrayList<>();

    /**
     * The capacity of this collection.
     */
    private final int capacity;

    /**
     * The policy of this collection.
     */
    private final StackPolicy policy;

    /**
     * The collection of {@link Item}s within this collection.
     */
    private final Item[] items;

    /**
     * The amount of non-{@code null} values within this collection.
     */
    private int size;

    /**
     * If events are currently being fired.
     */
    private boolean firingEvents = true;

    /**
     * Creates a new {@link ItemContainer}.
     *
     * @param capacity The capacity of this collection.
     * @param policy The collection of {@link Item}s within this collection.
     */
    public ItemContainer(int capacity, StackPolicy policy) {
        this.capacity = capacity;
        this.policy = policy;
        items = new Item[capacity];
    }

    /**
     * Iterates through all of the {@link Item}s within this collection and performs {@code action} on them, skipping empty
     * slots ({@code null} values) as they are encountered.
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
        return new ItemCollectionIterator(this);
    }

    /**
     * @return A stream associated with the elements in this collection, built using the {@code spliterator()}
     * implementation.
     */
    public Stream<Item> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Attempts to add {@code item} into this collection, preferably at {@code preferredSlot}.
     *
     * @param item The {@link Item} to add.
     * @param preferredSlot The preferable slot to add {@code item} to.
     * @return {@code true} if the {@code Item} was added, {@code false} if there was not enough space left.
     */
    public boolean add(Item item, int preferredSlot) {
        checkArgument(preferredSlot >= -1, "invalid slot identifier");

        ItemDefinition def = item.getDefinition();
        boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

        if (stackable) {
            preferredSlot = computeSlotForId(item.getId());
        }
        preferredSlot = (preferredSlot == -1 || items[preferredSlot] != null) ? computeFreeSlot() : preferredSlot;

        if (preferredSlot == -1) { // Not enough space in collection.
            return false;
        }

        if (stackable) {
            Item current = items[preferredSlot];
            items[preferredSlot] = (current == null) ? item : current.increment(item.getAmount());
            size++;
        } else {
            int remaining = computeRemainingSize();
            int until = (remaining > item.getAmount()) ? item.getAmount() : remaining;

            for (int index = 0; index < until; index++) {
                preferredSlot = (items[preferredSlot] == null) ? preferredSlot : computeFreeSlot();
                items[preferredSlot++] = new Item(item.getId());
                size++;
            }
        }
        fireItemsAddedEvent();
        return true;
    }

    /**
     * Attempts to add {@code item} into this collection.
     *
     * @param item The {@link Item} to add.
     * @return {@code true} the {@code Item} was added, {@code false} if there was not enough space left.
     */
    public boolean add(Item item) {
        return add(item, -1);
    }

    /**
     * Attempts to add {@code items} in bulk into this collection.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(Collection<? extends Item> items) {
        firingEvents = false;

        boolean added = false;
        boolean failed = false;
        try {
            for (Item item : items) {
                if (item == null) {
                    continue;
                }
                if (add(item)) {
                    added = true;
                } else {
                    failed = true;
                }
            }
        } finally {
            firingEvents = true;
        }

        if (added) {
            fireItemsAddedEvent();
        }
        if (failed) {
            fireCapacityExceededEvent();
        }
        return added;
    }

    /**
     * Attempts to add {@code items} in bulk into this collection.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(Item... items) {
        return addAll(Arrays.asList(items));
    }

    /**
     * Attempts to add {@code items} in bulk into this collection.
     *
     * @param items The {@link Item}s to add.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were added, {@code false} if none could be added.
     */
    public boolean addAll(ItemContainer items) {
        return addAll(items.items);
    }

    /**
     * Attempts to remove {@code item} from this collection, preferably from {@code preferredSlot}.
     *
     * @param item The {@link Item} to remove.
     * @param preferredSlot The preferable slot to remove {@code item} from.
     * @return {@code true} if the {@code Item} was removed, {@code false} if it isn't present in this collection.
     */
    public boolean remove(Item item, int preferredSlot) {
        checkArgument(preferredSlot >= -1, "invalid slot identifier");

        ItemDefinition def = item.getDefinition();
        boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

        if (stackable) {
            preferredSlot = computeSlotForId(item.getId());
        } else {
            boolean noMatch = Optional.ofNullable(items[preferredSlot]).
                filter(it -> it.getId() != item.getId()).
                isPresent();
            preferredSlot = (preferredSlot == -1 || noMatch) ? computeSlotForId(item.getId()) : preferredSlot;
        }

        if (preferredSlot == -1) { // Item isn't present within this collection.
            return false;
        }

        if (stackable) {
            Item current = items[preferredSlot];
            if (current.getAmount() > item.getAmount()) {
                items[preferredSlot] = current.decrement(item.getAmount());
            } else {
                items[preferredSlot] = null;
                size--;
            }
        } else {
            int until = computeAmountForId(item.getAmount());

            for (int index = 0; index < until; index++) {
                preferredSlot =
                    (items[preferredSlot] != null && items[preferredSlot].getId() == item.getId()) ? preferredSlot :
                        computeSlotForId(item.getId());
                items[preferredSlot++] = null;
                size--;
            }
        }
        fireItemsRemovedEvent();
        return true;
    }

    /**
     * Attempts to remove {@code item} from this collection.
     *
     * @param item The {@link Item} to remove.
     * @return {@code true} if the {@code Item} was removed, {@code false} if it isn't present in this collection.
     */
    public boolean remove(Item item) {
        return remove(item, -1);
    }

    /**
     * Attempts to remove {@code items} in bulk from this collection.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(Collection<? extends Item> items) {
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
        return removed;
    }

    /**
     * Attempts to remove {@code items} in bulk from this collection.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(Item... items) {
        return removeAll(Arrays.asList(items));
    }

    /**
     * Attempts to remove {@code items} in bulk from this collection.
     *
     * @param items The {@link Item}s to remove.
     * @return {@code true} if at least {@code 1} of the {@code Item}s were remove, {@code false} if none could be removed.
     */
    public boolean removeAll(ItemContainer items) {
        return removeAll(items.items);
    }

    /**
     * Computes the next free ({@code null}) slot in this collection.
     *
     * @return The free slot, {@code -1} if no free slots could be found.
     */
    public final int computeFreeSlot() {
        for (int index = 0; index < capacity; index++) {
            if (items[index] == null) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Computes the first slot found that {@code id} is in.
     *
     * @param id The identifier to compute for.
     * @return The first slot found, {@code -1} if no {@link Item} with {@code id} is in this collection.
     */
    public final int computeSlotForId(int id) {
        for (int index = 0; index < capacity; index++) {
            Optional<Item> optional = Optional.ofNullable(items[index]).
                filter(item -> item.getId() == id);
            if (optional.isPresent()) {
                return index;
            }
        }
        return -1;
    }

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

    public final boolean replace(int oldId, int newId) {
        int index = computeSlotForId(oldId);
        if (index == -1) {
            return false;
        }

        Item oldItem = items[index];
        Item newItem = oldItem.setId(newId);

        if (remove(oldItem, index)) {
            return add(newItem, index);
        }
        return false;
    }

    public final boolean replaceAll(int oldId, int newId) {
        boolean replaced = false;
        while (replace(oldId, newId)) {
            replaced = true;
        }
        return replaced;
    }

    public boolean hasCapacityFor(Item item) {
        ItemDefinition def = item.getDefinition();
        boolean stackable = (policy == STANDARD && def.isStackable()) || policy == ALWAYS;

        if (stackable) {
            int index = computeSlotForId(item.getId());
            if (index == -1) {
                return computeRemainingSize() >= 1;
            }

            Item existing = items[index];
            if ((existing.getAmount() + item.getAmount()) < 1) {
                return false;
            }
            return true;
        }
        return computeRemainingSize() >= item.getAmount();
    }

    public boolean contains(int id) {
        return computeSlotForId(id) != -1;
    }

    public boolean containsAll(int... identifiers) {
        return Arrays.stream(identifiers).allMatch(this::contains);
    }

    public boolean containsAny(int... identifiers) {
        return Arrays.stream(identifiers).anyMatch(this::contains);
    }

    public boolean contains(Item item) {
        return stream().filter(Objects::nonNull)
            .anyMatch(it -> it.getId() == item.getId() && it.getAmount() >= item.getAmount());
    }

    public boolean containsAll(Item... items) {
        return Arrays.stream(items).filter(Objects::nonNull).allMatch(this::contains);
    }

    public boolean containsAny(Item... items) {
        return Arrays.stream(items).filter(Objects::nonNull).anyMatch(this::contains);
    }

    public void swap(boolean insert, int oldSlot, int newSlot) {
        checkBounds(oldSlot, newSlot);

        if (insert) {
            if (newSlot > oldSlot) {
                for (int slot = oldSlot; slot < newSlot; slot++) {
                    swap(slot, slot + 1);
                }
            } else if (oldSlot > newSlot) {
                for (int slot = oldSlot; slot > newSlot; slot--) {
                    swap(slot, slot - 1);
                }
            }
            forceRefresh();
        } else {
            Item item = items[oldSlot];
            items[oldSlot] = items[newSlot];
            items[newSlot] = item;
            notifyItemUpdated(oldSlot);
            notifyItemUpdated(newSlot);
        }
    }

    public void shift() {
        Item[] previousItems = items;
        items = new Item[capacity];
        int newIndex = 0;
        for (int i = 0; i < items.length; i++) {
            if (previousItems[i] != null) {
                items[newIndex] = previousItems[i];
                newIndex++;
            }
        }
    }

    /**
     * Sets the collection of items to {@code items}. The collection will not hold any references to the array, nor the item
     * instances in the array.
     *
     * @param items the new array of items, the capacities of this must be equal to or lesser than the collection.
     */
    public final void setItems(Item[] items) {
        Preconditions.checkArgument(items.length <= capacity);
        clear();
        for (int i = 0; i < items.length; i++)
            this.items[i] = items[i] == null ? null : items[i].copy();
    }

    public final List<Item> getItems() {
        return Collections.unmodifiableList(Arrays.asList(items));
    }

    public final Item[] toArray() {
        return Arrays.copyOf(items, items.length);
    }

    public void set(int slot, Item item) {
        items[slot] = item;
    }

    /**
     * Retrieves the item located on {@code slot}.
     *
     * @param slot the slot to get the item on.
     * @return the item on the slot, or {@code null} if no item exists on the slot.
     */
    public Item get(int slot) {
        if (slot == -1 || slot >= items.length)
            return null;
        return items[slot];
    }

    /**
     * Clears all of the items in this collection.
     */
    public void clear() {
        items = new Item[capacity];
    }

    /**
     * Adds a new listener to this item collection.
     *
     * @param listener the listener to add to this collection.
     * @return {@code true} if the listener was successfully added, {@code false} otherwise.
     */
    public final boolean addListener(ItemContainerListener listener) {
        return listeners.add(listener);
    }

    /**
     * Removes an existing listener from this item collection.
     *
     * @param listener the listener to remove from this collection.
     * @return {@code true} if the listener was successfully removed, {@code false} otherwise.
     */
    public final boolean removeListener(ItemContainerListener listener) {
        return listeners.remove(listener);
    }

    private void fireItemsAddedEvent() {
        if (firingEvents) {
            listeners.forEach(evt -> evt.itemsAdded(this));
        }
    }

    private void fireItemsRemovedEvent() {
        if (firingEvents) {
            listeners.forEach(evt -> evt.itemsRemoved(this));
        }
    }

    private void fireCapacityExceededEvent() {
        if (firingEvents) {
            listeners.forEach(evt -> evt.capacityExceeded(this));
        }
    }

    public final int computeRemainingSize() {
        return capacity - size;
    }

    public final int getSize() {
        return size;
    }

    public final int getCapacity() {
        return capacity;
    }

    public final StackPolicy getPolicy() {
        return policy;
    }
}
