package io.luna.game.model.mob.attr;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * A model that contains key-value mappings for {@link Attribute} types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap implements Iterable<Entry<Attribute<?>, Object>> {

    /**
     * A set of all persistent keys. Used to ensure there are no duplicates.
     */
    public static final Map<String, Attribute<?>> persistentKeyMap = new ConcurrentHashMap<>();

    /**
     * A map that holds attribute key and value pairs.
     */
    private final Map<Attribute<?>, Object> attributes = new IdentityHashMap<>(64);

    /**
     * The last accessed key.
     */
    private Attribute<?> lastKey;

    /**
     * The last accessed value.
     */
    private Object lastValue;

    @Override
    public UnmodifiableIterator<Entry<Attribute<?>, Object>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }

    /**
     * Adds a loaded attribute to the backing map.
     *
     * @param key The attribute's name.
     * @param value The attribute's value.
     */
    public void load(String key, Object value) {
        attributes.put(persistentKeyMap.get(key), value);
    }

    /**
     * Sets {@code attr} to {@code value}.
     *
     * @param attr The attribute to set.
     * @param value The value to set it to.
     * @param <T> The attribute type.
     */
    public <T> void set(Attribute<T> attr, T value) {
        requireNonNull(value, "Value cannot be null.");
        attributes.put(attr, value);
        lastKey = attr;
        lastValue = value;
    }

    /**
     * Retrieves the value of {@code attr}. If it doesn't exist it will be generated with saved player data, or a default
     * value.
     *
     * @param attr The attribute to retrieve.
     * @param <T> The attribute type.
     * @return The value of the attribute.
     */
    public <T> T get(Attribute<T> attr) {
        // Attribute is equal to cached key, return last value.
        if (attr == lastKey) {
            return (T) lastValue;
        }

        // Check if we have a value loaded. If not, do so on the fly.
        Object value = attributes.computeIfAbsent(attr, k -> attr.getInitialValue());
        lastKey = attr;
        lastValue = value;
        return (T) lastValue;
    }

    /**
     * Determines if there is a value for {@code attr}.
     *
     * @param attr The attribute to check for.
     * @return {@code true} if there is a value for {@code attr}.
     */
    public boolean has(Attribute<?> attr) {
        return attributes.containsKey(attr);
    }
}
