package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.MAGIC_LONGBOW
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The magic longbow special attack animation id.
 */
val ANIMATION = 1062

/**
 * The graphic displayed when the magic longbow special attack is performed.
 */
val GRAPHIC = Graphic(252, 100, 0)

/**
 * Builds the magic longbow ranged projectile that travels from the attacker to the target.
 */
val PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(500)
        .setTicksToEnd(44)
        .setTicksToStart(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

/**
 * Calculates Powershot's damage cap using the launch-time ranged level plus ten and fired ammunition strength.
 * The snapshot is captured before ammunition consumption so the final arrow retains its damage bonus when
 * its equipment slot becomes empty before the special's launch callback runs.
 * Adds the rounding offset before integer division, retaining the full product until that final step.
 * The resulting cap is used for an inclusive roll from zero; guaranteed accuracy does not add damage.
 */
fun calculateMaxHit(source: PlayerRangedCombatAttack): Int {
    val combatStat = (source.launchedRangedLevel.toLong() + 10) * (source.launchedAmmo.strength.toLong() + 64)
    return ((combatStat + 320) / 640).toInt()
}

attack(type = MAGIC_LONGBOW,
       drain = 25,
       attackBonus = 1.0) {

    attack {
        ranged(ANIMATION,
               GRAPHIC, PROJECTILE, null,
               atkCombat.styleDef.speed, atkCombat.styleDef.range)
    }

    // Powershot guarantees accuracy, but does not bypass Protect from Missiles in PvP.
    launched {
        CombatDamageRequest.builder(attacker, victim, CombatDamageType.RANGED)
            .setBaseMaxHit(calculateMaxHit(attack as PlayerRangedCombatAttack))
            .setBaseAccuracy(1.0)
            .setFlatBonusAccuracy(1.0)
            .build().resolve()
    }
}
