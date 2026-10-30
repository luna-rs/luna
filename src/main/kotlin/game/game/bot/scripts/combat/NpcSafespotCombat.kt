package game.bot.scripts.combat

import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone
import engine.bot.combat.NpcSafespotMap
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Holds a ranged or magic bot at a map-derived stand and verifies actual NPC retaliation before learning the spot.
 * Script lifecycle and target checks own the temporary movement lease; ordinary combat retains its native rules.
 *
 * @author lare96
 */
class NpcSafespotCombat(private val bot: Bot, private val active: () -> Boolean) {

    private var candidate: NpcSafespotMap.Candidate? = null
    private var target: Npc? = null
    private var zone: SubZone? = null
    private var policy: InteractionPolicy? = null
    private var incoming: CombatAttack<*>? = null
    private var npcOutgoing: CombatAttack<*>? = null
    private var outgoing: CombatAttack<*>? = null
    private var startedTick = 0L
    private var stableSince = -1L
    private var observedTick = -1L
    private var outgoingAttacks = 0
    private var readyTicks = 0
    private var verified = false
    private var attempts = 0
    private var verifications = 0
    private var rejections = 0
    private var reason = "idle"

    val isHolding: Boolean get() = candidate != null

    /**
     * Attempts one exact stand before sending the normal attack packet. A null result permits ordinary combat
     * when the bounded map search finds no candidate; false means an attempted spot failed its live checks.
     */
    suspend fun engage(npc: Npc, selectedZone: SubZone, allowed: () -> Boolean): Boolean? {
        release()
        val attackPolicy = bot.combat.getDefaultAttack(npc).interactionPolicy
        if (attackPolicy.type != InteractionType.LINE_OF_SIGHT || attackPolicy.distance <= 1) return null
        val spot = NpcSafespotMap.propose(bot, npc, selectedZone, attackPolicy) ?: return null
        candidate = spot
        target = npc
        zone = selectedZone
        policy = attackPolicy
        attempts++
        reason = "approaching"
        try {
            // A previous chase must not replace this exact-tile walk.
            if (bot.combat.target === npc) bot.combat.target = null
            val reached = withTimeoutOrNull(12.seconds) {
                coroutineScope {
                    val walk = async { bot.actionHandler.webWalkToAny(listOf(spot.stand)) }
                    try {
                        while (!walk.isCompleted) {
                            if (!active() || !allowed() || !NpcSafespotMap.valid(bot, npc, spot, attackPolicy)) {
                                walk.cancelAndJoin()
                                return@coroutineScope false
                            }
                            delay(100)
                        }
                        walk.await()
                    } finally {
                        walk.cancel()
                    }
                }
            } == true
            if (!reached || !active() || !allowed() || bot.position != spot.stand ||
                !NpcSafespotMap.valid(bot, npc, spot, attackPolicy) ||
                !bot.world.collisionManager.reached(bot, npc, attackPolicy)) {
                reject("stand unavailable")
                return false
            }
            startedTick = bot.world.currentTick
            incoming = bot.combat.lastAttackReceived
            npcOutgoing = npc.combat.lastAttackSent
            outgoing = bot.combat.lastAttackSent
            val previous = outgoing
            reason = "observing"
            renew(npc)
            // The stand is already collision-checked, so this click cannot acquire an adjacent approach.
            bot.output.sendNpcInteraction(3, npc)
            val attacked = waitFor(6.seconds) {
                if (!active() || !allowed() || !observe()) return@waitFor true
                val sent = bot.combat.lastAttackSent
                sent != null && sent !== previous && sent.victim === npc
            }
            if (!attacked || candidate == null || !active() || !allowed()) {
                if (candidate != null) reject("attack did not start")
                return false
            }
            return true
        } catch (cancelled: CancellationException) {
            release()
            throw cancelled
        } catch (failure: Throwable) {
            release()
            throw failure
        }
    }

