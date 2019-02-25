package io.luna.game.model;

import io.luna.game.model.chunk.ChunkManager;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Predicate;
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
     * The world.
     */
    protected final World world;

    /**
     * The type of the entities.
     */
    protected final EntityType type;

    /**
     * The amount of tracked entities.
     */
    private int size;

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
    protected abstract void onRegister(E entity);

    /**
     * A function invoked when an entity is unregistered.
     *
     * @param entity The entity.
     */
    protected abstract void onUnregister(E entity);

    /**
     * Registers and shows {@code entity}.
     *
     * @param entity The entity to register.
     * @return {@code true} if {@code entity} was registered.
     */
    public final boolean register(E entity) {
        if (entity.getType() == type &&
                entity.getState() == EntityState.NEW) {
            onRegister(entity);
            size++;
            return true;
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
            onUnregister(entity);
            size--;
            return true;
        }
        return false;
    }

    /**
     * Unregisters all entities on {@code position} that match {@code filter}.
     *
     * @param position The position to unregister entities on.
     * @param filter The filter to apply.
     * @return {@code true} if at least one entity was unregistered.
     */
    public final boolean removeFromPosition(Position position, Predicate<E> filter) {
        boolean removed = false;
        for (E entity : getFromPosition(position)) {
            if (position.equals(entity.position) &&
                    filter.test(entity) &&
                    unregister(entity)) {
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Retrieves all entities on {@code position}.
     *
     * @param position The position to unregister entities on.
     * @return The set of entities.
     */
    public final Collection<E> getFromPosition(Position position) {
        ChunkManager chunks = world.getChunks();
        return chunks.load(position.getChunkPosition()).getAll(type);
    }

    /**
     * The amount of registered entities.
     *
     * @return The size.
     */
    public final int size() {
        return size;
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