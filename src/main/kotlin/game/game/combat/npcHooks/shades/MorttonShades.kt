package game.combat.npcHooks.shades

import api.attr.Attr
import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.net.msg.out.SoundMessageWriter
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadLocalRandom

/**
 * Mort'ton shadow hunting, emergence and return to shadow form, following Lost City's native shade scripts.
 * Numeric damage rises with an animation; hunting and splash retaliation pursue in shadow form until contact.
 * Loar shades retain their wounds on retreat, while the other families deliberately heal when their encounter ends.
 * Only originally dormant spawns receive this lifecycle; directly spawned living shades retain their native attacks.
 *
 * Lost City's three-tile hunt and ten-tile group pull are explicitly reconstructed distances. Luna retains its
 * ordinary combat ownership, tolerance, leash and new-bot protections. Temple building and wall attacks belong to
 * the minigame rather than this controller. Poisoned saved/custom states cannot reset away their damage credit.
 *
 * @author lare96
 */
object MorttonShades {

    /**
     * Dormant cache forms mapped to their corresponding active shades.
     */
    val FORMS = mapOf(1240 to 1241, 1243 to 1244, 1245 to 1246, 1247 to 1248, 1249 to 1250)

    /**
     * Source reconstruction distances, rather than recovered original server constants.
     */
    internal const val HUNT_RADIUS = 3
    internal const val GROUP_RADIUS = 10

    /**
     * Natural hunting delays reset eligibility; delivered numeric damage makes it eligible immediately.
     */
    internal const val HUNT_GRACE_TURNS = 95L
    internal const val TRANSFORM_TURNS = 500L
    internal const val LOAR_IDLE_TICKS = 8L

    /**
     * Lost City's npc_delay(1) resumes after the current tick plus two server ticks.
     */
    internal const val RISE_TICKS = 2

    /**
     * One NPC life owns its encounter, polling, transform lifetime and movement lock. Weak ownership does not retain
     * a logged-out player through a long-lived NPC. Counter progression pauses during emergence or external locks.
     *
     * @author lare96
     */
    private class Life(val spawnCount: Int) {
        var owner: WeakReference<Player>? = null
        var pullGroupOnAwaken = false
        var lastProcessTick = Long.MIN_VALUE
        var activeTurns = 0L
        var resetEligibleTurn = HUNT_GRACE_TURNS
        var nextPollTurn = 1L
        var lastCombatTick = Long.MIN_VALUE
        var nextAttemptTick = Long.MIN_VALUE
        var riseUntilTick = Long.MIN_VALUE
        var restoreWalkingLock: Boolean? = null
        var deathReported = false
    }

    /**
     * NPC state expires on respawn; the player's source hunt-suppression flag expires on the ordinary world clock.
     */
    private var Npc.shadeLife by Attr.nullableObj(Life::class)
    private var Player.shadeHuntUntil by Attr.long { Long.MIN_VALUE }

    /**
     * Processes the existing NPC collection once per world tick, without per-NPC recurring tasks.
     */
    fun process() {
        for (npc in world.npcs) process(npc)
    }

    /**
     * Pursues in shadow form, resumes a rise, and evaluates the source's family-specific reset timers in place.
     */
    internal fun process(npc: Npc) {
        val activeId = FORMS[npc.baseId] ?: return
        if (npc.state != EntityState.ACTIVE || !npc.isAlive || npc.id != npc.baseId && npc.id != activeId) return
        val life = life(npc)
        val now = npc.world.currentTick
        if (life.lastProcessTick == now) return
        life.lastProcessTick = now
        if (life.riseUntilTick != Long.MIN_VALUE) {
            if (now < life.riseUntilTick) return
            finishRise(npc, life)
        }
        if (npc.isLocked || npc.combat.isDisabled || npc.combat.isRetreating) return
        if (npc.id == npc.baseId) {
            val target = npc.combat.target as? Player
            if (target != null && liveOwner(npc, target) && npc.actions.contains(CombatAction::class.java)) {
                life.owner = WeakReference(target)
                return
            }
            val owner = npc.world.locator.findPlayers(npc, HUNT_RADIUS) { mayAwaken(npc, it) }.firstOrNull()
                ?: return
            life.owner = WeakReference(owner)
            life.pullGroupOnAwaken = true
            npc.combat.attack(owner)
            return
        }

        life.activeTurns++
        (npc.combat.target as? Player)?.takeIf { liveOwner(npc, it) }?.let { life.owner = WeakReference(it) }
        // Poison immunity is configured natively. A forced or saved poison must not die as an uncredited shadow.
        if (npc.status.isPoisoned()) return
        if (life.activeTurns >= TRANSFORM_TURNS && npc.index >= 0) {
            restoreWalking(npc, life)
            npc.despawn()
            npc.respawn()
            return
        }
        if (life.activeTurns < life.nextPollTurn) return
        life.nextPollTurn = life.activeTurns + random(1, 7)
        val owner = life.owner?.get()?.takeIf { liveOwner(npc, it) }
        val eligible = life.activeTurns >= life.resetEligibleTurn
        val reset = if (npc.baseId == 1240) {
            owner == null || eligible && elapsed(now, life.lastCombatTick) >= LOAR_IDLE_TICKS
        } else {
            owner == null || npc.combat.target == null ||
                !npc.actions.contains(CombatAction::class.java) || eligible && !groupAttacking(npc, owner, now)
        }
        if (reset) returnToShadow(npc, life)
    }

