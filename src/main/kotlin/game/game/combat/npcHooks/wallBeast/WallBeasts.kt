package game.combat.npcHooks.wallBeast

import api.attr.Attr
import api.predef.head
import api.predef.slayer
import io.luna.Luna
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.net.msg.out.SoundMessageWriter
import java.lang.ref.WeakReference
import java.util.Optional
import java.util.concurrent.ThreadLocalRandom

/**
 * Anchored wall-beast encounters use the existing seven holes, cache forms and native combat/death handling.
 * Lost City's supplied and public revision 377 sources contain the identities and animations, but no encounter
 * script. The proposed reconstruction is deterministic front-tile contact: a helmet repels the grab in two ticks;
 * an unprotected grab holds at tick two, deals one unmitigated 11..18 hit at tick six and releases at tick eight.
 * An active arm withdraws after four ticks without a legal front-tile target. These timings, damage distribution,
 * prayer bypass and reset rule are review choices, not recovered original constants. Wounds and kill credit survive
 * both form changes. An unprotected victim must leave the front before that same NPC life can grab them again.
 *
 * @author lare96
 */
object WallBeasts {

    /**
     * Revision 377 forms, wearable initial protection and native Slayer requirement.
     */
    const val DORMANT = 1826
    const val ACTIVE = 1827
    const val HELMET = 4551
    const val SLAYER_LEVEL = 35

    /**
     * Explicit reconstructed sequence and withdrawal deadlines, measured from the contact tick.
     */
    const val REPEL_TICKS = 2
    const val HOLD_TICKS = 2
    const val HIT_TICKS = 6
    const val GRAB_TICKS = 8
    const val RESET_TICKS = 4
    const val GRAB_MIN = 11
    const val GRAB_MAX = 18

    /**
     * Final standing tiles mapped to the original hole anchors. South is the only open cardinal side in the cache;
     * each inferred placement remains an explicit TODO because the original trigger script is unavailable.
     */
    internal val FRONTS = mapOf(
        // TODO verify SOUTH/front for hole (3161,9547,0).
        Position(3161, 9546) to Position(3161, 9547),
        // TODO verify SOUTH/front for hole (3162,9574,0).
        Position(3162, 9573) to Position(3162, 9574),
        // TODO verify SOUTH/front for hole (3164,9556,0).
        Position(3164, 9555) to Position(3164, 9556),
        // TODO verify SOUTH/front for hole (3198,9554,0).
        Position(3198, 9553) to Position(3198, 9554),
        // TODO verify SOUTH/front for hole (3198,9572,0).
        Position(3198, 9571) to Position(3198, 9572),
        // TODO verify SOUTH/front for hole (3215,9560,0).
        Position(3215, 9559) to Position(3215, 9560),
        // TODO verify SOUTH/front for hole (3216,9588,0).
        Position(3216, 9587) to Position(3216, 9588)
    )

    /**
     * NPC life state and victim-specific failed exposure markers are transient and never saved with an account.
     */
    private var Npc.wallLife by Attr.nullableObj(Life::class)
    private var Player.failedWallContact by Attr.nullableObj(FailedContact::class)
    private var Player.wallContact by Attr.nullableObj(ContactAction::class)

    /**
     * Runs after this player's complete walking turn, so a two-step run that skips the front tile does not trigger.
     * Darkness restricts the player's attacks, not the environmental grab; no Slayer level is needed to suffer it.
     */
    internal fun onPlayerTick(player: Player) {
        player.failedWallContact?.let { failed ->
            val npc = failed.npc.get()
            if (player.position != failed.front || npc == null || npc.spawnCount != failed.spawnCount ||
                npc.state != EntityState.ACTIVE) player.failedWallContact = null
        }
        val anchor = FRONTS[player.position] ?: return
        val npc = player.world.npcs.findFirst {
            it.baseId == DORMANT && it.basePosition == anchor && it.state == EntityState.ACTIVE
        }.orElse(null) ?: return
        val state = life(npc)
        if (!contactParticipant(npc, player) || state.contact != null) return
        if (npc.id == ACTIVE) {
            if (npc.combat.target !is Player || !frontParticipant(npc, npc.combat.target as Player)) {
                npc.combat.attack(player)
            }
            return
        }
        if (npc.id != DORMANT || player.isLocked || player.walking.isLocked) return
        val failed = player.failedWallContact
        if (failed?.npc?.get() === npc && failed.spawnCount == npc.spawnCount) return
        val repelled = !Luna.settings().skills().slayerEquipmentNeeded() || player.equipment.head?.id == HELMET
        val action = ContactAction(player, npc, state, repelled)
        state.contact = action
        player.submitAction(action)
        if (action.isFinished && state.contact === action) state.contact = null
    }

