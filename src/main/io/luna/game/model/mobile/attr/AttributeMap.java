package io.luna.game.model.mobile.attr;

import com.google.common.collect.Iterators;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;

/**
 * A wrapper for a {@link HashMap} that contains a function to retrieve an {@link AttributeValue} by its {@link String} key.
 * The retrieval of attributes is very high performing because of the fact that it interns all {@code String} keys eagerly on
 * startup and subsequently compares all {@code String} keys by identity instead of {@link Object} equality.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeMap implements Iterable<Entry<AttributeKey<?>, AttributeValue<?>>> {

    /**
     * A {@link HashMap} that holds our {@link AttributeKey} and {@link AttributeValue} pair.
     */
    private final Map<AttributeKey<?>, AttributeValue<?>> attributes = new HashMap<>();

    /**
     * Retrieves an {@link AttributeValue} by its {@code key}. Unfortunately this function is not type safe, so it may throw
     * a {@link ClassCastException} if used with the wrong underlying type.
     *
     * @param key The key to retrieve the {@code AttributeValue} with.
     * @return The retrieved {@code AttributeValue}.
     */
    @SuppressWarnings("unchecked")
    public <T> AttributeValue<T> get(String key) {
        AttributeKey<?> alias = AttributeKey.ALIASES.get(key);

        checkState(alias != null, "[" + key + "] needs to be aliased within AttributeKeyProvider in PluginBootstrap!");

        return (AttributeValue<T>) attributes.computeIfAbsent(alias, it -> new AttributeValue<>(alias.getInitialValue()));
    }

    @Override
    public Iterator<Entry<AttributeKey<?>, AttributeValue<?>>> iterator() {
        return Iterators.unmodifiableIterator(attributes.entrySet().iterator());
    }
}
