package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotMerchantCapital
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.InventoryBotScript
import api.bot.script.ProductionRecipe
import api.bot.zone.ProductionZones
import api.predef.SKILL_COOKING
import api.predef.SKILL_SMITHING
import engine.bot.economy.BotProductionMetrics
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils
import java.util.WeakHashMap
import kotlin.math.ln1p
import kotlin.time.Duration

/**
 * Resolves prepared native buying visits against a shared shortlist of executable recipe entries.
 *
 * @author lare96
 */
internal object NativeProductionOpportunities {
    private data class Hint(val output: Int, val price: Long, val entry: ProductionCatalogue.Entry)
    private data class Snapshot(val tick: Long, val hints: List<Hint>)
    private val snapshots = WeakHashMap<World, Snapshot>()

    private fun hints(world: World): List<Hint> {
        val tick = world.currentTick
        val saved = snapshots[world]
        if (saved?.tick == tick) return saved.hints
        val hints = NativeOutputDemand.candidateIds(world).take(16).flatMap { output ->
            val price = NativeOutputDemand.peakPrice(world, output)
            ProductionCatalogue.byOutput[output].orEmpty().take(4).map { Hint(output, price, it) }
        }.sortedByDescending { it.price }.take(24)
        snapshots[world] = Snapshot(tick, hints)
        return hints
    }

    private fun choices(bot: Bot): Map<Hint, Double> = hints(bot.world).asSequence()
        .filter { BotCombatActionHandler.mayGainExperience(bot, it.entry.skill) }.take(8)
        .mapNotNull { hint ->
            val recipe = hint.entry.describe(bot) ?: return@mapNotNull null
            if (recipe.level > bot.skill(recipe.skill).level || recipe.valuedProducts.keys != setOf(hint.output))
                return@mapNotNull null
            val baseCost = recipe.materials.entries.sumOf { (id, count) ->
                bot.world.economy.getPrice(id).coerceAtLeast(1L).toDouble() * count
            } + recipe.coinCost
            val revenue = hint.price.toDouble() * recipe.valuedProducts.getValue(hint.output) * recipe.productProbability
            val score = (revenue - baseCost) / (BotProductionPlanner.processingTicks(recipe) + overhead(bot, recipe) / 8.0)
            if (score > 0.0 && score.isFinite()) hint to score else null
        }.take(4).toMap()

    fun merchantHint(bot: Bot): Double = choices(bot).values.maxOrNull() ?: 0.0

    /**
     * Starts one target-specific native quote without opening several competing plans.
     */
    fun merchantScript(bot: Bot, duration: Duration): InventoryBotScript? {
        val selected = RandomUtils.weightedRoll(choices(bot).mapValues { (hint, score) ->
            val skill = hint.entry.skill
            val preferred = if (skill in bot.preferences.skills) 1.5 else 1.0
            val familiar = BotProductionMetrics.latestDecision(bot)?.skill == skill
            ln1p(score) * preferred * if (familiar) 1.25 else 1.0
        }) ?: return null
        val capital = BotMerchantCapital.snapshot(bot)
        if (capital.busy || capital.stockHeavy || capital.cashRecovery) return null
        val skill = selected.entry.skill
        return BotProductionPlanner.select(bot, skill, bot.skill(skill).staticLevel, false, duration,
            finalFood = selected.output.takeIf { skill == SKILL_COOKING && CookingProductionPaths.isFood(it) },
            merchantOutput = selected.output, maximumCoinsLimit = capital.electiveCoins)
    }

    /**
     * Accounts for the bank trip and distance to a suitable native workstation.
     */
    fun overhead(bot: Bot, recipe: ProductionRecipe): Double {
        val stations = when (recipe.skill) {
            SKILL_COOKING -> ProductionZones.RANGES
            SKILL_SMITHING -> if (recipe.key.startsWith("smelt:")) ProductionZones.FURNACES else ProductionZones.ANVILS
            else -> emptyList()
        }
        val distance = stations.minOfOrNull { it.inside.computeLongestDistance(bot.position) } ?: 36
        return 25.0 + distance.coerceIn(0, 160) * 0.6
    }
}
