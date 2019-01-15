package io.luna.game.model.item;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.EntityList;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

import java.util.HashSet;
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
        return Spliterators.spliterator(items, size());
    }

    @Override
    public UnmodifiableIterator<GroundItem> iterator() {
        return Iterators.unmodifiableIterator(items.iterator());
    }

    @Override
    public boolean add(GroundItem item) {
        if (item.def().isStackable()) {
            return addStackable(item);
        }
        return addNonStackable(item);
    }

    /**
     * Adds a stackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean addStackable(GroundItem item) {
        Optional<Boolean> added = findExisting(item).
                map(existing -> {
                    int newAmount = item.getAmount() + existing.getAmount();
                    if (newAmount < 0) { // Overflow.
                        return false;
                    }
                    remove(existing); // Remove existing.
                    GroundItem newItem = new GroundItem(item.getContext(), item.getId(), newAmount,
                            item.getPosition(), item.getPlayer());
                    return items.add(newItem) && register(newItem); // Add with new amount.
                });
        return added.orElse(false);
    }

    /**
     * Adds a non-stackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean addNonStackable(GroundItem item) {
        int amount = item.getAmount();
        int amountOnTile = world.getChunks().getChunk(item.getChunkPosition()).getAll(type).size();
        if (amountOnTile + amount > 255) { // Too many items on one tile.
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

    @Override
    public boolean remove(GroundItem item) {
        if (item.def().isStackable()) {
            // Remove stackable item.
            return findExisting(item).map(it ->
                    items.remove(it) && unregister(it)).orElse(false);
        }
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
        return !failed;
    }

    /**
     * Finds an existing ground item on the same tile and with the same identifier as {@code item}.
     *
     * @param item The item to find.
     * @return The found item.
     */
    private Optional<GroundItem> findExisting(GroundItem item) {
        Stream<GroundItem> localItems = world.getChunks().
                getChunk(item.getChunkPosition()).
                stream(type);
        return localItems.filter(it -> it.getId() == item.getId()).findFirst();
    }
}