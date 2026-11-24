package engine.bot.coordinator

import api.bot.action.trading.ProfitGatheringDemand
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.script.ProfitGatheringGoal
import api.bot.zone.SubZone
import api.predef.SKILL_HERBLORE
import api.predef.herblore
import engine.bot.coordinator.skill.BotProductionPlanner
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.CollectGroundItemBotScript
import game.bot.scripts.skills.GrindIngredientBotScript
import game.bot.scripts.trading.BuyHerbloreSecondaryBotScript
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Schedules secondary supplies through the existing shop, ground pickup and grinding actions.
 * Personal work banks wanted stock in bounded trips. Profit work uses native sale planning.
 *
 * @author lare96
 */
internal object HerbloreSecondaryActivities {
    private const val GRIND_BATCH = 27
    private const val GATHER_STOCK_CAP = 56
    private const val EYE_OF_NEWT = 221
    private val chocolate = Ingredient.CHOCOLATE_DUST
    val shopSecondaries = setOf(EYE_OF_NEWT, chocolate.newId)
    private val potionsBySecondary = FinishedPotion.entries.groupBy { it.secondary }

    /**
     * A placed, repeatable supply and the size of one visit.
     *
     * @author lare96
     */
    private data class Source(val id: Int, val zone: SubZone, val batch: Int, val ticksPerItem: Double,
                              val minimumCombat: Int = 0)

    private val sources = listOf(
        Source(231, SubZone.CRAFTING_GUILD_SNAPE_GRASS, 14, 50.0),
        // Combat-level aggression stops above twice a deadly red spider's combat level.
        Source(223, SubZone.VARROCK_SEWERS, 12, 50.0, minimumCombat = 69),
        Source(chocolate.id, SubZone.WEST_VARROCK_TREES, 4, 100.0)
    )

    /**
     * Adds a small personality-based reserve to the configured wanted quantity.
     */
    fun personalTarget(bot: Bot, id: Int): Int {
        val wanted = bot.preferences.getWantedItem(id)?.target() ?: return 0
        if (wanted <= 0) return 0
        val affinity = if (bot.preferences.isMerchant) 1.0
            else (bot.preferences.activities[BotActivity.MERCHANTING]?.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, 1.0)
        val surplusBasisPoints = (500 * affinity).roundToInt() + if (bot.preferences.isSkiller) 500 else 0
        val surplus = wanted.toLong() * surplusBasisPoints / 10_000
        return (wanted + surplus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun potionReserve(bot: Bot, id: Int): Int {
        val level = bot.herblore.staticLevel
        return potionsBySecondary[id].orEmpty().asSequence().filter { it.level <= level }.map { it.unf }
            .distinct().sumOf { ProfitGatheringGoal.physical(bot, it) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun general(bot: Bot, duration: Duration): BotScript? {
        if (!canWork(bot)) return null
        val time = minOf(duration, 15.minutes)
        for (ingredient in Ingredient.entries.shuffled()) {
            val missing = deficit(bot, ingredient.newId)
            if (missing < 1) continue
            val output = ProfitGatheringGoal.physical(bot, ingredient.newId)
            val target = (output + minOf(missing, GRIND_BATCH)).toInt()
            GrindIngredientBotScript(bot, ingredient, time, stockTarget = target,
                rawReserve = potionReserve(bot, ingredient.id)).takeIf { it.isEligible() }?.let { return it }
        }
        return buildList<BotScript> {
            for (source in availableSources(bot)) {
                val wanted = personalTarget(bot, source.id)
                val target = if (source.id == chocolate.id &&
                    ProfitGatheringGoal.physical(bot, Ingredient.PESTLE_AND_MORTAR) > 0)
                    maxOf(wanted, deficit(bot, chocolate.newId)) else wanted
                val stock = ProfitGatheringDemand.owned(bot, source.id)
                if (stock >= target) continue
                val physical = ProfitGatheringGoal.physical(bot, source.id)
                val batch = minOf(source.batch.toLong(), target - stock)
                add(collection(bot, source, (physical + batch).toInt(), time))
            }
            for (id in shopSecondaries) {
                val target = personalTarget(bot, id)
                if (deficit(bot, id, target) <= 0) continue
                BuyHerbloreSecondaryBotScript.candidate(bot, id, target)?.let(::add)
            }
        }.randomOrNull()
    }

    fun profit(bot: Bot, duration: Duration): BotScript? {
        if (!canWork(bot)) return null
        val time = minOf(duration, 15.minutes)
        val candidates = listOf<() -> BotScript?>(
            { profitableCollection(bot, time) },
            { profitableGrinding(bot, time) }
        )
        return candidates.shuffled().firstNotNullOfOrNull { it() }
    }

    private fun profitableCollection(bot: Bot, duration: Duration): BotScript? {
        val candidates = availableSources(bot).mapNotNull { source ->
            val stock = ProfitGatheringDemand.owned(bot, source.id)
            if (stock >= GATHER_STOCK_CAP) return@mapNotNull null
            val quote = ProfitGatheringDemand.quote(bot, source.id, source.ticksPerItem, marketOnly = true)
                ?: return@mapNotNull null
            val batch = minOf(source.batch, (GATHER_STOCK_CAP - stock).toInt(), quote.quantity)
            source to quote.copy(quantity = batch, score = quote.score * batch / quote.quantity)
        }.toMap()
        val source = ProfitGatheringDemand.select(bot, candidates) ?: return null
        val quote = candidates.getValue(source)
        val target = (ProfitGatheringGoal.physical(bot, source.id) + quote.quantity).toInt()
        return collection(bot, source, target, duration).also {
            it.profitGathering = ProfitGatheringGoal.create(bot, quote, duration)
        }
    }

    private fun profitableGrinding(bot: Bot, duration: Duration): BotScript? {
        val level = bot.herblore.staticLevel
        return Ingredient.entries.shuffled().firstNotNullOfOrNull { ingredient ->
            if (potionReserve(bot, ingredient.id) > 0 ||
                !GrindIngredientBotScript(bot, ingredient, duration).isEligible()) return@firstNotNullOfOrNull null
            BotProductionPlanner.select(bot, SKILL_HERBLORE, level, training = false, duration = duration,
                keyPrefix = "grind:${ingredient.name}", merchantOutput = ingredient.newId,
                maximumCoinsLimit = 0L, directOnly = true)
        }
    }

    private fun availableSources(bot: Bot) = sources.filter {
        bot.combatLevel >= it.minimumCombat && it.zone.entryBlocker(bot) == null
    }

    private fun collection(bot: Bot, source: Source, target: Int, duration: Duration) =
        CollectGroundItemBotScript(bot, source.id, target, duration, mutableListOf(source.zone), persistentOnly = true)

    private fun canWork(bot: Bot) = ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness()

    private fun deficit(bot: Bot, id: Int, target: Int = personalTarget(bot, id)) =
        (target - ProfitGatheringDemand.owned(bot, id)).coerceAtLeast(0).toInt()
}
