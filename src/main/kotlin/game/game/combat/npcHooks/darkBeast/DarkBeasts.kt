package game.combat.npcHooks.darkBeast

import api.attr.Attr
import api.predef.slayer
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import java.lang.ref.WeakReference

/**
 * Native dark-beast melee and opening magic on the existing revision 377 creature. A period guide describes one
 * opening cast when the player initiates and no further casts while that player remains still. The exact server
 * script is not present in Lost City. This reconstruction starts natural hunts with melee, arms an opening cast
 * only after an eligible delivered attack receives accepted retaliation, and permits one new cast when the victim
 * changes tile beyond actual melee contact. Stationary distance alone never repeats casts. Period safespot advice
 * also mentions magic, but does not resolve that original movement/reset algorithm; this is deliberately not a
 * claim to complete recovered AI. Later references corroborate four-tick cadence, magic maximum eight and crush;
 * Luna retains the selected period melee maximum sixteen. Range eight and the Fire Wave assets are reconstructed
 * presentation choices, not evidence that the creature casts that named player spell.
 *
 * @author lare96
 */
object DarkBeasts {

    /**
     * Native identity and the selected ordinary combat limits. Victim Slayer level never prevents NPC aggression.
     */
    const val NPC_ID = 2783
    const val SLAYER_LEVEL = 90
    const val MELEE_MAX = 16
    const val MAGIC_MAX = 8
    const val SPEED = 4
    const val MAGIC_RANGE = 8

    /**
     * Encounter and incoming-hit eligibility are transient; respawns cannot inherit another life's opening cast.
     */
    private var Npc.darkLife by Attr.nullableObj(Life::class)

    /**
     * Returns the ordinary native Slayer requirement without adding a quest or equipment prerequisite.
     */
    internal fun failureMessage(player: Player): String? =
        if (player.slayer.level < SLAYER_LEVEL) "You need a Slayer level of 90 to fight dark beasts." else null

    /**
     * Clears the previous incoming candidate on every defence call, including cancelled hits. The existing damage
     * action may retaliate after null damage, so a callback alone must not arm an opening spell. Dead attackers may
     * still deliver their already launched eligible hit; native retaliation independently requires them to be alive.
     */
    internal fun defend(npc: Npc, other: Mob, damage: CombatDamage?): CombatDamage? {
        val state = life(npc)
        state.incoming = null
        if (other is Player) {
            val failure = failureMessage(other)
            if (failure != null) {
                other.sendMessage(failure)
                return null
            }
            if (damage != null && damage.attacker === other && damage.victim === npc) {
                state.incoming = Incoming(other, npc.world.currentTick)
            }
        }
        return damage
    }

    /**
     * Confirms that the admitted incoming hit actually reached the damage pipeline, including zeroes and splashes.
     */
    internal fun afterDamage(npc: Npc, damage: CombatDamage) {
        val candidate = life(npc).incoming ?: return
        if (candidate.tick == npc.world.currentTick && candidate.player.get() === damage.attacker &&
            damage.victim === npc) candidate.delivered = true
    }

    /**
     * Only a newly accepted native retaliation binds an opening cast. Ordinary hits during an existing encounter
     * do not invoke this callback and cannot replenish a pending or already consumed opener.
     */
    internal fun retaliate(npc: Npc, other: Mob, action: CombatAction) {
        val state = life(npc)
        val incoming = state.incoming
        state.incoming = null
        val player = other as? Player
        val opening = player != null && failureMessage(player) == null && incoming?.delivered == true &&
            incoming.tick == npc.world.currentTick && incoming.player.get() === player
        if (!action.isFinished && npc.actions.first(CombatAction::class.java) === action && npc.combat.target === other) {
            state.encounter = Encounter(other, action, opening)
        }
    }

    /**
     * Supplies one mixed attack without consuming its pending spell during range inspection or cooldown waiting.
     */
    internal fun attack(npc: Npc, other: Mob): CombatAttack<Npc> = Attack(npc, other, encounter(npc, other))

    /**
     * Natural hunts begin in melee. Inspection outside the current native action cannot prepare an encounter or
     * replace an actual fight's pending opener. Both cached references are weak, including the action's pursuit state.
     */
    private fun encounter(npc: Npc, other: Mob): Encounter {
        val state = life(npc)
        val action = npc.actions.first(CombatAction::class.java)?.takeUnless { it.isFinished }
        if (action == null || npc.combat.target !== other) return Encounter(other, null, false)
        state.encounter?.takeIf {
            it.action.get() === action && it.target.get() === other
        }?.let { return it }
        return Encounter(other, action, false).also { state.encounter = it }
    }

    /**
     * Resolves per-life state without retaining old players or actions across an NPC respawn.
     */
    private fun life(npc: Npc): Life = npc.darkLife?.takeIf { it.spawnCount == npc.spawnCount }
        ?: Life(npc.spawnCount).also { npc.darkLife = it }

