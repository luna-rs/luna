package io.luna.game.model.mob.attr;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;

import java.util.IdentityHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a String wrapper also known as an alias that defines behavior and functionality for
 * attributes. String keys are forcibly interned and aliased on startup into an {@link IdentityHashMap} to
 * be easily accessible as well as high performing.
 * <p>
 * The naming convention for all String keys is {@code lower_underscore}. Spaces and uppercase letters are
 * not allowed.
 *
 * @param <T> The Object type represented by this key.
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeKey<T> {

    /**
     * A map of interned String keys to their metadata.
     */
    public static final Map<String, AttributeKey> ALIASES = new IdentityHashMap<>();

    static {
        // Persistent attributes. These are saved to the character file.
        forPersistent("run_energy", 100.0);
        forPersistent("first_login", true);
        forPersistent("unban_date", "n/a");
        forPersistent("unmute_date", "n/a");

        // Transient attributes. These are reset on logout.
        forTransient("last_bury_timer", 0L);
        forTransient("last_eat_timer", 0L);
        forTransient("last_drink_timer", 0L);
        forTransient("withdraw_as_note", false);
        forTransient("weight", 0.0);
        forTransient("trading_with", -1);
        forTransient("restoring_skills", false);
        forTransient("thread_left", 0);
    }

    /**
     * Aliases a new {@code persistent} key with the argued name and initial value.
     *
     * @param name The attribute name.
     * @param initialValue The attribute's initial value.
     */
    public static <T> void forPersistent(String name, T initialValue) {
        ALIASES.put(name, new AttributeKey<>(name, initialValue, true));
    }

    /**
     * Aliases a new {@code transient} key with the argued name and initial value.
     *
     * @param name The attribute name.
     * @param initialValue The attribute's initial value.
     */
    public static <T> void forTransient(String name, T initialValue) {
        ALIASES.put(name, new AttributeKey<>(name, initialValue, false));
    }

    /**
     * The name.
     */
    private final String name;

    /**
     * The initial value.
     */
    private final T initialValue;

    /**
     * If the attribute should be serialized.
     */
    private final boolean isPersistent;

    /**
     * The fully-qualified class name of the attribute type.
     */
    private final String typeName;

    /**
     * Creates a new {@link AttributeKey}.
     *
     * @param name The name of this alias.
     * @param initialValue The initial value.
     * @param isPersistent If the attribute should be serialized.
     */
    private AttributeKey(String name, T initialValue, boolean isPersistent) {
        checkState(!ALIASES.containsKey(name.intern()), "attribute {" + name + "} already exists");

        checkArgument(!name.isEmpty(), "attribute name length <= 0");

        checkArgument(CharMatcher.whitespace().matchesNoneOf(name),
                "attribute {" + name + "} has whitespace characters, use underscore characters instead");

        checkArgument(CharMatcher.forPredicate(Character::isUpperCase).matchesNoneOf(name),
                "attribute {" + name + "} has uppercase characters, use lowercase characters instead");

        this.name = name.intern();
        this.initialValue = initialValue;
        this.isPersistent = isPersistent;
        typeName = initialValue.getClass().getName();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("name", name).
                add("persistent", isPersistent).
                add("type", typeName).toString();
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The initial value.
     */
    public T getInitialValue() {
        return initialValue;
    }

    /**
     * @return {@code true} if the attribute should be serialized.
     */
    public boolean isPersistent() {
        return isPersistent;
    }

    /**
     * @return The fully-qualified class name of the attribute type.
     */
    public String getTypeName() {
        return typeName;
    }
}
