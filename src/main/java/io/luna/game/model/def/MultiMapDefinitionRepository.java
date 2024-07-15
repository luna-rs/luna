package io.luna.game.model.def;

import com.google.common.collect.ArrayListMultimap;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * An implementation of {@link DefinitionRepository} that is backed internally by a {@link ArrayListMultimap}.
 *
 * @author lare96
 */
public class MultiMapDefinitionRepository<V extends Definition> extends DefinitionRepository<V> {

    /**
     * A multimap of definitions.
     */
    private final ArrayListMultimap<Integer, V> definitions = ArrayListMultimap.create();

    @Override
    boolean put(int id, V definition) {
        definitions.put(id, definition);
        return true;
    }

    /**
     * Retrieves the first definition found with {@code id}.
     *
     * @param id The definition identifier.
     * @return The first definition found, if any.
     */
    @Override
    public Optional<V> get(int id) {
        if (!definitions.get(id).isEmpty()) {
            return Optional.ofNullable(definitions.get(id).iterator().next());
        }
        return Optional.empty();
    }

    @Override
    public Iterator<V> newIterator() {
        return definitions.values().iterator();
    }

    /**
     * Retrieves all definitions found with {@code id}.
     *
     * @param id The identifier.
     * @return The list of definitions found.
     */
    public List<V> getAll(int id) {
        return definitions.get(id);
    }
}
