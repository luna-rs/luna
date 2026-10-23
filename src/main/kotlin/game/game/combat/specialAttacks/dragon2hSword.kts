package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import engine.controllers.Controllers.inMultiArea
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_2H_SWORD
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The maximum number of mobs a single special attack can hit, including the primary target.
 */
val MAX_TARGETS = 14

/**
 * The animation played when performing the Dragon 2h sword special attack.
 */
val ANIMATION = 3157

/**
 * The graphic displayed when the Dragon 2h sword special attack is launched.
 */
val GRAPHIC = Graphic(559, 0, 0)

/**
 * Finds the other mobs hit by the Dragon 2h sword special attack.
 *
 * Other mobs are only hit in multi-combat areas. They must be the same type of mob as [victim] (players or NPCs),
 * stand within one tile of [attacker], and be attackable.
 *
 * @param attacker The player performing the special attack.
 * @param victim The primary target of the special attack.
 * @return Up to [MAX_TARGETS] - 1 other mobs to hit.
 */
fun findOtherTargets(attacker: Player, victim: Mob): List<Mob> {
    if (!attacker.inMultiArea()) {
        return emptyList()
    }
    return world.locator.findViewable<Mob>(victim.type, attacker) {
        it != attacker && it != victim && it.isWithinDistance(attacker, 1) &&
                it.inMultiArea() && it.combat.isAttackable
    }.take(MAX_TARGETS - 1)
}

/**
 * Applies the Dragon 2h sword special attack's secondary hit to a nearby target.
 *
 * A melee damage roll is resolved immediately and submitted as a [CombatDamageAction].
 *
 * @param attacker The player performing the special attack.
 * @param victim The nearby mob being hit.
 * @param attack The parent combat attack associated with the special attack.
 */
fun sendHit(attacker: Player, victim: Mob, attack: CombatAttack<Player>?) {
    val otherDamage = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE).build().resolve()
    victim.submitAction(CombatDamageAction(otherDamage, attack, true))
}

attack(type = DRAGON_2H_SWORD,
       drain = 60) {

    // Override the primary melee animation.
    attack { melee(ANIMATION) }

    // Hit nearby mobs of the victim's type when applicable.
    launched {
        attacker.graphic(GRAPHIC)
        for (other in findOtherTargets(attacker, victim)) {
            sendHit(attacker, other, attack)
        }
        damage // Preserves the original primary hit result from the special attack DSL.
    }
}