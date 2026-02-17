package io.luna.game.model.chunk;

import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.LocalEntity;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.World;
import io.luna.game.model.collision.CollisionMatrix;
import io.luna.game.model.mob.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.chunk.Chunk.SIZE;

/**
 * Per-chunk storage for {@link Entity} instances and queued chunk update requests.
 * <p>
 * Each {@link Chunk} owns a {@link ChunkRepository}. This repository is used by {@link ChunkManager} to:
 * <ul>
 *     <li>track which entities currently occupy the chunk (grouped by {@link EntityType})</li>
 *     <li>store and service queued {@link ChunkUpdatableRequest}s (temporary and persistent)</li>
 *     <li>maintain a {@link CollisionMatrix} for pathing/traversal checks on each height level</li>
 * </ul>
 * <p>
 * <b>Updates:</b>
 * <ul>
 *     <li>{@link #temporaryUpdates} stores one-tick requests (sounds, graphics, item amount changes, object
 *     animations, etc).</li>
 *     <li>{@link #persistentUpdates} caches requests that must survive across ticks and/or chunk clears
 *         (most commonly “show object/item” style updates).</li>
 * </ul>
 * <p>
 * <b>Collision snapshot:</b>
 * The repository maintains both live matrices ({@link #matrices}) and a volatile snapshot ({@link #snapshot}).
 * Callers may choose the snapshot for safe reads in contexts where the live matrices could be concurrently modified.
 *
 * @author lare96
 */
public final class ChunkRepository implements Iterable<Entity> {

    /**
     * The owning world instance.
     */
    private final World world;

    /**
     * The chunk this repository belongs to.
     */
    private final Chunk chunk;

    /**
     * The entities currently inside this chunk, grouped by {@link EntityType}.
     */
    private final Map<EntityType, Set<Entity>> entities;

    /**
     * Cached persistent update requests for {@link StationaryEntity} types in this chunk.
     * <p>
     * This typically stores “display/show” style requests that must be re-applied when a player loads the chunk
     * or when the chunk is refreshed.
     */
    private final Map<StationaryEntity, ChunkUpdatableRequest> persistentUpdates = new HashMap<>();

    /**
     * One-tick update requests for {@link StationaryEntity} types in this chunk.
     * <p>
     * Examples: local graphics/sounds, temporary object animations, transient item changes, etc.
     */
    private final List<ChunkUpdatableRequest> temporaryUpdates = new ArrayList<>();

    /**
     * The live collision matrices for this chunk, one per height level.
     */
    private final CollisionMatrix[] matrices = CollisionMatrix.createMatrices(
            Position.HEIGHT_LEVELS.upperEndpoint(), SIZE, SIZE);

    /**
     * Volatile snapshot of {@link #matrices} for safe reads.
     * <p>
     * Updated by {@link #snapshotCollisionMap()}.
     */
    private volatile CollisionMatrix[] snapshot = CollisionMatrix.createMatrices(
            Position.HEIGHT_LEVELS.upperEndpoint(), SIZE, SIZE);

    /**
     * True if this chunk has no map data in the cache index table and should be treated as entirely untraversable.
     */
    private final boolean untraversable;

    /**
     * Creates a new {@link ChunkRepository}.
     *
     * @param world The world instance.
     * @param chunk The chunk this repository is holding entities for.
     */
    ChunkRepository(World world, Chunk chunk) {
        this.world = world;
        this.chunk = chunk;

        entities = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.ALL) {
            entities.put(type, new HashSet<>());
        }

