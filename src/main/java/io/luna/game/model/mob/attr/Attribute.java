package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.gson.JsonElement;
import io.luna.game.model.mob.Player;
import io.luna.util.GsonUtils;

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
     * The initial value.
     */
    private final T initialValue;

    /**
     * The value class.
     */
    private final Class<T> valueType;

    /**
     * The serializer, if any.
     */
    private AttributeSerializer<T> serializer;

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
     * Converts the attribute value into a {@link JsonElement}, for serialization.
     *
     * @param out The attribute value.
     * @return The serialization object.
     */
    public JsonElement write(T out) {
        // Attempt to use a custom serializer to write data.
        if (serializer != null) {
            return serializer.write(out);
        }
        return GsonUtils.toJsonTree(out);
    }

    /**
     * Converts the deserialized {@link JsonElement} back into its original value.
     *
     * @param in The serialization object.
     * @return The attribute value.
     */
    public T read(JsonElement in) {
        // Attempt to use a custom serializer to read data.
        if (serializer != null) {
            return serializer.read(in);
        }
        return GsonUtils.getAsType(in, valueType);
    }

    /**
     * Makes this attribute save permanently to a {@link Player}'s character under {@code persistenceKey}. Attributes
     * cannot share the same key.
     *
     * @param persistenceKey The name for this attribute.
     * @return This attribute.
     */
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

    /**
     * Sets {@code serializer} as the current serializer. A value of {@code null} = default serializer used.
     */
    public void useSerializer(AttributeSerializer<T> serializer) {
        this.serializer = serializer;
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