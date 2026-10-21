package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.MAGIC_LONGBOW
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor

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
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

attack(type = MAGIC_LONGBOW,
       drain = 25,
       attackBonus = 1.0) {

    attack {
        ranged(ANIMATION,
               GRAPHIC, PROJECTILE, null,
               atkCombat.styleDef.speed, atkCombat.styleDef.range)
    }

    // Ensure attack hits at least a 1, use modified max hit formula.
    launched {
        val maxHit = floor(0.5 + ((attacker.ranged.level + 10) * (attacker.combat.ammoDef.strength + 64)) / 640).toInt()
        CombatDamageRequest.builder(attacker, victim, CombatDamageType.RANGED)
            .setBaseMaxHit(maxHit)
            .setFlatBonusDamage(1)
            .setBaseAccuracy(1.0)
            .setFlatBonusAccuracy(1.0)
            .ignoreProtectionPrayers()
            .build().resolve()
    }
}