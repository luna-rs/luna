package io.luna.game.model;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A model representing a manager for a repository of {@link StationaryEntity}s. It handles the registration and
 * tracking of the world's entities.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class EntityList<E extends StationaryEntity> implements Iterable<E> {

    /**
     * An {@link Iterator} implementation for this entity list that will unregister entities on {@link #remove()}.
     */
    public final class EntityListIterator implements Iterator<E> {

        /**
         * The last result of calling {@link #next()}.
         */
        private E lastEntity;

        /**
         * The delegate iterator.
         */
        private final Iterator<E> delegate;

        public EntityListIterator(Collection<E> entities) {
            delegate = entities.iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public E next() {
            lastEntity = delegate.next();
            return lastEntity;
        }

        @Override
        public void remove() {
            delegate.remove();
            lastEntity.hide();
            lastEntity.setState(EntityState.INACTIVE);
        }
    }

    /**
     * The world.
     */
    protected final World world;

    /**
     * The type of the entities.
     */
    protected final EntityType type;

    /**
     * Creates a new {@link EntityList}.
     *
     * @param world The world.
     * @param type The type of the entities.
     */
    public EntityList(World world, EntityType type) {
        this.world = world;
        this.type = type;
    }

    @Override
    public abstract Spliterator<E> spliterator();

    @Override
    public abstract Iterator<E> iterator();

    /**
     * A function invoked when an entity is registered.
     *
     * @param entity The entity.
     */
    protected abstract boolean onRegister(E entity);

    /**
     * A function invoked when an entity is unregistered.
     *
     * @param entity The entity.
     */
    protected abstract boolean onUnregister(E entity);

    /**
     * Returns the current amount of registered entities.
     */
    public abstract int size();

    /**
     * Registers and shows {@code entity}.
     *
     * @param entity The entity to register.
     * @return {@code true} if {@code entity} was registered.
     */
    public final boolean register(E entity) {
        if (entity.getType() == type &&
                entity.getState() == EntityState.NEW) {
            return onRegister(entity);
        }
        return false;
    }

    /**
     * Unregisters and hides {@code entity}.
     *
     * @param entity The entity to unregister.
     * @return {@code true} if {@code entity} was unregistered.
     */
    public final boolean unregister(E entity) {
        if (entity.getType() == type &&
                entity.getState() == EntityState.ACTIVE) {
            return onUnregister(entity);
        }
        return false;
    }

    /**
     * Unregisters all entities on {@code position} that match {@code filter}.
     *
     * @param position The position to unregister entities on.
     * @param test The filter to apply.
     * @return {@code true} if at least one entity was unregistered.
     */
    public final boolean removeFromPosition(Position position, Predicate<E> test) {

        List<E> toRemove = findAll(position).
                filter(entity -> position.equals(entity.position)).
                filter(test).
                collect(Collectors.toList());
        toRemove.forEach(this::unregister);
        return !toRemove.isEmpty();
    }

    /**
     * Retrieves all entities on {@code position}.
     *
     * @param position The position to unregister entities on.
     * @return The set of entities.
     */
    public final Stream<E> findAll(Position position) {
        var chunkManager = world.getChunks();
        Stream<E> insideChunk = chunkManager.load(position.getChunkPosition()).stream(type);
        return insideChunk.filter(entity -> entity.position.equals(position));
    }

    /**
     * A stream over every single entity of this type.
     *
     * @return The stream.
     */
    public final Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}