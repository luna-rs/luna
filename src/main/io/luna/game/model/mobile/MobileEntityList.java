package io.luna.game.model.mobile;

import com.google.common.primitives.Ints;
import io.luna.game.model.EntityState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Iterable} implementation acting as a repository that holds instances of {@link MobileEntity}s. Indexes are
 * cached to avoid expensive lookups whenever a new mob is added.
 *
 * @param <E> The specific type of {@code MobileEntity} being managed within this list.
 * @author lare96 <http://github.org/lare96>
 */
public final class MobileEntityList<E extends MobileEntity> implements Iterable<E> {

    /**
     * An {@link Iterator} implementation designed specifically {@link MobileEntityList}s.
     *
     * @param <E> The specific type of {@link MobileEntity} being managed within this {@code Iterator}.
     * @author lare96 <http://github.org/lare96>
     */
    public static final class MobileEntityListIterator<E extends MobileEntity> implements Iterator<E> {

        /**
         * The {@link MobileEntityList} this {@link Iterator} is dedicated to.
         */
        private final MobileEntityList<E> list;

        /**
         * The current index.
         */
        private int curr;

        /**
         * The previous index.
         */
        private int prev = -1;

        /**
         * Creates a new {@link MobileEntityListIterator}.
         *
         * @param list The {@link MobileEntityList} this {@link Iterator} is dedicated to.
         */
        public MobileEntityListIterator(MobileEntityList<E> list) {
            this.list = list;
        }

        @Override
        public boolean hasNext() {
            if (curr < list.capacity()) {
                return skipNullIndexes();
            }
            return false;
        }

        @Override
        public E next() {
            skipNullIndexes();

            checkPositionIndex(curr, list.capacity(), "No elements left");

            E mob = list.element(curr);
            prev = curr++;
            return mob;
        }

        @Override
        public void remove() {
            checkState(prev != -1, "remove() can only be called once after each call to next()");

            list.remove(list.get(prev));
            prev = -1;
        }

        /**
         * Forwards the {@code curr} marker until a {@code non-null} element is found.
         *
         * @return {@code true} if a non-null element is found, {@code false} otherwise.
         */
        private boolean skipNullIndexes() {
            while (list.get(curr) == null) {
                if (++curr >= list.capacity()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The mobs contained within this list.
     */
    private final E[] mobs;

    /**
     * A queue that acts as a cache for indexes.
     */
    private final Queue<Integer> indexes;

    /**
     * The internal size of this list.
     */
    private int size;

    /**
     * Creates a new {@link MobileEntityList}.
     *
     * @param capacity The length of the backing array plus {@code 1}.
     */
    @SuppressWarnings("unchecked")
    public MobileEntityList(int capacity) {
        mobs = (E[]) new MobileEntity[++capacity];
        indexes = new ArrayDeque<Integer>(Ints.asList(IntStream.rangeClosed(1, mobs.length).toArray()));
    }

    @Override
    public Iterator<E> iterator() {
        return new MobileEntityListIterator<E>(this);
    }

    /**
     * Finds the first element that matches {@code filter}.
     *
     * @param filter The filter to apply to the elements of this sequence.
     * @return An {@link Optional} containing the element, or an empty {@code Optional} if no element was found.
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
     *
     * @param filter The filter to apply to the elements of this sequence.
     * @return An {@link Optional} containing the element, or an empty {@code Optional} if no element was found.
     */
    public Optional<E> findLast(Predicate<? super E> filter) {
        for (int index = capacity(); index > 0; index--) {
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
     * @param filter The filter to apply to the elements of this sequence.
     * @return An {@link ArrayList} containing the elements.
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

    /**
     * {@inheritDoc}
     * <p>
     * As a rule of thumb, {@code stream()} and {@code parallelStream()} should always be used directly instead unless
     * absolutely needed.
     */
    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(mobs, Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    /**
     * Adds {@code mob} to this list. Will throw an exception if this list is full, or if the mob being added has a state of
     * {@code ACTIVE}.
     *
     * @param mob The mob to add to this list.
     */
    public void add(E mob) {
        checkArgument(mob.getState() != EntityState.ACTIVE, "state == ACTIVE");
        checkState(!isFull(), "isFull() == true");

        int index = indexes.remove();
        mobs[index] = mob;
        mob.setIndex(index);
        mob.setState(EntityState.ACTIVE);
        size++;
    }

    /**
     * Removes {@code mob} from this list. Will throw an exception if the mob being removed does not have a state of {@code
     * ACTIVE}.
     *
     * @param mob The mob to remove from this list.
     */
    public void remove(E mob) {
        checkArgument(mob.getState() == EntityState.ACTIVE, "state != ACTIVE");

        indexes.add(mob.getIndex());
        mobs[mob.getIndex()] = null;
        mob.setIndex(-1);
        mob.setState(EntityState.INACTIVE);
        size--;
    }

    /**
     * Removes a {@link MobileEntity} from this list at {@code index}. Will throw an exception if the mob being removed does
     * not have a state of {@code ACTIVE}.
     *
     * @param index The index to remove the {@link MobileEntity} at.
     */
    public void remove(int index) {
        remove(mobs[index]);
    }

    /**
     * Retrieves the element on {@code index}.
     *
     * @param index The index.
     * @return The retrieved element, possibly {@code null}.
     */
    public E get(int index) {
        return mobs[index];
    }

    /**
     * Retrieves the element on {@code index}, the only difference between this and {@code get(int)} is that this method
     * throws an exception if no mob is found on {@code index}.
     *
     * @param index The index.
     * @return The retrieved element, will never be {@code null}.
     */
    public E element(int index) {
        E mob = get(index);
        checkArgument(mob != null, "index -> null MobileEntity");
        return mob;
    }

    /**
     * Determines if this list contains {@code mob}.
     *
     * @param mob The mob to check for.
     * @return {@code true} if {@code mob} is contained, {@code false} otherwise.
     */
    public boolean contains(E mob) {
        return get(mob.getIndex()) != null;
    }

    /**
     * @return {@code true} if this list is full, {@code false} otherwise.
     */
    public boolean isFull() {
        return size() == capacity();
    }

    /**
     * @return {@code true} if this list is empty, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * @return The amount of mobs in this list.
     */
    public int size() {
        return size;
    }

    /**
     * @return The amount of free spaces remaining in this list.
     */
    public int remaining() {
        return capacity() - size();
    }

    /**
     * @return The length of the backing array.
     */
    public int capacity() {
        return mobs.length;
    }

    /**
     * <strong>Warning: This function does not give direct access to the backing array but instead creates a shallow
     * copy.</strong>
     *
     * @return The shallow copy of the backing array.
     */
    public E[] toArray() {
        return Arrays.copyOf(mobs, mobs.length);
    }

    /**
     * Calls {@code remove()} on every single {@link MobileEntity} in this list.
     */
    public void clear() {
        forEach(this::remove);
    }

    /**
     * @return The {@link Stream} that will traverse over this list. Automatically excludes {@code null} values.
     */
    public Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * @return The {@link Stream} that will traverse over this list in parallel. Automatically excludes {@code null} values.
     */
    public Stream<E> parallelStream() {
        Spliterator<E> split = Spliterators.spliterator(mobs, spliterator().characteristics() | Spliterator.IMMUTABLE);
        return StreamSupport.stream(split, true).filter(Objects::nonNull);
    }
}
