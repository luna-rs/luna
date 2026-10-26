package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.action.ActionType
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.SpecialAttackType.RUNE_THROWN_AXE
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack
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
 * The maximum number of rebounds after the initial hit, limiting one special attack to five targets in total.
 */
val MAX_REBOUNDS = 4

/**
 * Creates the rune thrown axe special attack projectile.
 */
val PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    // TODO@0.5.0 Fix projectile values, looks a bit off.
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(258)
        .setTicksToEnd(44)
        .setTicksToStart(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

/**
 * A lightweight [CombatAttack] wrapper used for rune thrownaxe chain hits.
 *
 * Chainhit reuses [CombatDamageAction] to apply delayed rebound damage, but it must not execute the original special
 * attack logic again. This wrapper keeps the original attack context while disabling attack execution and resolving
 * rebound damage with the original ammunition snapshot. Delivered damage awards experience through the captured stance,
 * without calling its special attack arrival callback and starting another chain.
 *
 * @param source The original rune thrownaxe special attack.
 * @author lare96
 */
class ChainHitCombatAttack(private val source: PlayerRangedCombatAttack) :
    CombatAttack<Player>(source.attacker, source.victim, source.interactionPolicy, source.delay) {

    /**
     * The hit delivered by the current synchronous rebound attempt, or null when delivery was skipped or cancelled.
     */
    private var appliedDamage: CombatDamage? = null

    /**
     * Applies one rebound through defence hooks and continues only after an accurate hit was actually delivered.
     * Resetting the result prevents a previous rebound from authorizing continuation when this victim is already dead.
     * A killing hit and an accurate zero-damage hit both permit continuation.
     *
     * @param damage The resolved rebound to attempt.
     * @return Whether delivery completed with an accurate hit.
     */
    fun applyRebound(damage: CombatDamage): Boolean {
        appliedDamage = null
        CombatDamageAction(damage, this, true, 1).run()
        return appliedDamage?.isAccurate == true
    }

    override fun attack() {}

    override fun calculateDamage(other: Mob): CombatDamage = calculateChainDamage(source, other)

    override fun onDamageApplied(damage: CombatDamage) {
        source.awardDamageExperience(damage)
        appliedDamage = damage
    }
}

/**
 * Checks the three-tile search radius and projectile visibility from the chain's original search position.
 * The distance check also rejects different height levels before raycasting against live collision data.
 *
 * @param origin The original target position captured when the initial axe was launched.
 * @param target The prospective rebound target.
 * @return Whether the target is within the visible search area.
 */
fun isVisibleRebound(origin: Position, target: Mob): Boolean =
    origin.isWithinDistance(target.position, 3) && world.collisionManager.raycast(origin, target.position)

/**
 * Resolves a Chainhit strike using its special ranged-level-plus-ten damage cap.
 * Both the initial throw and rebounds use ammunition strength without ordinary stance or ranged prayer bonuses
 * to max hit. The offensive roll and visible Ranged level are captured before launch; equipment, stance, prayer,
 * and level changes during flight cannot alter them. Each target's defence and protection prayers remain live.
 * Ammunition strength comes from the original attack's snapshot before consumption, so firing the last axe or
 * changing equipment during flight cannot substitute another ammunition definition for the launched axe.
 * The rounding offset is added before integer division, with a long intermediate product.
 */
fun calculateChainDamage(source: PlayerRangedCombatAttack, victim: Mob): CombatDamage {
    val attacker = source.attacker
    val combatStat = (source.launchedRangedLevel.toLong() + 10) * (source.launchedAmmo.strength.toLong() + 64)
    val maxHit = ((combatStat + 320) / 640).toInt()
    return CombatDamageRequest.builder(attacker, victim, CombatDamageType.RANGED)
        .setBaseAccuracy(CombatFormula.calculateRangedHitChance(source.launchedAttackRoll, victim))
        .setBaseMaxHit(maxHit)
        .build().resolve()
}

/**
 * Processes the next delayed rune thrownaxe chain hit.
 *
 * Each rebound validates attackability, controller permission, and multi-combat eligibility before paying its energy
 * cost and displaying a projectile. After the travel delay, it applies damage and schedules the next rebound.
 * Each rebound target enters combat at launch, independent of accuracy or its auto-retaliate setting.
 * Ineligible candidates are skipped without spending energy or consuming a rebound. Exhausted targets, exhausted
 * attacks, or insufficient energy end the chain and unlock the bar.
 * A failed accuracy roll also ends the chain after displaying that miss; an accurate zero-damage roll can continue.
 * Before each new rebound, the attacker must still be alive and active. Already launched hits can land after death,
 * but their target-owned actions must not launch more projectiles or spend more of the attacker's energy.
 * Interrupting a delayed rebound releases the special bar, including interruption caused by an action exception.
 * Normal completion retains the lock when the next rebound has taken over the chain.
 * A skipped or cancelled delivery ends the chain; continuation uses the delivered hit after defence hooks.
 * Range and visibility are rechecked before paying energy because gathered targets can move between rebounds.
 * The search centre stays at the initial target's launch-time position, even if that target moves during flight.
 *
 * @param attacker The player performing the special attack.
 * @param origin The fixed search position used for range and visibility checks throughout the chain.
 * @param lastVictim The mob most recently hit by the thrownaxe.
 * @param targets The remaining rebound targets gathered around the original victim.
 * @param source The wrapped combat attack context used to apply rebound damage.
 * @param attacksLeft The maximum number of rebound attacks still allowed.
 */
fun handleChainHit(
    attacker: Player,
    origin: Position,
    lastVictim: Mob,
    targets: Iterator<Mob>,
    source: ChainHitCombatAttack,
    attacksLeft: Int) {
    while (attacker.isAlive && targets.hasNext() && attacksLeft > 0) {
        val nextVictim = targets.next()
        if (!nextVictim.combat.isAttackable || !isVisibleRebound(origin, nextVictim) ||
            !attacker.controllers.checkCombat(nextVictim) || !attacker.combat.checkMultiCombat(nextVictim)) {
            continue
        }
        if (!attacker.combat.specialBar.drain(10, true)) {
            attacker.combat.specialBar.isLocked = false
            return
        }
        val nextDamage = source.calculateDamage(nextVictim)
        nextVictim.combat.markAttackedBy(attacker)
        PROJECTILE(lastVictim, nextVictim).display()
        nextVictim.submitAction(object : Action<Mob>(nextVictim, ActionType.SOFT, false, 1) {
            override fun run(): Boolean {
                if (source.applyRebound(nextDamage)) {
                    handleChainHit(attacker, origin, nextVictim, targets, source, attacksLeft - 1)
                } else {
                    attacker.combat.specialBar.isLocked = false
                }
                return true
            }

            override fun onFinished() {
                if (state == ActionState.INTERRUPTED) {
                    attacker.combat.specialBar.isLocked = false
                }
            }
        })
        return
    }
    attacker.combat.specialBar.isLocked = false
}

attack(type = RUNE_THROWN_AXE,
       drain = 10) {

    attack {
        ranged(animationId = ANIMATION,
               start = GRAPHIC,
               projectileFunction = PROJECTILE,
               projectileDiscarded = { attacker.combat.specialBar.isLocked = false })
    }

    launched {
        val initialDamage = calculateChainDamage(attack as PlayerRangedCombatAttack, victim)
        attacker.combat.specialBar.isLocked = true
        initialDamage
    }

    arrived {
        // A missed initial throw must not spend energy on any rebounds.
        if (!damage.isAccurate) {
            attacker.combat.specialBar.isLocked = false
            return@arrived
        }
        val attacksLeft = floor(attacker.combat.specialBar.energy / drain.toDouble()).toInt().coerceAtMost(MAX_REBOUNDS)
        val rangedSource = source as PlayerRangedCombatAttack
        val origin = rangedSource.launchedTargetPosition
        val filter: (Mob) -> Boolean =
            { it != attacker && it != victim && it.combat.isAttackable && attacker.controllers.checkCombat(it) &&
                    isVisibleRebound(origin, it) && attacker.combat.checkMultiCombat(it) }
        val targets = // Gather npc OR player targets.
            (if (victim is Npc) world.locator.findViewableNpcs(origin, filter)
            else world.locator.findViewablePlayers(origin, filter)).iterator()

        if (attacksLeft > 0 && targets.hasNext()) {
            // Use recursion to handle chain hits.
            handleChainHit(attacker, origin, victim, targets, ChainHitCombatAttack(rangedSource), attacksLeft)
        } else {
            attacker.combat.specialBar.isLocked = false
        }
    }
}
