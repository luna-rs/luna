package io.luna.game.model.def;

import com.google.common.collect.Iterators;

import java.util.Iterator;
import java.util.Optional;

/**
 * A {@link DefinitionRepository} implementation backed by a fixed-size array.
 * <p>
 * This repository is designed for definition types with:
 * <ul>
 *     <li>dense, bounded ids (0..N-1)</li>
 *     <li>exactly one definition per id</li>
 *     <li>fast O(1) retrieval by id</li>
 * </ul>
 * <p>
 * <b>Insertion rules:</b> {@link #put(int, Definition)} returns {@code false} and does not overwrite if an entry already
 * exists at that id.
 * <p>
 * <b>Iteration:</b> {@link #newIterator()} iterates over the backing array in index order. The iterator includes
 * {@code null} entries for ids that are not populated.
 */
public final class ArrayDefinitionRepository<T extends Definition> extends DefinitionRepository<T> {

    /**
     * Backing array of definitions indexed by definition id.
     */
    private final T[] definitions;

    /**
     * Creates a new {@link ArrayDefinitionRepository} with the specified capacity.
     *
     * @param length The length/capacity of the backing array.
     */
    @SuppressWarnings("unchecked")
    public ArrayDefinitionRepository(int length) {
        definitions = (T[]) new Definition[length];
    }

    /**
     * Inserts a definition into the repository if the slot is empty.
     *
     * @param id The definition id (array index).
     * @param definition The definition instance.
     * @return {@code true} if inserted; {@code false} if a definition already existed at {@code id}.
     */
    @Override
    boolean put(int id, T definition) {
        if (definitions[id] != null) {
            return false;
        }
        definitions[id] = definition;
        return true;
    }

    /**
     * Retrieves the definition stored at {@code id}.
     *
     * @param id The definition id.
     * @return The definition, if present.
     */
    @Override
    public Optional<T> get(int id) {
        return Optional.ofNullable(definitions[id]);
    }

    /**
     * Returns a new iterator over the backing array.
     *
     * <p>
     * This iterator traverses every array slot and may return {@code null} values for unpopulated ids.
     *
     * @return An iterator over the array.
     */
    @Override
    public Iterator<T> newIterator() {
        return Iterators.forArray(definitions);
    }
}
