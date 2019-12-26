package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkPosition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobList;
import io.luna.game.plugin.PluginManager;
import io.luna.game.service.GameService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing anything that can be interacted with.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Entity {

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
    protected Position position;

    /**
     * The current chunk.
     */
    protected Chunk currentChunk;

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
    public abstract int size();

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
     * {@link EntityList#register(StationaryEntity)} and the respective removal functions instead.
     *
     * @param newState The new state.
     */
    public final void setState(EntityState newState) {
        // TODO Might need to be volatile/atomic "state"
        checkArgument(newState != EntityState.NEW, "Cannot set state to NEW.");
        checkArgument(newState != state, "State already equal to " + newState + ".");
        checkArgument(state != EntityState.INACTIVE, "INACTIVE state cannot be changed.");

        state = newState;
        switch (state) {
            case ACTIVE:
                checkState(position != null, this + " cannot be registered until its position is set.");

                setCurrentChunk();
                onActive();
                break;
            case INACTIVE:
                try {
                    onInactive();
                } finally {
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
    protected void onPositionChange(Position oldPos) {

    }

    /**
     * Sets the current position and performs chunk checking.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        if (!newPosition.equals(position)) {
            Position old = position;
            position = newPosition;

            if (state == EntityState.ACTIVE) {
                setCurrentChunk();
                onPositionChange(old);
            }
        }
    }

    /**
     * Sets the chunk depending on the current position.
     */
    private void setCurrentChunk() {
        ChunkPosition next = position.getChunkPosition();
        if (currentChunk == null) {
            // We have no current chunk.
            currentChunk = world.getChunks().load(next);
            currentChunk.add(this);
        } else if (!currentChunk.getPosition().equals(next)) {
            // We have a chunk, and it's not equal to the new one.
            currentChunk.remove(this);

            currentChunk = world.getChunks().load(next);
            currentChunk.add(this);
        }
    }

    /**
     * Removes this Entity from its current chunk.
     */
    private void removeCurrentChunk() {
        if (currentChunk != null) {
            currentChunk.remove(this);
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
    public final ChunkPosition getChunkPosition() {
        return position.getChunkPosition();
    }

    /**
     * @return The chunk manager.
     */
    public ChunkManager getChunks() {
        return world.getChunks();
    }

    /**
     * @return The current chunk.
     */
    public Chunk getCurrentChunk() {
        return currentChunk;
    }
}
