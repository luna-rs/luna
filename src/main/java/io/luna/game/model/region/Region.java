package io.luna.game.model.region;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A location on the map that is {@code 32x32} in size. Used primarily for caching various types of {@link Entity}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Region {

    /**
     * The {@link RegionCoordinates} this class was constructed with.
     */
    private final RegionCoordinates coordinates;

    /**
     * A {@link Set} of active {@link Entity}s in this {@code Region}.
     */
    private final Queue<Entity> entities = new ConcurrentLinkedQueue<>();

    /**
     * Creates a new {@link Region}.
     *
     * @param coordinates The {@link RegionCoordinates} to construct this class with.
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
     * Adds an {@link Entity} to the backing queue.
     *
     * @param e The entity to add.
     */
    public void addEntity(Entity e) {
        entities.add(e);
    }

    /**
     * Removes an {@link Entity} from the backing queue.
     *
     * @param e The entity to remove.
     */
    public void removeEntity(Entity e) {
        entities.remove(e);
    }

    /**
     * Retrieves and returns an {@link ArrayList} of {@link Entity}s that correspond to the given {@code types}. The {@link
     * EntityType}s given must be in accordance with the type of list returned or a {@link ClassCastException} will be
     * thrown.
     *
     * @param types The types to include in the returned list.
     * @return The list with the types.
     */
    @SuppressWarnings("unchecked")
    public <E extends Entity> List<E> getEntities(EntityType... types) {
        Set<EntityType> typeFilter = EnumSet.copyOf(Arrays.asList(types));
        List<E> filtered = new ArrayList<>();
        entities.stream().filter(it -> typeFilter.contains(it.type())).forEach(it -> filtered.add((E) it));
        return filtered;
    }

    /**
     * @return A shallow, immutable copy of the {@link Entity}s in this region.
     */
    public ImmutableList<Entity> toList() {
        return ImmutableList.copyOf(entities);
    }

    /**
     * @return A shallow, mutable copy of the {@link Entity}s in this region.
     */
    public Entity[] toArray() {
        return Iterables.toArray(entities, Entity.class);
    }

    /**
     * @return The {@link RegionCoordinates} this class was constructed with.
     */
    public RegionCoordinates getCoordinates() {
        return coordinates;
    }
}
