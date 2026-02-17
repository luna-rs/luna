package io.luna.game.model.def;

import com.google.common.collect.ArrayListMultimap;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * A {@link DefinitionRepository} implementation backed by a {@link ArrayListMultimap}.
 * <p>
 * Unlike {@link ArrayDefinitionRepository} or a simple map-backed repository, this repository supports storing
 * <b>multiple</b> definitions under the same id key. This is useful when:
 * <ul>
 *     <li>a cache/source can emit duplicate ids</li>
 *     <li>you want to group multiple variants under a single logical identifier</li>
 *     <li>you need {@code id to many} lookups (e.g., some definition types that are not uniquely keyed)</li>
 * </ul>
 * <p>
 * <b>Lookup behavior:</b>
 * <ul>
 *     <li>{@link #get(int)} returns the <b>first</b> definition stored for an id, wrapped in an {@link Optional}.</li>
 *     <li>{@link #getAll(int)} returns the live {@link List} view of all values for an id.</li>
 * </ul>
 * <p>
 * <b>Iteration:</b> {@link #newIterator()} iterates over {@code definitions.values()}, which includes all entries across
 * all keys.
 * <p>
 * <b>Mutability note:</b> {@link #getAll(int)} returns the multimap's backing list view. Mutating it will mutate the
 * repository contents. If external callers should not modify repository state, consider returning an immutable copy.
 *
 * @author lare96
 */
public class MultiMapDefinitionRepository<V extends Definition> extends DefinitionRepository<V> {

    /**
     * The backing multimap of definitions, keyed by definition id.
     */
    private final ArrayListMultimap<Integer, V> definitions = ArrayListMultimap.create();

    /**
     * Inserts a definition into the repository.
     * <p>
     * This repository always accepts entries and allows multiple values per id.
     *
     * @param id The definition id.
     * @param definition The definition value.
     * @return {@code true}.
     */
    @Override
    boolean put(int id, V definition) {
        definitions.put(id, definition);
        return true;
    }

    /**
     * Retrieves the first definition stored under {@code id}.
     * <p>
     * If multiple definitions exist for the same id, this returns the first one inserted for that key.
     *
     * @param id The definition id.
     * @return The first stored definition for the id, if present.
     */
    @Override
    public Optional<V> get(int id) {
        if (!definitions.get(id).isEmpty()) {
            return Optional.ofNullable(definitions.get(id).iterator().next());
        }
        return Optional.empty();
    }

    /**
     * Returns a new iterator over all stored definitions across all ids.
     *
     * @return An iterator over every stored definition.
     */
    @Override
    public Iterator<V> newIterator() {
        return definitions.values().iterator();
    }

    /**
     * Retrieves all definitions stored under {@code id}.
     * <p>
     * The returned list is an immutable shallow-copied view backed by the underlying multimap.
     *
     * @param id The definition id.
     * @return A list view of all definitions stored for the id (possibly empty).
     */
    public List<V> getAll(int id) {
        return Collections.unmodifiableList(definitions.get(id));
    }
}
