package game.combat.npcHooks.killerwatt

import api.attr.Attr
import api.predef.slayer
import io.luna.Luna
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.LocalProjectile
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.PursuitAction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.net.msg.out.SoundMessageWriter
import java.lang.ref.WeakReference
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * Native killerwatt activation and first-striker combat on the existing passive orbs. Cache identities, Slayer37,
 * boots and animations are known; Lost City has no recovered encounter script. Click-wake and melee baiting follow
 * later descriptions. This reconstruction counts the NPC's committed attack or the player's noncancelled delivered
 * attempt, including zeroes/splashes, as the first strike. Player-first fights mix styles equally, with each choice
 * fixed until commitment. Any planned melee can use electrical ranged when ordinary greedy pursuit has no legal
 * step; that local result is not proof of global unreachability. A one-tick wake grace, range13, exact mixed-style
 * scheduling, Wind Strike presentation and fifteen damage without boots on both styles are explicit review choices.
 * Older accounts disagree about magical melee and ranged-only boot punishment; this implementation uses physical
 * stab and magic-defended ranged, retaining their own protection prayers. Both forms preserve wounds and kill credit.
 *
 * @author lare96
 */
object Killerwatts {

    /**
     * Revision377 forms, worn protection and retained native combat limits.
     */
    const val BALL = 3202
    const val ACTIVE = 3201
    const val BOOTS = 7159
    const val SLAYER_LEVEL = 37
    const val SPEED = 2
    const val RANGE = 13
    const val WAKE_TICKS = 1
    const val MELEE_MAX = 6
    const val RANGED_MAX = 8
    const val UNPROTECTED_MAX = 15

    /**
     * State belongs to one native life; weak references do not retain departed players or finished pursuit actions.
     */
    private var Npc.wattLife by Attr.nullableObj(Life::class)

    /**
     * Returns the native Slayer refusal. Missing boots increase damage and do not forbid attacking.
     */
    internal fun failureMessage(player: Player): String? =
        if (player.slayer.level < SLAYER_LEVEL) "You need a Slayer level of 37 to fight killerwatts." else null

    /**
     * A real attack click wakes before the reach-gated combat listener. Cancelling the player's approach therefore
     * leaves the NPC pursuing normally, without counting that click as a landed attack or locking the player.
     */
    internal fun wake(player: Player, npc: Npc): Boolean {
        if (!family(npc) || !registered(npc) || !resident(npc, player) || !player.isAlive || player.isLocked ||
            npc.isLocked || npc.combat.isDisabled || npc.combat.isRetreating || failureMessage(player) != null ||
            !npc.isViewableFrom(player) || !npc.combat.checkMultiCombat(player) ||
            !CombatRestriction.permits(npc, player) || !player.controllers.checkCombat(npc)) return false
        // An active creature's existing fight is arbitrated by real hits and native retaliation, not another click.
        if (npc.id == ACTIVE) return npc.combat.target === player
        val state = life(npc)
        val changed = reveal(npc, player)
        npc.combat.attack(player)
        val action = npc.actions.first(CombatAction::class.java)
        if (npc.combat.target === player && action != null) bind(npc, state, player, action)
        if (changed) npc.combat.attackDelay = maxOf(npc.combat.attackDelay, WAKE_TICKS)
        return npc.combat.target === player
    }

    /**
     * Reveals admitted manual spells and secondary hits before damage can kill the passive form. A projectile may
     * still land after its caster dies; revealing preserves that queued damage and does not attack a dead caster.
     */
    internal fun defend(npc: Npc, other: Mob, damage: CombatDamage?): CombatDamage? {
        val state = life(npc)
        state.received = null
        if (other is Player) {
            val failure = failureMessage(other)
            if (failure != null) {
                other.sendMessage(failure)
                return null
            }
        }
        if (damage != null && npc.id == BALL && registered(npc) && other.world === npc.world &&
            other.position.z == npc.position.z && CombatRestriction.permits(other, npc)) {
            reveal(npc, other as? Player)
            if (state.target?.get() == null) state.target = WeakReference(other)
            if (resident(npc, other) && npc.combat.checkMultiCombat(other) && !npc.combat.isDisabled &&
                !npc.combat.isRetreating) {
                npc.combat.attack(other)
                val action = npc.actions.first(CombatAction::class.java)
                if (npc.combat.target === other && action != null) bind(npc, state, other, action)
            }
            npc.combat.attackDelay = maxOf(npc.combat.attackDelay, WAKE_TICKS)
        }
        return damage
    }

