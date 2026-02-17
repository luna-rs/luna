package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.luna.game.model.Entity;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Represents a serializable, optionally persistent attribute associated with a type (e.g., {@link Entity}).
 * <p>
 * Attributes wrap a value of type {@code T} and optionally declare a persistence key, allowing them to be stored and
 * retrieved across sessions using JSON serialization. Each persistent key must be unique.
 *
 * @param <T> The value type of the attribute.
 * @author lare96
 */
public final class Attribute<T> {

    /**
     * Global Gson instance used for (de)serialization.
     */
    private static volatile Gson serializer = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /**
     * Sets the new JSON serializer.
     */
    public static void setGsonInstance(Gson newSerializer) {
        serializer = newSerializer;
    }

    /**
     * @return The JSON serializer.
     */
    public static Gson getGsonInstance() {
        return serializer;
    }

    /**
     * The default value supplier of the attribute.
     */
    private final Supplier<T> defaultValueSupplier;

    /**
     * The runtime class of the attribute's value.
     */
    private final Class<T> valueType;

    /**
     * Optional persistence key, uniquely identifying this attribute when saving/loading.
     */
    private String persistenceKey;

    /**
     * Creates a new {@link Attribute} with an optional initial value.
     *
     * @param type The type of the value supplier.
     * @param defaultValueSupplier The default value supplier.
     */
    public Attribute(Class<T> type, Supplier<T> defaultValueSupplier) {
        this.defaultValueSupplier = defaultValueSupplier;
        valueType = type;
    }

    /**
     * Marks this attribute to be saved to character files using the given key. Keys must be lowercase, non-empty,
     * and without whitespace or '@'.
     *
     * @param persistenceKey The unique identifier for persistent storage.
     * @return This attribute instance for chaining.
     */
    public Attribute<T> persist(String persistenceKey) {
        checkArgument(!persistenceKey.isEmpty(), "Persistent attribute keys must not be empty.");
        checkArgument(CharMatcher.whitespace().matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has whitespace characters, use underscores instead.");
        checkArgument(CharMatcher.forPredicate(Character::isUpperCase).matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has uppercase characters, use lowercase ones instead.");
        checkArgument(CharMatcher.forPredicate(c -> c.equals('@')).matchesNoneOf(persistenceKey),
                "Persistent attribute cannot contain '@' character.");
        checkState(AttributeMap.persistentKeyMap.put(persistenceKey, this) == null,
                "Persistent attribute with key {" + persistenceKey + "} already exists.");
        return setPersistenceKey(persistenceKey);
    }

    /**
     * Assigns the persistence key directly (internal use only).
     */
    Attribute<T> setPersistenceKey(String persistenceKey) {
        this.persistenceKey = persistenceKey;
        return this;
    }

    /**
     * @return {@code true} if this attribute will be saved to the character file.
     */
    public boolean isPersistent() {
        return persistenceKey != null;
    }

    /**
     * @return The default value supplier.
     */
    public Supplier<T> getDefaultValueSupplier() {
        return defaultValueSupplier;
    }

    /**
     * @return The value class.
     */
    public Class<T> getValueType() {
        return valueType;
    }

    /**
     * @return The persistence key, if permanently saved.
     */
    public String getPersistenceKey() {
        return persistenceKey;
    }
}