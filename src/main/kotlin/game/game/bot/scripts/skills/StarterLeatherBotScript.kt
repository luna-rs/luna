package game.bot.scripts.skills

import api.bot.action.BotSupplySources
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradePurpose
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProductionPlan
import api.predef.SKILL_CRAFTING
import com.google.gson.JsonObject
import engine.bot.coordinator.isEarlyGame
import engine.bot.coordinator.skill.BotProductionPlanner
import engine.bot.coordinator.skill.CraftingScriptFactory
import engine.bot.coordinator.skill.ProductionCatalogue
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.NEEDLE_ID
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.THREAD_ID
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Funds one starter glove batch with native shop purchases and, when needed, soft-leather tanning.
 * The same deadline and allowance follow the batch through a cowhide collection detour and native production.
 *
 * @author lare96
 */
class StarterLeatherBotScript(bot: Bot, private val data: BatchData) : BotScript(bot) {
    override val allowsPaidTravel = false

    /**
     * Retains the original allowance and verified spending across pauses and relogs.
     *
     * @author lare96
     */
    class BatchData : BotScriptData() {
        var deadline = 0L
        var allowance = 0
        var spent = 0
        var collectionAttempted = false

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
            allowance = data.get("allowance").asInt
            spent = data.get("spent").asInt
            collectionAttempted = data.get("collection_attempted").asBoolean
            require(deadline > 0L && spent in 0..allowance)
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
            data.addProperty("allowance", allowance)
            data.addProperty("spent", spent)
            data.addProperty("collection_attempted", collectionAttempted)
        }
    }

    /**
     * A batch sized by available stock and actual GP quotes, including any native tanning fee.
     *
     * @author lare96
     */
    private data class Batch(val amount: Int, val material: Int, val targets: Map<Int, Int>,
                             val ceilings: Map<Int, Long>, val fee: Int, val cost: Long)

    companion object {
        private const val COINS = 995
        private const val MAX_BATCH = 25
        private val leather = Hide.SOFT_LEATHER
        private val gloves = HideArmor.LEATHER_GLOVES
        private val materials = listOf(leather.tan, leather.hide)

        internal fun select(bot: Bot, level: Int, duration: Duration): StarterLeatherBotScript? {
            if (level !in gloves.level until HideArmor.LEATHER_BOOTS.level || duration <= Duration.ZERO) return null
            val allowance = (owned(bot, COINS) * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY))
                .toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (batches(bot, allowance, shopping = true).isEmpty() &&
                (!bot.isEarlyGame() || quote(bot, leather.hide, allowance, shopping = true, collect = true) == null))
                return null
            // The free route from the cow field can take most of a short visit, including two bank stops.
            val deadline = System.currentTimeMillis() + duration.coerceIn(15.minutes, 20.minutes).inWholeMilliseconds
            return StarterLeatherBotScript(bot, BatchData().also {
                it.allowance = allowance
                it.deadline = deadline
            })
        }

        private fun owned(bot: Bot, id: Int): Long =
            bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)

        private fun batches(bot: Bot, coins: Int, shopping: Boolean): List<Batch> = materials
            .mapNotNull { quote(bot, it, coins, shopping) }
            .sortedWith(compareByDescending<Batch> { it.amount }.thenBy { it.cost }.thenBy { it.fee })

        private fun quote(bot: Bot, material: Int, coins: Int, shopping: Boolean, collect: Boolean = false): Batch? {
            val owned = listOf(NEEDLE_ID, THREAD_ID, material).associateWith { owned(bot, it) }
            val quotes = mutableMapOf<Pair<Int, Int>, BotSupplySources.Quote?>()
            for (amount in minOf(MAX_BATCH, bot.inventory.capacity() - 3) downTo 1) {
                val fee = if (material == leather.hide) amount * leather.cost else 0
                if (fee > coins) continue
                val targets = linkedMapOf(NEEDLE_ID to 1, THREAD_ID to amount, material to amount)
                val ceilings = linkedMapOf<Int, Long>()
                var cost = fee.toLong()
                for ((id, target) in targets) {
                    val missing = (target - owned.getValue(id)).coerceAtLeast(0L).toInt()
                    if (missing == 0 || collect && id == material) continue
                    val source = if (!shopping) null else {
                        val key = id to missing
                        if (key !in quotes) quotes[key] = BotSupplySources.quote(bot, id, missing)
                        quotes[key]?.takeIf { it.quantity >= missing }
                    }
                    if (source == null) {
                        cost = coins.toLong() + 1
                        break
                    }
                    cost += source.totalCoins
                    if (cost > coins) break
                    ceilings[id] = source.maximumUnitPrice
                }
                if (cost <= coins) return Batch(amount, material, targets, ceilings, fee, cost)
            }
            return null
        }
    }

    private fun remainingCoins(): Int = minOf((data.allowance - data.spent).toLong(), owned(bot, COINS)).toInt()

    override suspend fun run(): Boolean {
        val attempted = hashSetOf<Int>()
        while (System.currentTimeMillis() < data.deadline && !trainingComplete()) {
            val ready = batches(bot, remainingCoins(), shopping = false).firstOrNull()
            val offer = batches(bot, remainingCoins(), shopping = true).firstOrNull {
                it.material !in attempted && (ready == null || it.amount > ready.amount || it.cost < ready.cost)
            }
            if (offer != null) {
                attempted += offer.material
                buy(offer)
                continue
            }
            if (ready != null) {
                queueProduction(ready)
            } else if (!data.collectionAttempted && bot.isEarlyGame()) {
                val collection = quote(bot, leather.hide, remainingCoins(), shopping = true, collect = true)
                    ?: return true
                buy(collection)
                val funded = quote(bot, leather.hide, remainingCoins(), shopping = false, collect = true)
                    ?: return true
                val time = (data.deadline - System.currentTimeMillis()).milliseconds
                if (time > Duration.ZERO) {
                    val gather = CraftingScriptFactory.getCollectHidesScript(bot, mutableListOf(),
                        pickUpHides = true, stockTarget = funded.amount, duration = minOf(time, 3.minutes))
                    if (gather != null) {
                        data.collectionAttempted = true
                        bot.scriptStack.pushHead(gather)
                        return false
                    }
                }
            }
            return true
        }
        return true
    }

    private suspend fun buy(batch: Batch) {
        val remaining = data.deadline - System.currentTimeMillis()
        if (remaining <= 0L) return
        withTimeoutOrNull(minOf(240_000L, remaining)) {
            val kit = batch.targets.filterKeys { it != batch.material && it in batch.ceilings }
            val kitBudget = remainingCoins() - batch.fee
            if (kit.isNotEmpty() && kitBudget > 0)
                handler.shop.obtainWantedItems(kit, kitBudget, batch.ceilings) { data.spent += it }

            if (batch.material !in batch.ceilings || owned(bot, NEEDLE_ID) == 0L) return@withTimeoutOrNull
            val amount = minOf(batch.amount.toLong(), owned(bot, THREAD_ID)).toInt()
            val fee = if (batch.material == leather.hide) amount * leather.cost else 0
            val budget = remainingCoins() - fee
            if (amount > 0 && budget > 0 && amount > owned(bot, batch.material))
                handler.shop.obtainWantedItems(mapOf(batch.material to amount), budget, batch.ceilings) { data.spent += it }
        }
    }

    private fun queueProduction(batch: Batch) {
        val remaining = (data.deadline - System.currentTimeMillis()).milliseconds
        if (remaining <= Duration.ZERO) return
        val craft = ProductionCatalogue.byOutput.getValue(gloves.id).firstNotNullOf {
            it.describe(bot)?.takeIf { recipe -> recipe.key == "hide:${gloves.name}" }
        }
        val tan = if (batch.material == leather.hide) {
            ProductionCatalogue.byOutput.getValue(leather.tan).firstNotNullOf {
                it.describe(bot)?.takeIf { recipe -> recipe.key == "tan:${leather.name}" }
            }
        } else null
        val plan = ProductionPlan(tan ?: craft, training = true, batch.amount, data.allowance.toLong(),
            (craft.materials.keys + craft.tools + listOfNotNull(tan?.materials?.keys?.single()))
                .associateWith { 1L }, data.deadline, spentCoins = data.spent.toLong(),
            continuationRecipes = if (tan == null) emptyList() else listOf(craft), allowAcquisition = false)
        BotProductionPlanner.continuePlan(bot, plan, remaining)?.let { bot.scriptStack.softPushHead(it) }
    }

    override fun snapshot(): BatchData = data
}
