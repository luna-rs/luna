package io.luna.game.model;

import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Player;
import io.luna.game.plugin.PluginManager;
import io.luna.game.service.GameService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing anything that can be interacted with (or be displayed) in the game world.
 *
 * @author lare96
 */
public abstract class Entity {

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
     * If this entity can be interacted with.
     */
    private boolean interactable = true;

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
                chunkRepository.updateCollisionMap(this, false);
                break;
            case INACTIVE:
                try {
                    onInactive();
                } finally {
                    chunkRepository.updateCollisionMap(this, true);
                    removeCurrentChunk();
                }
                break;
        }
    }

    /**
     * Invoked when this entity's position changes.
     *
     * @param oldPos The old position.
     */
    protected void onPositionChanged(Position oldPos) {

    }

    /**
     * Sets the current position and performs chunk checking.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        if (!newPosition.equals(position)) {
            if (type == EntityType.PLAYER && state == EntityState.ACTIVE) {
                Player player = (Player) this;
                if (!player.getControllers().checkMovement(newPosition)) {
                    return;
                }
            }

            Position old = position;
            position = newPosition;

            if (state == EntityState.ACTIVE) {
                setCurrentChunk();
                onPositionChanged(old);
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

    /**
     * Sets if this entity can be interacted with.
     *
     * @param interactable The new value.
     */
    public void setInteractable(boolean interactable) {
        this.interactable = interactable;
    }

    /**
     * @return If this entity can be interacted with.
     */
    public boolean isInteractable() {
        return interactable;
    }
}
