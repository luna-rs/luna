package io.luna.game.model.mob.attr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import static java.util.Objects.requireNonNull;

/**
 * A model that contains key-value mappings for {@link Attribute} types.
 *
 * @author lare96
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
            } else { // Then we process complex attribute maps.
                var newKey = nextMap.entrySet().stream().findFirst().get();
                String[] keyToken = newKey.getKey().split("@"); // Split the name and type.
                String attrName = keyToken[0];
                String attrType = keyToken[1];
                try {
                    // Get the type, and perform the necessary conversions to be able to add it.
                    Class<?> typeClass = Class.forName(attrType);
                    // Convert from Object -> JsonObject.
                    JsonElement jsonValue = Attribute.getGsonInstance().toJsonTree(newKey.getValue()).getAsJsonObject();
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
     * Creates a copy of this map for saving.
     *
     * @return A copy of this map.
     */
    public List<Object> save() {
        List<Object> attrList = new ArrayList<>();
        List<Object> complexAttributes = new ArrayList<>();
        Map<String, Object> basicAttributes = new HashMap<>();
        attributes.forEach((Attribute<?> key, Object value) -> {
            // Persist all necessary attributes.
            if (key.isPersistent()) {
                Class<?> valueClass = value.getClass();
                if (Attribute.isSpecialType(valueClass)) {
                    // Persist special types in their own map, save with type.
                    Map<String, Object> specialAttribute = new HashMap<>();
                    specialAttribute.put(key.getPersistenceKey() + "@" + valueClass.getName(), value);
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
     * Sets {@code attr} to {@code value}.
     *
     * @param attr The attribute to set.
     * @param value The value to set it to.
     * @param <T> The attribute type.
     * @return The previous value, possibly null if there was no value.
     */
    public <T> T set(Attribute<T> attr, T value) {
        requireNonNull(value, "Value cannot be null.");
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
        if (!attributes.containsKey(attr)) {
            String key = attr.getPersistenceKey();
            return key != null && loadedAttributes.containsKey(key);
        }
        return true;
    }
}
