package io.luna.game.model.mobile.attr;

import com.google.common.collect.Iterators;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A wrapper for a {@link Map} that contains a function to retrieve an {@link AttributeValue} by its {@code String} key. The
 * retrieval of attributes is very high performing because it utilizes string interning and its own method of caching.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap implements Iterable<Entry<String, AttributeValue<?>>> {

    /**
     * An {@link IdentityHashMap} that holds our {@link AttributeKey} and {@link AttributeValue} pair.
     */
    private final Map<String, AttributeValue<?>> attributes = new IdentityHashMap<>(AttributeKey.ALIASES.size());

    /**
     * The last retrieved key.
     */
    private String lastKey;

    /**
     * The last retrieved value.
     */
    private AttributeValue lastValue;

    /**
     * Retrieves an {@link AttributeValue} by its {@code key}. Unfortunately this function is not type safe, so it may throw
     * a {@link ClassCastException} if used with the wrong underlying type.
     *
     * @param key The key to retrieve the {@code AttributeValue} with.
     * @return The retrieved {@code AttributeValue}.
     */
    @SuppressWarnings("unchecked")
    public <T> AttributeValue<T> get(String key) {
        //noinspection StringEquality
        if (lastKey == requireNonNull(key)) {
            return lastValue;
        }

        AttributeKey<?> alias = Optional.ofNullable(AttributeKey.ALIASES.get(key)).
            orElse(AttributeKey.ALIASES.get(key.intern()));

        checkState(alias != null, "attributes need to be aliased in the AttributeKey class");

        lastKey = alias.getName();
        lastValue = attributes.computeIfAbsent(alias.getName(), it -> new AttributeValue<>(alias.getInitialValue()));

        return lastValue;
    }

    @Override
    public Iterator<Entry<String, AttributeValue<?>>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }
}
