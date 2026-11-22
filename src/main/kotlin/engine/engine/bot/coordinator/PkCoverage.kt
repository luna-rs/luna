package engine.bot.coordinator

import api.bot.isRealBot
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.script.ZonedBotScript
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.IdleBotScript
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.PkPatrolArea
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import java.util.IdentityHashMap

/**
 * Replenishes separate PK patrols in proportion to the online autonomous bot population.
 * Preparation and travel have bounded reservations. Only ready, geographically eligible hunters cover the floor.
 * Clan calls fill a party with assigned online members and preserve their interrupted scripts.
 *
 * @author lare96
 */
internal class PkCoverage(
    private val dispatch: (Bot, Purpose) -> PkBotScript? = { bot, purpose ->
        PkingCoordinator.getScript(bot, coverage = true, purpose = purpose) as? PkBotScript
    }
) {
    /**
     * Counts preparation and arrival failures without logging individual accounts.
     *
     * @author lare96
     */
    enum class Failure { RUNES, CLAN, KIT, RISK, TRAVEL, ARRIVAL_TIMEOUT }

    /**
     * One opportunity for nearby or busy clanmates to rally on the requester's outing.
     *
     * @author lare96
     */
    private data class ClanCall(val caller: Bot, val trip: PkBotScript, val expiresAt: Long)

    companion object {
        private const val FULL_POPULATION = 1850
        private val fullMinimum = mapOf(Purpose.SKILL to 30, Purpose.CLAN to 100, Purpose.GENERIC to 30)
        private val fullTarget = mapOf(Purpose.SKILL to 66, Purpose.CLAN to 225, Purpose.GENERIC to 66)
        private const val PARTY_SIZE = 6
        private const val ARRIVAL_WINDOW_MILLIS = 240_000L
        private const val CLAN_ARRIVAL_WINDOW_MILLIS = 360_000L
        private const val CALL_WINDOW_MILLIS = 180_000L
        private const val REVIEW_WINDOW_MILLIS = 12_000L
        val live = PkCoverage()

        internal fun scaled(online: Int, atFullPopulation: Int): Int =
            (online.coerceAtLeast(0).toLong() * atFullPopulation / FULL_POPULATION).toInt()

        internal fun minimum(online: Int, purpose: Purpose): Int =
            if (purpose == Purpose.SKILL)
                ((online.coerceAtLeast(0).toLong() * fullMinimum.getValue(purpose) + FULL_POPULATION / 2) /
                    FULL_POPULATION).toInt()
            else scaled(online, fullMinimum[purpose] ?: 0)

        internal fun target(online: Int, purpose: Purpose): Int = scaled(online, fullTarget[purpose] ?: 0)

        internal fun online(bot: Bot): Boolean = bot.isRealBot() && !bot.isTemporary &&
            !bot.isConnectedPlayer && bot.state == EntityState.ACTIVE && !bot.client.isPendingLogout

        internal fun eligible(bot: Bot): Boolean =
            online(bot) && bot.isAlive && PkingCoordinator.activityWeight(bot) > 0.0

        @JvmStatic
        fun idleScript(bot: Bot): BotScript? = live.forIdle(bot)

        /**
         * Pauses ordinary activities between native interactions. Travel, combat, interfaces and multi-step
         * script stacks are allowed to settle before an outstanding call can be taken.
         */
        internal fun safeToRecruit(bot: Bot): Boolean {
            if (!bot.isAlive || !ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() ||
                bot.reflex.activeReflex != null || bot.overlays.hasWindow() || bot.dialogues != null ||
                bot.navigator.isActive || !bot.walking.isEmpty || bot.actions.size(ActionType.WEAK) > 0 ||
                bot.scriptStack.size() > 1) return false
            val current = bot.scriptStack.current()
            return current == null || current is IdleBotScript || current is ZonedBotScript
        }

        private fun trip(bot: Bot): PkBotScript? = bot.scriptStack.current() as? PkBotScript
            ?: bot.scriptStack.findActive(PkBotScript::class.java).orElse(null)

        private fun cape(bot: Bot): Int? = bot.world.botManager.clanRegistry.capeFor(bot.username)
    }

    private val incoming = IdentityHashMap<PkBotScript, Long>()
    private val retryAt = IdentityHashMap<Bot, Long>()
    private val calls = mutableMapOf<Int, ClanCall>()
    private val clanStaffing = mutableMapOf<Int, Int>()
    private val staffing = IntArray(Purpose.values().size)
    private val vacancies = IntArray(staffing.size)
    private val startsLeft = IntArray(staffing.size)
    private val attemptsLeft = IntArray(staffing.size)
    private val skillBrackets = IntArray(PkPatrolArea.combatBrackets.size)
    private val fights = Array(PkPatrolArea.entries.size) { LongArray(PkPatrolArea.combatBrackets.size) }
    private val failures = LongArray(Failure.entries.size)
    private val belowMinimumMillis = LongArray(staffing.size)
    private val lastActive = IntArray(staffing.size)
    private var lastPopulation = 0
    private var reviewedAt = Long.MIN_VALUE
    private var reportedAt = Long.MIN_VALUE

    internal fun forIdle(bot: Bot, now: Long = System.currentTimeMillis()): PkBotScript? {
        if (reviewedAt == Long.MIN_VALUE || now - reviewedAt >= REVIEW_WINDOW_MILLIS ||
            bot.scriptStack.size() != 0 || !eligible(bot)) return null
        val cape = cape(bot)
        if (cape != null && calls[cape]?.let { validCall(it, now) } == true) {
            reserve(bot, Purpose.CLAN, now, cape, answeringCall = true)?.let { return it }
        }
        val purpose = bot.preferences.pk?.purpose() ?: return null
        return if (purpose == Purpose.CLAN) null else reserve(bot, purpose, now)
    }

    internal fun requestClanmates(bot: Bot, script: PkBotScript, now: Long = System.currentTimeMillis()) {
        if (!eligible(bot) || script.coveragePurpose() != Purpose.CLAN ||
            !(script.coverageIncoming(now) || script.coverageActive(now))) return
        val cape = cape(bot) ?: return
        if (!canFillParty(cape)) return
        if (calls[cape]?.let { validCall(it, now) } != true) calls[cape] = ClanCall(bot, script, now + CALL_WINDOW_MILLIS)
    }

    private fun validCall(call: ClanCall, now: Long): Boolean =
        now < call.expiresAt && eligible(call.caller) && trip(call.caller) === call.trip &&
            (call.trip.coverageIncoming(now) || call.trip.coverageActive(now))

    private fun canFillParty(cape: Int): Boolean = (clanStaffing[cape] ?: 0) < PARTY_SIZE

    private fun canDispatch(purpose: Purpose, pairing: Boolean = false): Boolean =
        startsLeft[purpose.ordinal] > 0 && attemptsLeft[purpose.ordinal] > 0 &&
            (vacancies[purpose.ordinal] > 0 || pairing)

    private fun needsPartner(cape: Int): Boolean = clanStaffing[cape] == 1 &&
        staffing[Purpose.CLAN.ordinal] < target(lastPopulation, Purpose.CLAN) + maxOf(2, scaled(lastPopulation, 10))

    private fun arrivalWindow(purpose: Purpose): Long =
        if (purpose == Purpose.CLAN) CLAN_ARRIVAL_WINDOW_MILLIS else ARRIVAL_WINDOW_MILLIS

    private fun recruitable(bot: Bot, now: Long): Boolean =
        (retryAt[bot] ?: 0L) <= now && trip(bot) == null && safeToRecruit(bot)

    private fun reserve(bot: Bot, purpose: Purpose, now: Long, cape: Int? = null,
                        answeringCall: Boolean = false): PkBotScript? {
        val pairing = answeringCall && cape != null && needsPartner(cape)
        if (!canDispatch(purpose, pairing) || cape != null && !canFillParty(cape) || !recruitable(bot, now)) return null
        attemptsLeft[purpose.ordinal]--
        retryAt[bot] = now + 60_000L
        val script = dispatch(bot, purpose) ?: return null
        incoming[script] = now + arrivalWindow(purpose)
        if (cape != null) clanStaffing.merge(cape, 1, Int::plus)
        if (purpose == Purpose.SKILL) skillBrackets[PkPatrolArea.combatBracket(bot.combatLevel)]++
        staffing[purpose.ordinal]++
        if (vacancies[purpose.ordinal] > 0) vacancies[purpose.ordinal]--
        startsLeft[purpose.ordinal]--
        return script
    }

    fun recordFailure(bot: Bot, failure: Failure) {
        if (!eligible(bot)) return
        failures[failure.ordinal]++
        if (failure == Failure.KIT || failure == Failure.RISK || failure == Failure.ARRIVAL_TIMEOUT) {
            retryAt[bot] = System.currentTimeMillis() + 180_000L
        }
    }

    /**
     * Records committed Wilderness PvP initiated by a ready PK bot.
     */
    fun recordFight(attacker: Player, victim: Player, now: Long = System.currentTimeMillis()) {
        if (attacker !is Bot || !eligible(attacker) || !victim.inWilderness()) return
        val patrol = trip(attacker) ?: return
        if (!patrol.coverageActive(now)) return
        val area = PkPatrolArea.at(attacker.position)
        fights[area.ordinal][PkPatrolArea.combatBracket(attacker.combatLevel)]++
    }

    /**
     * Answers a clan call or starts a party with available members of the same clan. A lone caller may recruit
     * one partner even if general patrol staffing has reached its target.
     */
    private fun recruitClan(cape: Int, members: List<Bot>, now: Long, answeringCall: Boolean) {
        val rally = members.firstOrNull()?.let { PkBotScript.clanRallyAnchor(it) }
        val candidates = members.shuffled().sortedBy { member ->
            val distance = rally?.let { member.position.computeLongestDistance(it) } ?: 0
            val reassignment = when (member.preferences.pk?.purpose()) {
                Purpose.CLAN -> 0
                Purpose.VALUE -> 25
                else -> 50
            }
            distance + reassignment
        }
        for (member in candidates) {
            if (!canDispatch(Purpose.CLAN, answeringCall && needsPartner(cape)) || !canFillParty(cape)) break
            val trip = reserve(member, Purpose.CLAN, now, cape, answeringCall) ?: continue
            member.scriptStack.pushHead(trip)
            requestClanmates(member, trip, now)
        }
    }

    /**
     * Samples physical presence and reserves work at a bounded rate. Real online bots of every activity contribute
     * to the scale. The per-purpose budgets keep a clan shortage from crowding out the other patrols.
     */
    fun review(bots: Collection<Bot>, now: Long = System.currentTimeMillis()): String? {
        val loggedIn = bots.filter(::online)
        val population = loggedIn.filter { it.isAlive && PkingCoordinator.activityWeight(it) > 0.0 }
        retryAt.keys.retainAll(population.toSet())
        calls.values.removeIf { !validCall(it, now) }
        val observed = HashSet<PkBotScript>()
        val active = IntArray(staffing.size)
        val clansPresent = HashSet<Int>()
        var grouped = 0
        var awaitingFormation = 0
        val presence = Array(PkPatrolArea.entries.size) { IntArray(PkPatrolArea.combatBrackets.size) }
        staffing.fill(0)
        skillBrackets.fill(0)
        clanStaffing.clear()

        for (bot in population) {
            val trip = trip(bot) ?: continue
            val purpose = trip.coveragePurpose() ?: continue
            observed += trip
            val bracket = PkPatrolArea.combatBracket(bot.combatLevel)
            if (trip.coverageActive(now)) {
                active[purpose.ordinal]++
                if (purpose == Purpose.CLAN) {
                    cape(bot)?.let(clansPresent::add)
                    if (trip.hasNearbyClanSupport()) grouped++
                }
                presence[PkPatrolArea.at(bot.position).ordinal][bracket]++
                incoming.remove(trip)
            } else if (trip.coverageIncoming(now)) {
                if (purpose == Purpose.CLAN && trip.readyForClanPatrol()) awaitingFormation++
                val until = incoming.getOrPut(trip) { now + arrivalWindow(purpose) }
                if (now >= until) {
                    if (until != Long.MIN_VALUE) recordFailure(bot, Failure.ARRIVAL_TIMEOUT)
                    incoming[trip] = Long.MIN_VALUE
                    continue
                }
            } else {
                incoming.remove(trip)
                continue
            }
            staffing[purpose.ordinal]++
            if (purpose == Purpose.SKILL) skillBrackets[bracket]++
            if (purpose == Purpose.CLAN) cape(bot)?.let { clanStaffing.merge(it, 1, Int::plus) }
        }
        incoming.keys.retainAll(observed)

        for (purpose in fullTarget.keys) {
            val id = purpose.ordinal
            if (reviewedAt != Long.MIN_VALUE && lastActive[id] < minimum(lastPopulation, purpose)) {
                belowMinimumMillis[id] += (now - reviewedAt).coerceAtLeast(0)
            }
            lastActive[id] = active[id]
            val target = target(loggedIn.size, purpose)
            // Travelling clanmates can be spread across the world. Keep a bounded reserve of extra
            // arrivals until the physical floor is met, without treating departures as active coverage.
            val reserve = if (purpose == Purpose.CLAN && active[id] < minimum(loggedIn.size, purpose))
                scaled(loggedIn.size, 45) else 0
            vacancies[id] = (target + reserve - staffing[id]).coerceAtLeast(0)
            startsLeft[id] = (vacancies[id] / 8 + 1).coerceAtMost(if (purpose == Purpose.CLAN) 12 else 6)
            attemptsLeft[id] = startsLeft[id] * 4
        }
        reviewedAt = now
        lastPopulation = loggedIn.size

        val clans = if (calls.isNotEmpty() || canDispatch(Purpose.CLAN)) {
            population.mapNotNull { bot -> cape(bot)?.let { it to bot } }.groupBy({ it.first }, { it.second })
        } else emptyMap()
        for (cape in calls.keys.toList()) {
            recruitClan(cape, clans[cape].orEmpty(), now, answeringCall = true)
        }

        for (purpose in listOf(Purpose.SKILL, Purpose.GENERIC)) {
            if (!canDispatch(purpose)) continue
            val candidates = population.filter { it.preferences.pk?.purpose() == purpose }
                .shuffled().sortedWith(compareByDescending<Bot> { it.preferences.isPker }.thenBy {
                    if (purpose == Purpose.SKILL) skillBrackets[PkPatrolArea.combatBracket(it.combatLevel)] else 0
                })
            for (bot in candidates) {
                if (!canDispatch(purpose)) break
                reserve(bot, purpose, now)?.let { bot.scriptStack.pushHead(it) }
            }
        }
        if (canDispatch(Purpose.CLAN)) {
            for ((cape, members) in clans.entries.shuffled()) {
                if (!canDispatch(Purpose.CLAN) || vacancies[Purpose.CLAN.ordinal] < 2) break
                if (cape in clanStaffing || members.count { recruitable(it, now) } < 2) continue
                recruitClan(cape, members, now, answeringCall = false)
            }
        }
        if (reportedAt == Long.MIN_VALUE) reportedAt = now
        if (now - reportedAt < 60_000L) return null
        reportedAt = now

        val coverage = fullTarget.keys.associateWith { purpose ->
            val id = purpose.ordinal
            "active=${active[id]} incoming=${staffing[id] - active[id]} " +
                "min=${minimum(loggedIn.size, purpose)} target=${target(loggedIn.size, purpose)} " +
                "belowMinimumSeconds=${belowMinimumMillis[id] / 1000}"
        }
        val blocked = Failure.entries.associateWith { failures[it.ordinal] }
        val byArea = PkPatrolArea.entries.associateWith { presence[it.ordinal].toList() }
        val fightStarts = PkPatrolArea.entries.associateWith { fights[it.ordinal].toList() }
        failures.fill(0)
        fights.forEach { it.fill(0L) }
        return "Wilderness PK coverage online=${loggedIn.size} eligible=${population.size} patrols=$coverage " +
            "activeClans=${clansPresent.size} inFormation=$grouped awaitingBackup=$awaitingFormation " +
            "combatBrackets=${PkPatrolArea.combatBrackets} " +
            "presence=$byArea fightStarts=$fightStarts failures=$blocked"
    }
}
