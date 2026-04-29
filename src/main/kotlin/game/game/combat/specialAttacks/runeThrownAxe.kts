package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.RUNE_THROWN_AXE
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.math.floor

/**
 * The rune thrown axe special attack animation id.
 */
val ANIMATION = 1068

/**
 * The graphic displayed when the rune thrown axe special attack is performed.
 */
val GRAPHIC = Graphic(257, 100, 0)

/**
 * Creates the rune thrown axe special attack projectile.
 */
val PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    // TODO@0.5.0 Fix projectile values, looks a bit off.
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(258)
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

/**
 * A lightweight [CombatAttack] wrapper used for rune thrownaxe chain hits.
 *
 * Chainhit reuses [CombatDamageAction] to apply delayed rebound damage, but it must not execute the original special
 * attack logic again. This wrapper keeps the original attack context while disabling attack execution and delegated
 * damage calculation.
 *
 * @param source The original rune thrownaxe special attack.
 * @author lare96
 */
class ChainHitCombatAttack(source: CombatAttack<Player>) :
    CombatAttack<Player>(source.attacker, source.victim, source.interactionPolicy, source.delay) {

    /**
     * Does nothing.
     *
     * Chainhit damage is already resolved manually before the delayed damage action runs.
     */
    override fun attack() {}

    /**
     * Returns no calculated damage.
     *
     * Rebound damage is supplied directly through [CombatDamageAction], so this wrapper should not
     * calculate another hit.
     *
     * @param other The possible target of the attack.
     * @return Always `null`.
     */
    override fun calculateDamage(other: Mob?): CombatDamage? = null
}

/**
 * Processes the next delayed rune thrownaxe chain hit.
 *
 * Each rebound displays a projectile from the previous victim to the next valid target, waits for the
 * projectile travel delay, drains additional special attack energy, applies the resolved ranged damage,
 * then recursively schedules the next rebound. The chain ends once there are no more valid targets or
 * no attacks remain.
 *
 * @param attacker The player performing the special attack.
 * @param lastVictim The mob most recently hit by the thrownaxe.
 * @param targets The remaining rebound targets gathered around the original victim.
 * @param source The wrapped combat attack context used to apply rebound damage.
 * @param attacksLeft The maximum number of rebound attacks still allowed.
 */
fun handleChainHit(
    attacker: Player,
    lastVictim: Mob,
    targets: Iterator<Mob>,
    source: CombatAttack<Player>,
    attacksLeft: Int) {
    if (targets.hasNext() && attacksLeft > 0) {
        val nextVictim = targets.next()
        val nextDamage = CombatDamageRequest.standard(attacker, nextVictim, CombatDamageType.RANGED).resolve()
        PROJECTILE(lastVictim, nextVictim).display()
        nextVictim.submitAction(object : Action<Mob>(nextVictim, ActionType.SOFT, false, 1) {
            override fun run(): Boolean {
                attacker.combat.specialBar.drain(10, true)
                CombatDamageAction(nextDamage, source, true, 1).run()
                handleChainHit(attacker, nextVictim, targets, source, attacksLeft - 1)
                return true
            }
        })
    } else {
        attacker.combat.specialBar.isLocked = false
    }
}

attack(type = RUNE_THROWN_AXE,
       drain = 10) {

    attack {
        ranged(animationId = ANIMATION,
               start = GRAPHIC,
               projectileFunction = PROJECTILE)
    }

    launched { attacker.combat.specialBar.isLocked = true; damage }

    arrived {
        val attacksLeft = floor(attacker.combat.specialBar.energy / drain.toDouble()).toInt().coerceAtMost(5)
        val filter: (Mob) -> Boolean =
            { it != attacker && it != victim && it.isWithinDistance(victim, 3) && attacker.combat.checkMultiCombat(it) }
        val targets = // Gather npc OR player targets.
            (if (victim is Npc) world.locator.findViewableNpcs(victim, filter)
            else world.locator.findViewablePlayers(victim, filter)).iterator()

        if (attacksLeft > 0 && targets.hasNext()) {
            // Use recursion to handle chain hits.
            handleChainHit(attacker, victim, targets, ChainHitCombatAttack(source), attacksLeft)
        }
    }
}