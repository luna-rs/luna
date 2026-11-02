package io.luna.game.model.map;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.PlayerController;

/**
 * A controller specifically for managing player logic inside a {@link DynamicMap}.
 *
 * <p>Used to customize behaviors like entering or exiting the instance, or restricting movement. Automatically
 * registered when a player joins the instance.</p>
 *
 * <p>Override {@link #enter(Player)} and {@link #exit(Player)} to implement custom logic for each instance type.</p>
 *
 * @author lare96
 */
public abstract class DynamicMapController extends PlayerController {
    /**
     * Creates a new {@link PlayerController}.
     *
     * @param player The player this controller controls.
     */
    public DynamicMapController(Player player) {
        super(player);
    }
    /**
     * The map this per-player controller belongs to after registration.
     */
    private DynamicMap map;

    /**
     * Binds a new controller to its map and owner before it becomes active.
     *
     * @param map The owning instance.
     * @param player The entering player.
     */
    final void attach(DynamicMap map, Player player) {
        com.google.common.base.Preconditions.checkState(this.map == null, "Use a fresh controller for each entry.");
        com.google.common.base.Preconditions.checkArgument(getPlayer() == player, "Controller belongs to another player.");
        this.map = map;
    }

    @Override
    public final void register() {
        enter(getPlayer());
    }

    @Override
    public final void unregister() {
        if (getPlayer().getDynamicMap() == map) map.remove(getPlayer());
    }

    @Override
    public void move() {
        if (getPlayer().getDynamicMap() == map && !map.contains(getPlayer().getPosition())) map.remove(getPlayer());
    }

    /**
     * Initializes activity state after the player is placed inside the instance.
     *
     * @param player The entering player.
     */
    public abstract void enter(Player player);

    /**
     * Cleans up activity state and chooses a real-world destination when the player leaves, logs out,
     * changes controller, or the instance is deleted. The destination must be outside the reserved space.
     *
     * @param player The departing player.
     * @return The real-world exit destination.
     */
    public abstract Position exit(Player player);
    /**
     * Supplies a real-world login destination for saves made inside this instance. This is a read-only
     * query: it must not move the player, exit the activity, or change membership.
     *
     * @param player The player whose snapshot is being saved.
     * @return A valid destination outside the reserved instance space.
     */
    public abstract Position recovery(Player player);

}
