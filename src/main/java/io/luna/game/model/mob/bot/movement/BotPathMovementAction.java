package io.luna.game.model.mob.bot.movement;

import io.luna.game.model.mob.WalkingQueue.Step;
import io.luna.game.model.mob.bot.Bot;

import java.util.Deque;

public class BotPathMovementAction extends BotMovementAction {

    private final Deque<Step> path;
    public BotPathMovementAction(Deque<Step> path) {
        this.path = path;
    }

    @Override
    public void execute(Bot bot) {
        bot.getWalking().addPath(path);
    }
}
