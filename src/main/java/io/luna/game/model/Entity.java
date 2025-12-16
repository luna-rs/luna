package io.luna.game.model;

import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.Position.PositionDistanceComparator;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.attr.Attributable;
import io.luna.game.model.mob.attr.AttributeMap;
import io.luna.game.plugin.PluginManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing anything that can be interacted with (or be displayed) in the game world.
 *
 * @author lare96
 */
public abstract class Entity implements Attributable, Locatable {

    /**
     * A {@link Comparator} that sorts {@link Entity} types by closest to furthest distance from the base entity.
     */
    public static final class EntityDistanceComparator implements Comparator<Entity> {

        /**
         * The position distance comparator.
         */
        private final PositionDistanceComparator comparator;

        /**
         * Creates a new {@link EntityDistanceComparator}.
         *
         * @param base The base entity.
         */
        public EntityDistanceComparator(Entity base) {
            comparator = new PositionDistanceComparator(base.position);
        }

        @Override
        public int compare(Entity o1, Entity o2) {
            return comparator.compare(o1.position, o2.position);
        }
    }

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The context instance.
     */
    protected final LunaContext context;

    /**
     * The plugin manager.
     */
    protected final PluginManager plugins;

    /**
     * The game service.
     */
    protected final GameService service;

    /**
     * The world.
     */
    protected final World world;

    /**
     * The type.
     */
    protected final EntityType type;

    /**
     * The current state.
     */
    protected EntityState state = EntityState.NEW;

    /**
     * The current position.
     */
    protected volatile Position position;

    /**
     * The current chunk.
     */
    protected volatile ChunkRepository chunkRepository;

    /**
     * The attribute map.
     */
    private AttributeMap attributes;

    /**
     * Creates a new {@link Entity}.
     *
     * @param context The context instance.
     * @param position The current position.
     * @param type The type.
     */
    public Entity(LunaContext context, Position position, EntityType type) {
        this.context = context;
        this.position = position;
        this.type = type;

        plugins = context.getPlugins();
        service = context.getGame();
        world = context.getWorld();
    }

    /**
     * Creates a new {@link Entity}.
     *
     * @param context The context instance.
     * @param type The type.
     */
    public Entity(LunaContext context, EntityType type) {
        this.context = context;
        this.type = type;

        plugins = context.getPlugins();
        service = context.getGame();
        world = context.getWorld();
    }

    /**
     * Forward to implementing classes.
     */
    @Override
    public abstract int hashCode();

    /**
     * Forward to implementing classes.
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Forward to implementing classes.
     */
    @Override
    public abstract String toString();

    @Override
    public final AttributeMap attributes() {
        return getAttributes();
    }

    @Override
    public final boolean contains(Position other) {
        return position.equals(other);
    }

    @Override
    public final Position absLocation() {
        return position;
    }

    @Override
    public final int getX() {
        return position.getX();
    }

    @Override
    public final int getY() {
        return position.getY();
    }

    /**
     * Returns this entity's size.
     *
     * @return The size.
     */
    public int size() {
        return sizeX() * sizeY();
    }

    /**
     * Returns this entity's size.
     *
     * @return The size.
     */
    public abstract int sizeX();

    /**
     * Returns this entity's size.
     *
     * @return The size.
     */
    public abstract int sizeY();

    /**
     * Determines if {@code other} is viewable from this entity.
     *
     * @param other The entity to compare.
     * @return {@code true} if {@code other} is viewable.
     */
    public boolean isViewableFrom(Entity other) {
        return position.isViewable(other.position);
    }

    /**
     * Returns the longest distance between this entity and {@code other}.
     *
     * @param other The entity to compare.
     * @return The longest distance from {@code other}, in tiles.
     */
    public int computeLongestDistance(Entity other) {
        return position.computeLongestDistance(other.position);
    }

    /**
     * Invoked when entering an {@code ACTIVE} state.
     */
    protected void onActive() {
    }

    /**
     * Invoked when entering an {@code INACTIVE} state.
     */
    protected void onInactive() {
    }

