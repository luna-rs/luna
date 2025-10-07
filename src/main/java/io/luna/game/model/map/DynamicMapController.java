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
 */
public abstract class DynamicMapController extends PlayerController {
    // TODO finish, test, need to think more about what could be added here

    @Override
    public final void onRegister(Player player) {
        enter(player);
    }

    @Override
    public final void onUnregister(Player player) {
        player.move(exit(player));
    }

    @Override
    public final boolean canMove(Player player, Position newPos) {
        if(!player.getDynamicMap().getAssignedSpace().contains(newPos)) {
            System.out.println("player left dynamic map space!!");
            // todo debugging
        }
        return true;
    }

    public abstract void enter(Player player);

    public abstract Position exit(Player player);
}
