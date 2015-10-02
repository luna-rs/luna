package io.luna.game.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.plugin.PluginManager;

/**
 * Something that exists in the Runescape world.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public abstract class Entity {

    /**
     * The {@link PluginManager} dedicated to this {@link Entity}.
     */
    protected final PluginManager plugins;

    /**
     * The {@link GameService} dedicated to this {@link Entity}.
     */
    protected final GameService service;

    /**
     * The {@link World} dedicated to this {@link Entity}.
     */
    protected final World world;

    /**
     * The state of this {@link Entity}.
     */
    private EntityState state = EntityState.IDLE;

    /**
     * The position of this {@link Entity}.
     */
    private Position position;

    /**
     * Creates a new {@link Entity}.
     *
     * @param context The context to be managed in.
     * @param position The position of this {@code Entity}.
     */
    public Entity(LunaContext context, Position position) {
        checkState(size() > 0, "size <= 0");
        checkState(type() != null, "type == null");

        this.position = requireNonNull(position);

        plugins = context.getPlugins();
        service = context.getService();
        world = context.getWorld();

        onIdle();
    }

    /**
     * Creates a new {@link Entity} at the default position.
     *
     * @param context The context to be managed in.
     */
    public Entity(LunaContext context) {
        this(context, EntityConstants.DEFAULT_POSITION);
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException(getExceptionMsg("hashCode"));
    }

    @Override
    public boolean equals(Object obj) {
        throw new UnsupportedOperationException(getExceptionMsg("equals"));
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException(getExceptionMsg("toString"));
    }

    /**
     * Gets the message for an {@link UnsupportedOperationException} with
     * {@code impl} as the method name.
     * 
     * @param impl The implementing method name.
     */
    private String getExceptionMsg(String impl) {
        return String.format("No '%s' for Entity, subclasses must implement.", impl);
    }

    /**
     * @return The size of this {@link Entity}, will never be below {@code 0}.
     */
    public abstract int size();

    /**
     * @return The {@link EntityType} designated for this {@link Entity}.
     */
    public abstract EntityType type();

    /**
     * Fired when the state of this {@link Entity} is set to {@code IDLE}.
     */
    public void onIdle() {}

    /**
     * Fired when the state of this {@link Entity} is set to {@code ACTIVE}.
     */
    public void onActive() {}

    /**
     * Fired when the state of this {@link Entity} is set to {@code INACTIVE}.
     */
    public void onInactive() {}

    /**
     * @return The current state that this {@link Entity} is in.
     */
    public final EntityState getState() {
        return state;
    }

    /**
     * Sets the value for {@link #state}. When a state is set, a corresponding
     * listener of either {@code onIdle()}, {@code onActive()}, or
     * {@code onInactive()} will be fired. If the value being set is equal to
     * the current value, an exception will be thrown.
     * 
     * @param state The state to set, cannot be {@code null} or {@code IDLE}.
     * @throws IllegalArgumentException If the value being set is equal to the
     *         current value.
     */
    public final void setState(EntityState state) {
        checkArgument(state != this.state, "This state has already been set!");
        checkArgument(state != EntityState.IDLE, "This state cannot be explicitly set!");

        this.state = requireNonNull(state);

        switch (state) {
        case IDLE:
            onIdle();
            break;
        case ACTIVE:
            onActive();
            break;
        case INACTIVE:
            onInactive();
            break;
        }
    }

    /**
     * @return The position of this {@link Entity}.
     */
    public final Position getPosition() {
        return position;
    }

    /**
     * Sets the value for {@link #position}, cannot be {@code null}.
     */
    public final void setPosition(Position position) {
        this.position = requireNonNull(position);
    }
}
