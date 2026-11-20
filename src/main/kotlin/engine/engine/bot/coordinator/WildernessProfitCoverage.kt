package engine.bot.coordinator

import api.bot.script.ZonedBotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.magic
import api.predef.mining
import api.predef.thieving
import api.predef.rand
import engine.bot.coordinator.skill.MiningScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator
import engine.bot.gear.BotGearPurpose
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.NpcDropGatheringScript
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.skills.HerbGatheringScript
import game.bot.scripts.skills.MineBotScript
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.TelegrabBotScript
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import game.skill.thieving.pickpocketNpc.ThievingNpcType.ROGUE
import game.skill.mining.Ore
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import java.util.IdentityHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sends non-PKers on ordinary, supply-gated profit trips to Wilderness camps.
 * Each destination has its own population-scaled capacity so one camp cannot absorb the entire rollout.
 *
 * @author lare96
 */
internal object WildernessProfitCoverage {
    /**
     * An existing profit activity and its trip budget at the reference population.
     *
     * @author lare96
     */
    private enum class Route(val zone: SubZone, val fullTarget: Int, val level: Int) {
        EDGEVILLE_HERBS(SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS, 10, 25),
        LOWER_TEMPLE(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE, 12, 35),
        DEEP_TEMPLE(SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE, 10, 55),
        WINE(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE, 6, TelekineticGrabAction.LEVEL),
        ROGUE_CAMP(SubZone.WILDERNESS_ROGUE_CAMP, 4, ROGUE.level),
        GREEN_DRAGONS(SubZone.GREEN_DRAGONS, 12, 75),
        RUNITE(SubZone.DEEP_WILDERNESS_RUNITE_ROCKS, 2, Ore.RUNE.level);

        fun accepts(script: ZonedBotScript): Boolean = when (this) {
            GREEN_DRAGONS -> script is NpcDropGatheringScript && script.sellOnCompletion
            WINE -> script is TelegrabBotScript && script.profit && script.itemId == TelegrabBotScript.WINE
            ROGUE_CAMP -> script is PickpocketBotScript && script.sellOnCompletion && ROGUE in script.npcs
            RUNITE -> script is MineBotScript && script.profitMining && Ore.RUNE in script.ores
            else -> script is HerbGatheringScript
        }

        fun eligible(bot: Bot): Boolean {
            val skilling = this == RUNITE || this == WINE || this == ROGUE_CAMP
            val activity = if (skilling) BotActivity.PROFIT_SKILLS else BotActivity.PROFIT_COMBAT
            if (!bot.preferences.canChooseActivity(activity)) return false
            return when (this) {
                RUNITE -> bot.mining.level >= level
                WINE -> bot.magic.level >= level
                ROGUE_CAMP -> bot.thieving.level >= level && bot.personality.confidence >= 0.5
                else -> CombatCoordinator.combatReadiness(bot, BotGearPurpose.MELEE) >= level
            }
        }

        fun create(bot: Bot, duration: Duration): ZonedBotScript? = when (this) {
            GREEN_DRAGONS -> CombatCoordinator.profitDropGatheringScript(bot, duration, zone)
            WINE -> if (zone in TelegrabBotScript.wineZones(bot, profit = true)) {
                TelegrabBotScript(bot, TelegrabBotScript.WINE, true, duration, mutableListOf(zone), ownedRunesOnly = true)
                    .takeIf { it.isEligible() }
            } else null
            ROGUE_CAMP -> if (SkillingCoordinator.hasThievingFood(bot) && zone.entryBlocker(bot) == null) {
                PickpocketBotScript(bot, setOf(ROGUE), duration, mutableListOf(zone), sellOnCompletion = true)
            } else null
            RUNITE -> MiningScriptFactory.profitCandidate(bot, bot.mining.level, mine = zone)
                ?.start(mutableListOf(), duration) as? MineBotScript
            else -> HerbGatheringScript.profitCandidate(bot, duration, zone)
        }
    }

    private val arriving = IdentityHashMap<ZonedBotScript, Long>()
    private val retryAt = IdentityHashMap<Bot, Long>()
    private var reportedAt = Long.MIN_VALUE

    fun review(bots: Collection<Bot>, now: Long): String? {
        val loggedIn = bots.filter(PkCoverage::online)
        val population = loggedIn.filter { it.isAlive && !it.preferences.isPker }
        retryAt.keys.retainAll(population.toSet())
        val active = IntArray(Route.entries.size)
        val committed = IntArray(Route.entries.size)
        val observed = HashSet<ZonedBotScript>()
        for (bot in population) {
            val stack = bot.scriptStack.scripts
            if (stack.any { it is PkBotScript && !it.isTerminated() && !it.isFinished() }) continue
            val script = stack.firstOrNull { it is ZonedBotScript && !it.isTerminated() && !it.isFinished() }
                as? ZonedBotScript ?: continue
            val present = bot.inWilderness()
            val route = Route.entries.firstOrNull { it.accepts(script) &&
                (script.zones.singleOrNull() == it.zone || present && it.zone in script.zones && bot.position in it.zone.area) }
                ?: continue
            observed += script
            if (present && bot.position in route.zone.area) {
                active[route.ordinal]++
                committed[route.ordinal]++
                arriving.remove(script)
            } else if (now < arriving.getOrPut(script) { now + 240_000L }) {
                committed[route.ordinal]++
            }
        }
        arriving.keys.retainAll(observed)

        val eligible = population.filter {
            (retryAt[it] ?: 0L) <= now && PkCoverage.safeToRecruit(it)
        }
        val targets = Route.entries.map { PkCoverage.scaled(loggedIn.size, it.fullTarget) }
        for (route in Route.entries.shuffled()) {
            val id = route.ordinal
            var starts = minOf(2, targets[id] - committed[id])
            if (starts <= 0) continue
            val candidates = eligible.asSequence().filter { route.eligible(it) }
                .sortedBy { it.position.computeLongestDistance(route.zone.inside) }
            var attempts = 8
            for (bot in candidates) {
                if (starts <= 0 || attempts <= 0) break
                if ((retryAt[bot] ?: 0L) > now || !PkCoverage.safeToRecruit(bot) ||
                    bot.recentEscape?.avoids(route.zone) == true) continue
                attempts--
                retryAt[bot] = now + 120_000L
                val trip = route.create(bot, rand(28, 45).minutes) ?: continue
                bot.scriptStack.pushHead(trip)
                arriving[trip] = now + 240_000L
                committed[id]++
                starts--
            }
        }

        if (reportedAt == Long.MIN_VALUE) reportedAt = now
        if (now - reportedAt < 60_000L) return null
        reportedAt = now
        val routes = Route.entries.associateWith {
            val id = it.ordinal
            "active=${active[id]} incoming=${committed[id] - active[id]} target=${targets[id]}"
        }
        return "Wilderness profit coverage online=${loggedIn.size} active=${active.sum()} routes=$routes"
    }
}
