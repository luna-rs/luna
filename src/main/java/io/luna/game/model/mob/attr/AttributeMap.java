package io.luna.game.model.mob.attr;

import com.google.gson.internal.LinkedTreeMap;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A model that contains key-value mappings for {@link Attribute} types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap {

    /**
     * A set of all persistent keys. Used to ensure there are no duplicates.
     */
    public static final Map<String, Attribute<?>> persistentKeyMap = new ConcurrentHashMap<>();

    /**
     * A map of persistent attributes waiting to be assigned.
     */
    private final Map<String, Object> loadedAttributes = new HashMap<>();

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

    /**
     * Loads attribute values from the loaded map.
     *
     * @param loadedAttributeMap The loaded attributes.
     */
    public void load(Map<String, Object> loadedAttributeMap) {
        loadedAttributeMap.forEach((String key, Object value) -> {
            // Because Google gson changes Map to LinkedTreeMap.
            if (value instanceof LinkedTreeMap<?, ?>) {
                value = new HashMap<>((LinkedTreeMap<?, ?>) value);
            }

            checkState(loadedAttributes.put(key, value) == null,
                "Duplicate persistent attribute key {%s}.", key);
        });
    }

    /**
     * Creates a copy of this map for saving.
     *
     * @return A copy of this map.
     */
    public Map<String, Object> save() {
        Map<String, Object> attributesCopy = new HashMap<>();
        attributes.forEach((Attribute<?> key, Object value) -> {
            if (key.isPersistent()) {
                attributesCopy.put(key.getPersistenceKey(), value);
            }
        });
        attributesCopy.putAll(loadedAttributes);
        return attributesCopy;
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
        var previousValue = attributes.put(attr, value);
        lastKey = attr;
        lastValue = value;
        if (attr.isPersistent() && previousValue == null) {
            // There's now proper mapping for a loaded attribute, remove it.
            loadedAttributes.remove(attr.getPersistenceKey());
        }
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
        Object value = attributes.get(attr);
        if (value == null) {
            if (attr.isPersistent()) {
                // Attribute persistent, load it from saved data or load it's initial value.
                Object loadedValue = loadedAttributes.remove(attr.getPersistenceKey());
                value = loadedValue == null ? attr.getInitialValue() : loadedValue;
            } else {
                // Attribute not persistent, load it's initial value.
                value = attr.getInitialValue();
            }
            attributes.put(attr, value);
        }

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
