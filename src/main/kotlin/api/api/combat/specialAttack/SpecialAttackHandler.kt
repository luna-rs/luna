package api.combat.specialAttack

import api.combat.specialAttack.dsl.SpecialAttackReceiver
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.SpecialAttackType

object SpecialAttackHandler {

    private val specialAttacks = HashMap<SpecialAttackType, SpecialAttackReceiver>()

    fun handleActivated(plr: Player): Boolean {
        val type = plr.combat.weapon.specialAttackType
        val receiver = specialAttacks[type] ?: return false

        val specialBar = plr.combat.specialBar
        if (specialBar.energy < receiver.drain) {
            // We don't have enough energy.
            specialBar.sendEnergyWarning()
            return false
        }

        if (!receiver.activationOnly) {
            // This weapon has an actual special attack.
            if(receiver.instant) {
                // Run the attack instantly if we're in combat, can run on the same tick as another attack. does not
                // reset the attack delay!
                // we need to do combat checks just like if we were in the main loop! only drain special energy and
                // deselect if the attack went through. Attack will only go through if the interaction policy is good.
                // selected primary attack always takes prescendence.
                // we need two combat attacks, one instant, one that acts as normal like now!
                // if it can't be ran right away, queue it for the next attack like a normal special? but with no delay?
                return true // todo combat checks..
            } else {
                // Queue the attack to be run during the combat loop like normal.
                return true
            }
        } else {
            // Non-combat special attacks activate after a small delay.
            specialBar.isLocked = true
            plr.actions.submitIfAbsent(SpecialActivationAction(plr, receiver))
            return true
        }
    }

    // special and activate serve different functions, can only use one, they overwrite each other
    fun special(type: SpecialAttackType, drain: Int, action: SpecialAttackReceiver.() -> Unit) {
        val receiver = SpecialAttackReceiver(drain, false)
        action(receiver)
        specialAttacks[type] = receiver
    }

    fun activate(type: SpecialAttackType, drain: Int, action: Player.() -> Unit) {
        // Hide special attack modulators from activation specials.
        val receiver = SpecialAttackReceiver(drain, true)
        receiver.attack { action(mob); null }
        specialAttacks[type] = receiver
    }
}