package io.luna.util;

import com.google.common.collect.Iterators;

import java.util.Iterator;

/**
 * A model representing an iterable array wrapper.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class IterableArray<T> implements Iterable<T> {
// TODO Ugly stuff. Think of alternative and remove
    /**
     * The array.
     */
    private final T[] array;

    /**
     * Creates a new {@link IterableArray}.
     *
     * @param array The array.
     */
    public IterableArray(T[] array) {
        this.array = array;
    }

    /**
     * Creates a new {@link IterableArray}.
     *
     * @param size The array size.
     */
    public IterableArray(int size) {
        this((T[]) new Object[size]);
    }

    /**
     * Sets the value of an index.
     */
    public void set(int index, T value) {
        array[index] = value;
    }

    /**
     * Gets the value of an index.
     */
    public T get(int index) {
        return array[index];
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.forArray(array);
    }

    /**
     * @return The backing array.
     */
    public T[] getArray() {
        return array;
    }
}
