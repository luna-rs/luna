package io.luna.game.event;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Objects;

/**
 * A model representing arguments of events that will be matched.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventArguments implements Iterable<Object> {

    /**
     * The arguments.
     */
    private final ImmutableList<Object> args;

    /**
     * Creates a new {@link EventArguments}.
     *
     * @param withArgs The arguments.
     */
    public EventArguments(Object[] withArgs) {
        args = ImmutableList.copyOf(withArgs);
    }

    @Override
    public UnmodifiableIterator<Object> iterator() {
        return args.iterator();
    }

    /**
     * Determines if the arguments contains {@code value}.
     */
    public boolean contains(Object value) {
        return args.contains(value);
    }

    /**
     * Determines if the argument at {@code index} equals {@code value}.
     */
    public boolean equals(int index, Object value) {
        return Objects.equals(get(index), value);
    }

    /**
     * Retrieves the argument at {@code index}.
     */
    public Object get(int index) {
        return args.get(index);
    }

    /**
     * Returns the amount of arguments.
     */
    public int size() {
        return args.size();
    }
}