    /**
     * Only actual delivered player attempts can win the first-striker race. Their first mixed style is selected once
     * here, rather than being repeatedly rolled while a supplied attack waits for reach or cooldown.
     */
    internal fun afterDamage(npc: Npc, damage: CombatDamage, random: Random = ThreadLocalRandom.current()) {
        val player = damage.attacker as? Player ?: return
        if (damage.victim !== npc || failureMessage(player) != null || npc.id != ACTIVE) return
        val state = life(npc)
        val target = state.target?.get()
        val ranged = if (target === player && state.first != First.UNKNOWN) state.plannedRanged else random.nextBoolean()
        state.received = Received(player, npc.world.currentTick, ranged)
        if (target == null) state.target = WeakReference(player)
        if (state.target?.get() === player && state.first == First.UNKNOWN) {
            state.first = First.PLAYER
            state.plannedRanged = ranged
        }
    }

    /**
     * Native accepted retaliation may transfer a legitimate fight to another participant. Cancelled ball hits cannot
     * start default zero-damage combat, and a different player's old first-striker result is never inherited.
     */
    internal fun retaliate(npc: Npc, other: Mob, action: CombatAction) {
        if (npc.actions.first(CombatAction::class.java) !== action || action.isFinished || npc.combat.target !== other) return
        if (npc.id == BALL) {
            clearTarget(npc)
            return
        }
        bind(npc, life(npc), other, action)
    }

    /**
     * Supplies a pure range/style view. First-striker and next-style state change only on real incoming/outgoing hits.
     */
    internal fun attack(npc: Npc, other: Mob, random: Random = ThreadLocalRandom.current()): CombatAttack<Npc> =
        Attack(npc, other, life(npc), random)

    /**
     * Installs the NPC-owned monitor before native combat can queue pursuit. No global task or registry is needed.
     */
    internal fun initialize(npc: Npc) {
        if (family(npc) && npc.state == EntityState.ACTIVE) life(npc)
    }