    /**
     * Drives the anchored NPC's soft lifecycle. Native death, poison and execution keep ownership of lethal paths.
     */
    internal fun process(npc: Npc): Boolean {
        val state = npc.wallLife ?: return !tracked(npc)
        if (state.spawnCount != npc.spawnCount || npc.state != EntityState.ACTIVE || !npc.isAlive) {
            state.contact?.interrupt()
            return true
        }
        state.contact?.let {
            if (!it.valid()) it.interrupt()
            return false
        }
        if (npc.id != ACTIVE) return false
        val target = npc.combat.target as? Player
        if (target != null && frontParticipant(npc, target) && npc.combat.checkMultiCombat(target) &&
            CombatRestriction.permits(npc, target)) {
            state.idleSince = Long.MIN_VALUE
            return false
        }
        val now = npc.world.currentTick
        if (state.idleSince == Long.MIN_VALUE) state.idleSince = now
        if (now - state.idleSince + 1 >= RESET_TICKS && !npc.isLocked && !npc.status.isPoisoned() &&
            !npc.combat.isExecutionPending && !npc.combat.isAwaitingExecution && !npc.combat.isRetreating) {
            npc.navigator.cancel()
            npc.walking.clear()
            npc.combat.target = null
            npc.interact(null)
            npc.transformKeepingLevels(DORMANT)
            npc.animation(Animation(1801))
            state.idleSince = Long.MIN_VALUE
        }
        return false
    }

    /**
     * Gives ordinary player attack admission a clear refusal without making the helmet an every-hit requirement.
     */
    internal fun failureMessage(player: Player, npc: Npc): String? = when {
        npc.id != DORMANT && npc.id != ACTIVE -> null
        npc.id == DORMANT || npc.wallLife?.contact != null -> "The wall beast has not emerged yet."
        player.slayer.level < SLAYER_LEVEL -> "You need a Slayer level of 35 to fight wall beasts."
        tracked(npc) && !frontParticipant(npc, player) -> "You must stand in front of the wall beast to fight it."
        else -> null
    }

    /**
     * Rechecks controllers and native ownership for actual attacks, independently of environmental contact.
     */
    internal fun permitsFight(player: Player, npc: Npc): Boolean =
        failureMessage(player, npc) == null && player.controllers.checkCombat(npc) &&
            npc.combat.checkMultiCombat(player) && CombatRestriction.permits(player, npc)

    /**
     * Cancels dormant, incomplete, displaced and newly ineligible queued hits without granting damage experience.
     */
    internal fun defend(npc: Npc, other: Mob, damage: CombatDamage?): CombatDamage? =
        if (npc.id != ACTIVE || npc.wallLife?.contact != null || other is Player &&
            (other.slayer.level < SLAYER_LEVEL || tracked(npc) && !positionedParticipant(npc, other) ||
                other.state != EntityState.ACTIVE || other.world !== npc.world ||
                !other.controllers.checkCombat(npc) || !npc.combat.checkMultiCombat(other) ||
                !CombatRestriction.permits(other, npc))) null else damage

