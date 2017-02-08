package io.luna.game.model.mobile;

import io.luna.game.model.EntityState;

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
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A model representing a list of mobs.
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
            skip(); /* If 'hasNext()' is not called, skip here. */

            if (curr >= capacity()) {
                throw new NoSuchElementException("No elements left");
            }

            E mob = element(curr);
            prev = curr++;
            return mob;
        }

        @Override
        public void remove() {
            if (prev == -1) {
                throw new IllegalStateException("remove() can only be called once after each call to next()");
            }
            MobList.this.remove(get(prev)); /* 'Moblist.this' needed to get around overloading conflicts. */
            prev = -1;
        }

        /**
         * Iterates until a non-{@code null} element is found. Returns {@code true} if one was found.
         */
        private boolean skip() {
            while (get(curr) == null) {
                if (++curr >= capacity()) { /* Stop at end of list. */
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The elements.
     */
    private final E[] mobs;

    /**
     * The index cache.
     */
    private final Queue<Integer> indexes = new ArrayDeque<>();

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
    public MobList(int capacity) {
        mobs = (E[]) new Mob[(capacity + 1)];

         /* Initialize the index cache. */
        for (int index = 1; index < mobs.length; index++) {
            indexes.add(index);
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new MobListIterator();
    }

    /**
     * Finds the first element that matches {@code filter}.
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
     * Finds the last element that matches {@code filter}.
     */
    public Optional<E> findLast(Predicate<? super E> filter) {

        /* Can't use the iterator because we need to look backwards. */
        for (int index = capacity(); index > 1; index--) {
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
     */
    public Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * Returns a {@code null}-free parallel stream.
     */
    public Stream<E> parallelStream() {
        return StreamSupport.stream(spliterator(), true).filter(Objects::nonNull);
    }

    /**
     * Attempts to add {@code mob}.
     */
    public void add(E mob) {
        checkArgument(mob.getState() != EntityState.ACTIVE, "state == ACTIVE");
        checkState(!isFull(), "isFull() == true");

        int index = indexes.remove(); /* No lookup, just retrieve from the index cache. */
        mobs[index] = mob;
        mob.setIndex(index);

        mob.setState(EntityState.ACTIVE);

        size++;
    }

    /**
     * Attempts to remove {@code mob}.
     */
    public void remove(E mob) {
        checkArgument(mob.getState() == EntityState.ACTIVE, "state != ACTIVE");
        checkArgument(mob.getIndex() != -1, "index == -1");

        indexes.add(mob.getIndex()); /* We're done with the index, add it back to the cache. */

        mob.setState(EntityState.INACTIVE);

        mobs[mob.getIndex()] = null;
        mob.setIndex(-1);

        size--;
    }

    /**
     * Retrieves the mob on {@code index}.
     */
    public E get(int index) {
        return mobs[index];
    }

    /**
     * Retrieves the mob on {@code index}. Identical to {@code get(int)} but throws an exception if no mob is
     * contained on the index.
     */
    public E element(int index) {
        return requireNonNull(get(index));
    }

    /**
     * Determines if {@code mob} is contained within this list.
     */
    public boolean contains(E mob) {
        return get(mob.getIndex()) != null;
    }

    /**
     * Returns {@code true} if this list is full.
     */
    public boolean isFull() {
        return size() == capacity();
    }

    /**
     * Returns {@code true} if this list is empty.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the amount of taken indexes.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the amount of free indexes.
     */
    public int remaining() {
        return capacity() - size();
    }

    /**
     * Returns the total amount indexes (both free and taken).
     */
    public int capacity() {
        return mobs.length;
    }

    /**
     * Creates a <strong>shallow copy</strong> of the backing array.
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
