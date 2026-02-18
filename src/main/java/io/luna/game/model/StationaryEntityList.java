package io.luna.game.model;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents a registry and lifecycle manager for a specific type of {@link StationaryEntity} within the {@link World}.
 * <p>
 * A {@code StationaryEntityList} is responsible for tracking entities that do not dynamically move between tiles
 * (e.g., objects, ground items, decorations). It provides controlled registration and unregistration logic, enforces
 * type safety via {@link EntityType}, and exposes utility methods for querying entities by position or streaming over
 * all active entries.
 * <p>
 * Concrete implementations are expected to define how entities are physically stored (e.g., chunk-based storage,
 * flat arrays, hash maps) by implementing {@link #spliterator()}, {@link #iterator()}, {@link #onRegister(StationaryEntity)},
 * and {@link #onUnregister(StationaryEntity)}.
 * <p>
 * Registration and unregistration operations are guarded by entity type and lifecycle state checks to prevent invalid
 * transitions:
 * <ul>
 *     <li>An entity must be in {@link EntityState#NEW} state to be registered.</li>
 *     <li>An entity must be in {@link EntityState#ACTIVE} state to be unregistered.</li>
 * </ul>
 *
 * @param <E> The specific subtype of {@link StationaryEntity} managed by this list.
 * @author lare96
 * @see StationaryEntity
 * @see World
 * @see EntityType
 */
public abstract class StationaryEntityList<E extends StationaryEntity> implements Iterable<E> {

    /**
     * The {@link World} instance.
     */
    protected final World world;

    /**
     * The {@link EntityType} this list is responsible for.
     */
    protected final EntityType type;

    /**
     * Creates a new {@link StationaryEntityList}.
     *
     * @param world The world context.
     * @param type The entity type managed by this list.
     */
    public StationaryEntityList(World world, EntityType type) {
        this.world = world;
        this.type = type;
    }

    /**
     * Returns a {@link Spliterator} over the active entities.
     * <p>
     * Implementations should provide an efficient spliterator matching their underlying storage model.
     */
    @Override
    public abstract Spliterator<E> spliterator();

    /**
     * Returns an {@link Iterator} over the active entities.
     */
    @Override
    public abstract Iterator<E> iterator();

    /**
     * Invoked internally when an entity is successfully validated for registration. Implementations should add the
     * entity to their internal storage structure and update its state appropriately.
     *
     * @param entity The entity being registered.
     * @return {@code true} if registration succeeded.
     */
    protected abstract boolean onRegister(E entity);

    /**
     * Invoked internally when an entity is successfully validated for unregistration. Implementations should
     * remove the entity from storage and update its state.
     *
     * @param entity The entity being unregistered.
     * @return {@code true} if unregistration succeeded.
     */
    protected abstract boolean onUnregister(E entity);

    /**
     * Returns the total number of currently registered entities.
     *
     * @return The number of active entities.
     */
    public abstract int size();

    /**
     * Registers and makes {@code entity} visible within the world.
     * <p>
     * The entity must match this list's {@link #type} and be in {@link EntityState#NEW} state.
     *
     * @param entity The entity to register.
     * @return {@code true} if the entity was successfully registered.
     */
    public final boolean register(E entity) {
        if (entity.getType() == type &&
                entity.getState() == EntityState.NEW) {
            return onRegister(entity);
        }
        return false;
    }

    /**
     * Unregisters and removes {@code entity} from the world.
     * <p>
     * The entity must match this list's {@link #type} and be in {@link EntityState#ACTIVE} state.
     *
     * @param entity The entity to unregister.
     * @return {@code true} if the entity was successfully removed.
     */
    public final boolean unregister(E entity) {
        if (entity.getType() == type &&
                entity.getState() == EntityState.ACTIVE) {
            return onUnregister(entity);
        }
        return false;
    }

    /**
     * Unregisters all entities at the specified {@code position} that satisfy the given {@code filter}.
     *
     * @param position The tile to remove entities from.
     * @param filter A predicate used to select matching entities.
     * @return {@code true} if at least one entity was removed.
     */
    public final boolean removeFromPosition(Position position, Predicate<E> filter) {

        List<E> toRemove = findAll(position)
                .filter(filter)
                .collect(Collectors.toList());

        toRemove.forEach(this::unregister);
        return !toRemove.isEmpty();
    }

    /**
     * Determines whether the specified {@code position} is occupied by at least one entity of this type.
     * <p>
     * This method is more efficient than streaming over {@link #findAll(Position)} because it queries the chunk directly.
     *
     * @param position The position to check.
     * @return {@code true} if at least one entity occupies the tile.
     */
    public final boolean isOccupied(Position position) {
        for (Entity entity : world.getChunks().load(position).getAll(type)) {
            if (entity.getPosition().equals(position)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a {@link Stream} of all entities located at {@code position}.
     *
     * @param position The position to query.
     * @return A stream of matching entities.
     */
    public final Stream<E> findAll(Position position) {
        var chunkManager = world.getChunks();
        Stream<E> insideChunk = chunkManager.load(position.getChunk()).stream(type);
        return insideChunk.filter(entity -> entity.position.equals(position));
    }

    /**
     * Returns a sequential stream over all active entities of this type.
     *
     * @return A stream of entities.
     */
    public final Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