    /**
     * Applies Luna's existing admission rules to the source's guessed shadow hunting distance. Hunting itself does
     * not require visibility; only the optional group pull checks line of sight, as in the source hunt modes.
     */
    internal fun mayAwaken(npc: Npc, player: Player): Boolean = mayHunt(npc, player, HUNT_RADIUS, false)

    /**
     * Creates a native crush strike. A naturally hunting shadow changes form only when its ordinary contact attack
     * commits, so the existing combat action and navigator perform pursuit without a separate movement system.
     */
    fun attack(npc: Npc, other: Mob): CombatAttack<Npc> {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        return ShadeMeleeAttack(npc, other)
    }

    /**
     * Starts numeric emergence before the existing hit is delivered, preserving native death and damage credit even
     * for an arrow or cannonball whose player has since died. Splashes and effect-only spells instead use the normal
     * retaliation pursuit and have no rise animation. This hook never delivers a second copy of the incoming hit.
     */
    internal fun beforeHit(npc: Npc, attacker: Mob, damage: CombatDamage?): Boolean {
        val activeId = FORMS[npc.baseId] ?: return false
        if (damage == null || npc.id != npc.baseId || npc.state != EntityState.ACTIVE || !npc.isAlive ||
            npc.isLocked || npc.combat.isDisabled || attacker !is Player || attacker.world !== npc.world ||
            !CombatRestriction.permits(attacker, npc)) return false
        if (NpcDefinition.ALL.get(activeId).isEmpty || NpcCombatDefinition.ALL.get(activeId).isEmpty) return false
        val life = life(npc)
        life.owner = WeakReference(attacker)
        life.pullGroupOnAwaken = false
        life.lastCombatTick = npc.world.currentTick
        if (!damage.amount.isPresent || damage.rawAmount < 0) return true
        awaken(npc, life, attacker, true)
        return true
    }

    /**
     * Records received combat and shortens the Loar hero's hunt suppression after death. The actual highest damage
     * contributor owns that five-tick exception; the most recent target or incoming attacker is not necessarily it.
     */
    fun afterHit(npc: Npc, damage: CombatDamage) {
        if (npc.baseId !in FORMS && npc.id !in FORMS.values) return
        val life = life(npc)
        if (damage.attacker is Player) life.lastCombatTick = npc.world.currentTick
        if (!npc.isAlive) onDeath(npc, npc.combat.damageStack.highestDamage)
    }

    /**
     * Records an actually accepted retaliation and its native flinch deadline, including for directly spawned
     * living shades. Reactive ownership must not pull nearby shadows as though the player had been hunted.
     */
    fun afterRetaliate(npc: Npc, other: Mob) {
        val player = other as? Player ?: return
        if (npc.baseId !in FORMS && npc.id !in FORMS.values) return
        val life = life(npc)
        life.owner = WeakReference(player)
        life.pullGroupOnAwaken = false
        life.nextAttemptTick = npc.world.currentTick + npc.combat.attackDelay
    }

    /**
     * Applies the source's Loar-death exception. An ordinary death hook may also call this for a non-combat death;
     * per-life ownership makes repeated death observations harmless.
     */
    fun onDeath(npc: Npc, hero: Player?) {
        if (npc.id !in setOf(1240, 1241)) return
        val life = life(npc)
        if (life.deathReported) return
        life.deathReported = true
        val now = npc.world.currentTick
        if (hero != null && hero.shadeHuntUntil > now) {
            hero.shadeHuntUntil = minOf(hero.shadeHuntUntil, now + 5)
        }
    }

