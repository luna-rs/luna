package io.luna.util;

import com.google.common.base.Suppliers;

import java.util.function.Supplier;

/**
 * A model representing a lazily initialized value. <strong>This model should only be used in single-threaded
 * environments.</strong> For a thread safe version, see {@link Suppliers#memoize(com.google.common.base.Supplier)}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LazyVal<T> implements Supplier<T> {

    /**
     * The supplier that will initialize the value.
     */
    private final Supplier<T> delegate;

    /**
     * The value.
     */
    private T value;

    /**
     * Creates a new {@link LazyVal}.
     *
     * @param delegate The supplier that will initialize the value.
     */
    public LazyVal(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        if (value == null) {
            // Initialize and cache value.
            value = delegate.get();
        }
        return value;
    }
}