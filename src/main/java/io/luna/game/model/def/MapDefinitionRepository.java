package io.luna.game.model.def;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link DefinitionRepository} implementation backed by a {@link LinkedHashMap}.
 * <p>
 * This repository stores at most one definition per id. Insert order is preserved by {@link LinkedHashMap}, which makes
 * iteration deterministic (useful for debugging, dumps, or any logic that depends on stable ordering).
 * <p>
 * <b>Uniqueness:</b> {@link #put(int, Definition)} returns {@code true} only when the id was not already present.
 * If a definition already exists for the id, it will be replaced and {@code false} is returned.
 * <p>
 * <b>Lookup:</b> {@link #get(int)} performs O(1) average-time retrieval and returns an {@link Optional}.
 * <p>
 * <b>Iteration:</b> {@link #newIterator()} iterates over {@code definitions.values()} in insertion order.
 */
public final class MapDefinitionRepository<V extends Definition> extends DefinitionRepository<V> {

    /**
     * Backing map of definitions, keyed by definition id.
     */
    private final Map<Integer, V> definitions = new LinkedHashMap<>();

    /**
     * Inserts a definition into the repository.
     *
     * @param id The definition id.
     * @param definition The definition.
     * @return {@code true} if this id was not already present; {@code false} if an existing value was replaced.
     */
    @Override
    boolean put(int id, V definition) {
        return definitions.put(id, definition) == null;
    }

    /**
     * Retrieves the definition stored under {@code id}.
     *
     * @param id The definition id.
     * @return The definition, if present.
     */
    @Override
    public Optional<V> get(int id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /**
     * Returns a new iterator over all stored definitions in insertion order.
     *
     * @return An iterator over all stored definitions.
     */
    @Override
    public Iterator<V> newIterator() {
        return definitions.values().iterator();
    }
}