    /**
     * Sets the current state and invokes the corresponding state function. <strong>Warning:</strong> Do not call
     * this directly unless you're familiar with the internal API. Use {@link MobList#add(Mob)}/
     * {@link StationaryEntityList#register(StationaryEntity)} and the respective removal functions instead.
     *
     * @param newState The new state.
     */
    public final void setState(EntityState newState) {
        checkArgument(newState != EntityState.NEW, "Cannot set state to NEW.");
        checkArgument(newState != state, "State already equal to " + newState + ".");
        checkArgument(state != EntityState.INACTIVE, "INACTIVE state cannot be changed.");

        state = newState;
        switch (state) {
            case ACTIVE:
                checkState(position != null, this + " cannot be registered until its position is set.");

                if (type == EntityType.PLAYER) {
                    Player player = (Player) this;
                    if (!player.getControllers().checkMovement(position)) {
                        position = Luna.settings().game().startingPosition();
                        player.sendMessage("You have been teleported home because you logged out in an invalid area.");
                        logger.warn("Player {} logged out in unexpected area! Teleporting them back home.", player.getUsername());
                    }
                }

                setCurrentChunk();
                onActive();
                world.getCollisionManager().updateEntity(this, false);
                break;
            case INACTIVE:
                try {
                    onInactive();
                } finally {
                    world.getCollisionManager().updateEntity(this, true);
                    removeCurrentChunk();
                }
                break;
        }
    }

    /**
     * Determines if this entity is within {@code radius} to {@code other}.
     *
     * @param other The other entity.
     * @param radius The radius.
     * @return {@code true} if the entity is within range.
     */
    public boolean isWithinDistance(Entity other, int radius) {
        return isWithinDistance(other.position, radius);
    }

    /**
     * Determines if this entity is within {@code radius} to {@code other}.
     *
     * @param other The other position.
     * @param radius The radius.
     * @return {@code true} if the entity is within range.
     */
    public boolean isWithinDistance(Position other, int radius) {
        return position.isWithinDistance(other, radius);
    }

    /**
     * Sets the current position and performs chunk checking.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        boolean isLocal = this instanceof LocalEntity;
        if (!newPosition.equals(position) && !isLocal) {
            if(state == EntityState.ACTIVE) {
                if (type == EntityType.PLAYER) {
                    Player player = (Player) this;
                    if (!player.getControllers().checkMovement(newPosition)) {
                        return;
                    }
                }
            }
            Region old = position == null ? null : position.getRegion();
            position = newPosition;
            if (state == EntityState.ACTIVE) {
                if(old != null) {
                    Region now = newPosition.getRegion();
                    if (!old.equals(now) && this instanceof Player) {
                        plugins.post(new RegionChangedEvent((Player) this, old, now));
                    }
                }
                setCurrentChunk();
            }
        }
    }

    /**
     * Sets the chunk depending on the current position.
     */
    private void setCurrentChunk() {
        Chunk nextChunk = position.getChunk();
        if (chunkRepository == null) {
            // We have no current chunk.
            chunkRepository = world.getChunks().load(nextChunk);
            chunkRepository.add(this);
        } else if (!chunkRepository.getChunk().equals(nextChunk)) {
            // We have a chunk, and it's not equal to the new one.
            chunkRepository.remove(this);

            chunkRepository = world.getChunks().load(nextChunk);
            chunkRepository.add(this);
        }
    }

    /**
     * Removes this Entity from its current chunk.
     */
    private void removeCurrentChunk() {
        if (chunkRepository != null) {
            chunkRepository.remove(this);
        }
    }

    /**
     * @return The attribute map.
     */
    public final AttributeMap getAttributes() {
        if (attributes == null) {
            // Lazy initialization is necessary, otherwise way too much memory will be used.
            attributes = new AttributeMap();
        }
        return attributes;
    }

    /**
     * Sets the attribute map.
     */
    public void setAttributes(AttributeMap attributes) {
        this.attributes = attributes;
    }

    /**
     * Determines if this entity has attributes.
     */
    public boolean hasAttributes() {
        return attributes != null && attributes.size() > 0;
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The plugin manager.
     */
    public final PluginManager getPlugins() {
        return plugins;
    }

    /**
     * @return The game service.
     */
    public final GameService getService() {
        return service;
    }

    /**
     * @return The world.
     */
    public final World getWorld() {
        return world;
    }

    /**
     * @return The type.
     */
    public EntityType getType() {
        return type;
    }

    /**
     * @return The current state.
     */
    public final EntityState getState() {
        return state;
    }

    /**
     * @return The current position.
     */
    public final Position getPosition() {
        return position;
    }

    /**
     * @return A new instance of the chunk position.
     */
    public final Chunk getChunk() {
        return chunkRepository.getChunk();
    }

    /**
     * @return The current chunk.
     */
    public ChunkRepository getChunkRepository() {
        return chunkRepository;
    }
}
