package game.combat.specialAttacks

import api.combat.player.VoidCombatAttack
import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.Direction
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SPEAR


val SHOVE_GRAPHIC = Graphic(253, 100, 0)
val STUNNED_GRAPHIC = Graphic(254, 100, 0)
val ANIMATION = 1064

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
        if(pushDir != Direction.NONE) {
            pushDir = attacker.lastDirection
        }
        // Cancel any pending queued movement for the victim.
        victim.navigator.cancel()
        victim.walking.clear()

        // Stun and move the victim.
        victim.navigator.step(pushDir)
        victim.lock(5) // todo status effects: stunned (equivalent to locked)
        null
    }
}