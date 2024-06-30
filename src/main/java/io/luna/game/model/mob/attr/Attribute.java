package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import io.luna.game.model.mob.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A model representing metadata for a attribute value.
 *
 * @author lare96
 */
public final class Attribute<T> {

    /**
     * The map of specialized persisted types.
     */
    private static final Map<Class<?>, TypeAdapter<?>> specialTypes = new ConcurrentHashMap<>();

    /**
     * The JSON serializer.
     */
    private static volatile Gson serializer = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /**
     * Adds a specialized type that can be persisted.
     *
     * @param typeClass The class of the type.
     * @param typeAdapter The type adapter.
     */
    public static <E> void addSpecialType(GsonBuilder builder, Class<E> typeClass, TypeAdapter<E> typeAdapter) {
        specialTypes.put(typeClass, typeAdapter);
        builder.registerTypeAdapter(typeClass, typeAdapter);
    }

    /**
     * Determines if {@code typeClass} is a special type.
     *
     * @param typeClass The type to check.
     * @return {@code true} if a special type.
     */
    public static boolean isSpecialType(Class<?> typeClass) {
        return specialTypes.containsKey(typeClass);
    }


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
     * The initial value.
     */
    private final T initialValue;

    /**
     * The value class.
     */
    private final Class<T> valueType;

    /**
     * The persistence key, if permanently saved.
     */
    private String persistenceKey;

    /**
     * Creates a new {@link Attribute}.
     *
     * @param initialValue The initial value.
     */
    public Attribute(T initialValue) {
        this.initialValue = requireNonNull(initialValue, "Initial value cannot be <null>.");
        valueType = (Class<T>) initialValue.getClass();
    }

    /**
     * Creates a new {@link Attribute} that might not have an initial value.
     *
     * @param type The type of the value.
     * @param initialValue The initial value, possibly {@code null}.
     */
    public Attribute(Class<T> type, T initialValue) {
        this.initialValue = initialValue;
        valueType = type;
    }

    /**
     * Makes this attribute save permanently to a {@link Player}'s character under {@code persistenceKey}. Attributes
     * cannot share the same key.
     *
     * @param persistenceKey The name for this attribute.
     * @return This attribute.
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
     * Does the same thing as {@link #persist(String)}, but does not add it to the persistent key set.
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
     * @return The initial value.
     */
    public T getInitialValue() {
        return initialValue;
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