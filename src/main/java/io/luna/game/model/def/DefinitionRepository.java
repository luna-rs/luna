package io.luna.game.model.def;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkState;


/**
 * A model representing a repository for a definition implementation. Definition repositories are immutable
 * after they are locked and ordered by definition identifier.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class DefinitionRepository<T extends Definition> implements Iterable<T> {

    /**
     * If this repository is read-only.
     */
    private boolean locked;

    /**
     * The size of this repository.
     */
    private int size;

    @Override
    public final UnmodifiableIterator<T> iterator() {
        return Iterators.unmodifiableIterator(newIterator());
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), size,
                Spliterator.ORDERED | Spliterator.IMMUTABLE);
    }

    /**
     * Stores {@code definition} in this repository. Should only ever be used by this class.
     *
     * @param id         The definition identifier.
     * @param definition The definition.
     */
    abstract void put(int id, T definition);

    /**
     * Performs a lookup for the definition with {@code id}. Wraps the result in an optional.
     *
     * @param id The definition identifier.
     * @return The definition instance, wrapped in an optional.
     */
    public abstract Optional<T> get(int id);

    /**
     * Creates and returns an iterator, iterating over all definitions in this repository. The returned
     * iterator will be made immutable by {@link DefinitionRepository#iterator()}.
     *
     * @return An iterator.
     */
    public abstract Iterator<T> newIterator();

    /**
     * Performs a lookup for the definition with {@code id}, or throws an exception if it doesn't
     * exist.
     *
     * @param id The definition identifier.
     * @return The definition instance.
     * @throws NoSuchElementException If there is no definition for {@code id}.
     */
    public final T retrieve(int id) throws NoSuchElementException {
        return get(id).orElseThrow(() -> new NoSuchElementException("No definition mapped for " + id));
    }

    /**
     * Finds the first definition that matches {@code predicate}.
     *
     * @param predicate The predicate.
     * @return The matching definition.
     */
    public Optional<T> lookup(Predicate<T> predicate) {
        Iterator<T> it = newIterator();
        while (it.hasNext()) {
            T next = it.next();
            if (next != null && predicate.test(next)) {
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to store {@code definition} in this repository.
     *
     * @param definition The definition to store.
     */
    public final void storeDefinition(T definition) {
        checkState(!locked, "Cannot add definitions to a locked repository.");

        int id = definition.getId();
        checkState(!get(id).isPresent(), "Cannot have two definitions mapped to the same identifier.");

        put(id, definition);
        size++;
    }

    /**
     * Attempts to store all {@code definitions}, and locks the repository afterwards.
     *
     * @param definitions The definitions to store.
     */
    public final void storeAndLock(Collection<? extends T> definitions) {
        definitions.forEach(this::storeDefinition);
        lock();
    }

    /**
     * Locks this repository, making it read-only. This cannot be undone.
     */
    public final void lock() {
        checkState(!locked, "This repository is already locked.");
        locked = true;
    }

    /**
     * Creates and returns a new stream over the definitions in this repository.
     *
     * @return The new stream.
     */
    public final Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false).filter(Objects::nonNull);
    }

    /**
     * @return The size of this repository.
     */
    public final int getSize() {
        return size;
    }
}