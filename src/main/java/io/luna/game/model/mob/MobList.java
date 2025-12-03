package io.luna.game.model.mob;

import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A repository that stores and manages all active {@link Mob} instances of a given type within the game world. Each
 * mob is assigned a stable index in the range {@code [1, capacity]}, which is used by the update protocol.
 * <p>
 * Indexes are internally pooled, meaning insertions are O(1) and do not require scanning for free slots. Null slots
 * are automatically skipped during iteration.
 * </p>
 *
 * <p>
 * <strong>Important:</strong> This list should never be used directly to remove players. Use {@link Player#logout()}
 * or {@link Player#forceLogout()} instead, both of which safely handle disconnection and persistence.
 * </p>
 *
 * @param <E> The specific mob subtype (e.g., {@link Player}, {@link Npc}).
 * @author lare96
 */
public final class MobList<E extends Mob> implements Iterable<E> {

    /**
     * Iterator implementation that transparently skips {@code null} slots so consumers never encounter empty indices.
     */
    private final class MobListIterator implements Iterator<E> {

        /**
         * Current scanning index.
         */
        private int curr;

        /**
         * Index of last returned element (needed for safe remove).
         */
        private int prev = -1;

        @Override
        public boolean hasNext() {
            return curr < capacity() && skip();
        }

        @Override
        public E next() {
            // Skip ahead in case the user did not call hasNext().
            skip();

            if (curr >= capacity()) {
                throw new NoSuchElementException("No more elements in MobList.");
            }

            E mob = get(curr);
            prev = curr++;
            return mob;
        }

        @Override
        public void remove() {
            checkState(prev != -1, "remove() may only be invoked once after next()");
            MobList.this.remove(get(prev));
            prev = -1;
        }

        /**
         * Advances {@link #curr} until a non-null mob is found.
         *
         * @return {@code true} if a valid mob was found; {@code false} if end reached.
         */
        private boolean skip() {
            while (get(curr) == null) {
                if (++curr >= capacity()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The world that owns this list.
     */
    private final World world;

    /**
     * Backing array storing mobs by index (1-indexed; slot 0 unused).
     */
    private final E[] mobs;

    /**
     * Pool of free indexes. Implemented as a FIFO to reduce index fragmentation.
     */
    private final Queue<Integer> indexPool;

    /**
     * Number of active mobs.
     */
    private int size;

    /**
     * Creates a new {@link MobList} with the specified capacity.
     *
     * @param world The owning world.
     * @param capacity Maximum mob count. Actual array size will be {@code capacity + 1}
     * because index {@code 0} is reserved/unusable.
     */
    public MobList(World world, int capacity) {
        this.world = world;
        this.mobs = (E[]) new Mob[capacity + 1];

        // Build initial free-index pool.
        indexPool = new ArrayDeque<>(capacity);
        for (int index = 1; index < capacity + 1; index++) {
            indexPool.add(index);
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new MobListIterator();
    }

    /**
     * Finds the first mob satisfying {@code filter}.
     *
     * @param filter Predicate used to test each mob.
     * @return An {@link Optional} containing the first match, or empty if none match.
     */
    public Optional<E> findFirst(Predicate<? super E> filter) {
        for (E e : this) {
            if (filter.test(e)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the last mob satisfying {@code filter}.
     *
     * <p>Unlike {@link #findFirst}, this method performs a manual reverse scan.</p>
     *
     * @param filter Predicate used to test mobs.
     * @return The last matching mob, or empty if none match.
     */
    public Optional<E> findLast(Predicate<? super E> filter) {
        for (int i = capacity() - 1; i > 0; i--) {
            E mob = mobs[i];
            if (mob != null && filter.test(mob)) {
                return Optional.of(mob);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns all mobs matching {@code filter}.
     *
     * @param filter Predicate used for filtering.
     * @return A new list of matching mobs.
     */
    public List<E> findAll(Predicate<? super E> filter) {
        List<E> list = new ArrayList<>();
        for (E e : this) {
            if (filter.test(e)) {
                list.add(e);
            }
        }
        return list;
    }

    @Override
    public Spliterator<E> spliterator() {
        // Underlying array may contain nulls; caller must filter when streaming.
        return Spliterators.spliterator(mobs, Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    /**
     * Returns a stream of all non-null mobs.
     *
     * @return A null-free stream.
     */
    public Stream<E> stream() {
        return Arrays.stream(mobs).filter(Objects::nonNull);
    }

    /**
     * Inserts a mob into the list and assigns it a unique index.
     *
     * @param mob The mob to register.
     * @throws IllegalStateException If the list is full.
     * @throws IllegalArgumentException If the mob is already ACTIVE.
     */
    public void add(E mob) {
        checkArgument(mob.getState() != EntityState.ACTIVE, "Mob is already ACTIVE.");
        checkState(!isFull(), "MobList is full.");

        int index = indexPool.remove();
        mobs[index] = mob;
        mob.setIndex(index);
        size++;

        // Update state & world ownership.
        if (mob.getType() == EntityType.NPC) {
            mob.setState(EntityState.ACTIVE);
        } else if (mob.getType() == EntityType.PLAYER) {
            world.addPlayer(mob.asPlr());
        }
    }

    /**
     * Removes a mob from the list and frees its index.
     *
     * <p><strong>Warning:</strong> Never use this for players. Use {@link Player#logout()} or
     * {@link Player#forceLogout()}.</p>
     *
     * @param mob The mob to remove.
     */
    public void remove(E mob) {
        checkArgument(mob.getIndex() != -1,
                "Mob has no assigned index.");

        mob.setState(EntityState.INACTIVE);

        if (mob.getType() == EntityType.PLAYER) {
            world.removePlayer(mob.asPlr());
        }

        indexPool.add(mob.getIndex());
        mobs[mob.getIndex()] = null;
        mob.setIndex(-1);
        size--;
    }

    /**
     * Retrieves a mob by index (returns null if out of bounds or empty).
     */
    public E get(int index) {
        if (index < 0 || index >= capacity()) {
            return null;
        }
        return mobs[index];
    }

    /**
     * Same as {@link #get(int)} but returns an {@link Optional}.
     */
    public Optional<E> retrieve(int index) {
        return Optional.ofNullable(get(index));
    }

    /**
     * @return {@code true} if this mob exists at its recorded index.
     */
    public boolean contains(E mob) {
        return get(mob.getIndex()) != null;
    }

    /**
     * @return {@code true} if no more mobs can be inserted.
     */
    public boolean isFull() {
        return size == capacity();
    }

    /**
     * @return {@code true} if the list contains zero mobs.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return The count of active mobs.
     */
    public int size() {
        return size;
    }

    /**
     * @return Number of unused/free mob indexes.
     */
    public int remaining() {
        return capacity() - size();
    }

    /**
     * @return Total number of slots (including free and used). <strong>Note:</strong> This is {@code mobs.length},
     * which includes index {@code 0}.
     */
    public int capacity() {
        return mobs.length;
    }

    /**
     * Returns a shallow copy of the backing array. Mutations to the returned array do not affect the list.
     */
    public E[] copy() {
        return Arrays.copyOf(mobs, mobs.length);
    }

    /**
     * Removes all mobs in the list. Equivalent to repeatedly calling {@link #remove(Mob)}.
     */
    public void clear() {
        forEach(this::remove);
    }
}
