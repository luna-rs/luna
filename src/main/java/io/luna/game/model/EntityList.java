package io.luna.game.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mob.Player;

import java.util.Set;
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
     * The local entities, mapped to the player they're updating for.
     */
    protected final SetMultimap<Player, E> local = HashMultimap.create(64, 3);

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
    public abstract UnmodifiableIterator<E> iterator();

    /**
     * Forwards to {@link #register(StationaryEntity)} by default.
     */
    public boolean add(E entity) {
        return register(entity);
    }

    /**
     * Registers and shows {@code entity}.
     *
     * @param entity The entity to register.
     * @return {@code true} if {@code entity} was registered.
     */
    protected final boolean register(E entity) {
        if (entity.getType() == type && entity.getState() == EntityState.NEW &&
                entity.getPlayer().map(plr -> local.remove(plr, entity)).orElse(true)) {
            size++;
            entity.getPlayer().ifPresent(plr -> local.put(plr, entity));
            entity.setState(EntityState.ACTIVE);
            entity.show();
            return true;
        }
        
        return false;
    }

    /**
     * Forwards to {@link #unregister(StationaryEntity)} by default.
     */
    public boolean remove(E entity) {
        return unregister(entity);
    }

    /**
     * Unregisters and hides {@code entity}.
     *
     * @param entity The entity to unregister.
     * @return {@code true} if {@code entity} was unregistered.
     */
    protected final boolean unregister(E entity) {
        if (entity.getType() == type && entity.getState() == EntityState.ACTIVE &&
                entity.getPlayer().map(plr -> local.remove(plr, entity)).orElse(true)) {
            size--;
            entity.setState(EntityState.INACTIVE);
            entity.hide();
            return true;
        }
        
        return false;
    }

    /**
     * Unregisters and hides all local entities updating for {@code player}.
     *
     * @param player The player.
     */
    public final void removeLocal(Player player) {
        for (E entity : local.get(player)) {
            unregister(entity);
        }
    }

    /**
     * Retrieves all local entities updating for {@code player}.
     *
     * @param player The player.
     * @return The local entities.
     */
    public final Set<E> getLocal(Player player) {
        return local.get(player);
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
            if (position.equals(entity.position) && filter.test(entity) && unregister(entity)) {
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
    public final Set<E> getFromPosition(Position position) {
        var chunkManager = world.getChunks();
        return chunkManager.getChunk(position.getChunkPosition()).getAll(type);
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