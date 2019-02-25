package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a group of {@link Entity}s within a chunk.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ChunkRepository {

    /**
     * The map of entities.
     */
    private final Map<EntityType, Set<Entity>> entities;

    /**
     * Protected constructor to restrict instantiation.
     */
    protected ChunkRepository() {
    }

    // TODO Rename this and test file
    {
        // Use enum map to split up entities by type.
        entities = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.ALL) {
            entities.put(type, new HashSet<>(4));
        }
    }

    /**
     * Adds an entity to this chunk.
     *
     * @param entity The entity to add.
     */
    public void add(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.add(entity), "Entity could not be added to chunk.");
    }

    /**
     * Removes an entity from this chunk.
     *
     * @param entity The entity to remove.
     */
    public void remove(Entity entity) {
        Set<Entity> entitySet = entities.get(entity.getType());
        checkState(entitySet.remove(entity), "Entity could not be removed from chunk.");
    }

    /**
     * Returns a {@link Set} containing all entities of the specified type in this chunk. The cast type must match
     * the argued type or a {@link ClassCastException} will be thrown.
     *
     * @param type The type of entities to get.
     * @param <E> The type to cast to. Must be a subclass of Entity.
     * @return A set of entities casted to {@code <E>}. As long as {@code <E>} matches {@code type}, no errors will
     * be thrown.
     */
    public <E extends Entity> Set<E> setOf(EntityType type) {
        return (Set<E>) entities.get(type);
    }
}