package io.luna.game.model.region;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A model representing a location on the map {@code 32x32} in size.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Region {

    /**
     * The coordinates.
     */
    private final RegionCoordinates coordinates;

    /**
     * A set of entities within this region.
     */
    private final Set<Entity> entities = Sets.newConcurrentHashSet();

    /**
     * Creates a new {@link Region}.
     *
     * @param coordinates The coordinates.
     */
    Region(RegionCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Region) {
            Region region = (Region) obj;
            return Objects.equals(coordinates, region.coordinates);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinates);
    }

    /**
     * Adds an entity to this region.
     */
    public void addEntity(Entity e) {
        entities.add(e);
    }

    /**
     * Removes an entity from this region.
     */
    public void removeEntity(Entity e) {
        entities.remove(e);
    }

    /**
     * Retrieves a list of entities of the argued type.
     */
    @SuppressWarnings("unchecked")
    public <E extends Entity> List<E> getEntities(EntityType type) {
        List<E> filtered = new ArrayList<>();
        entities.stream().filter(it -> it.getType() == type).forEach(it -> filtered.add((E) it));
        return filtered;
    }

    /**
     * Returns a shallow and immutable copy of the backing set.
     */
    public ImmutableList<Entity> toList() {
        return ImmutableList.copyOf(entities);
    }

    /**
     * Returns a shallow copy of the backing set.
     */
    public Entity[] toArray() {
        return Iterables.toArray(entities, Entity.class);
    }

    /**
     * @return The coordinates.
     */
    public RegionCoordinates getCoordinates() {
        return coordinates;
    }
}
