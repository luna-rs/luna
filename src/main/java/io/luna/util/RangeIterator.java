package io.luna.util;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.Iterator;

/**
 * @author lare96 <http://github.com/lare96>
 */
public final class RangeIterator<C extends Comparable> implements Iterator<C> {

    private final Iterator<C> delegate;

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