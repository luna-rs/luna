package game.combat.specialAttacks

import api.combat.player.VoidCombatAttack
import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.ext.*
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.Direction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SPEAR
import io.luna.game.model.mob.combat.damage.CombatDamageRequest


val SHOVE_GRAPHIC = Graphic(253, 100, 0)
val STUNNED_GRAPHIC = Graphic(254, 100, 0)
val ANIMATION = 1064

/**
 * Applies the shove's existing seven-tick duration through the shared stun status.
 * Repeated shoves refresh one timer instead of leaving older timers that can unlock combat early.
 * Expiration removes only this status and cannot clear independent combat restrictions such as Slayer execution.
 * The queued server-side shove remains available while stun checks block new player actions.
 *
 * @param victim The shoved mob.
 */
fun applyShoveStun(victim: Mob) {
    victim.interact(null)
    victim.status.add(BasicStatusEffect(victim, StatusEffectType.STUNNED, 7.ticks, refreshable = true))
}

attack(type = DRAGON_SPEAR,
       drain = 25) {

    attack {
        if (victim.size() > 1) {
            attacker.sendMessage("That creature is too large to knock back!")
            VoidCombatAttack(attacker, victim)
        } else {
            melee(ANIMATION)
        }
    }

    launched {
        attacker.graphic(SHOVE_GRAPHIC)
        victim.graphic(STUNNED_GRAPHIC)

        // Determine push direction.
        var pushDir = Direction.between(attacker.position, victim.position)
        if (pushDir == Direction.NONE) {
            pushDir = attacker.lastDirection
        }
        // Cancel any pending queued movement for the victim.
        victim.navigator.cancel()
        victim.walking.clear()
        attacker.combat.target = null

        // Stun and move the victim.
        victim.navigator.step(pushDir)
        applyShoveStun(victim)
        CombatDamageRequest.effect(attacker, victim).resolve()
    }
}
