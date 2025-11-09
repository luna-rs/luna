package io.luna.game.model.mob.controller;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * A type of {@link PlayerController} that can only be registered and unregistered by {@link Player} movement.
 *
 * @author lare96
 */
public abstract class PlayerLocationController extends PlayerController {

    /**
     * A set of locatables tracked by this controller.
     */
    private ImmutableSet<Locatable> locatables;

    @Override
    public final void onRegister(Player player) {
        // Will never be called.
    }

    @Override
    public final void onUnregister(Player player) {
        // Will never be called.
    }

    /**
     * Called when the player is about to enter any of {@link #locatables}, and determines if the player can do so.
     *
     * @param player The player.
     * @param newPos The position the player is about to move to (inside this area).
     * @return {@code true} if the player can enter this controlled area.
     */
    public boolean canEnter(Player player, Position newPos) {
        return true;
    }

    /**
     * Called when the player is about to exit any of {@link #locatables}, and determines if the player can do so.
     *
     * @param player The player.
     * @param newPos The position the player is about to move to (outside of this area).
     * @return {@code true} if the player can leave this controlled area.
     */
    public boolean canExit(Player player, Position newPos) {
        return true;

    }

    /**
     * Determines if {@code position} is located inside any of {@link #locatables}.
     *
     * @param position The position to check.
     * @return {@code true} if {@code position} is located within this controller's bounds.
     */
    public final boolean inside(Position position) {
        for (Locatable locatable : getLocations()) {
            if (locatable.contains(position)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes a set of locatables that will be tracked by this controller, for caching.
     *
     * @return The computed set of locatables.
     */
    public abstract ImmutableSet<Locatable> computeLocations();

    /**
     * The cached set of locatables tracked by this controller.
     *
     * @return The cached set of locatables.
     */
    public ImmutableSet<Locatable> getLocations() {
        if (locatables == null) {
            locatables = computeLocations();
        }
        return locatables;
    }
}