    /**
     * Keeps a weak quarry while it remains on the original plane/region, within ordinary view/leash safety. A valid
     * replacement fighter wins over a departed quarry. Invalid navigation is cleared before COMBAT; out-of-view
     * quarries are remembered without repeatedly resubmitting pursuits. Only passive-base lives return to balls.
     */
    internal fun process(npc: Npc): Boolean {
        val state = npc.wattLife ?: return true
        if (state.spawnCount != npc.spawnCount || npc.state != EntityState.ACTIVE || !npc.isAlive) return true
        if (npc.id == BALL) {
            if (npc.combat.target != null) clearTarget(npc)
            state.target = null
            state.action = null
            state.first = First.UNKNOWN
            state.plannedRanged = false
            state.received = null
            return false
        }
        val current = npc.combat.target
        if (current != null && !resident(npc, current)) clearTarget(npc)
        if (npc.wattLife !== state || npc.spawnCount != state.spawnCount ||
            npc.state != EntityState.ACTIVE || !npc.isAlive) return true
        // Cancelling old navigation may synchronously install a legitimate replacement fight.
        npc.combat.target?.takeIf { resident(npc, it) }?.let {
            bind(npc, state, it, npc.actions.first(CombatAction::class.java))
        }
        var quarry = state.target?.get()
        if (quarry == null || !resident(npc, quarry)) {
            val delivered = state.received?.takeIf { it.tick == npc.world.currentTick }?.player?.get()
                ?.takeIf { resident(npc, it) && npc.combat.checkMultiCombat(it) }
            val recent = delivered ?: npc.combat.lastCombatWith?.takeIf {
                npc.combat.isSingleCombatLocked && resident(npc, it) && it.combat.target === npc &&
                    npc.combat.checkMultiCombat(it)
            }
            if (recent != null) {
                bind(npc, state, recent, null)
                quarry = recent
            } else {
                if (npc.baseId == BALL && !npc.isLocked && !npc.status.isPoisoned() &&
                    !npc.combat.isExecutionPending && !npc.combat.isAwaitingExecution && !npc.combat.isRetreating) {
                    clearTarget(npc)
                    npc.transformKeepingLevels(BALL)
                    state.target = null
                    state.action = null
                    state.first = First.UNKNOWN
                    state.plannedRanged = false
                    state.received = null
                }
                return false
            }
        }
        if (!npc.isViewableFrom(quarry)) {
            if (npc.combat.target === quarry) clearTarget(npc)
            state.action = null
            return false
        }
        if (npc.combat.target !== quarry && !npc.isLocked && !npc.combat.isDisabled && !npc.combat.isRetreating &&
            npc.combat.isWithinLeash(quarry) && npc.combat.checkMultiCombat(quarry) && CombatRestriction.permits(npc, quarry)) {
            npc.combat.attack(quarry)
        }
        if (npc.combat.target === quarry) {
            val action = npc.actions.first(CombatAction::class.java)
            if (state.action?.get() !== action) state.action = action?.let { WeakReference(it) }
        }
        return false
    }

    /**
     * Per-life initialization also repairs an interrupted monitor without changing combat selection or first striker.
     */
    private fun life(npc: Npc): Life {
        val state = npc.wattLife?.takeIf { it.spawnCount == npc.spawnCount }
            ?: Life(npc.spawnCount).also { npc.wattLife = it }
        if (state.monitor == null || state.monitor!!.isFinished) {
            val monitor = object : Action<Npc>(npc, ActionType.SOFT) {
                override fun run(): Boolean = mob.wattLife !== state || process(mob)
            }
            state.monitor = monitor
            npc.submitAction(monitor)
        }
        return state
    }

    /**
     * A clicked or delivered attack changes the definition in place and preserves all current levels and credit.
     */
    private fun reveal(npc: Npc, player: Player?): Boolean {
        if (npc.id != BALL) return false
        npc.transformKeepingLevels(ACTIVE)
        player?.queue(SoundMessageWriter(2076, 1, 0))
        return true
    }

    /**
     * Adopts actual native target changes, with a delivered first attempt retained through accepted retaliation.
     */
    private fun bind(npc: Npc, state: Life, other: Mob, action: CombatAction?) {
        if (state.target?.get() !== other) {
            state.target = WeakReference(other)
            state.first = First.UNKNOWN
            state.plannedRanged = false
        }
        val accepted = action?.takeUnless { it.isFinished }
        if (state.action?.get() !== accepted) state.action = accepted?.let { WeakReference(it) }
        val received = state.received
        if (state.first == First.UNKNOWN && received != null && received.player.get() === other &&
            received.tick == npc.world.currentTick) {
            state.first = First.PLAYER
            state.plannedRanged = received.ranged
        }
    }

    /**
     * The existing spawn region is a native lifetime boundary, not an invented bot camp or routing area.
     */
    private fun resident(npc: Npc, other: Mob): Boolean = other.state == EntityState.ACTIVE && other.isAlive &&
        other.world === npc.world && other.position.z == npc.position.z && (npc.baseId != BALL ||
        other.position.z == npc.basePosition.z && other.position.regionId == npc.basePosition.regionId)

