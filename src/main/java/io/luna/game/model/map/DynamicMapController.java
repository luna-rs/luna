package io.luna.game.model.map;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.PlayerController;

public abstract class DynamicMapController extends PlayerController {

    // TODO temporary? need to think more about what could be added here
    @Override
    public final void onRegister(Player player) {
        player.teleport(enter(player));
    }

    @Override
    public final void onUnregister(Player player) {
        player.teleport(exit(player));
    }

    public abstract Position enter(Player player);

    public abstract Position exit(Player player);
}
