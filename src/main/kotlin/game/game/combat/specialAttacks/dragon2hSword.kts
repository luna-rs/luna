package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_2H_SWORD
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The maximum number of nearby secondary targets hit per mob category.
 *
 * This limit is applied separately to local players and local NPCs.
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
 * Attempts to apply the Dragon 2h sword special attack's secondary hit to a nearby target.
 *
 * A hit is only applied if the supplied [victim] is within melee distance of [attacker]. When that condition is met,
 * a melee damage roll is resolved immediately and submitted as a [CombatDamageAction].
 *
 * @param attacker The player performing the special attack.
 * @param victim The nearby mob being checked for a secondary hit.
 * @param attack The parent combat attack associated with the special attack.
 * @return `true` if a secondary hit was submitted for the target, or `false` if the target was out of range.
 */
fun sendHit(attacker: Player, victim: Mob, attack: CombatAttack<Player>?): Boolean {
    if (victim.isWithinDistance(attacker, 1)) {
        val otherDamage = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE).build().resolve()
        victim.submitAction(CombatDamageAction(otherDamage, attack, true))
        return true
    }
    return false
}

attack(type = DRAGON_2H_SWORD,
       drain = 60) {

    // Override the primary melee animation.
    attack { melee(ANIMATION) }

    // Hit nearby players and NPCs when applicable.
    launched {
        attacker.graphic(GRAPHIC)
        var playersHit = 0
        var npcsHit = 0
        // TODO Does it only damage mobs of the same type?
        // Apply potential damage to players.
        for (local in attacker.localMobs.localPlayers()) {
            if (playersHit >= MAX_TARGETS) {
                break
            }
            if (local != victim && sendHit(attacker, local, attack)) {
                playersHit++
            }
        }
        // Apply potential damage to NPCs.
        for (local in attacker.localMobs.localNpcs()) {
            if (npcsHit >= MAX_TARGETS) {
                break
            }
            if (local != victim && sendHit(attacker, local, attack)) {
                npcsHit++
            }
        }
        damage // Preserves the original primary hit result from the special attack DSL.
    }
}