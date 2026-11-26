package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import game.content.templeTrekking.Juvinates
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.SpecialAttackType.ROD_OF_IVANDIS

/**
 * The Rod of Ivandis's special, Retainer: it holds a weakened vampyre juvinate still and deals no damage. A failed
 * attempt gives the energy back.
 */
attack(type = ROD_OF_IVANDIS, drain = 10) {
    launched {
        if (!Juvinates.retain(attacker, victim as? Npc)) attacker.combat.specialBar.restore(10)
        null
    }
}
