package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.BotScript
import api.bot.script.ProductionMode
import api.bot.script.ProductionStockGoal
import api.predef.*
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import api.predef.ext.wantedItemsToAcquire
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.skills.MillFlourBotScript
import game.content.flourMill.FlourMill
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.util.RandomUtils
import kotlin.math.roundToLong
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Dispatches the native output-producing methods under one of two economic purposes.
 *
 * @author lare96
 */
object ProductionCoordinator : BotCoordinator {
    internal val productionSkills = setOf(SKILL_CRAFTING, SKILL_FLETCHING, SKILL_HERBLORE, SKILL_SMITHING,
        SKILL_COOKING, SKILL_MAGIC, SKILL_RUNECRAFTING, SKILL_FARMING)

    override fun accept(bot: Bot) {
        select(bot, ProductionMode.PRODUCTION, SkillingScriptFactory.getDuration(bot))?.let { bot.scriptStack.push(it) }
    }

    internal fun select(bot: Bot, mode: ProductionMode, duration: Duration,
                        skillWeights: Map<Int, Double>? = null): BotScript? {
        if (!BotScript.ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness()) return null
        val deficits = if (mode == ProductionMode.PRODUCTION) outputDeficits(bot) else emptyMap()
        val weights = productionSkills.asSequence()
            .filter { BotCombatActionHandler.mayGainExperience(bot, it) }
            .associateWith { skillWeights?.get(it) ?: (if (it in bot.preferences.skills) 1.5 else 1.0) }
            .toMutableMap()
        while (weights.isNotEmpty()) {
            val skill = RandomUtils.weightedRoll(weights) ?: return null
            weights.remove(skill)
            val script = selectSkill(bot, skill, mode, duration, deficits) ?: continue
            bot.profitMode = mode == ProductionMode.PROFIT
            bot.memory.diary.training(skill)
            return script
        }
        return null
    }

    internal fun selectSkill(bot: Bot, skill: Int, mode: ProductionMode, duration: Duration,
                             deficits: Map<Int, Int>): BotScript? {
        return when (skill) {
            SKILL_RUNECRAFTING -> RunecraftingScriptFactory.getProductionScript(bot, mode, duration, deficits)
            SKILL_FARMING -> FarmingScriptFactory.getProductionScript(bot, mode, duration, deficits)
            else -> {
                if (skill == SKILL_COOKING)
                    MillFlourBotScript.sharedSession(bot, mode, duration, deficits[FlourMill.POT_OF_FLOUR] ?: 0)
                        ?.let { return it }
                BotProductionPlanner.select(bot, skill, bot.skill(skill).staticLevel, training = false,
                    duration = duration, mode = mode, outputDeficits = deficits)
            }
        }
    }