    /**
     * Changes form without healing or clearing contributions. Numeric damage pauses movement for the source rise;
     * natural contact waking instead reserves the player's hunt flag and may pull wandering shadows of this family.
     */
    private fun awaken(npc: Npc, life: Life, owner: Player, numeric: Boolean) {
        val activeId = FORMS[npc.baseId] ?: return
        if (npc.id != npc.baseId) return
        if (!numeric && life.pullGroupOnAwaken && owner.shadeHuntUntil <= npc.world.currentTick) pullGroup(npc, owner)
        npc.transformKeepingLevels(activeId)
        life.owner = WeakReference(owner)
        life.pullGroupOnAwaken = false
        life.activeTurns = 0
        life.resetEligibleTurn = if (numeric) 0 else HUNT_GRACE_TURNS
        life.nextPollTurn = if (numeric && npc.baseId == 1240) random(5, 15) else random(1, 7)
        life.lastProcessTick = npc.world.currentTick
        if (numeric) {
            npc.navigator.cancel()
            npc.walking.clear()
            life.restoreWalkingLock = npc.walking.isLocked
            npc.walking.isLocked = true
            life.riseUntilTick = npc.world.currentTick + RISE_TICKS
            npc.combat.attackDelay = maxOf(npc.combat.attackDelay, RISE_TICKS)
            life.nextAttemptTick = npc.world.currentTick + npc.combat.attackDelay
            npc.animation(Animation(1288))
        } else {
            owner.shadeHuntUntil = npc.world.currentTick + 100
            if (liveOwner(npc, owner)) owner.queue(SoundMessageWriter(590, 1, 0))
        }
    }

    /**
     * Pulls only idle shadows of the same family, within the source's guessed ten-tile visible radius. Existing
     * locks, single-combat ownership, leashes and bot protection remain authoritative for every candidate.
     */
    private fun pullGroup(npc: Npc, owner: Player) {
        val nearby = npc.world.locator.findNpcs(npc.position, GROUP_RADIUS) {
            it !== npc && it.baseId == npc.baseId && it.id == npc.id &&
                !it.actions.contains(CombatAction::class.java) &&
                mayHunt(it, owner, GROUP_RADIUS + HUNT_RADIUS, true) &&
                npc.world.collisionManager.raycast(npc.position, it.position)
        }
        for (other in nearby) {
            val state = life(other)
            state.owner = WeakReference(owner)
            state.pullGroupOnAwaken = false
            other.combat.attack(owner)
        }
    }

    /**
     * Evaluates a shadow's ordinary aggression using the live form's metadata rather than the dormant no-aggro form.
     */
    private fun mayHunt(npc: Npc, player: Player, radius: Int, ignoreHuntFlag: Boolean): Boolean {
        val activeId = FORMS[npc.baseId] ?: return false
        val definition = NpcDefinition.ALL.get(activeId).orElse(null) ?: return false
        val combat = NpcCombatDefinition.ALL.get(activeId).orElse(null) ?: return false
        val aggression = combat.aggression ?: return false
        if (!npc.isAlive || npc.state != EntityState.ACTIVE || npc.id != npc.baseId || npc.isLocked ||
            npc.combat.isDisabled || npc.combat.isRetreating || npc.combat.target != null ||
            !liveOwner(npc, player) || player.isLocked || !npc.position.isWithinDistance(player.position, radius) ||
            player.isBot && player.timePlayed.toDays() < 2 ||
            !ignoreHuntFlag && player.shadeHuntUntil > npc.world.currentTick ||
            !combat.movement.containsTarget(npc.basePosition, player.position) ||
            !npc.combat.checkMultiCombat(player) || !CombatRestriction.permits(npc, player)) return false
        if (aggression.toleranceMinutes != -1 &&
            player.tolerance.duration.toMinutes() >= aggression.toleranceMinutes) return false
        return when (aggression.policy) {
            NpcAggressionPolicy.ALWAYS -> true
            NpcAggressionPolicy.COMBAT_LEVEL -> player.combatLevel <= definition.combatLevel * 2
            else -> false
        }
    }

    /**
     * Higher shades remain awake while any shade of the same active form recently attempted to strike their owner.
     * The source searches three tiles around the player; misses still advance that attack deadline.
     */
    private fun groupAttacking(npc: Npc, owner: Player, now: Long): Boolean =
        npc.world.locator.findNpcs(owner.position, 3) {
            it.id == npc.id && it.state == EntityState.ACTIVE && it.isAlive &&
                it.shadeLife?.let { state ->
                    state.spawnCount == it.spawnCount && state.owner?.get() === owner &&
                        state.nextAttemptTick != Long.MIN_VALUE && now < state.nextAttemptTick + 5
                } == true
        }.isNotEmpty()