    /**
     * Revalidates the held stand before the targeting script's ordinary active-combat fast path.
     */
    fun maintain(npc: Npc?, selectedZone: SubZone?, allowed: () -> Boolean): Boolean {
        val held = target ?: return false
        if (npc !== held || selectedZone != zone || !active() || !allowed() || !held.isAlive ||
            (bot.combat.target != null && bot.combat.target !== held)) {
            release()
            return false
        }
        if (!observe()) return false
        renew(held)
        return true
    }

    /**
     * Drops this session's lease without touching a replacement target or another script's movement.
     */
    fun release() {
        bot.combat.releasePursuit(this)
        if (target != null && bot.combat.target === target) bot.combat.target = null
        candidate = null
        target = null
        zone = null
        policy = null
        incoming = null
        npcOutgoing = null
        outgoing = null
        stableSince = -1
        observedTick = -1
        outgoingAttacks = 0
        readyTicks = 0
        verified = false
        reason = "idle"
    }

    /**
     * Small scalar diagnostics for live inspection without exposing mutable game objects.
     */
    fun snapshot(): Map<String, Any> = mapOf(
        "state" to reason, "targetId" to (target?.id ?: -1), "stand" to (candidate?.stand?.toString() ?: ""),
        "trap" to (candidate?.trap?.toString() ?: ""), "outgoingAttacks" to outgoingAttacks,
        "readyTicks" to readyTicks,
        "stableTicks" to if (stableSince < 0) 0L else bot.world.currentTick - stableSince,
        "verified" to verified, "attempts" to attempts, "verifications" to verifications, "rejections" to rejections)

    private fun renew(npc: Npc) {
        bot.combat.holdPursuit(this, npc) { active() && target === npc && candidate != null }
    }

    private fun observe(): Boolean {
        val spot = candidate ?: return false
        val npc = target ?: return false
        val reach = policy ?: return false
        if (!bot.isAlive || !npc.isAlive || bot.position != spot.stand || !bot.walking.isEmpty ||
            !NpcSafespotMap.valid(bot, npc, spot, reach)) {
            reject("geometry changed")
            return false
        }
        val received = bot.combat.lastAttackReceived
        val launched = npc.combat.lastAttackSent
        if (received != null && received !== incoming && received.attacker is Npc ||
            launched != null && launched !== npcOutgoing && launched.victim === bot) {
            reject("NPC attacked the stand")
            return false
        }
        incoming = received
        npcOutgoing = launched
        val tick = bot.world.currentTick
        if (tick == observedTick) return true
        observedTick = tick
        val stable = npc.position == spot.trap && npc.walking.isEmpty && npc.combat.target === bot
        if (!stable) {
            stableSince = -1
            outgoingAttacks = 0
            readyTicks = 0
        } else if (stableSince < 0) stableSince = tick
        if (stable && npc.combat.isAttackReady) readyTicks++
        val sent = bot.combat.lastAttackSent
        if (stable && sent != null && sent !== outgoing && sent.victim === npc && sent.launchTick >= stableSince) {
            outgoingAttacks++
        }
        outgoing = sent
        val observationTicks = maxOf(10, npc.combatDef().attackSpeed * 2)
        if (!verified && stableSince >= 0 && tick - stableSince >= observationTicks &&
            outgoingAttacks >= 2 && readyTicks >= 2) {
            NpcSafespotMap.verify(spot)
            verified = true
            verifications++
            reason = "verified"
            bot.log("Verified NPC safespot: npc=${npc.id}, stand=${spot.stand}, trap=${spot.trap}.")
        } else if (!verified && tick - startedTick >= 50) {
            reject("retaliation did not settle")
            return false
        }
        return true
    }

    private fun reject(why: String) {
        candidate?.let(NpcSafespotMap::reject)
        rejections++
        bot.log("Rejected NPC safespot: npc=${target?.id}, reason=$why.")
        release()
        reason = why
    }
}
