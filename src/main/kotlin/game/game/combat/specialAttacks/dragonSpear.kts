package game.combat.specialAttacks

import api.combat.player.VoidCombatAttack
import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SPEAR
import io.luna.game.model.mob.combat.damage.CombatDamageRequest


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
        if (pushDir == Direction.NONE) {
            pushDir = attacker.lastDirection
        }
        // Cancel any pending queued movement for the victim.
        victim.navigator.cancel()
        victim.walking.clear()
        attacker.combat.target = null

        // Stun and move the victim.
        victim.navigator.step(pushDir)
        // todo status effects: stunned (equivalent to locked but can still be moved by server)
        // todo stunned should disable walking like immobiliaztion but also eating, equipping items, etc.
        // todo but stunned should still allow the walking queue to move the player
        // todo for now just  disable combat
        // todo stunned just a combination of disabled combat, immobilization (with no message prints), and restricted actions?
        victim.combat.isDisabled = true
        victim.submitAction(object : Action<Mob>(victim, ActionType.SOFT, false, 7) {
            override fun run(): Boolean {
                victim.combat.isDisabled = false
                return true
            }
        })
        CombatDamageRequest.effect(attacker, victim).resolve()// todo status effects: stunned (equivalent to locked)
    }
}