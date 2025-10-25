package io.luna.game.model.mob.attr;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * A container that manages dynamic and persistent {@link Attribute} values for mobs.
 *
 * @author lare96
 */
public final class AttributeMap {

    /**
     * Global registry of persistence keys used to prevent collisions.
     */
    public static final Map<String, Attribute<?>> persistentKeyMap = new ConcurrentHashMap<>();

    /**
     * Attributes loaded from disk or deserialization that await activation.
     */
    private final Map<String, Object> loadedAttributes = new HashMap<>();

    /**
     * Live attribute values mapped by identity
     */
    private final Map<Attribute<?>, Object> attributes = new IdentityHashMap<>(12);

    /**
     * Last used key for fast access optimization.
     */
    private Attribute<?> lastKey;

    /**
     * Last used value for fast access optimization.
     */
    private Object lastValue;

    /**
     * Loads attribute values from serialized data.
     *
     * @param loadedAttributeMap A list of objects representing persisted attributes.
     */
    public void load(Map<String, Object> loadedAttributeMap) {
        attributes.clear();
        loadedAttributes.clear();
        loadedAttributeMap.forEach((String key, Object value) -> {
            // First retrieve the runtime type.
            String[] tokens = key.split("@");
            String name = tokens[0];
            String type = tokens[1];
            try {
                // Instantiate the type, and convert our loaded value.
                Class<?> typeClass = Class.forName(type);
                if (value == null || value.equals("null")) {
                    // Value is nullable and was set to null. Don't load anything.
                    return;
                }

                // Convert from JsonElement -> typeClass using type adapters.
                Object convertedValue = Attribute.getGsonInstance().
                        fromJson(Attribute.getGsonInstance().toJsonTree(value), typeClass);
                checkState(loadedAttributes.put(name, convertedValue) == null,
                        "Duplicate persistent attribute key {%s}.", key);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Serializes this map's persistent attributes to a list format.
     *
     * @return A list ready for JSON serialization.
     */
    public LinkedHashMap<String, Object> save() {
        LinkedHashMap<String, Object> save = new LinkedHashMap<>();
        attributes.forEach((Attribute<?> key, Object value) -> {
            // Persist all necessary attributes.
            if (key.isPersistent()) {
                Class<?> valueClass = key.getValueType();
                save.put(key.getPersistenceKey() + "@" + valueClass.getName(), value == null ? "null" : value);
            }
        });
        return save;
    }

    /**
     * Associates a value with an attribute.
     *
     * @param attr The attribute key.
     * @param value The new value.
     * @param <T> The value type.
     * @return The old value, if any.
     */
    public <T> T set(Attribute<T> attr, T value) {
        var previousValue = attributes.put(attr, value);
        lastKey = attr;
        lastValue = value;
        if (attr.isPersistent() && previousValue == null) {
            // There's now proper mapping for a loaded attribute, remove it.
            loadedAttributes.remove(attr.getPersistenceKey());
            return null;
        }
        return (T) previousValue;
    }

    /**
     * Retrieves a value from the map or loads it using the initial or persisted value.
     *
     * @param attr The attribute key.
     * @param <T> The value type.
     * @return The resolved value.
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
     * Checks if the map contains a value for a given attribute.
     *
     * @param attr The attribute key.
     * @return {@code true} if a value is present or loadable.
     */
    public boolean has(Attribute<?> attr) {
        if (!attributes.containsKey(attr)) {
            String key = attr.getPersistenceKey();
            return key != null && loadedAttributes.containsKey(key);
        }
        return true;
    }

    /**
     * @return How many attributes are within this map.
     */
    public int size() {
        return attributes.size() + loadedAttributes.size();
    }
}
