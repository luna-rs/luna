package api.combat.specialAttack

import api.combat.specialAttack.dsl.SpecialAttackDataReceiver
import api.combat.specialAttack.dsl.SpecialAttackLaunchedReceiver
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/**
 * Action that executes a queued special attack activation for a player.
 *
 * This action unlocks the player's special attack bar and drains the configured amount of special attack energy. The
 * receiver's launch callback runs only if the drain succeeds and the same weapon special is still wielded; otherwise
 * the bar is switched off.
 *
 * @param plr The player performing the special attack.
 * @param receiver The receiver containing the special attack activation logic and drain amount.
 * @author lare96
 */
class SpecialActivationAction(plr: Player, private val receiver: SpecialAttackDataReceiver) :
    Action<Player>(plr, ActionType.SOFT, false, 2) {

    private val type = plr.combat.weapon.specialAttackType

    override fun run(): Boolean {
        val specialBar = mob.combat.specialBar
        specialBar.isLocked = false
        // The weapon or the energy can change during the delay.
        if (mob.combat.weapon.specialAttackType == type && specialBar.drain(receiver.drain, true)) {
            receiver.launchedTransformer(SpecialAttackLaunchedReceiver(mob, mob, null, null))
        } else {
            specialBar.forceOff()
        }
        return true
    }
}