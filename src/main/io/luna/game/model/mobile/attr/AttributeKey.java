package io.luna.game.model.mobile.attr;

import com.google.common.base.MoreObjects;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A {@link String} wrapper also known as an alias that defines behavior and functionality for attributes. {@code String}
 * keys are forcibly interned and aliased on startup into an {@link IdentityHashMap} to be easily accessible as well as high
 * performing.
 * <p>
 * <p>
 * The naming convention for all {@code String} keys is {@code lower_underscore}.
 *
 * @param <T> The {@link Object} type represented by this key.
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeKey<T> {

    /**
     * An {@link IdentityHashMap} of {@link String} keys mapped to their {@code AttributeKey} aliases. All {@code String}s
     * added to this map are forcibly interned so we can compare them by their identity for faster performance.
     */
    public static final Map<String, AttributeKey<?>> ALIASES = new IdentityHashMap<>();

    /**
     * Aliases {@code name} with an initial value of {@code initialValue} that will be written to and read from the character
     * file.
     *
     * @param name The name of this key.
     * @param initialValue The initial value of this key.
     */
    public static <T> void forPersistent(String name, T initialValue) {
        ALIASES.put(name, new AttributeKey<>(name, initialValue, true));
    }

    /**
     * Aliases {@code name} with an initial value of {@code null} that will be written to and read from the character file.
     *
     * @param name The name of this key.
     */
    public static void forPersistent(String name) {
        forPersistent(name, null);
    }

    /**
     * Aliases {@code name} with an initial value of {@code initialValue}.
     *
     * @param name The name of this key.
     * @param initialValue The initial value of this key.
     */
    public static <T> void forTransient(String name, T initialValue) {
        ALIASES.put(name, new AttributeKey<>(name, initialValue, false));
    }

    /**
     * Aliases {@code name} with an initial value of {@code null}.
     *
     * @param name The name of this key.
     */
    public static void forTransient(String name) {
        forTransient(name, null);
    }

    /**
     * The name of this alias.
     */
    private final String name;

    /**
     * The initial value of this alias.
     */
    private final T initialValue;

    /**
     * If the value of this alias should be serialized.
     */
    private final boolean isPersistent;

    /**
     * The fully-qualified class name of this attribute type.
     */
    private final String typeName;

    /**
     * Creates a new {@link AttributeKey}.
     *
     * @param name The name of this alias.
     * @param initialValue The initial value of this alias.
     * @param isPersistent If the value of this alias should be serialized.
     */
    private AttributeKey(String name, T initialValue, boolean isPersistent) {
        String threadName = Thread.currentThread().getName();

        checkState(threadName.equals("LunaInitializationThread"), "[" + name + "]  alias attributes from PluginBootstrap!");
        checkArgument(!name.isEmpty(), "attribute name length <= 0");
        checkState(ALIASES.put(name.intern(), this) == null, "[" + name + "] cannot alias more than once!");

        this.name = requireNonNull(name.intern());
        this.initialValue = initialValue;
        this.isPersistent = isPersistent;
        typeName = initialValue.getClass().getName();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AttributeKey<?>) {
            AttributeKey<?> other = (AttributeKey<?>) obj;
            return name == other.name;
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).add("persistent", isPersistent).toString();
    }

    /**
     * @return The name of this alias.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The initial value of this alias.
     */
    public T getInitialValue() {
        return initialValue;
    }

    /**
     * @return {@code true} if the value of this alias should be serialized, {@code false} otherwise.
     */
    public boolean isPersistant() {
        return isPersistent;
    }

    /**
     * @return The fully-qualified class name of this attribute type.
     */
    public String getTypeName() {
        return typeName;
    }
}
