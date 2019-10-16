package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.luna.game.model.mob.Player;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A model representing metadata for a player-assigned value.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Attribute<T> {

    /**
     * The JSON serializer.
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
     * Makes this attribute save permanently to a {@link Player}'s character under {@code persistenceKey}. Attributes
     * cannot share the same key.
     *
     * @param persistenceKey The name for this attribute.
     * @return This attribute.
     */
    public Attribute<T> persist(String persistenceKey) {
        checkState(AttributeMap.persistentKeyMap.put(persistenceKey, this) == null,
                "Persistent attribute with key {" + persistenceKey + "} already exists.");
        checkArgument(!persistenceKey.isEmpty(), "Persistent attribute keys must not be empty.");
        checkArgument(CharMatcher.whitespace().matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has whitespace characters, use underscores instead.");
        checkArgument(CharMatcher.forPredicate(Character::isUpperCase).matchesNoneOf(persistenceKey),
                "Persistent attribute key {" + persistenceKey + "} has uppercase characters, use lowercase ones instead.");
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