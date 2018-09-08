package io.luna.game.model.region;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A model containing entities within a map region.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Region {

    // TODO Only create the concurrent sets as needed, to save memory.
    // TODO Might be better to declare four separate sets instead of trying to combine them in a map (RegionSet?)

    /**
     * The region coordinates.
     */
    private final RegionCoordinates coordinates;

    /**
     * A map of entities.
     */
    private final ImmutableMap<EntityType, Set<Entity>> entities;

    { // Initializes the repository by creating concurrent sets for each entity type.
        Map<EntityType, Set<Entity>> mutableEntities = new EnumMap<>(EntityType.class);

        for(EntityType type : EntityType.ALL) {
            mutableEntities.put(type, Sets.newConcurrentHashSet());
        }
        entities = Maps.immutableEnumMap(mutableEntities);
    }

    /**
     * Creates a new {@link Region}.
     *
     * @param coordinates The region coordinates.
     */
    Region(RegionCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("coordinates", coordinates).
                add("entities", entities).toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Region) {
            Region other = (Region) obj;
            return coordinates.equals(other.coordinates);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return coordinates.hashCode();
    }

    /**
     * Iterates through every {@link Entity} in this region and applies the consumer
     * on them.
     *
     * @param action The consumer to apply.
     */
    public void forEach(Consumer<? super Entity> action) {
        for (Set<Entity> entitySet : entities.values()) {
            entitySet.forEach(action);
        }
    }

    /**
     * Adds an entity to this repository.
     *
     * @param entity The entity to add.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean add(Entity entity) {
        return entities.get(entity.getType()).add(entity);
    }

    /**
     * Removes an entity from this repository.
     *
     * @param entity The entity to remove.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean remove(Entity entity) {
        return entities.get(entity.getType()).remove(entity);
    }

    /**
     * Determines if this repository contains an entity.
     *
     * @param entity The entity to determine for.
     * @return {@code true} if this repository contains the entity, {@code false} otherwise.
     */
    public boolean contains(Entity entity) {
        return entities.get(entity.getType()).contains(entity);
    }

    /**
     * Returns a {@link Set} containing all entities in this region of the specified type. The cast type
     * must match the argued type or a {@link ClassCastException} will be thrown.
     *
     * @param type The type of entities to get.
     * @param <E> The type to cast to. Must be a subclass of Entity.
     * @return A set of entities casted to {@code <E>}. As long as {@code <E>} matches {@code type}, no errors will
     * be thrown.
     */
    public <E extends Entity> Set<E> getAll(EntityType type) {
        //noinspection unchecked
        return (Set<E>) entities.get(type);
    }

    /**
     * @return The coordinates.
     */
    public RegionCoordinates getCoordinates() {
        return coordinates;
    }
}
