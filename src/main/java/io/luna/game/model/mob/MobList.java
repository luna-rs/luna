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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a repository that is used to keep track of all mobs in the world. Assigned indexes
 * are cached in order to improve performance.
 *
 * @param <E> The type of mobs to contain.
 * @author lare96 <http://github.org/lare96>
 */
public final class MobList<E extends Mob> implements Iterable<E> {

    /**
     * A {@code null}-skipping iterator.
     */
    private final class MobListIterator implements Iterator<E> {

        /**
         * The current index.
         */
        private int curr;

        /**
         * The previous index.
         */
        private int prev = -1;

        @Override
        public boolean hasNext() {
            return curr < capacity() && skip();
        }

        @Override
        public E next() {
            // If 'hasNext()' is not called, skip nulls here.
            skip();

            if (curr >= capacity()) {
                throw new NoSuchElementException("No elements left");
            }

            E mob = get(curr);
            prev = curr++;
            return mob;
        }

        @Override
        public void remove() {
            checkState(prev != -1, "remove() can only be called once after each call to next()");
            MobList.this.remove(get(prev));
            prev = -1;
        }

        /**
         * Iterates until a non-{@code null} element is found.
         *
         * @return {@code true} if a non-{@code null} element was found.
         */
        private boolean skip() {
            // Stop at the end of the repository, or until 'get(curr)' is non-null.
            while (get(curr) == null) {
                if (++curr >= capacity()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The world.
     */
    private final World world;

    /**
     * The elements.
     */
    private final E[] mobs;

    /**
     * The index cache.
     */
    private final Queue<Integer> indexes;

    /**
     * The size.
     */
    private int size;

    /**
     * Creates a new {@link MobList}.
     *
     * @param capacity The amount of mobs that can be contained.
     */
    @SuppressWarnings("unchecked")
    public MobList(World world, int capacity) {
        this.world = world;
        this.mobs = (E[]) new Mob[capacity + 1];

        // Initialize the index cache.
        this.indexes = IntStream.rangeClosed(1, capacity).boxed()
            .collect(Collectors.toCollection(() -> new ArrayDeque<>(capacity)));
    }

    @Override
    public Iterator<E> iterator() {
        return new MobListIterator();
    }

    /**
     * Finds the first element that matches {@code filter}.
     *
     * @param filter The predicate to test.
     * @return The first element matching {@code filter}.
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
     * Finds the last element matching {@code filter}.
     *
     * @param filter The predicate to test.
     * @return The last element matching {@code filter}.
     */
    public Optional<E> findLast(Predicate<? super E> filter) {
        // Iterator doesn't support reverse iteration.
        for (int index = capacity() - 1; index > 1; index--) {
            E mob = mobs[index];
            if (mob == null) {
                continue;
            }
            if (filter.test(mob)) {
                return Optional.of(mob);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all elements that match {@code filter}.
     *
     * @param filter The predicate to test.
     * @return A list of matching elements.
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
        return Spliterators.spliterator(mobs, Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    /**
     * Returns a {@code null}-free stream.
     *
     * @return The stream.
     */
    public Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * Attempts to add {@code mob}.
     *
     * @param mob The mob to add.
     */
    public void add(E mob) {
        checkArgument(mob.getState() != EntityState.ACTIVE, "state == ACTIVE");
        checkState(!isFull(), "isFull() == true");

        // No lookup, just grab next free index.
        int index = indexes.remove();
        mobs[index] = mob;
        mob.setIndex(index);
        size++;

        if (mob.getType() == EntityType.NPC) {
            mob.setState(EntityState.ACTIVE);
        } else if (mob.getType() == EntityType.PLAYER) {
            world.addPlayer(mob.asPlr());
        }
    }

    /**
     * Attempts to remove {@code mob}. <strong>Do not use this to remove players. Use {@link Player#logout()} to send the
     * logout packet or use {@link Player#disconnect()} to destroy the player's connection.</strong>
     *
     * @param mob The mob to remove.
     */
    public void remove(E mob) {
        checkArgument(mob.getIndex() != -1, "index == -1");

        if (mob.getType() == EntityType.NPC) {
            mob.setState(EntityState.INACTIVE);
        } else if (mob.getType() == EntityType.PLAYER) {
            checkState(mob.asPlr().getState() == EntityState.INACTIVE,
                "Player must be inactive. Do not use MobList#remove(Mob) to logout players.");
            world.removePlayer(mob.asPlr());
        }

        // Put back index, so other mobs can use it.
        indexes.add(mob.getIndex());

        mobs[mob.getIndex()] = null;
        mob.setIndex(-1);
        size--;
    }

    /**
     * Retrieves the mob on {@code index}.
     *
     * @param index The index to retrieve.
     * @return The mob.
     */
    public E get(int index) {
        return mobs[index];
    }

    /**
     * Retrieves the mob on {@code index}. Identical to {@code get(int)} but may return {@code Optional.empty}.
     *
     * @param index The index to retrieve.
     * @return The mob.
     */
    public Optional<E> retrieve(int index) {
        return Optional.ofNullable(get(index));
    }

    /**
     * Determines if {@code mob} is contained within this list.
     *
     * @param mob The mob to check for.
     * @return {@code true} if the mob is contained.
     */
    public boolean contains(E mob) {
        return retrieve(mob.getIndex()).filter(mob::equals).isPresent();
    }

    /**
     * Determines if the current size is equal to the capacity.
     *
     * @return {@code true} if this list is full.
     */
    public boolean isFull() {
        return size == capacity();
    }

    /**
     * Determines if the current size is 0.
     *
     * @return {@code true} if this list is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return The amount of taken indexes.
     */
    public int size() {
        return size;
    }

    /**
     * Computes and returns the amount of free indexes.
     *
     * @return The remaining indexes.
     */
    public int remaining() {
        return capacity() - size();
    }

    /**
     * Returns the total amount indexes (both free and taken).
     *
     * @return The amount of free and taken indexes.
     */
    public int capacity() {
        return mobs.length;
    }

    /**
     * Creates a <strong>shallow copy</strong> of the backing array.
     *
     * @return A shallow copy of the backing array.
     */
    public E[] toArray() {
        return Arrays.copyOf(mobs, mobs.length);
    }

    /**
     * Removes all mobs from this list.
     */
    public void clear() {
        forEach(this::remove);
    }
}