    /**
     * Supplies ordinary crushing melee. Final checks happen at commitment, after native pursuit and cooldowns.
     */
    internal fun attack(npc: Npc, other: Mob): CombatAttack<Npc> {
        if (npc.id == ACTIVE && !tracked(npc)) {
            return MeleeCombatAttack(npc, other, npc.combatDef().attackAnimation, 1, npc.combatDef().attackSpeed)
        }
        return object : MeleeCombatAttack<Npc>(npc, other, 1802, 1, npc.combatDef().attackSpeed) {
            private val spawnCount = npc.spawnCount

            override fun onAttack(damage: CombatDamage?): CombatDamage? {
                val player = victim as? Player ?: return null
                if (attacker.spawnCount != spawnCount || attacker.id != ACTIVE ||
                    attacker.wallLife?.contact != null || attacker.combat.target !== player ||
                    !frontParticipant(attacker, player) || !attacker.combat.checkMultiCombat(player) ||
                    !CombatRestriction.permits(attacker, player)) return null
                attacker.wallLife?.idleSince = Long.MIN_VALUE
                return damage
            }
        }
    }

    /**
     * Creates one soft monitor per native life and fixes the arm's facing without inventing movement routes.
     */
    internal fun initialize(npc: Npc) {
        if (tracked(npc) && npc.state == EntityState.ACTIVE) life(npc)
    }

    /**
     * Resolves the exact native life. A stale player action cannot change a replacement NPC life or its monitor.
     */
    private fun life(npc: Npc): Life {
        npc.wallLife?.takeIf { it.spawnCount == npc.spawnCount }?.let { return it }
        npc.wallLife?.contact?.interrupt()
        val state = Life(npc.spawnCount)
        npc.wallLife = state
        npc.defaultDirection = Optional.of(Direction.SOUTH)
        npc.navigator.cancel()
        npc.walking.clear()
        npc.submitAction(object : Action<Npc>(npc, ActionType.SOFT) {
            override fun run(): Boolean = mob.wallLife !== state || process(mob)
        })
        return state
    }

    /**
     * Only the existing hole anchors participate in the native trap lifecycle.
     */
    private fun tracked(npc: Npc): Boolean = npc.baseId == DORMANT && npc.basePosition in FRONTS.values

    /**
     * Presence and live collision are checked on the exact front, rather than a radius around the wall.
     */
    private fun frontParticipant(npc: Npc, player: Player): Boolean = player.isAlive && positionedParticipant(npc, player)

    /**
     * Queued attacks may still land after their attacker dies; the working tile, controller and phase still apply.
     */
    private fun positionedParticipant(npc: Npc, player: Player): Boolean = tracked(npc) &&
        npc.state == EntityState.ACTIVE && npc.isAlive && npc.position == npc.basePosition &&
        player.state == EntityState.ACTIVE && player.world === npc.world &&
        FRONTS[player.position] == npc.basePosition &&
        npc.world.collisionManager.traversable(npc.position, EntityType.PLAYER, Direction.SOUTH, false)

    /**
     * Initial contact preserves ordinary single/multi-combat ownership and encounter restrictions.
     */
    private fun contactParticipant(npc: Npc, player: Player): Boolean = frontParticipant(npc, player) &&
        !npc.isLocked && !npc.combat.isDisabled && npc.combat.checkMultiCombat(player) &&
        CombatRestriction.permits(npc, player)

    /**
     * State belongs to one native life; both forms intentionally share health and combat levels in their profiles.
     *
     * @author lare96
     */
    private class Life(val spawnCount: Int) {
        var contact: ContactAction? = null
        var idleSince = Long.MIN_VALUE
    }

    /**
     * Per-victim suppression prevents stacked players alternating failed grabs without leaving the front tile.
     *
     * @author lare96
     */
    private class FailedContact(npc: Npc, val spawnCount: Int, val front: Position) {
        val npc = WeakReference(npc)
    }

