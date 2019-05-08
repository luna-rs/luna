package io.luna.game.model.mob.attr;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.HashMap;
import java.util.IdentityHashMap;
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
public final class AttributeMap implements Iterable<Entry<String, AttributeValue>> {

    /**
     * A map that holds attribute key and value pairs.
     */
    private final Map<String, AttributeValue> attributes = new IdentityHashMap<>(AttributeKey.ALIASES.size());

    /**
     * The last key.
     */
    private String lastKey;

    /**
     * The last value.
     */
    private AttributeValue lastValue;

    @Override
    public UnmodifiableIterator<Entry<String, AttributeValue>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }

    /**
     * Retrieves the value of an attribute by its String key. Not type safe.
     *
     * @param key The attribute key.
     * @return The attribute value.
     * @throws AttributeTypeException If the return value cannot be casted to {@code <T>}.
     */
    @SuppressWarnings("unchecked")
    public <T> AttributeValue<T> get(String key) throws AttributeTypeException {

        //noinspection StringEquality
        if (lastKey == requireNonNull(key)) { // Check if we can use our cached value.
            return lastValue;
        }

        // Try and retrieve the key. If it's null, try again and forcibly intern the argument.
        AttributeKey<?> alias = getAttributeKey(key);
        checkState(alias != null, "attributes need to be aliased in the AttributeKey class");

        try {
            // Cache key and new attribute value, return cached value.
            lastKey = alias.getName();
            lastValue = attributes
                    .computeIfAbsent(alias.getName(), it -> new AttributeValue<>(alias.getInitialValue()));
            return lastValue;
        } catch (ClassCastException e) {
            // Throw an exception on type mismatch.
            throw new AttributeTypeException(alias);
        }
    }

    /**
     * Determines if {@code key} is a valid attribute in the backing map.
     *
     * @param key The key to check.
     * @return {@code true} if the backing map contains the key.
     */
    public boolean contains(String key) {
        return getAttributeKey(key) != null;
    }

    /**
     * Converts this attribute map into a {@link Map} for serialization.
     */
    public Map<String, Object> toMap() {
        var attrMap = new HashMap<String, Object>();
        for (Entry<String, AttributeValue> entry : this) {
            AttributeKey key = AttributeKey.ALIASES.get(entry.getKey());
            AttributeValue value = entry.getValue();

            if (key.isPersistent()) {
                attrMap.put(key.getName(), value.get());
            }
        }
        return attrMap;
    }

    /**
     * Loads the argued map into the underlying attribute map.
     */
    public void fromMap(Map<String, Object> attributes) {
        for (Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            get(key).set(value);
        }
    }

    /**
     * Retrieves the {@link AttributeKey} instance from a String {@code key}.
     *
     * @param key The key.
     * @return The attribute key instance.
     */
    private AttributeKey<?> getAttributeKey(String key) {
        return Optional.ofNullable(AttributeKey.ALIASES.get(key)).
                orElse(AttributeKey.ALIASES.get(key.intern()));
    }
}
