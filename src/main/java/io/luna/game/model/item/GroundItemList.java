package io.luna.game.model.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import io.luna.Luna;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntityList;
import io.luna.game.model.World;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.task.Task;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link StationaryEntityList} implementation that owns and manages every {@link GroundItem} in a {@link World}.
 * <p>
 * <b>Primary responsibilities:</b>
 * <ul>
 *     <li><b>Tile-indexed storage</b> via a {@link ListMultimap} keyed by {@link Position} for fast per-tile lookups.</li>
 *     <li><b>Visibility lifecycle</b> by calling {@link GroundItem#show()} on registration and {@link GroundItem#hide()}
 *     on removal, using the item's {@link ChunkUpdatableView} for local/global visibility rules.</li>
 *     <li><b>Optional stack merging</b> for stackable items on the same tile and same view (configurable).</li>
 *     <li><b>Expiration processing</b> driven by a tick task that converts local tradeable items to global after a delay,
 *     and removes items after their lifetime expires.</li>
 * </ul>
 * <p>
 * <b>Tile cap / client limitation:</b>
 * RuneScape clients have an upper bound on the number of ground-item models per tile. This list enforces a hard cap of
 * {@value #MAX_ITEMS_PER_TILE} items per {@link Position}. When a tile is full, additional items are queued in
 * {@link #pending} until space becomes available.
 *
 * @author lare96
 */
public final class GroundItemList extends StationaryEntityList<GroundItem> {

    /**
     * The maximum number of ground items that may be visible on a single tile.
     */
    private static final int MAX_ITEMS_PER_TILE = 255;

    /**
     * A {@link Task} that processes ground item expiration.
     */
    private final class ExpirationTask extends Task {

        /**
         * The number of ticks before a tradeable local item becomes global.
         */
        private static final int TRADEABLE_LOCAL_TICKS = 100;

        /**
         * The number of ticks before an untradeable local item is removed.
         */
        private static final int UNTRADEABLE_LOCAL_TICKS = 300;

        /**
         * The number of ticks before a global item is removed.
         */
        private static final int GLOBAL_TICKS = 300;

        /**
         * Creates a new {@link ExpirationTask} that runs once per tick.
         */
        public ExpirationTask() {
            super(false, 1);
        }

        @Override
        protected void execute() {
            processItems();
            processPendingItems();
        }

        /**
         * Scans active items and updates their expiration state.
         */
        private void processItems() {
            Iterator<GroundItem> it = active.values().iterator();
            while (it.hasNext()) {
                GroundItem item = it.next();
                if (!item.isExpiring()) {
                    continue;
                }

                boolean isTradeable = item.def().isTradeable();
                int expireTicks = item.addExpireTick();

                if (item.isLocal()) {
                    if (isTradeable && expireTicks >= TRADEABLE_LOCAL_TICKS) {
                        /*
                         * Local + tradeable: convert to global visibility.
                         */
                        it.remove();
                        item.hide();
                        item.setState(EntityState.INACTIVE);

                        GroundItem globalItem = new GroundItem(
                                item.getContext(),
                                item.getId(),
                                item.getAmount(),
                                item.getPosition(),
                                ChunkUpdatableView.globalView());
                        pending.put(item.getPosition(), globalItem);
                    } else if (!isTradeable && expireTicks >= UNTRADEABLE_LOCAL_TICKS) {
                        /*
                         * Local + untradeable: remove after local-only lifetime.
                         */
                        it.remove();
                        item.hide();
                        item.setState(EntityState.INACTIVE);
                    }
                } else if (item.isGlobal() && expireTicks >= GLOBAL_TICKS) {
                    /*
                     * Global: remove after global lifetime.
                     */
                    it.remove();
                    item.hide();
                    item.setState(EntityState.INACTIVE);
                }
            }
        }

        /**
         * Drains queued items from {@link #pending} into {@link #active} when a tile has available capacity.
         * <p>
         * Items are promoted per-tile, in insertion order, until the tile reaches {@link #MAX_ITEMS_PER_TILE}.
         */
        private void processPendingItems() {
            for (Map.Entry<Position, Collection<GroundItem>> nextEntry : pending.asMap().entrySet()) {
                List<GroundItem> activeList = active.get(nextEntry.getKey());
                int spaces = MAX_ITEMS_PER_TILE - activeList.size();
                if (spaces < 1) {
                    /*
                     * No spaces yet; leave these queued.
                     */
                    continue;
                }

                Iterator<GroundItem> it = nextEntry.getValue().iterator();
                while (it.hasNext()) {
                    GroundItem pendingItem = it.next();
                    pendingItem.setState(EntityState.ACTIVE);
                    pendingItem.show();
                    activeList.add(pendingItem);
                    it.remove();

                    if (--spaces <= 0) {
                        /*
                         * Tile is full again; keep the rest queued.
                         */
                        break;
                    }
                }
            }
        }
    }

    /**
     * Active ground items, keyed by exact tile {@link Position}.
     */
    private final ListMultimap<Position, GroundItem> active = ArrayListMultimap.create(128, 128);

    /**
     * Overflow queue for tiles that have reached {@link #MAX_ITEMS_PER_TILE}.
     * <p>
     * Items in this multimap are <b>not visible</b> until promoted by {@link ExpirationTask#processPendingItems()}.
     */
    private final ListMultimap<Position, GroundItem> pending = ArrayListMultimap.create(128, 128);

    /**
     * If the expiration task has been scheduled.
     */
    private boolean expiring;

    /**
     * Creates a new {@link GroundItemList}.
     *
     * @param world The owning world instance.
     */
    public GroundItemList(World world) {
        super(world, EntityType.ITEM);
    }

    @Override
    public Spliterator<GroundItem> spliterator() {
        return Spliterators.spliterator(active.values(), Spliterator.NONNULL | Spliterator.SIZED | Spliterator.ORDERED);
    }

    @Override
    public Iterator<GroundItem> iterator() {
        return Iterators.unmodifiableIterator(active.values().iterator());
    }

    @Override
    protected boolean onRegister(GroundItem item) {
        if (item.def().isStackable() && Luna.settings().game().mergeStackableGroundItems()) {
            return addOrMerge(item);
        }
        return add(item);
    }

    @Override
    protected boolean onUnregister(GroundItem item) {
        if (item.def().isStackable() && Luna.settings().game().mergeStackableGroundItems()) {
            return removeOrUnmerge(item);
        }
        return remove(item);
    }

    @Override
    public int size() {
        return active.size();
    }

    /**
     * Starts the ground-item expiration task.
     * <p>
     * This should typically be called once during world initialization.
     *
     * @throws IllegalStateException If the expiration task has already been started.
     */
    public void startExpirationTask() {
        checkState(!expiring, "The expiration task has already been started.");
        expiring = true;
        world.schedule(new ExpirationTask());
    }

    /**
     * Adds a stackable ground item, merging it into an existing stack if one exists.
     * <p>
     * Matching is by:
     * <ul>
     *     <li>same {@link Position}</li>
     *     <li>same item id</li>
     *     <li>same {@link ChunkUpdatableView}</li>
     * </ul>
     *
     * @param item The item to add.
     * @return {@code true} if the add/merge succeeded.
     */
    private boolean addOrMerge(GroundItem item) {
        Position position = item.getPosition();
        GroundItem existing = findExisting(item);

        if (existing != null) {
            int newAmount = item.getAmount() + existing.getAmount();
            if (newAmount < 0) {
                /*
                 * Overflow: refuse to merge.
                 */
                return false;
            }
            if (removeFromSet(existing)) {
                GroundItem replace = new GroundItem(item.getContext(), item.getId(), newAmount, position, item.getView());
                replace.setExpireTicks(existing.getExpireTicks());
                addToSet(replace);
                return true;
            }
        } else {
            addToSet(item);
            return true;
        }
        return false;
    }

    /**
     * Removes a stackable ground item, decrementing an existing stack if present.
     * <p>
     * If the resulting amount is {@code <= 0}, the stack is fully removed. If no matching stack exists, this method
     * is treated as a successful no-op (mirrors typical “remove-if-present” semantics).
     *
     * @param item The item to remove (the amount represents how much to remove).
     * @return {@code true} if the removal/decrement succeeded (or the stack was not present).
     */
    private boolean removeOrUnmerge(GroundItem item) {
        int removeAmount = item.getAmount();
        Position position = item.getPosition();
        GroundItem existing = findExisting(item);

        if (existing != null) {
            int newAmount = existing.getAmount() - removeAmount;
            if (newAmount <= 0) {
                return removeFromSet(existing);
            }

            if (removeFromSet(existing)) {
                GroundItem replace = new GroundItem(item.getContext(), item.getId(), newAmount, position, item.getView());
                replace.setExpireTicks(existing.getExpireTicks());
                addToSet(replace);
                return true;
            }
        }

        /*
         * Treat “not found” as success.
         */
        return true;
    }

    /**
     * Adds a ground item without stack merging.
     * <p>
     * For non-stackable definitions, {@link GroundItem#getAmount()} represents how many separate ground-item entities
     * should be created (1 per unit). For stackable definitions, only a single entity is created.
     *
     * @param item The item to add.
     * @return {@code true} if at least one entity was added.
     */
    private boolean add(GroundItem item) {
        int addAmount = item.def().isStackable() ? 1 : item.getAmount();
        if (addAmount == 1) {
            addToSet(item);
            return true;
        }

        boolean failed = true;
        for (int i = 0; i < addAmount; i++) {
            addToSet(new GroundItem(item.getContext(), item.getId(), 1, item.getPosition(), item.getView()));
            failed = false;
        }
        return !failed;
    }

    /**
     * Removes a ground item without stack merging.
     * <p>
     * For stackable definitions, this removes at most one matching entity (same tile + same id + same view). For
     * non-stackable definitions, this removes up to {@link GroundItem#getAmount()} entities (1 per unit).
     *
     * @param item The item to remove.
     * @return {@code true} if at least one entity was removed.
     */
    private boolean remove(GroundItem item) {
        int loops = item.def().isStackable() ? 1 : item.getAmount();
        Position position = item.getPosition();
        Iterator<GroundItem> it = active.get(position).iterator();

        boolean changed = false;
        while (it.hasNext()) {
            GroundItem existing = it.next();
            if (existing.getId() == item.getId()
                    && existing.getView().equals(item.getView())) {
                existing.hide();
                existing.setState(EntityState.INACTIVE);
                it.remove();
                changed = true;
            }

            if (--loops <= 0) {
                break;
            }
        }
        return changed;
    }

    /**
     * Adds {@code item} to the backing multimap and shows it, if the tile has capacity.
     * <p>
     * If the tile is full, the item is queued into {@link #pending} instead, and will become visible later when
     * {@link ExpirationTask#processPendingItems()} promotes it.
     *
     * @param item The item to add.
     */
    private void addToSet(GroundItem item) {
        List<GroundItem> tileItems = active.get(item.getPosition());
        if (tileItems.size() < MAX_ITEMS_PER_TILE) {
            tileItems.add(item);
            item.setState(EntityState.ACTIVE);
            item.show();
        } else {
            /*
             * Tile overflow: keep it queued and invisible for now.
             */
            pending.put(item.getPosition(), item);
        }
    }

    /**
     * Removes {@code item} from either the active set or the pending queue, and hides it.
     * <p>
     * If the item is not present in either multimap, this returns {@code false}.
     *
     * @param item The item to remove.
     * @return {@code true} if the item was found and removed.
     */
    private boolean removeFromSet(GroundItem item) {
        if (active.remove(item.getPosition(), item) ||
                pending.remove(item.getPosition(), item)) {
            if(item.getState() == EntityState.ACTIVE) {
                item.hide();
                item.setState(EntityState.INACTIVE);
            }
            return true;
        }
        return false;
    }

    /**
     * Finds the first active ground item on the same tile that matches {@code item}'s id and view.
     * <p>
     * Used for:
     * <ul>
     *     <li>stack merging</li>
     *     <li>stack decrement/removal</li>
     * </ul>
     *
     * @param item The item to find a match for.
     * @return The matching ground item, or {@code null} if not found.
     */
    private GroundItem findExisting(GroundItem item) {
        Position position = item.getPosition();
        List<GroundItem> existingList = active.get(position);
        for (GroundItem existing : existingList) {
            if (existing.getId() == item.getId()
                    && existing.getView().equals(item.getView())) {
                return existing;
            }
        }
        return null;
    }
}