    /**
     * Uses the exact collision-aware greedy choices from live pursuit. No legal local step does not imply that a
     * full pathfinder could never find a route. Freezes, overlaps and disabled movement do not manufacture this case.
     */
    private fun noContactStep(npc: Npc, other: Mob): Boolean {
        if (npc.position.z != other.position.z || npc.position == other.position || npc.isLocked ||
            npc.walking.isLocked || npc.combat.isDisabled || npc.status.isHeldBySpell()) return false
        val collision = npc.world.collisionManager
        val reached = collision.reached(npc, other, InteractionPolicy.STANDARD_SIZE)
        return !reached && PursuitAction.nextStep(npc.position, other.position, 1, false) { direction ->
            collision.traversable(npc.position, EntityType.NPC, direction, npc.size(), npc.routeStrategy, false)
        } == Direction.NONE
    }

    /**
     * Stored mixed choices remain unchanged by the bounded electrical escape from a planned melee turn.
     */
    private fun ranged(npc: Npc, other: Mob, state: Life): Boolean =
        state.first == First.PLAYER && state.plannedRanged || noContactStep(npc, other)

    /**
     * Exact native registration rejects synthetic click targets; queued damage still keeps its captured source.
     */
    private fun registered(npc: Npc): Boolean = npc.state == EntityState.ACTIVE && npc.isAlive &&
        npc.world.npcs[npc.index] === npc

    /**
     * Both direct active forms and the original ball resources share combat without an owner-only restriction.
     */
    private fun family(npc: Npc): Boolean = npc.id == BALL || npc.id == ACTIVE

    /**
     * Completes the invalid native pursuit and its queued step. Navigation to a different target owns replacement
     * walking and is left intact; a one-shot navigation request remains active until its queued path is consumed.
     */
    private fun clearTarget(npc: Npc) {
        val target = npc.combat.target
        val pursuit = npc.actions.first(PursuitAction::class.java)
        val navigationTarget = npc.navigator.currentTarget
        pursuit?.complete()
        if (target != null && navigationTarget === target) npc.navigator.cancel()
        val replacement = npc.navigator.currentTarget
        if (npc.combat.target === target) {
            if ((replacement == null || replacement === target) &&
                (pursuit != null || target != null && navigationTarget === target)) npc.walking.clear()
            npc.combat.target = null
            if (target != null && npc.interactingWith === target) npc.interact(null)
        }
    }

    /**
     * First striker is an actual committed NPC attack or a noncancelled delivered player attempt.
     *
     * @author lare96
     */
    private enum class First { UNKNOWN, NPC, PLAYER }

    /**
     * The NPC-owned monitor and weak encounter state survive ordinary click cancellation, not a new spawn life.
     *
     * @author lare96
     */
    private class Life(val spawnCount: Int) {
        var monitor: Action<Npc>? = null
        var target: WeakReference<Mob>? = null
        var action: WeakReference<CombatAction>? = null
        var first = First.UNKNOWN
        var plannedRanged = false
        var received: Received? = null
    }

    /**
     * A delivered hit carries its one-time mixed choice through the native accepted-retaliation callback.
     *
     * @author lare96
     */
    private class Received(player: Player, val tick: Long, val ranged: Boolean) {
        val player = WeakReference(player)
    }

