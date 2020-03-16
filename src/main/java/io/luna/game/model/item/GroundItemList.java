package io.luna.game.model.item;

import com.google.common.collect.Iterators;
import io.luna.game.model.EntityList;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.task.Task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link EntityList} implementation model for {@link GroundItem}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GroundItemList extends EntityList<GroundItem> {

    /**
     * A {@link Task} that will handle ground item expiration.
     */
    private final class ExpirationTask extends Task {

        /**
         * The amount of ticks it takes for a tradeable item to become global.
         */
        private static final int TRADEABLE_LOCAL_TICKS = 100;

        /**
         * The amount of ticks it takes for an untradeable item to expire.
         */
        private static final int UNTRADEABLE_LOCAL_TICKS = 300;

        /**
         * The amount of ticks it takes for a global item to expire.
         */
        private static final int GLOBAL_TICKS = 300;

        /**
         * Creates a new {@link ExpirationTask}.
         */
        public ExpirationTask() {
            super(false, 1);
        }

        /**
         * A queue of items awaiting registration.
         */
        private final Queue<GroundItem> registerQueue = new ArrayDeque<>();

        /**
         * A queue of items awaiting unregistration.
         */
        private final Queue<GroundItem> unregisterQueue = new ArrayDeque<>();

        @Override
        protected boolean onSchedule() {
            checkState(!expiring, "The expiration task has already been started.");
            expiring = true;
            return true;
        }

        @Override
        protected void execute() {
            processItems();
            processUnregistrations();
            processRegistrations();
        }

        /**
         * Process expiration timers for all perishable items.
         */
        private void processItems() {
            for (var item : world.getItems()) {
                if (!item.isExpiring()) {
                    continue;
                }
                boolean isTradeable = item.def().isTradeable();
                int expireTicks = item.addExpireTick();
                if (item.isLocal()) {
                    if (isTradeable && expireTicks >= TRADEABLE_LOCAL_TICKS) {
                        // Item is tradeable and only visible to one player, make it global.
                        var globalItem = new GroundItem(item.getContext(), item.getId(), item.getAmount(),
                                item.getPosition(), Optional.empty());
                        unregisterQueue.add(item);
                        registerQueue.add(globalItem);
                    } else if (!isTradeable && expireTicks >= UNTRADEABLE_LOCAL_TICKS) {
                        // Item is untradeable and only visible to one player, unregister it.
                        unregisterQueue.add(item);
                    }
                } else if (item.isGlobal() && expireTicks >= GLOBAL_TICKS) {
                    // Item is visible to everyone, unregister it.
                    unregisterQueue.add(item);
                }
            }
        }

        /**
         * Handle any new unregistrations from expiration timer processing.
         */
        private void processUnregistrations() {
            for (; ; ) {
                var nextItem = unregisterQueue.poll();
                if (nextItem == null) {
                    break;
                }
                world.getItems().unregister(nextItem);
            }
        }

        /**
         * Handle any new registrations from expiration timer processing.
         */
        private void processRegistrations() {
            for (; ; ) {
                var nextItem = registerQueue.poll();
                if (nextItem == null) {
                    break;
                }
                world.getItems().register(nextItem);
            }
        }
    }

    /**
     * The ground items.
     */
    private final List<GroundItem> items = new ArrayList<>(128);

    /**
     * If the expiration task was started.
     */
    private boolean expiring;

    /**
     * Creates a new {@link GroundItemList}.
     *
     * @param world The world.
     */
    public GroundItemList(World world) {
        super(world, EntityType.ITEM);
    }

    /**
     * @implSpec The spliterator reports the characteristics of SIZED, SUBSIZED, NONNULL, and IMMUTABLE.
     */
    @Override
    public Spliterator<GroundItem> spliterator() {
        return Spliterators.spliterator(items, Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.DISTINCT);
    }

    @Override
    public Iterator<GroundItem> iterator() {
        return Iterators.unmodifiableIterator(items.iterator());
    }

    @Override
    protected boolean onRegister(GroundItem item) {
        if (item.def().isStackable()) {
            return addStackable(item);
        } else {
            return add(item);
        }
    }

    @Override
    protected boolean onUnregister(GroundItem item) {
        if (item.def().isStackable()) {
            return removeStackable(item);
        } else {
            return remove(item);
        }
    }

    @Override
    public int size() {
        return items.size();
    }

    /**
     * Starts the ground item expiration task. This will make it so that items are automatically unregistered after
     * not being picked up for a certain amount of time.
     */
    public void startExpirationTask() {
        world.schedule(new ExpirationTask());
    }

    /**
     * Adds a stackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean addStackable(GroundItem item) {
        var position = item.getPosition();
        Optional<GroundItem> foundItem = findExisting(item);
        if (foundItem.isPresent()) {
            var existing = foundItem.get();
            int newAmount = item.getAmount() + existing.getAmount();
            if (newAmount < 0) { // Overflow.
                return false;
            }

            if (removeFromSet(existing)) { // Remove some of the existing item, add with new amount.
                return addToSet(new GroundItem(item.getContext(), item.getId(), newAmount,
                        position, item.getOwner()));
            }
        } else if (tileSpaceFor(position, 1)) {
            return addToSet(item);
        }
        return false;
    }

    /**
     * Removes a stackable ground item.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean removeStackable(GroundItem item) {
        int removeAmount = item.getAmount();
        var position = item.getPosition();
        Optional<GroundItem> foundItem = findExisting(item);
        if (foundItem.isPresent()) {
            var existing = foundItem.get();
            int newAmount = existing.getAmount() - removeAmount;
            if (newAmount <= 0) { // Remove all of the item.
                return removeFromSet(existing);
            }

            if (removeFromSet(existing)) { // Remove item, add with new amount.
                return addToSet(new GroundItem(item.getContext(), item.getId(), newAmount,
                        position, item.getOwner()));
            }
        }
        return true; // Item wasn't found.
    }

    /**
     * Adds unstackable ground items.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean add(GroundItem item) {
        int addAmount = item.getAmount();
        if (!tileSpaceFor(item.getPosition(), addAmount)) { // Too many items on one tile.
            return false;
        }
        if (addAmount == 1) {
            return addToSet(item);
        }

        boolean failed = true;
        for (int i = 0; i < addAmount; i++) { // Add items 1 by 1.
            if (addToSet(new GroundItem(item.getContext(), item.getId(), 1, item.getPosition(), item.getOwner()))) {
                failed = false;
            }
        }
        return !failed;
    }

    /**
     * Removes unstackable ground items.
     *
     * @param item The item.
     * @return {@code true} if successful.
     */
    private boolean remove(GroundItem item) {
        int loops = item.getAmount();
        if (loops == 1) {
            return findExisting(item).map(this::removeFromSet).orElse(true);
        }
        boolean failed = true;
        var iter = findAllExisting(item).iterator();
        while (iter.hasNext()) {
            var nextItem = iter.next();
            if (removeFromSet(nextItem)) {
                failed = false;
            }
            if (--loops <= 0) {
                break;
            }
        }
        return !failed;
    }

    /**
     * Determines if this tile has space for {@code addAmount} new item models.
     *
     * @param position The tile.
     * @param addAmount The amount of item models.
     * @return {@code true} if this tile has enough space.
     */
    private boolean tileSpaceFor(Position position, int addAmount) {
        return world.getChunks().load(position).stream(type).
                filter(it -> it.getPosition().equals(position)).count() + addAmount <= 255;
    }

    /**
     * Adds {@code item} to the backing set and makes it visible.
     *
     * @param item The item to add.
     * @return {@code true} if successful.
     */
    private boolean addToSet(GroundItem item) {
        if (items.add(item)) {
            item.setState(EntityState.ACTIVE);
            item.show();
            return true;
        }
        return false;
    }

    /**
     * Removes {@code item} from the backing set and makes it invisible.
     *
     * @param item The item to remove.
     * @return {@code true} if successful.
     */
    private boolean removeFromSet(GroundItem item) {
        if (items.remove(item)) {
            item.hide();
            item.setState(EntityState.INACTIVE);
            return true;
        }
        return false;
    }

    /**
     * Finds all existing ground items matching {@code item}.
     *
     * @param item The item to find.
     * @return The found items.
     */
    private Stream<GroundItem> findAllExisting(GroundItem item) {
        Position position = item.getPosition();
        Stream<GroundItem> localItems = world.getChunks().
                load(position).
                stream(type);
        return localItems.filter(it -> it.getId() == item.getId() &&
                it.getPosition().equals(position) &&
                it.getOwner().equals(item.getOwner()));
    }

    /**
     * Finds an existing ground item matching {@code item}.
     *
     * @param item The item to find.
     * @return The found item.
     */
    private Optional<GroundItem> findExisting(GroundItem item) {
        return findAllExisting(item).findFirst();
    }
}