    /**
     * One reserved sequence owns both the native action lock and its separate walking lock. Cancellation restores
     * only those acquired locks, and every stage rechecks the same NPC life and exact participant before effects.
     *
     * @author lare96
     */
    private class ContactAction(player: Player, private val npc: Npc, private val state: Life,
                                private val repelled: Boolean) : LockedAction(player, false, 1) {
        private val startedAt = world.currentTick
        private var walkingLocked = false
        private var previousDisabled = false
        private var holding = false
        private var hit = false

        override fun beforeLock(): Boolean = state.contact === this && npc.wallLife === state &&
            npc.spawnCount == state.spawnCount && npc.id == DORMANT && contactParticipant(npc, mob) &&
            !mob.isLocked && !mob.walking.isLocked && (mob.wallContact == null || mob.wallContact!!.isFinished)

        override fun onLock() {
            mob.wallContact = this
            mob.navigator.cancel()
            mob.walking.clear()
            mob.walking.isLocked = true
            walkingLocked = true
            previousDisabled = npc.combat.isDisabled
            npc.combat.isDisabled = true
            mob.interact(npc)
            npc.interact(mob)
            if (repelled) {
                npc.animation(Animation(1805))
                mob.queue(SoundMessageWriter(1139, 1, 0))
                mob.sendMessage(if (mob.equipment.head?.id == HELMET) "Your helmet repels the wall beast!"
                    else "The wall beast emerges from the wall.")
            } else {
                mob.failedWallContact = FailedContact(npc, state.spawnCount, mob.position)
                npc.animation(Animation(1806))
                mob.animation(Animation(1809))
                mob.queue(SoundMessageWriter(1142, 1, 0))
            }
        }

        override fun onProcess() {
            if (!valid()) interrupt()
        }

        override fun run(): Boolean {
            if (!valid()) return true
            val elapsed = world.currentTick - startedAt
            if (repelled) {
                if (elapsed < REPEL_TICKS) return false
                npc.transformKeepingLevels(ACTIVE)
                npc.animation(Animation(-1))
                state.idleSince = Long.MIN_VALUE
                npc.combat.attack(mob)
                npc.combat.attackDelay = REPEL_TICKS
                return true
            }
            if (!holding && elapsed >= HOLD_TICKS) {
                holding = true
                npc.animation(Animation(1807))
                mob.animation(Animation(1810))
            }
            if (!hit && elapsed >= HIT_TICKS) {
                hit = true
                npc.animation(Animation(1808))
                mob.animation(Animation(1811))
                val source = GrabAttack(npc, mob)
                source.calculateDamage(mob).apply(source)
            }
            return elapsed >= GRAB_TICKS
        }

        override fun onUnlock() {
            if (mob.wallContact === this) {
                if (walkingLocked) mob.walking.isLocked = false
                mob.wallContact = null
            }
            walkingLocked = false
            if (npc.wallLife === state && npc.spawnCount == state.spawnCount && state.contact === this) {
                state.contact = null
                if (npc.state == EntityState.ACTIVE && npc.isAlive) {
                    npc.combat.isDisabled = previousDisabled
                    if (npc.id == DORMANT) {
                        npc.animation(Animation(-1))
                        npc.interact(null)
                    }
                }
            }
        }

        /**
         * This action's own lock is expected; life, position and ownership changes still invalidate the sequence.
         */
        fun valid(): Boolean = state.contact === this && npc.wallLife === state &&
            npc.spawnCount == state.spawnCount && npc.id == DORMANT && frontParticipant(npc, mob) &&
            mob.wallContact === this && npc.combat.checkMultiCombat(mob) && CombatRestriction.permits(npc, mob)
    }

    /**
     * An attributed environmental grab deals the selected unmitigated hit without a normal strike, player combat
     * experience, automatic retaliation or a second attack. Ordinary active-arm melee retains normal prayers.
     *
     * @author lare96
     */
    private class GrabAttack(npc: Npc, player: Player) : CombatAttack<Npc>(npc, player, InteractionPolicy.UNSPECIFIED, 1) {
        override fun attack() = Unit
        override fun suppressesCombatEngagement(damage: CombatDamage?): Boolean = true
        override fun calculateDamage(other: Mob): CombatDamage =
            CombatDamageRequest.Builder(attacker, other, CombatDamageType.MELEE)
                .setBaseAccuracy(1.0).setBaseMaxHit(0)
                .setFlatBonusDamage(ThreadLocalRandom.current().nextInt(GRAB_MIN, GRAB_MAX + 1))
                .ignoreProtectionPrayers().build().resolve()
    }
}
