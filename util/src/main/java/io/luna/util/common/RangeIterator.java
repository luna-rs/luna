package io.luna.util.common;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.Iterator;

/**
 * An {@link Iterator} implementation that iterates over a {@link Range}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RangeIterator<C extends Comparable<? super C>> implements Iterator<C> {

    /**
     * The delegate iterator.
     */
    private final Iterator<C> delegate;

    /**
     * Creates a new {@link RangeIterator}.
     *
     * @param range The range.
     * @param discreteDomain The discrete domain.
     */
    public RangeIterator(Range<C> range, DiscreteDomain<C> discreteDomain) {
        delegate = ContiguousSet.create(range, discreteDomain).iterator();
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public C next() {
        return delegate.next();
    }
}