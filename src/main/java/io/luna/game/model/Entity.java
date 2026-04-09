package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.attr.Attributable;
import io.luna.game.model.mob.attr.AttributeMap;
import io.luna.game.plugin.PluginManager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Base type for anything that can exist in the world model and/or be represented to the client.
 * <p>
 * {@link Entity} manages:
 * <ul>
 *   <li>World position and location helpers ({@link Locatable}).</li>
 *   <li>State transitions ({@link EntityState}) and registration/unregistration hooks.</li>
 *   <li>Chunk membership (via {@link ChunkRepository}) for view / update routing.</li>
 *   <li>Lazy per-entity attributes via {@link AttributeMap}.</li>
 * </ul>
 *
 * <h3>State model</h3>
 * <ul>
 *   <li>{@link EntityState#NEW}: freshly constructed, not yet registered in world/chunks.</li>
 *   <li>{@link EntityState#ACTIVE}: registered; chunk/collision tracking is active.</li>
 *   <li>{@link EntityState#INACTIVE}: unregistered; cannot transition to another state.</li>
 * </ul>
 * <p>
 * <b>Important:</b> Do not call {@link #setState(EntityState)} directly unless you are working inside the internal
 * registration API. Prefer world lists/managers (e.g., {@link MobList#add(Mob)} and the corresponding removal methods)
 * so invariants are respected.
 *
 * <h3>Equality</h3>
 * Implementations must define stable {@link #equals(Object)} and {@link #hashCode()} semantics appropriate for their
 * identity (commonly an index for mobs, or identity for ephemeral types).
 *
 * @author lare96
 */
public abstract class Entity implements Attributable, Locatable {

    /**
     * The context instance this entity belongs to.
     */
    protected final LunaContext context;

    /**
     * Convenience reference to the plugin manager.
     */
    protected final PluginManager plugins;

    /**
     * Convenience reference to the game service.
     */
    protected final GameService service;

    /**
     * Convenience reference to the world instance.
     */
    protected final World world;

    /**
     * The entity classification (player, npc, object, etc.).
     */
    protected final EntityType type;

    /**
     * Current lifecycle state.
     */
    protected EntityState state = EntityState.NEW;

    /**
     * Current position in the world.
     */
    protected volatile Position position;

    /**
     * The current chunk repository this entity is registered in while {@link EntityState#ACTIVE}.
     */
    protected volatile ChunkRepository chunkRepository;

    /**
     * Lazily-created attribute storage.
     */
    private AttributeMap attributes;

    /**
     * Creates a new entity with an initial position.
     *
     * @param context The context instance.
     * @param position The initial position.
     * @param type The entity type.
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
     * Creates a new entity without assigning a position.
     * <p>
     * Implementations must ensure a valid position is set before registration (i.e., before transitioning to
     * {@link EntityState#ACTIVE}).
     *
     * @param context The context instance.
     * @param type The entity type.
     */
    public Entity(LunaContext context, EntityType type) {
        this.context = context;
        this.type = type;

        plugins = context.getPlugins();
        service = context.getGame();
        world = context.getWorld();
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

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
    public final Position abs() {
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
     * @return Total tile area occupied by this entity ({@code sizeX() * sizeY()}).
     */
    public int size() {
        return sizeX() * sizeY();
    }

    /**
     * @return Width in tiles.
     */
    public abstract int sizeX();

    /**
     * @return Height in tiles.
     */
    public abstract int sizeY();

    /**
     * Hook invoked when transitioning into {@link EntityState#ACTIVE}.
     * <p>
     * Override to initialize state that requires the entity to be registered (e.g., starting updates).
     */
    protected void onActive() {
    }

    /**
     * Hook invoked when transitioning into {@link EntityState#INACTIVE}.
     * <p>
     * Override to release resources/state tied to being registered.
     */
    protected void onInactive() {
    }

    /**
     * Transitions this entity to a new lifecycle state and runs the appropriate hooks.
     * <p>
     * Registration rules:
     * <ul>
     *   <li>Cannot transition to {@link EntityState#NEW}.</li>
     *   <li>Cannot transition to the same state.</li>
     *   <li>{@link EntityState#INACTIVE} is terminal.</li>
     * </ul>
     * <p>
     * When becoming {@link EntityState#ACTIVE}, this ensures a valid position, assigns chunk membership, triggers
     * {@link #onActive()}, and updates collision tracking. When becoming {@link EntityState#INACTIVE}, it triggers
     * {@link #onInactive()}, updates collision tracking, and removes chunk membership.
     * <p>
     * <b>Note:</b> Movement restriction checks for players are enforced here and in {@link #setPosition(Position)}.
     *
     * @param newState The new lifecycle state.
     */
    public final void setState(EntityState newState) {
        checkArgument(newState != EntityState.NEW, "Cannot set state to NEW.");
        checkArgument(newState != state, "State already equal to " + newState + ".");
        checkArgument(state != EntityState.INACTIVE, "INACTIVE state cannot be changed.");

        state = newState;
        switch (state) {
            case ACTIVE:
                checkState(position != null, this + " cannot be registered until its position is set.");

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
     * Updates this entity's position and performs chunk/region checks when applicable.
     * <p>
     * When {@link EntityState#ACTIVE}:
     * <ul>
     *   <li>Player movement is validated by controllers before accepting the new position.</li>
     *   <li>If a {@link Player} changes {@link Region}, a {@link RegionChangedEvent} is posted.</li>
     *   <li>Chunk membership is updated if the entity crosses a chunk boundary.</li>
     * </ul>
     * <p>
     * {@link LocalEntity} instances are excluded from chunk tracking here since they are not part of the persistent
     * world model and typically only emit chunk updates.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        boolean isLocal = this instanceof LocalEntity;
        if (!newPosition.equals(position) && !isLocal) {
            Region old = position == null ? null : position.getRegion();
            position = newPosition;
            if (state == EntityState.ACTIVE) {
                if (type == EntityType.PLAYER) {
                    Player player = (Player) this;
                    player.getControllers().checkPosition();
                    player.getTolerance().refresh();
                }
                if (old != null) {
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
     * Updates chunk membership to match {@link #position}.
     * <p>
     * If the entity has no current chunk repository, one is loaded and the entity is added. If it moves to a different
     * chunk, it is removed from the old repository and added to the new one.
     */
    private void setCurrentChunk() {
        Chunk nextChunk = position.getChunk();
        if (chunkRepository == null) {
            chunkRepository = world.getChunks().load(nextChunk);
            chunkRepository.add(this);
        } else if (!chunkRepository.getChunk().equals(nextChunk)) {
            chunkRepository.remove(this);

            chunkRepository = world.getChunks().load(nextChunk);
            chunkRepository.add(this);
        }
    }

    /**
     * Removes this entity from its current chunk repository (if any).
     */
    private void removeCurrentChunk() {
        if (chunkRepository != null) {
            chunkRepository.remove(this);
        }
    }

    /**
     * Returns this entity's attribute map, creating it lazily on first access.
     * <p>
     * Lazy allocation avoids per-entity memory overhead when attributes are unused.
     */
    public final AttributeMap getAttributes() {
        if (attributes == null) {
            attributes = new AttributeMap();
        }
        return attributes;
    }

    /**
     * Sets the attribute map (primarily for loading/restoring entity state).
     */
    public void setAttributes(AttributeMap attributes) {
        this.attributes = attributes;
    }

    /**
     * @return {@code true} if this entity currently has at least one attribute stored.
     */
    public boolean hasAttributes() {
        return attributes != null && attributes.size() > 0;
    }
    /**
     * @return The owning Luna context.
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
     * @return The entity type.
     */
    public EntityType getType() {
        return type;
    }

    /**
     * @return The entity state.
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
     * @return The current chunk (requires that this entity is registered/active).
     */
    public final Chunk getChunk() {
        return chunkRepository.getChunk();
    }

    /**
     * @return The current chunk repository, or {@code null} if not registered.
     */
    public ChunkRepository getChunkRepository() {
        return chunkRepository;
    }
}
