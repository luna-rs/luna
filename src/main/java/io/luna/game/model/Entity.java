package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.impl.PositionChangeEvent;
import io.luna.game.model.region.Region;
import io.luna.game.model.region.RegionCoordinates;
import io.luna.game.plugin.PluginManager;

import static com.google.common.base.Preconditions.checkArgument;

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
     * The current region.
     */
    protected Region currentRegion;

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
        service = context.getService();
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
    public boolean isViewable(Entity other) {
        return position.isViewable(other.position);
    }

    /**
     * Returns the distance between this entity and {@code other}.
     *
     * @param other The entity to compare.
     * @return The distance from {@code other}, in tiles.
     */
    public int distanceFrom(Entity other) {
        return position.getDistance(other.position);
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
     * Sets the current state and invokes the corresponding state function.
     *
     * @param newState The new state.
     */
    public final void setState(EntityState newState) {
        checkArgument(newState != EntityState.NEW, "cannot set to NEW");
        checkArgument(newState != state, "state already equal to " + newState);

        state = newState;
        switch (state) {
            case ACTIVE:
                onActive();
                break;
            case INACTIVE:
                onInactive();
                if (currentRegion != null) {
                    currentRegion.remove(this);
                }
                break;
        }
    }

    /**
     * Sets the current position and performs region checking.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        if (!newPosition.equals(position)) {
            RegionCoordinates next = newPosition.getRegionCoordinates();
            if (position != null) {
                if (currentRegion.getCoordinates().equals(next)) {
                    plugins.post(new PositionChangeEvent(this, position, newPosition));
                    position = newPosition;
                    return;
                }
                currentRegion.remove(this);
            }
            currentRegion = world.getRegions().getRegion(next);
            currentRegion.add(this);

            plugins.post(new PositionChangeEvent(this, position, newPosition));
            position = newPosition;
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
     * @return The current region.
     */
    public Region getCurrentRegion() {
        return currentRegion;
    }
}