        untraversable = !world.getContext()
                .getCache()
                .getMapIndexTable()
                .getIndexTable()
                .containsKey(chunk.getAbsPosition().getRegion());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ChunkRepository) {
            ChunkRepository other = (ChunkRepository) obj;
            return chunk.equals(other.chunk);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return chunk.hashCode();
    }

    /**
     * Iterates over every entity in this chunk across all {@link EntityType}s.
     *
     * @return An iterator over all entities in this chunk.
     */
    @Override
    public Iterator<Entity> iterator() {
        return entities.values().stream().flatMap(Collection::stream).iterator();
    }

    /**
     * Determines whether {@code next} is traversable for an entity of {@code type} moving in {@code direction}.
     * <p>
     * This checks the {@link CollisionMatrix} for the appropriate height level and tile within this chunk.
     * <p>
     * If {@link #untraversable} is true, this method always returns {@code false}.
     *
     * @param next The candidate tile position (absolute coordinates).
     * @param type The moving entity type.
     * @param direction The movement direction.
     * @param safe If {@code true}, read from the snapshot matrix; otherwise, read from the live matrix.
     * @return {@code true} if the tile is traversable.
     */
    public boolean traversable(Position next, EntityType type, Direction direction, boolean safe) {
        if (untraversable) {
            return false;
        }

        CollisionMatrix matrix = safe ? snapshot[next.getZ()] : matrices[next.getZ()];
        int x = next.getX(), y = next.getY();

        return !matrix.untraversable(x % SIZE, y % SIZE, type, direction);
    }

    /**
     * Creates and publishes a deep snapshot of the live collision matrices.
     * <p>
     * The snapshot is stored in {@link #snapshot} and can be used for thread safe read-only access via
     * {@link #traversable(Position, EntityType, Direction, boolean)} with {@code safe=true}.
     */
    public void snapshotCollisionMap() {
        CollisionMatrix[] copy = new CollisionMatrix[matrices.length];
        for (int i = 0; i < matrices.length; i++) {
            copy[i] = matrices[i].copy();
        }
        snapshot = copy;
    }

    /**
     * Adds an entity to this chunk.
     * <p>
     * The entity is placed into the set for its {@link EntityType}.
     *
     * @param entity The entity to add.
     * @throws IllegalStateException If the entity was already present in the set.
     */
    public void add(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.add(entity), entity + " could not be added to chunk.");
    }

    /**
     * Removes an entity from this chunk.
     * <p>
     * The entity is removed from the set for its {@link EntityType}.
     *
     * @param entity The entity to remove.
     * @throws IllegalStateException If the entity was not present in the set.
     */
    public void remove(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.remove(entity), entity + " could not be removed from chunk.");
    }

    /**
     * Queues a temporary update request for this chunk.
     * <p>
     * The request is appended to {@link #temporaryUpdates} and will be considered by {@link #getUpdates(Player)}.
     * Persistent requests are still queued here first and later moved to {@link #persistentUpdates} by
     * {@link #resetUpdates()}.
     *
     * @param update The update request to queue.
     */
    public void queueUpdate(ChunkUpdatableRequest update) {
        temporaryUpdates.add(update);
    }

    /**
     * Removes a cached persistent update for {@code entity}, if present.
     *
     * @param entity The stationary entity whose persistent request should be removed.
     */
    public void removeUpdate(StationaryEntity entity) {
        persistentUpdates.remove(entity);
    }

    /**
     * Clears all temporary updates and caches any persistent updates.
     * <p>
     * This method processes {@link #temporaryUpdates} and for each request:
     * <ul>
     *     <li>If the request is persistent and the target entity is active, it is cached in {@link #persistentUpdates}.</li>
     *     <li>If the request targets a {@link LocalEntity}, that entity's lifecycle is ended by setting it inactive.</li>
     *     <li>The request is removed from {@link #temporaryUpdates}.</li>
     * </ul>
     * <p>
     * Discard rule: persistent requests targeting inactive entities are not cached.
     */
    public void resetUpdates() {
        var it = temporaryUpdates.iterator();
        while (it.hasNext()) {
            var request = it.next();

            if (request.isPersistent()) {
                StationaryEntity entity = (StationaryEntity) request.getUpdatable();

                // Discard requests with inactive entities instead of caching.
                if (entity.getState() == EntityState.INACTIVE) {
                    it.remove();
                    continue;
                }

                /*
                 * Some requests must be persisted because the temporary list is cleared every tick.
                 * For example, an update request that displays an object must be resent whenever
                 * a chunk is cleared of entities and rebuilt for a player.
                 */
                persistentUpdates.put(entity, request);
            }

            if (request.getUpdatable() instanceof LocalEntity) {
                // End life-cycle for local entities after their update has been processed.
                LocalEntity entity = (LocalEntity) request.getUpdatable();
                entity.setState(EntityState.INACTIVE);
            }

            it.remove();
        }
    }

    /**
     * Collects all queued temporary update messages that are visible to {@code player}.
     * <p>
     * Visibility is determined by {@link ChunkUpdatableView#isViewableFor(Player)} using each updatable's
     * {@link ChunkUpdatable#computeCurrentView()}.
     * <p>
     * This method only reads {@link #temporaryUpdates}. Persistent requests are exposed via
     * {@link #getPersistentUpdates()} for the chunk system to re-apply when appropriate.
     *
     * @param player The player to collect updates for.
     * @return A list of visible update messages.
     */
    public List<ChunkUpdatableMessage> getUpdates(Player player) {
        List<ChunkUpdatableMessage> messages = new ArrayList<>();
        for (ChunkUpdatableRequest request : temporaryUpdates) {
            ChunkUpdatableView view = request.getUpdatable().computeCurrentView();
            if (view.isViewableFor(player)) {
                messages.add(request.getMessage());
            }
        }
        return messages;
    }

    /**
     * Returns the set of entities of a given {@link EntityType} in this chunk.
     * <p>
     * The returned set is the live backing set. Mutating it will mutate the repository.
     * <p>
     * This method performs an unchecked cast. The caller must ensure {@code E} matches the expected runtime
     * type for the provided {@code type}, otherwise a {@link ClassCastException} may occur later.
     *
     * @param type The entity type.
     * @param <E> The entity subtype expected by the caller.
     * @return The set of entities for {@code type}.
     */
    @SuppressWarnings("unchecked")
    public <E extends Entity> Set<E> getAll(EntityType type) {
        return (Set<E>) entities.get(type);
    }

    /**
     * Returns a stream over entities of the given type in this chunk.
     *
     * @param type The entity type.
     * @param <E> The entity subtype expected by the caller.
     * @return A stream of entities for {@code type}.
     */
    @SuppressWarnings("unchecked")
    public <E extends Entity> Stream<E> stream(EntityType type) {
        return (Stream<E>) getAll(type).stream();
    }

    /**
     * @return The world instance.
     */
    public World getWorld() {
        return world;
    }

    /**
     * @return The chunk this repository belongs to.
     */
    public Chunk getChunk() {
        return chunk;
    }

    /**
     * @return The backing entity map by type.
     */
    public Map<EntityType, Set<Entity>> getAll() {
        return entities;
    }

    /**
     * @return The live collision matrices for this chunk.
     */
    public CollisionMatrix[] getMatrices() {
        return matrices;
    }

    /**
     * @return The current collision snapshot matrices.
     */
    public CollisionMatrix[] getSnapshot() {
        return snapshot;
    }

    /**
     * Returns an unmodifiable view of all cached persistent update requests.
     * <p>
     * These are typically re-applied by the chunk system when rebuilding a player's view of the chunk.
     *
     * @return An unmodifiable collection of persistent requests.
     */
    public Collection<ChunkUpdatableRequest> getPersistentUpdates() {
        return Collections.unmodifiableCollection(persistentUpdates.values());
    }
}
