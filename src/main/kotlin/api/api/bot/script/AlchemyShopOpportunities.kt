package api.bot.script

import api.attr.Attr
import api.bot.action.BotSupplySources
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradePurpose
import api.predef.SKILL_MAGIC
import api.predef.ext.totalItemAmount
import api.predef.itemDef
import game.bot.scripts.trading.TradeBotScript
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.mob.bot.Bot

/**
 * Quotes one finite alchemy basket from prepared native sellers and stocked shops.
 *
 * @author lare96
 */
internal object AlchemyShopOpportunities {
    private const val BATCH_SIZE = 28
    private const val RETRY_MILLIS = 10 * 60_000L
    private val nextAttempt = Attr.long()

    fun attempted(bot: Bot, now: Long) {
        bot.attributes()[nextAttempt.attr] = now + RETRY_MILLIS
    }

    fun select(bot: Bot, type: AlchemyType, allowed: Set<Int>, profit: Boolean): ProductionPlan? {
        val now = System.currentTimeMillis()
        if (now < bot.attributes()[nextAttempt.attr]) return null
        fun owned(id: Int) = bot.totalItemAmount(id).toLong()
        val budget = (owned(995) * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        if (budget < 1L) return null
        val runes = AlchemyPolicy.runeCosts(bot, type, AlchemyPolicy.resolveFireStaff(bot))
        val direct = TradeBotScript.preparedSaleQuotes(bot, allowed + runes.map { it.id })
        val inputs = allowed.asSequence().shuffled().filter { owned(it) == 0L && itemDef(it).value > 0 }
        val available = (direct.keys.asSequence().filter { it in allowed && owned(it) == 0L } +
            inputs.filterNot(direct::containsKey).filter { BotSupplySources.quote(bot, it, 1) != null })
            .take(12)
        val plans = available.mapNotNull { target ->
            val materials = linkedMapOf(target to 1)
            for (rune in runes) materials.merge(rune.id, rune.amount, Int::plus)
            val prices = HashMap<Int, Long>()
            var operations = BATCH_SIZE
            for ((id, amount) in materials) {
                val stock = owned(id)
                val offer = direct[id]
                val shop = BotSupplySources.quote(bot, id, BATCH_SIZE * amount)
                val price = listOfNotNull(offer?.price, shop?.maximumUnitPrice).minOrNull()
                val availableUnits = (offer?.takeIf { it.price == price }?.item?.amount ?: 0).toLong() +
                    (shop?.takeIf { it.maximumUnitPrice == price }?.quantity ?: 0)
                operations = minOf(operations, ((stock + availableUnits) / amount).coerceAtMost(BATCH_SIZE.toLong()).toInt())
                if (operations == 0) return@mapNotNull null
                val guide = bot.world.economy.getPrice(id).coerceAtLeast(1L)
                prices[id] = if (stock > 0L) maxOf(guide, price ?: 0L) else price ?: return@mapNotNull null
            }
            val payout = (itemDef(target).value * type.valueMultiplier).toInt().coerceAtLeast(1)
            val recipe = ProductionRecipe("alchemy:${type.name}:$target", SKILL_MAGIC, type.level, materials,
                emptySet(), mapOf(995 to payout), type.xp)
            val quote = ProductionEconomics.quote(recipe, false, operations, Double.MAX_VALUE,
                materials.keys.associateWith(::owned), budget, prices, mapOf(995 to 1L), allowLoss = !profit)
                ?: return@mapNotNull null
            ProductionPlan(recipe, false, quote.operations, quote.maximumCoins, quote.maximumUnitPrices,
                now + RETRY_MILLIS)
        }.toList()
        return plans.randomOrNull()
    }
}
