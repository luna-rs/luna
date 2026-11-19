package engine.bot.coordinator

import api.bot.isRealBot
import api.bot.script.BotScript.ActionSafety
import api.bot.script.BotScript
import api.bot.skill.SkillingBotScript
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.IdleBotScript
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.PkPatrolArea
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.util.IdentityHashMap

/**
 * Maintains a wilderness patrol target using existing autonomous PK profiles and native departures.
 * Incoming trips reserve capacity briefly; resupplying and departing trips do not cover the wilderness.
 * All decisions and script-stack mutations belong to the scheduled game-thread review.
 *
 * @author lare96
 */
internal class PkCoverage(
    private val dispatch: (Bot) -> PkBotScript? = { PkingCoordinator.getScript(it, coverage = true) as? PkBotScript }
) {
    private val incoming = IdentityHashMap<PkBotScript, Long>()
    private val retryAt = IdentityHashMap<Bot, Long>()
    private val staffing = Array(PkPatrolArea.entries.size) { IntArray(PkPatrolArea.combatBrackets.size) }
    private val fights = Array(PkPatrolArea.entries.size) { LongArray(PkPatrolArea.combatBrackets.size) }
    private val failures = LongArray(Failure.entries.size)
    private var vacancies = 0
    private var startsLeft = 0
    private var attemptsLeft = 0
    private var reviewedAt = Long.MIN_VALUE
    private var reportedAt = Long.MIN_VALUE
    private var lastActive = 0
    private var belowMinimumMillis = 0L

    /**
     * Counts preparation and arrival failures without logging player identifiers or individual attacks.
     *
     * @author lare96
     */
    enum class Failure { RUNES, CLAN, KIT, RISK, TRAVEL, ARRIVAL_TIMEOUT }

    companion object {
        const val MINIMUM = 25
        const val TARGET = 30
        private const val STARTS_PER_REVIEW = 2
        private const val ATTEMPTS_PER_REVIEW = 16
        private const val ARRIVAL_WINDOW_MILLIS = 180_000L
        val live = PkCoverage()

        /**
         * Gives a safe idle decision priority over random work when the latest review found a deficit.
         */
        @JvmStatic
        fun idleScript(bot: Bot): BotScript? = live.forIdle(bot)

        internal fun eligible(bot: Bot): Boolean = bot.isRealBot() && !bot.isTemporary &&
            !bot.isConnectedPlayer && bot.state == EntityState.ACTIVE && bot.isAlive &&
            !bot.client.isPendingLogout && PkingCoordinator.activityWeight(bot) > 0.0

        /**
         * Recruits idle bots and ordinary gatherers between interactions, preserving protected task stacks.
         */
        internal fun safeToRecruit(bot: Bot): Boolean {
            if (!ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() ||
                bot.reflex.activeReflex != null || bot.overlays.hasWindow() || bot.dialogues != null ||
                bot.navigator.isActive || !bot.walking.isEmpty || bot.actions.size(ActionType.WEAK) > 0 ||
                bot.scriptStack.size() > 1) return false
            val current = bot.scriptStack.current()
            return current == null || current is IdleBotScript || current is SkillingBotScript<*>
        }
    }

    internal fun forIdle(bot: Bot, now: Long = System.currentTimeMillis()): PkBotScript? {
        if (bot.scriptStack.size() != 0) return null
        return reserve(bot, now)
    }

    private fun canDispatch(): Boolean = vacancies > 0 && startsLeft > 0 && attemptsLeft > 0

    private fun reserve(bot: Bot, now: Long): PkBotScript? {
        if (reviewedAt == Long.MIN_VALUE || now - reviewedAt >= 12_000L || !canDispatch() ||
            (retryAt[bot] ?: 0L) > now || !eligible(bot) ||
            bot.scriptStack.containsActive(PkBotScript::class.java) || !safeToRecruit(bot)) return null
        attemptsLeft--
        retryAt[bot] = now + 60_000L
        val bracket = PkPatrolArea.combatBracket(bot.combatLevel)
        val routes = PkBotScript.patrolGroups(bot)
        if (routes.isEmpty()) {
            recordFailure(bot, Failure.TRAVEL)
            return null
        }
        val areas = routes.flatten().map(PkPatrolArea::at).toSet()
        val area = PkPatrolArea.staffed.filter { it in areas }.maxWithOrNull(
            compareBy<PkPatrolArea> { TARGET * it.share / 10 - staffing[it.ordinal].sum() }
                .thenBy { -staffing[it.ordinal][bracket] })
        val script = dispatch(bot) ?: return null
        script.patrolArea = area
        incoming[script] = now + ARRIVAL_WINDOW_MILLIS
        staffing[(area ?: PkPatrolArea.OTHER).ordinal][bracket]++
        vacancies--
        startsLeft--
        return script
    }

    fun recordFailure(bot: Bot, failure: Failure) {
        if (eligible(bot)) failures[failure.ordinal]++
    }

    /**
     * Records fresh, committed Wilderness PvP initiated by a ready PK bot.
     */
    fun recordFight(attacker: Player, victim: Player, now: Long = System.currentTimeMillis()) {
        if (attacker !is Bot || !eligible(attacker) || !victim.inWilderness()) return
        val patrol = attacker.scriptStack.findActive(PkBotScript::class.java).orElse(null) ?: return
        if (!patrol.coverageActive(now)) return
        val area = PkPatrolArea.at(attacker.position)
        fights[area.ordinal][PkPatrolArea.combatBracket(attacker.combatLevel)]++
    }

    /**
     * Scans the population once per review. Scheduled and idle recruitment share one bounded attempt budget.
     * Emits a minute summary of physical presence, coverage gaps, failures and committed PvP starts.
     */
    fun review(online: Collection<Bot>, now: Long = System.currentTimeMillis()): String? {
        val population = online.filter(::eligible)
        retryAt.keys.retainAll(population.toSet())
        val observed = java.util.Collections.newSetFromMap(IdentityHashMap<PkBotScript, Boolean>())
        val presence = Array(staffing.size) { IntArray(PkPatrolArea.combatBrackets.size) }
        staffing.forEach { it.fill(0) }
        var active = 0
        var arriving = 0
        for (bot in population) {
            val script = (bot.scriptStack.current() as? PkBotScript
                ?: bot.scriptStack.findActive(PkBotScript::class.java).orElse(null)) ?: continue
            observed.add(script)
            val bracket = PkPatrolArea.combatBracket(bot.combatLevel)
            if (script.coverageActive(now)) {
                active++
                val area = PkPatrolArea.at(bot.position).ordinal
                presence[area][bracket]++
                staffing[area][bracket]++
                incoming.remove(script)
            } else if (script.coverageIncoming(now)) {
                val until = incoming.getOrPut(script) { now + ARRIVAL_WINDOW_MILLIS }
                if (now < until) {
                    arriving++
                    staffing[script.incomingArea().ordinal][bracket]++
                } else if (until != Long.MIN_VALUE) {
                    recordFailure(bot, Failure.ARRIVAL_TIMEOUT)
                    incoming[script] = Long.MIN_VALUE
                }
            } else incoming.remove(script)
        }
        incoming.keys.retainAll(observed)
        if (reviewedAt != Long.MIN_VALUE && lastActive < MINIMUM) {
            belowMinimumMillis += (now - reviewedAt).coerceAtLeast(0L)
        }
        lastActive = active
        vacancies = (minOf(TARGET, population.size) - active - arriving).coerceAtLeast(0)
        startsLeft = STARTS_PER_REVIEW
        attemptsLeft = ATTEMPTS_PER_REVIEW
        reviewedAt = now
        if (canDispatch()) {
            val byBracket = PkPatrolArea.combatBrackets.indices.map { bracket -> staffing.sumOf { it[bracket] } }
            val candidates = population.shuffled().sortedWith(compareByDescending<Bot> { it.preferences.isPker }
                .thenBy { byBracket[PkPatrolArea.combatBracket(it.combatLevel)] })
            for (bot in candidates) {
                if (!canDispatch()) break
                val script = reserve(bot, now) ?: continue
                bot.scriptStack.pushHead(script)
            }
        }
        if (reportedAt == Long.MIN_VALUE) reportedAt = now
        if (now - reportedAt < 60_000L) return null
        reportedAt = now
        val blocked = Failure.entries.associate { it.name to failures[it.ordinal] }
        val byArea = PkPatrolArea.entries.associate { it.name to presence[it.ordinal].toList() }
        val starts = PkPatrolArea.entries.associate { it.name to fights[it.ordinal].toList() }
        failures.fill(0)
        fights.forEach { it.fill(0L) }
        return "Wilderness PK coverage active=$active minimum=$MINIMUM target=$TARGET eligible=${population.size} " +
            "incoming=${arriving + STARTS_PER_REVIEW - startsLeft} belowMinimumSeconds=${belowMinimumMillis / 1000} " +
            "combatBrackets=${PkPatrolArea.combatBrackets} presence=$byArea fightStarts=$starts failures=$blocked"
    }
}
