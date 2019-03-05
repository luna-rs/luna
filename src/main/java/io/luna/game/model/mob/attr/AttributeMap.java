package io.luna.game.model.mob.attr;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A model that contains key-value mappings for {@link Attribute} types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap implements Iterable<Entry<Attribute<?>, Object>> {

    /**
     * A set of all persistent keys. Used to ensure there are no duplicates.
     */
    public static final Set<String> persistentKeySet = Sets.newConcurrentHashSet();

    /**
     * A map that holds attribute key and value pairs.
     */
    private final Map<Attribute<?>, Object> attributes = new IdentityHashMap<>(32);

    /**
     * The last accessed key.
     */
    private Attribute<?> lastKey;

    /**
     * The last accessed value.
     */
    private Object lastValue;

    public <T> void set(Attribute<T> attr, T value) {
        attributes.put(attr, value);
        lastKey = attr;
        lastValue = value;
    }

    public void load(String key, Object value) {

    }

    public <T> T get(Attribute<T> attr) {
        // Attribute is equal to cached key, return last value.
        if (attr == lastKey) {
            return (T) lastValue;
        }

        // Compute or lookup a value for the key.
        lastKey = attr;
        lastValue = attributes.computeIfAbsent(attr, Attribute::getInitialValue);
        return (T) lastValue;
    }

    public boolean has(Attribute<?> attr) {
        return attributes.containsKey(attr);
    }

    @Override
    public UnmodifiableIterator<Entry<Attribute<?>, Object>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }
}