    /**
     * One native apply handles each physical stab or electrical ranged attack. The ranged attack rolls Ranged
     * offence against Magic defence, with Protect from Missiles, rather than pretending to cast a magic spell.
     * Boots are captured at commitment. Wind Strike90/91/92, heights40/36 and46-plus-five-cycles-per-tile flight
     * are reconstructed presentation parameters; human3170 is the verified shock animation, never skeleton3171.
     *
     * @author lare96
     */
    private class Attack(npc: Npc, other: Mob, private val state: Life, private val random: Random) :
        CombatAttack<Npc>(npc, other, InteractionPolicy.STANDARD_SIZE, SPEED) {

        /**
         * The committed style and launch-time insulation state remain attached to this strike through delivery.
         */
        private var electrical = false
        private var unprotected = false

        override fun getInteractionPolicy(): InteractionPolicy =
            if (ranged(attacker, victim, state)) InteractionPolicy(InteractionType.LINE_OF_SIGHT, RANGE)
            else InteractionPolicy.STANDARD_SIZE

        override fun calculateDamage(other: Mob): CombatDamage {
            electrical = ranged(attacker, other, state)
            unprotected = Luna.settings().skills().slayerEquipmentNeeded() && other is Player &&
                other.equipment[Equipment.BOOTS]?.id != BOOTS
            val request = CombatDamageRequest.Builder(attacker, other,
                if (electrical) CombatDamageType.RANGED else CombatDamageType.MELEE)
                .setBaseMaxHit(if (unprotected) UNPROTECTED_MAX else if (electrical) RANGED_MAX else MELEE_MAX)
            if (electrical) request.setBaseAccuracy(CombatFormula.calculateMagicDefendedRangedHitChance(attacker, other))
            return request.build().resolve()
        }

        override fun onAttack(damage: CombatDamage?): CombatDamage? {
            val action = state.action?.get()
            if (damage == null || attacker.wattLife !== state || attacker.spawnCount != state.spawnCount ||
                attacker.id != ACTIVE || !attacker.isAlive || attacker.state != EntityState.ACTIVE ||
                !resident(attacker, victim) || !attacker.isViewableFrom(victim) || attacker.isLocked ||
                attacker.combat.isDisabled || attacker.combat.isRetreating || state.target?.get() !== victim ||
                attacker.combat.target !== victim || action == null || action.isFinished ||
                attacker.actions.first(CombatAction::class.java) !== action || !attacker.combat.checkMultiCombat(victim) ||
                !CombatRestriction.permits(attacker, victim) || !attacker.world.collisionManager.reached(attacker, victim,
                    if (electrical) InteractionPolicy(InteractionType.LINE_OF_SIGHT, RANGE) else InteractionPolicy.STANDARD_SIZE)) return null
            if (state.first == First.UNKNOWN) state.first = First.NPC
            state.plannedRanged = state.first == First.PLAYER && random.nextBoolean()
            return damage
        }

        override fun getNpcAttackSound(): Int = if (electrical) -1 else attacker.combatDef().attackSound

        override fun attack() {
            attacker.animation(Animation(if (electrical) 3164 else 3163, Animation.AnimationPriority.HIGH))
            if (electrical) {
                attacker.graphic(Graphic(90, 96))
                val projectile = LocalProjectile.followEntity(attacker.context).setSourceEntity(attacker).setTargetEntity(victim)
                    .setId(91).setStartHeight(40).setEndHeight(36).setInitialSlope(15).setLaunchOffset(11)
                    .setTiming(0, 46 + 5 * attacker.position.computeLongestDistance(victim.position)).build()
                projectile.display()
                scheduleProjectileArrival(projectile.endCycle / 30)
            } else {
                victim.combat.impacts.schedule(if (victim is Player) 0 else 1, {
                    victim.submitAction(CombatDamageAction(nextDamage, this, true))
                }, { interrupted -> onProjectileFinished(interrupted) })
            }
        }

        override fun onProjectileReached() {
            if (victim.state == EntityState.ACTIVE && victim.isAlive && CombatRestriction.permits(attacker, victim)) {
                victim.submitAction(CombatDamageAction(nextDamage, this, true))
            }
        }

        override fun onDamageApplied(damage: CombatDamage) {
            if (electrical) damage.victim.graphic(Graphic(92, 96))
            val player = damage.victim as? Player ?: return
            if (unprotected && damage.rawAmount > 0 && player.isAlive) {
                player.animation(Animation(3170, Animation.AnimationPriority.HIGH))
                player.queue(SoundMessageWriter(if (ThreadLocalRandom.current().nextBoolean()) 2094 else 2095, 1, 0))
            }
        }
    }
}
