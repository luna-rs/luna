package io.luna.game.model.def;

import com.google.common.collect.Iterators;

import java.util.Iterator;
import java.util.Optional;

/**
 * An implementation of {@link DefinitionRepository} that is backed internally by an array.
 */
public final class ArrayDefinitionRepository<T extends Definition> extends DefinitionRepository<T> {

    /**
     * An array of definitions.
     */
    public final T[] definitions;

    /**
     * Creates a new {@link ArrayDefinitionRepository}.
     *
     * @param length
     *         The length of the backing array.
     */
    @SuppressWarnings("unchecked")
    public ArrayDefinitionRepository(int length) {
        definitions = (T[]) new Definition[length];
    }

    @Override
    boolean put(int id, T definition) {
        if(definitions[id] != null) {
            return false;
        }
        definitions[id] = definition;
        return true;
    }

    @Override
    public Optional<T> get(int id) {
        return Optional.ofNullable(definitions[id]);
    }

    @Override
    public Iterator<T> newIterator() {
        return Iterators.forArray(definitions);
    }
}