package io.luna.game.model.chunk;

import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.World;
import io.luna.game.model.collision.CollisionMatrix;
import io.luna.game.model.collision.CollisionUpdate;
import io.luna.game.model.collision.CollisionUpdateType;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.chunk.Chunk.SIZE;

/**
 * A model containing {@link Entity} types that exist within a {@link Chunk} on the Runescape map. Every chunk is
 * assigned its own repository as needed by the {@link ChunkManager}.
 *
 * @author lare96
 */
public final class ChunkRepository implements Iterable<Entity> {
// TODO documentation, clean up

    private final World world;
    /**
     * This chunk's position.
     */
    private final Chunk chunk;

    /**
     * The entities within this chunk.
     */
    private final Map<EntityType, Set<Entity>> entities;

    /**
     * A list of pending updates to {@link StationaryEntity} types within this chunk.
     */
    private final List<ChunkUpdatableRequest> pendingUpdates = new ArrayList<>();

    /**
     * The {@link CollisionMatrix} for this chunk.
     */
    private final CollisionMatrix[] matrices = CollisionMatrix.createMatrices(Position.HEIGHT_LEVELS.upperEndpoint(),
            SIZE, SIZE);

    /**
     * Creates a new {@link ChunkRepository}.
     *
     * @param chunk The chunk this repository is holding entities for.
     */
    ChunkRepository(World world, Chunk chunk) {
        this.world = world;
        this.chunk = chunk;
        entities = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.ALL) {
            entities.put(type, new HashSet<>());
        }
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

    @NotNull
    @Override
    public Iterator<Entity> iterator() {
        return entities.values().stream().flatMap(Collection::stream).iterator();
    }


    public void updateCollisionMap(Entity entity, boolean removal) {
        if (entity.getType() != EntityType.OBJECT) {
            return;
        }

        CollisionUpdate.Builder builder = new CollisionUpdate.Builder();
        if (!removal) {
            builder.type(CollisionUpdateType.ADDING);
        } else {
            builder.type(CollisionUpdateType.REMOVING);
        }

        builder.object((GameObject) entity);
        world.getCollisionManager().apply(builder.build());
    }

    /**
     * Adds an entity to this chunk repository.
     *
     * @param entity The entity to add.
     */
    public void add(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.add(entity), "Entity could not be added to chunk.");
    }

    /**
     * Removes an entity from this chunk repository.
     *
     * @param entity The entity to remove.
     */
    public void remove(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.remove(entity), "Entity could not be removed from chunk.");
    }

    /**
     * Queues an update for a {@link StationaryEntity} within this chunk.
     *
     * @param update The update to queue.
     */
    public void queueUpdate(ChunkUpdatableRequest update) {
        pendingUpdates.add(update);
    }

    /**
     * Retrieves all pending updates applicable to {@code player}.
     *
     * @param player The player to get updates for.
     * @return The list of pending updates.
     */
    public List<ChunkUpdatableMessage> getUpdates(Player player) {
        return pendingUpdates.stream().
                filter(update -> update.getUpdatable().computeCurrentView().isViewableFor(player)).
                map(ChunkUpdatableRequest::getMessage).collect(Collectors.toList());
    }

    /**
     * Resets the list of pending updates.
     */
    public void resetUpdates() {
        pendingUpdates.clear();
    }

    /**
     * Returns a {@link Set} containing all entities of the specified type in this chunk. The cast type must match
     * the argued type or a {@link ClassCastException} will be thrown.
     *
     * @param type The type of entities to get.
     * @param <E> The type to cast to.
     * @return A set of entities cast to {@code <E>}. It must match the correct {@code type}.
     */
    public <E extends Entity> Set<E> getAll(EntityType type) {
        return (Set<E>) entities.get(type);
    }

    /**
     * Returns a stream over {@code type} entities in this chunk.
     *
     * @param type The entity type.
     * @param <E> The type.
     * @return The stream.
     */
    public <E extends Entity> Stream<E> stream(EntityType type) {
        return (Stream<E>) getAll(type).stream();
    }

    /**
     * Returns an iterator over {@code type} entities in this chunk.
     *
     * @param type The entity type.
     * @param <E> The type.
     * @return The iterator.
     */
    public <E extends Entity> Iterator<E> iterator(EntityType type) {
        return (Iterator<E>) getAll(type).iterator();
    }

    /**
     * @return The position.
     */
    public Chunk getChunk() {
        return chunk;
    }

    public void clear() {
        entities.clear();
    }

    public Map<EntityType, Set<Entity>> getAll() {
        return entities;
    }

    public CollisionMatrix[] getMatrices() {
        return matrices;
    }

    public boolean traversable(Position next, EntityType type, Direction direction) {
        CollisionMatrix matrix = matrices[next.getZ()];
        int x = next.getX(), y = next.getY();

        return !matrix.untraversable(x % SIZE, y % SIZE, type, direction);
    }
}