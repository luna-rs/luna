package api.combat.specialAttack

import api.combat.specialAttack.dsl.SpecialAttackReceiver
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/**
 * Action that executes a queued special attack activation for a player.
 *
 * This action invokes the receiver's activation callback, unlocks the player's special attack bar, and drains the
 * configured amount of special attack energy.
 *
 * @param plr The player performing the special attack.
 * @param receiver The receiver containing the special attack activation logic and drain amount.
 * @author lare96
 */
class SpecialActivationAction(plr: Player, private val receiver: SpecialAttackReceiver) :
    Action<Player>(plr, ActionType.SOFT, false, 2) {

    /**
     * Executes the special attack activation.
     *
     * The receiver activation callback is invoked first, then the player's
     * special attack bar is unlocked, and finally the configured special
     * attack energy is drained.
     *
     * @return `true` after the special attack has been processed.
     */
    override fun run(): Boolean {
        receiver.attack(mob.combat, mob)
        mob.combat.specialBar.isLocked = false
        mob.combat.specialBar.drain(receiver.drain!!, true)
        return true
    }
}