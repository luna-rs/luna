package api.combat.specialAttack

import api.combat.specialAttack.dsl.SpecialAttackDataReceiver
import api.combat.specialAttack.dsl.SpecialAttackLaunchedReceiver
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/**
 * Action that executes a queued special attack activation for a player.
 *
 * This action invokes the receiver's launch callback, unlocks the player's special attack bar, and drains the
 * configured amount of special attack energy.
 *
 * @param plr The player performing the special attack.
 * @param receiver The receiver containing the special attack activation logic and drain amount.
 * @author lare96
 */
class SpecialActivationAction(plr: Player, private val receiver: SpecialAttackDataReceiver) :
    Action<Player>(plr, ActionType.SOFT, false, 2) {

    override fun run(): Boolean {
        receiver.launchedTransformer(SpecialAttackLaunchedReceiver(mob, mob, null))
        mob.combat.specialBar.isLocked = false
        mob.combat.specialBar.drain(receiver.drain, true)
        return true
    }
}