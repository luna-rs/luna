package io.luna.game.model.mob.attr;

import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public void load(List<Object> loadedAttributeMap) {
        AtomicBoolean processedBasic = new AtomicBoolean(false);
        loadedAttributeMap.forEach((Object value) -> {
            // Because Google gson changes Map to LinkedTreeMap.
            if (value instanceof LinkedTreeMap<?, ?>) {
                value = new LinkedHashMap<>((LinkedTreeMap<?, ?>) value);
            }

            var nextMap = (LinkedHashMap<String, Object>) value;
            if (!processedBasic.get()) { // First value is always the basic attribute map.
                nextMap.forEach((String key, Object value2) -> {
                    checkState(loadedAttributes.put(key, value2) == null,
                            "Duplicate persistent attribute key {%s}.", key);
                });
                processedBasic.set(true);
            } else {
                // Then we process complex attribute maps.
                var newKey = nextMap.entrySet().stream().findFirst().get();
                String[] keyToken = newKey.getKey().split("@"); // Split the name and type.
                String attrName = keyToken[0];
                String attrType = keyToken[1];
                try {
                    // Get the type, and perform the necessary conversions to be able to add it.
                    Class<?> typeClass = Class.forName(attrType);
                    Object objValue = newKey.getValue();
                    if (objValue == null || objValue.equals("null")) {
                        // Value is nullable and was set to null. Don't load anything.
                        return;
                    }
                    // Convert from Object -> JsonObject.
                    JsonElement jsonValue = Attribute.getGsonInstance().toJsonTree(objValue).getAsJsonObject();

                    // Then from JsonObject -> typeClass using type adapters.
                    Object convertedValue = Attribute.getGsonInstance().fromJson(jsonValue, typeClass);
                    checkState(loadedAttributes.put(attrName, convertedValue) == null,
                            "Duplicate persistent attribute key {%s}.", newKey.getKey());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

            }
        });
    }

    /**
     * Serializes this map's persistent attributes to a list format.
     *
     * @return A list ready for JSON serialization.
     */
    public List<Object> save() {
        List<Object> attrList = new ArrayList<>();
        List<Object> complexAttributes = new ArrayList<>();
        Map<String, Object> basicAttributes = new HashMap<>();
        attributes.forEach((Attribute<?> key, Object value) -> {
            // Persist all necessary attributes.
            if (key.isPersistent()) {
                Class<?> valueClass = key.getValueType();
                if (Attribute.isSpecialType(valueClass)) {
                    // Persist special types in their own map, save with type.
                    Map<String, Object> specialAttribute = new HashMap<>();
                    specialAttribute.put(key.getPersistenceKey() + "@" + valueClass.getName(), value == null ? "null" : value);
                    complexAttributes.add(specialAttribute);
                } else {
                    // Persist basic attributes normally.
                    basicAttributes.put(key.getPersistenceKey(), value);
                }
            }
        });
        // Always save the basic attribute map first.
        attrList.add(basicAttributes);
        attrList.addAll(complexAttributes);
        return attrList;
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
