package io.luna.game.model.mob.bot.movement;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.object.GameObject;

public class BotInteractionMovementAction extends BotMovementAction {

    private final GameObject obstacle;

    public BotInteractionMovementAction(GameObject obstacle) {
        this.obstacle = obstacle;
    }

    @Override
    public void execute(Bot bot) {
        // Open, climb, etc. etc.
        bot.getOutput().sendObjectInteraction(1, obstacle);
    }
}
