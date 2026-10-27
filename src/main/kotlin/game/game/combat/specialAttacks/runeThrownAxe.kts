package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.EntityState
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
import java.time.Instant
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
 * without calling its special attack arrival callback and starting another chain. Launch metadata is delegated to
 * the original attack so combat history, bot retaliation, and stall checks retain the initial throw's time. The
 * callback's damage identifies each rebound recipient; this wrapper's victim remains the original primary target.
 *
 * @param source The original rune thrownaxe special attack.
 * @author lare96
 */
class ChainHitCombatAttack(private val source: PlayerRangedCombatAttack) :
    CombatAttack<Player>(source.attacker, source.victim, source.interactionPolicy, source.delay) {

    override fun isSmiteActive(): Boolean = source.isSmiteActive

    /**
     * The hit delivered by the current synchronous rebound attempt, or null when delivery was skipped or cancelled.
     */
    private var appliedDamage: CombatDamage? = null

    /**
     * The last committed projectile's landing tile. Rebounds share the one consumed axe's recovery ownership.
     */
    var recoveryPosition: Position = source.launchedTargetPosition

    /**
     * Ends the chain and recovers its single surviving axe, including misses, exhaustion, and interruption.
     */
    fun finish() {
        source.recoverAmmo(recoveryPosition, 0)
        attacker.combat.specialBar.isLocked = false
    }

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

    /**
     * Retains the original throw's launch tick without executing another attack or resetting its cooldown.
     *
     * @return The initial throw's world tick, or `-1` if that attack has not committed.
     */
    override fun getLaunchTick(): Long = source.launchTick

    /**
     * Retains the initial throw's observation timestamp for history consumers such as bot stall checks.
     *
     * @return The original launch timestamp, or `null` before the initial throw commits.
     */
    override fun getTimestamp(): Instant? = source.timestamp

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
 * but their target-owned impacts must not launch more projectiles or spend more of the attacker's energy.
 * Interrupting a delayed rebound releases the special bar, including interruption caused by an impact callback failure.
 * Normal completion retains the lock when the next rebound has taken over the chain.
 * Each rebound uses its recipient's impact queue with a one-world-tick delay. Clearing incoming combat during
 * activity or life cleanup cancels delivery, recovers the axe, and releases the special bar immediately.
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
    while (attacker.isAlive && attacker.state == EntityState.ACTIVE && targets.hasNext() && attacksLeft > 0) {
        val nextVictim = targets.next()
        if (!nextVictim.combat.isAttackable || !isVisibleRebound(origin, nextVictim) ||
            !attacker.controllers.checkCombat(nextVictim) || !attacker.combat.checkMultiCombat(nextVictim)) {
            continue
        }
        if (!attacker.combat.specialBar.drain(10, true)) {
            source.finish()
            return
        }
        val nextDamage = source.calculateDamage(nextVictim)
        source.recoveryPosition = nextVictim.position
        nextVictim.combat.markAttackedBy(attacker)
        PROJECTILE(lastVictim, nextVictim).display()
        nextVictim.combat.impacts.schedule(1, {
            if (source.applyRebound(nextDamage)) {
                handleChainHit(attacker, origin, nextVictim, targets, source, attacksLeft - 1)
            } else {
                source.finish()
            }
        }, { interrupted ->
            if (interrupted) {
                source.finish()
            }
        })
        return
    }
    source.finish()
}

attack(type = RUNE_THROWN_AXE,
       drain = 10) {

    attack {
        ranged(animationId = ANIMATION,
               start = GRAPHIC,
               projectileFunction = PROJECTILE,
               projectileDiscarded = {
                   recoverAmmo(launchedTargetPosition, 0)
                   attacker.combat.specialBar.isLocked = false
               },
               recoverAtLaunch = false)
    }

    launched {
        val initialDamage = calculateChainDamage(attack as PlayerRangedCombatAttack, victim)
        attacker.combat.specialBar.isLocked = true
        initialDamage
    }

    arrived {
        val rangedSource = source as PlayerRangedCombatAttack
        // A missed initial throw must not spend energy on any rebounds.
        if (!damage.isAccurate) {
            rangedSource.recoverAmmo(rangedSource.launchedTargetPosition, 0)
            attacker.combat.specialBar.isLocked = false
            return@arrived
        }
        val attacksLeft = floor(attacker.combat.specialBar.energy / drain.toDouble()).toInt().coerceAtMost(MAX_REBOUNDS)
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
            rangedSource.recoverAmmo(rangedSource.launchedTargetPosition, 0)
            attacker.combat.specialBar.isLocked = false
        }
    }
}
