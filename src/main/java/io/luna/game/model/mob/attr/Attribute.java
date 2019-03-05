package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.gson.JsonElement;
import io.luna.util.GsonUtils;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * @author lare96 <http://github.com/lare96>
 */
public final class Attribute<T> {
    private static final Map<Class<?>, AttributeSerializer> serializerMap = new HashMap<>();

    public static void registerSerializer(AttributeSerializer<?> serializer) {
        serializerMap.put(serializer.valueType(), serializer);
    }

    // TODO Make final but make it so you can add serializers that change behaviour of certain types
    private final T initialValue;
    private final Class<T> valueType;
    private String persistenceKey;

    public Attribute(T initialValue) {
        this.initialValue = requireNonNull(initialValue, "Initial value cannot be <null>.");
        valueType = (Class<T>) initialValue.getClass();
    }

    public JsonElement write(T out) {
        // Attempt to use a custom serializer to write data.
        AttributeSerializer<T> serializer = serializerMap.get(valueType);
        if (serializer != null) {
            return serializer.read(in);
        }
        return GsonUtils.toJsonTree(out);
    }

    public T read(JsonElement in) {
        // Attempt to use a custom serializer to write data.
        AttributeSerializer<T> serializer = serializerMap.get(valueType);
        if (serializer != null) {
            return serializer.read(in);
        }

        // None found, read data normally.
        return GsonUtils.getAsType(in, valueType);
    }

    public Attribute<T> persist(String persistenceKey) {
        checkState(AttributeMap.persistentKeySet.add(persistenceKey),
                "Persistent attribute with key {" + persistenceKey + "} already exists.");
        checkArgument(!persistenceKey.isEmpty(), "Persistent attribute keys must not be empty.");
        checkArgument(CharMatcher.whitespace().matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has whitespace characters, use underscores instead.");
        checkArgument(CharMatcher.forPredicate(Character::isUpperCase).matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has uppercase characters, use lowercase ones instead.");
        this.persistenceKey = persistenceKey;
        return this;
    }

    public boolean isTransient() {
        return persistenceKey == null;
    }

    public boolean isPersistent() {
        return persistenceKey != null;
    }

    public T getInitialValue() {
        return initialValue;
    }

    public Class<T> getValueType() {
        return valueType;
    }

    public String getPersistenceKey() {
        return persistenceKey;
    }
}