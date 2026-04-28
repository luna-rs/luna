package game.combat.npcHooks.skeletalWyvern

import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.RangedCombatAttack

/**
 * A skeletal wyvern ranged combat attack.
 *
 * @param npc The skeletal wyvern performing the attack.
 * @param other The target being attacked.
 * @author lare96
 */
class WyvernRangedAttack(npc: Npc, other: Mob) : RangedCombatAttack<Npc>(
    npc,
    other,
    RANGED_CAST_ANIMATION,
    RANGED_START_GRAPHIC,
    RANGED_PROJECTILE,
    null,
    6,
    npc.combatDef().attackSpeed) {

    companion object {

        /**
         * The start graphic displayed when a skeletal wyvern uses its ranged attack.
         */
        val RANGED_START_GRAPHIC = Graphic(499, 100, 0)

        /**
         * Builds the skeletal wyvern ranged projectile that travels from the attacker to the target.
         */
        val RANGED_PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
            LocalProjectile.followEntity(ctx)
                .setSourceEntity(mob)
                .setTargetEntity(other)
                .setId(500)
                .setTicksToStart(44)
                .setTicksToEnd(3)
                .setStartHeight(43)
                .setEndHeight(31)
                .setInitialSlope(0)
                .build()
        }

        /**
         * The cast animation used for skeletal wyvern ranged attacks.
         */
        const val RANGED_CAST_ANIMATION = 2989
    }
}