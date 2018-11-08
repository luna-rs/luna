package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.impl.PositionChangeEvent;
import io.luna.game.model.region.Region;
import io.luna.game.model.region.RegionCoordinates;
import io.luna.game.plugin.PluginManager;

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
        checkArgument(newState != EntityState.NEW, "Cannot set state to NEW.");
        checkArgument(newState != state, "State already equal to " + newState + ".");
        checkArgument(state != EntityState.INACTIVE, "INACTIVE state cannot be changed.");

        state = newState;
        switch (state) {
            case ACTIVE:
                checkState(position != null, "ACTIVE entity must have position set.");

                setCurrentRegion();
                onActive();
                break;
            case INACTIVE:
                onInactive();
                removeCurrentRegion();
                break;
        }
    }

    /**
     * Sets the current position and performs region checking.
     *
     * @param newPosition The new position.
     */
    public final void setPosition(Position newPosition) {
        checkState(state != EntityState.INACTIVE, "Cannot set position for INACTIVE entity.");
        if (!newPosition.equals(position)) {
            Position old = position;
            position = newPosition;
            plugins.post(new PositionChangeEvent(this, old, newPosition));

            if (state == EntityState.ACTIVE) {
                setCurrentRegion();
            }
        }
    }

    /**
     * Sets the current region depending on the current position.
     */
    private void setCurrentRegion() {
        RegionCoordinates next = position.getRegionCoordinates();
        if (currentRegion == null) {
            // We have no current region.
            currentRegion = world.getRegions().getRegion(next);
            currentRegion.add(this);
        } else if (!currentRegion.getCoordinates().equals(next)) {
            // We have a region, and it's not equal to the new one.
            currentRegion.remove(this);

            currentRegion = world.getRegions().getRegion(next);
            currentRegion.add(this);
        }
    }

    /**
     * Removes this Entity from its current region.
     */
    private void removeCurrentRegion() {
        if (currentRegion != null) {
            currentRegion.remove(this);
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
