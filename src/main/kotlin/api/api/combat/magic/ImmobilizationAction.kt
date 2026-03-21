package api.combat.magic

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player

/**
 * An action that represents a temporary immobilization effect on a [Mob].
 *
 * While this action is active, the affected mob is considered frozen and should be unable to move through normal
 * movement handling. The action itself does not perform per-tick logic and simply exists for the configured duration.
 *
 * If the affected mob is a [Player], a freeze message is sent when the action is first submitted.
 *
 * @param mob The mob being immobilized.
 * @param delay The duration of the immobilization effect in ticks.
 */
class ImmobilizationAction(mob: Mob, delay: Int) : Action<Mob>(mob, ActionType.SOFT, false, delay) {

    /**
     * Performs submission-time logic for the immobilization effect.
     *
     * If the affected mob is a [Player], a message is sent informing them that
     * they have been frozen.
     */
    override fun onSubmit() {
        if (mob is Player) {
            mob.sendMessage("You have been frozen!")
        }
    }

    /**
     * Completes immediately when processed.
     *
     * The immobilization effect is represented by the action's presence and delay, so no per-tick processing is
     * required here.
     *
     * @return `true`, always.
     */
    override fun run(): Boolean = true
}