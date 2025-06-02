package io.luna.game.model.map;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.PlayerController;

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
        /*if(!player.getDynamicMap().getAssignedSpace().contains(newPos)) {
            System.out.println(newPos.getRegion());
            System.out.println(player.getDynamicMap().getAssignedSpace().getAllRegions());
            player.getWalking().clear();
            return false;
        }*/
        return true;
    }

    public abstract void enter(Player player);

    public abstract Position exit(Player player);
}
