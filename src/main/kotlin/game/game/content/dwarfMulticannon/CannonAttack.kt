package game.content.dwarfMulticannon

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * A [CombatAttack] for one cannonball. The owner of the cannon is the attacker, so the hit counts towards their
 * drops. It is never applied through combat: [Cannon] fires it, and it doesn't put the owner in combat.
 * Ranged experience is awarded after delivery using the applied damage, so cancelled hits and overkill grant no
 * extra experience.
 *
 * @author TheLining
 */
class CannonAttack(owner: Player, victim: Npc) :
    CombatAttack<Player>(owner, victim, InteractionPolicy.UNSPECIFIED, 0) {

    companion object {

        /**
         * The most damage a cannonball deals.
         */
        const val MAX_HIT = 30
    }

    override fun attack() {
        /* Cannon fires it. */
    }

    override fun onDamageApplied(damage: CombatDamage) {
        if (damage.rawAmount > 0) {
            attacker.skill(Skill.RANGED).addExperience(2.0 * damage.rawAmount)
        }
    }

    /**
     * Rolls the hit with the owner's attack roll for their current combat style.
     */
    override fun calculateDamage(other: Mob): CombatDamage {
        val combat = attacker.combat
        val style = when {
            combat.magic.isCasting -> CombatDamageType.MAGIC
            combat.weapon.isRanged -> CombatDamageType.RANGED
            else -> CombatDamageType.MELEE
        }
        val request = if (CombatFormula.isAccurateHit(attacker, other, style)) {
            CombatDamageRequest.damage(attacker, other, CombatDamageType.RANGED, MAX_HIT)
        } else {
            CombatDamageRequest.zero(attacker, other, CombatDamageType.RANGED)
        }
        return request.resolve()
    }
}
