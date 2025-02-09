package io.luna.game.model.mob.controller;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Location;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * A type of {@link PlayerController} that can only be registered and unregistered by {@link Player} movement.
 *
 * @author lare96
 */
public abstract class PlayerLocationController extends PlayerController {

    /**
     * A set of locations tracked by this controller.
     */
    private ImmutableSet<Location> locations;


    @Override
    public final void onRegister(Player player) {
        // Will never be called.
    }

    @Override
    public final void onUnregister(Player player) {
        // Will never be called.
    }

    /**
     * Called when the player is about to enter any of {@link #locations}, and determines if the player can do so.
     *
     * @param player The player.
     * @return {@code true} if the player can enter this controlled area.
     */
    public boolean canEnter(Player player) {
        return true;
    }

    /**
     * Called when the player is about to exit any of {@link #locations}, and determines if the player can do so.
     *
     * @param player The player.
     * @return {@code true} if the player can leave this controlled area.
     */
    public boolean canExit(Player player) {
        return true;

    }

    /**
     * Determines if the player is within any of {@link #locations}.
     *
     * @return {@code true} if the player is within this controlled area.
     */
    public final boolean inside(Player player) {
        Position position = player.getPosition();
        for (Location location : getLocations()) {
            if (location.contains(position)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes a set of locations that will be tracked by this controller, for caching.
     *
     * @return The computed set of locations.
     */
    protected abstract ImmutableSet<Location> computeLocations();

    /**
     * The cached set of locations tracked by this controller.
     *
     * @return The cached set of locations.
     */
    protected ImmutableSet<Location> getLocations() {
        if (locations == null) {
            locations = computeLocations();
        }
        return locations;
    }
}
