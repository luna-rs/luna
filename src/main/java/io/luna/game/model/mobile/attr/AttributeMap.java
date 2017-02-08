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
 * A model that uses a map internally along with its own caching mechanisms to manage attributes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap implements Iterable<Entry<String, AttributeValue<?>>> {

    /**
     * A map that holds attribute key and value pairs.
     */
    private final Map<String, AttributeValue<?>> attributes = new IdentityHashMap<>(AttributeKey.ALIASES.size());

    /**
     * The last key.
     */
    private String lastKey;

    /**
     * The last value.
     */
    private AttributeValue lastValue;

    /**
     * Retrieves the value of an attribute by its String key. Not type safe.
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

        try {
            lastKey = alias.getName();
            lastValue = attributes
                .computeIfAbsent(alias.getName(), it -> new AttributeValue<>(alias.getInitialValue()));

            return lastValue;
        } catch (ClassCastException e) {
            throw new AttributeTypeException(alias);
        }
    }

    @Override
    public Iterator<Entry<String, AttributeValue<?>>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }
}
