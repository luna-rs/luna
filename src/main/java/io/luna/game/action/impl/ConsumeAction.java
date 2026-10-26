package io.luna.game.action.impl;

import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.mob.Player;

/**
 * Applies food or potion effects during input handling, before incoming hits and outgoing attacks.
 * Strong actions still suppress consumption, and the shared time source enforces the normal throttle.
 */
public abstract class ConsumeAction extends ThrottledAction<Player> {

    public ConsumeAction(Player player, TimeSource source, int delay) {
        super(player, source, delay);
    }

    @Override
    public void onSubmit() {
        if (mob.getActions().size(ActionType.STRONG) > 0) {
            interrupt();
        } else {
            try {
                if (run()) {
                    complete();
                }
            } catch (Exception e) {
                logger.error("An error occurred while consuming an item.", e);
                interrupt();
            }
        }
    }
}