    /**
     * Pure selection uses the native footprint-aware contact test. Only a committed attack records a new tile.
     */
    private fun wantsMagic(npc: Npc, other: Mob, encounter: Encounter): Boolean = encounter.opening ||
        other.position != encounter.lastCommittedTile &&
        !npc.world.collisionManager.reached(npc, other, InteractionPolicy.STANDARD_SIZE)

    /**
     * A prepared attack may commit only for the same living participants and the same current native action/life.
     */
    private fun canCommit(npc: Npc, other: Mob, encounter: Encounter, spawnCount: Int): Boolean {
        val action = encounter.action.get() ?: return false
        return npc.id == NPC_ID && npc.spawnCount == spawnCount && npc.state == EntityState.ACTIVE && npc.isAlive &&
            other.state == EntityState.ACTIVE && other.isAlive && other.world === npc.world && other.position.z == npc.position.z &&
            !npc.isLocked && !npc.combat.isDisabled && !npc.combat.isRetreating && !action.isFinished &&
            npc.darkLife?.encounter === encounter && encounter.target.get() === other &&
            npc.actions.first(CombatAction::class.java) === action && npc.combat.target === other &&
            npc.combat.checkMultiCombat(other) && CombatRestriction.permits(npc, other)
    }

    /**
     * One life keeps incoming admission separate from the current outgoing combat encounter.
     *
     * @author lare96
     */
    private class Life(val spawnCount: Int) {
        var incoming: Incoming? = null
        var encounter: Encounter? = null
    }

    /**
     * A native defence admission is confirmed by afterDamage before accepted retaliation can consume it.
     *
     * @author lare96
     */
    private class Incoming(player: Player, val tick: Long) {
        val player = WeakReference(player)
        var delivered = false
    }

    /**
     * The first observed tile establishes natural melee pursuit; later changes matter only beyond actual contact.
     *
     * @author lare96
     */
    private class Encounter(victim: Mob, action: CombatAction?, var opening: Boolean) {
        val target = WeakReference(victim)
        val action = WeakReference(action)
        var lastCommittedTile: Position = victim.position
    }

    /**
     * One native apply call owns each typed melee or magic strike. The opening flag and movement tile are consumed
     * in the accepted onAttack commit, never by attack supply or damage preview. Custom magic preserves its actual
     * accuracy result, spell prayer and recipient-owned delivery. Fire Wave visuals155/156/157, centered launch,
     * height40/36 and46-plus-five-cycles-per-tile flight are explicitly reconstructed presentation.
     *
     * @author lare96
     */
    private class Attack(npc: Npc, other: Mob, private val encounter: Encounter) :
        CombatAttack<Npc>(npc, other, InteractionPolicy.STANDARD_SIZE, SPEED) {

        private val spawnCount = npc.spawnCount
        private var magic = false

        override fun getInteractionPolicy(): InteractionPolicy =
            if (wantsMagic(attacker, victim, encounter)) InteractionPolicy(InteractionType.LINE_OF_SIGHT, MAGIC_RANGE)
            else InteractionPolicy.STANDARD_SIZE

        override fun calculateDamage(other: Mob): CombatDamage {
            magic = wantsMagic(attacker, other, encounter)
            return CombatDamageRequest.Builder(attacker, other,
                if (magic) CombatDamageType.MAGIC else CombatDamageType.MELEE)
                .setBaseMaxHit(if (magic) MAGIC_MAX else MELEE_MAX).build().resolve()
        }

        override fun onAttack(damage: CombatDamage?): CombatDamage? {
            if (damage == null || !canCommit(attacker, victim, encounter, spawnCount)) return null
            val reach = if (magic) InteractionPolicy(InteractionType.LINE_OF_SIGHT, MAGIC_RANGE)
                else InteractionPolicy.STANDARD_SIZE
            if (!attacker.world.collisionManager.reached(attacker, victim, reach)) return null
            if (magic) encounter.opening = false
            encounter.lastCommittedTile = victim.position
            return damage
        }

        override fun getNpcAttackSound(): Int = if (magic) -1 else attacker.combatDef().attackSound

        override fun attack() {
            attacker.animation(Animation(2731, Animation.AnimationPriority.HIGH))
            if (magic) {
                attacker.graphic(Graphic(155, 100))
                val projectile = LocalProjectile.followEntity(attacker.context)
                    .setSourceEntity(attacker).setSourcePosition(attacker.position.translate(attacker.size() / 2, attacker.size() / 2))
                    .setTargetEntity(victim).setId(156).setStartHeight(40).setEndHeight(36).setInitialSlope(16)
                    .setLaunchOffset(16).setTiming(0, 46 + 5 * attacker.position.computeLongestDistance(victim.position))
                    .build()
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
            if (magic) damage.victim.graphic(if (damage.isAccurate) Graphic(157, 100) else Graphic(85, 100))
        }
    }
}
