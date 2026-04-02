package io.luna.game.model.mob.controller;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * A listener that responds to a {@link Player} entering, leaving, or remaining within one or more tracked areas.
 * <p>
 * Each listener owns an immutable set of {@link Locatable} regions that define its active bounds. The controller
 * system uses these bounds to determine when {@link #enter(Player)} and {@link #exit(Player)} should be invoked.
 * <p>
 * Subclasses may also override {@link #process(Player)} to perform logic while the player remains inside the
 * listener's tracked area.
 *
 * @author lare96
 */
public abstract class PlayerAreaListener {

    /**
     * The immutable set of locatable regions that define this listener's bounds.
     */
    private final ImmutableSet<Locatable> locatables;

    /**
     * Creates a new {@link PlayerAreaListener} and computes its tracked bounds.
     */
    protected PlayerAreaListener() {
        locatables = computeLocatables();
    }

    /**
     * Invoked when {@code player} enters this listener's tracked area.
     *
     * @param player The player entering the area.
     */
    public abstract void enter(Player player);

    /**
     * Invoked when {@code player} exits this listener's tracked area.
     *
     * @param player The player leaving the area.
     */
    public abstract void exit(Player player);

    /**
     * Computes the immutable set of {@link Locatable} regions monitored by this listener.
     *
     * @return The area bounds for this listener.
     */
    public abstract ImmutableSet<Locatable> computeLocatables();

    /**
     * Processes this listener for {@code player} while the player is active within its tracked area.
     * <p>
     * The default implementation does nothing.
     *
     * @param player The player being processed.
     */
    public void process(Player player) {

    }

    /**
     * Checks whether {@code position} is contained within any of this listener's tracked {@link Locatable} bounds.
     *
     * @param position The position to test.
     * @return {@code true} if the position is inside any tracked bound, otherwise {@code false}.
     */
    public final boolean inside(Position position) {
        for (Locatable locatable : locatables) {
            if (locatable.contains(position)) {
                return true;
            }
        }
        return false;
    }
}