    internal fun recipeWeight(bot: Bot, fraction: Double, workTicks: Double, stocked: Boolean,
                              hasBuyer: Boolean): Double {
        val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)
        val merchanting = merchantingAffinity(bot)
        val readiness = if (stocked) 1.25 else 1.0
        val efficiency = 1.0 / (1.0 + workTicks / 600.0)
        val demand = if (hasBuyer) 1.0 + 0.50 * merchanting else 1.0
        return fraction.coerceAtLeast(0.001).pow(1.0 + intelligence) *
            efficiency.pow(intelligence) * readiness * demand
    }

    private fun merchantingAffinity(bot: Bot): Double =
        bot.preferences.activities[BotActivity.MERCHANTING]?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0

    private fun bufferedTarget(base: Int, affinity: Double): Long =
        base.toLong() + (base * (0.10 + 0.10 * affinity)).roundToLong()

    internal fun stockAmount(bot: Bot, id: Int): Long = stockAmount(bot, id, emptyMap())

    private fun stockAmount(bot: Bot, id: Int, pending: Map<Int, Long>): Long =
        bot.totalItemAmount(id).toLong() + (pending[id] ?: 0L) +
            if (id == 1993 && ItemDefinition.ALL.get(1995).isPresent) bot.totalItemAmount(1995) else 0

    internal fun deficit(bot: Bot, goal: ProductionStockGoal): Int =
        (goal.target - stockAmount(bot, goal.itemId)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    internal fun orderedQuantity(bot: Bot, id: Int): Int = NativeOutputDemand.depth(bot, id).maxOfOrNull { it.quantity } ?: 0

    /**
     * Subtracts physical and incoming stock once from wanted and personal targets. Propagates a shortlist of
     * unfinished needs to manufacturable ingredients so preparation has its own finite target.
     */
    internal fun outputDeficits(bot: Bot): Map<Int, Int> {
        val pending = emptyMap<Int, Long>()
        val stock = HashMap<Int, Long>()
        fun owned(id: Int): Long = stock.getOrPut(id) { stockAmount(bot, id, pending) }
        val now = System.currentTimeMillis()
        val commitments = BotTradeIntentions.committedSupplyMinimums(bot, now)
        val desired = HashMap<Int, Long>()
        val wants = bot.preferences.wantedItemsToAcquire() +
            commitments.keys.mapNotNull(bot.preferences::getWantedItem)
        for (wanted in wants.distinctBy { it.id }) {
            val level = if (wanted.skill == -1) bot.combatLevel else bot.skill(wanted.skill).staticLevel
            if (!bot.canUseWantedItem(wanted.id) || wanted.target < 1 ||
                wanted.maxLevel != -1 && level > wanted.maxLevel) continue
            val repeatUse = wanted.min >= 0 && wanted.target > 1 &&
                (WantedItemTag.SKILLING_CONSUMABLE in wanted.tags || WantedItemTag.COMBAT_CONSUMABLE in wanted.tags) &&
                ItemDefinition.ALL.get(wanted.id).map { it.isTradeable }.orElse(false)
            val committed = if (repeatUse) commitments[wanted.id] ?: 0 else 0
            if (owned(wanted.id) >= wanted.target.toLong() + committed) continue
            val target = if (repeatUse) bufferedTarget(wanted.target, merchantingAffinity(bot)) else wanted.target.toLong()
            desired[wanted.id] = (target + committed).coerceAtMost(Int.MAX_VALUE.toLong())
        }
        for (id in BotTradeIntentions.supplyDemandIds(bot, now)) {
            val supply = BotTradeIntentions.supplyDemand(bot, id, now) ?: continue
            desired.merge(id, supply.target.toLong(), ::maxOf)
        }
        val deficits = HashMap<Int, Int>()
        for ((id, target) in desired) {
            val missing = (target - owned(id)).coerceAtLeast(0L).toInt()
            if (missing > 0) deficits[id] = missing
        }
        val visited = HashSet<Int>()
        val queue = ArrayDeque(deficits.keys.filter { it in ProductionCatalogue.byOutput }.sorted()
            .map { it to 0 })
        while (queue.isNotEmpty() && visited.size < 64) {
            val (id, depth) = queue.removeFirst()
            if (!visited.add(id) || depth >= 3) continue
            val goal = deficits.getValue(id)
            for (entry in ProductionCatalogue.byOutput[id].orEmpty().take(8)) {
                val recipe = entry.describe(bot)?.takeIf {
                    bot.skill(it.skill).level >= it.level && BotCombatActionHandler.mayGainExperience(bot, it.skill)
                } ?: continue
                val quantity = recipe.valuedProducts[id] ?: continue
                val operations = (goal + quantity.toLong() - 1L) / quantity
                for ((ingredient, amount) in recipe.materials) {
                    if (ingredient !in ProductionCatalogue.byOutput && ingredient != FlourMill.POT_OF_FLOUR) continue
                    val missing = (operations * amount - owned(ingredient)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    if (missing == 0) continue
                    deficits.merge(ingredient, missing, ::maxOf)
                    if (ingredient !in visited) queue.add(ingredient to depth + 1)
                }
            }
        }
        return deficits
    }
}
