package io.luna.game.model.item;

import com.google.common.collect.Iterators;
import io.luna.game.model.EntityList;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;

/**
 * An {@link EntityList} implementation model for {@link GroundItem}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GroundItemList extends EntityList<GroundItem> {

    /**
     * An {@link Iterator} that will unregister ground items on {@link #remove()}.
     */
    public final class GroundItemListIterator implements Iterator<GroundItem> {

        // TODO Unit tests for this.
        /**
         *
         */
        private GroundItem lastItem;
        private final Iterator<GroundItem> delegate = items.iterator();

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public GroundItem next() {
            lastItem = delegate.next();
            return lastItem;
        }

        @Override
        public void remove() {
            delegate.remove();
            unregister(lastItem);
        }
    }

    /**
     * The ground items.
     */
    private final Set<GroundItem> items = new HashSet<>(128);

    /**
     * Creates a new {@link GroundItemList}.
     *
     * @param world The world.
     */
    public GroundItemList(World world) {
        super(world, EntityType.ITEM);
    }

    @Override
    public Spliterator<GroundItem> spliterator() {
        return Spliterators.spliterator(iterator(), size(), Spliterator.NONNULL | Spliterator.IMMUTABLE);
    }

    @Override
    public Iterator<GroundItem> iterator() {
        return Iterators.unmodifiableIterator(items.iterator());
    }

    @Override
    protected void onRegister(GroundItem item) {
        if (item.def().isStackable()) {
            addStackable(item);
        } else {
            add(item);
        }
    }

    @Override
    protected void onUnregister(GroundItem item) {
        if (item.def().isStackable()) {
            removeStackable(item);
        } else {
            remove(item);
        }
    }

    /**
     * Adds a stackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private void addStackable(GroundItem item) {
        Position position = item.getPosition();
        Optional<GroundItem> foundItem = findExisting(item);
        if (foundItem.isPresent()) {
            GroundItem existing = foundItem.get();
            int newAmount = item.getAmount() + existing.getAmount();
            if (newAmount < 0) { // Overflow.
                return;
            }
            unregister(existing); // Remove existing.

            // Add with new amount.
            GroundItem newItem = new GroundItem(item.getContext(), item.getId(), newAmount,
                    position, item.getPlayer());
            items.add(newItem);
            newItem.setState(EntityState.ACTIVE);
            newItem.show();
        } else if (checkItemAmount(position, 1)) {
            items.add(item);
            item.setState(EntityState.ACTIVE);
            item.show();
        }
    }

    private void removeStackable(GroundItem item) {
        // TODO new item when items have different owners. that's it :) or look in client again?
        int amount = item.getAmount();
        Position position = item.getPosition();
        Optional<GroundItem> foundItem = findExisting(item);
        if (foundItem.isPresent()) {
            GroundItem existing = foundItem.get();
            int newAmount = item.getAmount() + existing.getAmount();
            if (newAmount < 0) { // Overflow.
                return;
            }
            unregister(existing); // Remove existing.

            // Add with new amount.
            GroundItem newItem = new GroundItem(item.getContext(), item.getId(), newAmount,
                    position, item.getPlayer());
            items.add(newItem);
            newItem.show();
        } else if (checkItemAmount(position, 1)) {
            items.add(item);
            item.show();
        }
        // Remove stackable item.
        //       return findExisting(item).map(it ->
        //             items.remove(it) && unregister(it)).orElse(false);
    }

    /**
     * Adds a unstackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean add(GroundItem item) {
        int amount = item.getAmount();
        if (!checkItemAmount(item.getPosition(), amount)) { // Too many items on one tile.
            return false;
        }

        boolean failed = true;
        for (int i = 0; i < amount; i++) { // Add items 1 by 1.
            item = new GroundItem(item.getContext(), item.getId(), 1,
                    item.getPosition(), item.getPlayer());
            if (items.add(item) && register(item)) {
                failed = false;
            }
        }
        return !failed;
    }

    private void remove(GroundItem item) {

        boolean failed = true;
        for (int i = 0; i < item.getAmount(); i++) {
            Optional<GroundItem> foundItem = findExisting(item);
            if (!foundItem.isPresent()) {
                break; // No more items left to remove.
            }

            GroundItem found = foundItem.get();
            if (items.remove(found) && unregister(found)) {
                failed = false;
            }
        }
    }

    private boolean checkItemAmount(Position position, int addAmount) {
        return world.getChunks().load(position).stream(type).
                filter(it -> it.getPosition().equals(position)).count() + addAmount <= 255;
    }

    /**
     * Finds an existing ground item on the same tile and with the same identifier as {@code item}.
     *
     * @param item The item to find.
     * @return The found item.
     */
    private Optional<GroundItem> findExisting(GroundItem item) {
        Position position = item.getPosition();
        Stream<GroundItem> localItems = world.getChunks().
                load(position).
                stream(type);
        return localItems.filter(it -> it.getId() == item.getId() &&
                it.getPosition().equals(position)).findFirst();
    }
}