    /**
     * Resumes native pursuit after the numeric-hit rise without restarting its attack cooldown.
     */
    private fun finishRise(npc: Npc, life: Life) {
        restoreWalking(npc, life)
        life.riseUntilTick = Long.MIN_VALUE
        npc.animation(Animation(-1))
        val owner = life.owner?.get()?.takeIf { liveOwner(npc, it) }
        if (owner != null && !npc.isLocked && !npc.combat.isDisabled && !npc.combat.isRetreating &&
            npc.combat.checkMultiCombat(owner) && CombatRestriction.permits(npc, owner)) npc.combat.attack(owner)
    }

    /**
     * Returns in place. Higher shade healing is an intentional fresh encounter, so reaching full health clears its
     * prior credit through the ordinary health setter; Loar wounds and credited contributions remain intact.
     */
    private fun returnToShadow(npc: Npc, life: Life) {
        restoreWalking(npc, life)
        npc.navigator.cancel()
        npc.walking.clear()
        npc.combat.target = null
        npc.interact(null)
        npc.transformKeepingLevels(npc.baseId)
        if (npc.baseId != 1240) npc.health = npc.totalHealth
        npc.shadeLife = Life(npc.spawnCount).also { it.lastProcessTick = npc.world.currentTick }
    }

    /**
     * Releases only the movement lock acquired for this life and emergence sequence.
     */
    private fun restoreWalking(npc: Npc, life: Life) {
        life.restoreWalkingLock?.let { npc.walking.isLocked = it }
        life.restoreWalkingLock = null
    }

    /**
     * Tests current player presence without retaining an expired target or crossing world boundaries.
     */
    private fun liveOwner(npc: Npc, player: Player): Boolean = player.state == EntityState.ACTIVE &&
        player.isAlive && player.world === npc.world

    /**
     * Resolves the lifecycle owned by the NPC's current spawn count.
     */
    private fun life(npc: Npc): Life = npc.shadeLife?.takeIf { it.spawnCount == npc.spawnCount }
        ?: Life(npc.spawnCount).also { npc.shadeLife = it }

    /**
     * Returns elapsed world ticks without overflowing an uninitialized or rewound timestamp.
     */
    private fun elapsed(now: Long, then: Long): Long =
        if (then == Long.MIN_VALUE || now < then) Long.MAX_VALUE else now - then

    /**
     * Selects an inclusive source polling interval.
     */
    private fun random(minimum: Int, maximum: Int): Long =
        ThreadLocalRandom.current().nextInt(minimum, maximum + 1).toLong()

    /**
     * A shade strike keeps its normal animation, cadence and zero hitsplat when inaccurate, but only accurate rolls
     * establish ownership and auto-retaliation. Positive delivered damage alone may drain one current Strength level.
     *
     * @author lare96
     */
    private class ShadeMeleeAttack(npc: Npc, other: Mob) :
        MeleeCombatAttack<Npc>(npc, other, 1283, 1, npc.combatDef().attackSpeed) {

        override fun onAttack(damage: CombatDamage?): CombatDamage? {
            if (damage == null || !attacker.isAlive || attacker.state != EntityState.ACTIVE) return null
            val state = life(attacker)
            if (state.riseUntilTick > attacker.world.currentTick) return null
            if (attacker.id in FORMS) {
                val owner = victim as? Player ?: return null
                if (FORMS[attacker.baseId] == null || !liveOwner(attacker, owner) || attacker.isLocked ||
                    attacker.combat.isDisabled || attacker.combat.target !== owner) return null
                awaken(attacker, state, owner, false)
            }
            (victim as? Player)?.let { state.owner = WeakReference(it) }
            state.nextAttemptTick = attacker.world.currentTick + delay
            if (damage.isAccurate) state.lastCombatTick = attacker.world.currentTick
            return damage
        }

        override fun suppressesCombatEngagement(damage: CombatDamage?): Boolean = damage?.isAccurate != true

        override fun onDamageApplied(damage: CombatDamage) {
            val player = damage.victim as? Player ?: return
            if (!attacker.isAlive || damage.attacker !== attacker || damage.rawAmount <= 0) return
            player.graphic(Graphic(293, 0, 0))
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                player.strength.adjustLevel(-1)
                player.sendMessage("You feel weakened.")
            }
        }
    }
}
