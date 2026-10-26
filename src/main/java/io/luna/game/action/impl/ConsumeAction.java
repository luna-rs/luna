package io.luna.game.action.impl;

import io.luna.game.action.ActionType;
import io.luna.game.action.TimeSource;
import io.luna.game.model.mob.Player;

/**
 * A {@link ThrottledAction} that applies a player's food or potion effects when submitted.
 * <p>
 * Client input submits consumption before the world processes incoming hits and outgoing attacks. Completing
 * consumption during {@link #onSubmit()} makes healing and food delays visible to that tick's combat processing.
 * <p>
 * An unfinished {@link ActionType#STRONG} action prevents consumption without advancing the throttle. Otherwise,
 * the shared {@link TimeSource} determines whether {@link #execute()} may run. The action completes during
 * submission, including when the throttle rejects it, and is not retained for a second execution.
 *
 * @author lare96
 */
public abstract class ConsumeAction extends ThrottledAction<Player> {

    /**
     * Creates a new {@link ConsumeAction}.
     *
     * @param player The player consuming the item.
     * @param source The shared throttle for this consumption family.
     * @param delay The minimum number of game ticks between successful throttle checks.
     */
    public ConsumeAction(Player player, TimeSource source, int delay) {
        super(player, source, delay);
    }

    /**
     * Applies the inherited throttle and consumption behavior immediately, unless a strong action prevents it.
     * <p>
     * Failures interrupt the action and are logged through the normal action logger.
     */
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
