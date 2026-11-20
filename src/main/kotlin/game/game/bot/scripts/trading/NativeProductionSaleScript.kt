package game.bot.scripts.trading

import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProductionSaleGuard
import com.google.gson.JsonObject
import engine.bot.economy.BotProductionMetrics
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.milliseconds

/**
 * Carries a measured sale batch through one finite native trading visit.
 *
 * @author lare96
 */
class NativeProductionSaleScript(bot: Bot, private val data: SaleData) : BotScript(bot) {

    /**
     * Persists the remaining cargo and the original quote floor when the account logs out.
     *
     * @author lare96
     */
    class SaleData : BotScriptData() {
        lateinit var guard: ProductionSaleGuard
        var remaining = 0
        var retained = 0
        var attempts = 0
        var deadline = 0L
        var quotedPrice = 0L

        override fun save(data: JsonObject) {
            data.add("guard", guard.save())
            data.addProperty("remaining", remaining)
            data.addProperty("retained", retained)
            data.addProperty("attempts", attempts)
            data.addProperty("deadline", deadline)
        }

        override fun load(data: JsonObject) {
            guard = ProductionSaleGuard.load(data.getAsJsonObject("guard"))
            remaining = data.get("remaining").asInt
            retained = data.get("retained").asInt
            attempts = data.get("attempts").asInt
            deadline = data.get("deadline").asLong
            require(remaining >= 0 && retained >= 0 && attempts in 0..2 && deadline > 0L)
        }

        /**
         * Removes only a quantity sold through the final validated trade screen.
         */
        fun settle(bot: Bot, quantity: Int, coins: Long) {
            val cost = guard.costFor(quantity, remaining)
            remaining -= quantity
            guard = guard.copy(batchCost = guard.batchCost - cost)
            if (remaining > 0) NativeOutputDemand.track(bot, guard.identity,
                mapOf(guard.itemId to remaining.toLong()), deadline)
            else NativeOutputDemand.release(guard.identity)
            BotProductionMetrics.recordMerchantSale(quantity, coins, cost,
                (System.currentTimeMillis() - guard.startedAt).coerceAtLeast(0))
        }
    }

    companion object {
        /**
         * Carries new goods only, leaving prior and personally retained stock in the account.
         */
        internal fun createForOutput(bot: Bot, planId: String, itemId: Int, amount: Int, minimumUnitPrice: Long,
                                     deadline: Long): BotScript? {
            if (minimumUnitPrice !in 1..Int.MAX_VALUE.toLong()) return null
            return create(bot, ProductionSaleGuard(itemId, minimumUnitPrice, 0L, System.currentTimeMillis(), planId),
                amount, deadline)
        }

        /**
         * Transfers a production commitment to its sale visit after measuring the final output.
         */
        internal fun create(bot: Bot, guard: ProductionSaleGuard, produced: Int, deadline: Long): BotScript? {
            if (produced < 1) return null
            val minimum = maxOf(guard.minimumPrice,
                ProductionSaleGuard.minimumGross(guard.batchCost, produced.toLong(), taxed = false))
            if (minimum > Int.MAX_VALUE) return null
            val owned = BotTradeManager.transferableAmount(bot, guard.itemId)
            val amount = minOf(produced, owned, BotTradeManager.saleableAmount(bot, guard.itemId))
            if (amount < 1 || !NativeOutputDemand.track(bot, guard.identity,
                    mapOf(guard.itemId to amount.toLong()), deadline)) return null
            return NativeProductionSaleScript(bot, SaleData().also {
                it.guard = guard.copy(minimumPrice = minimum, batchCost = guard.costFor(amount, produced))
                it.remaining = amount
                it.retained = owned - amount
                it.deadline = deadline
            })
        }
    }

    override suspend fun run(): Boolean {
        val time = data.deadline - System.currentTimeMillis()
        val guard = data.guard
        if (time < 1 || data.remaining < 1 || data.attempts >= 2 || !awaitActionSafety()) return true
        val buyer = NativeOutputDemand.traders(bot).mapNotNull { trader ->
            trader.profitGatheringPurchase(guard.itemId)?.takeIf { it.price >= guard.minimumPrice }?.let { trader to it }
        }.maxByOrNull { it.second.item.amount } ?: return true
        if (!NativeOutputDemand.track(bot, guard.identity,
                mapOf(guard.itemId to data.remaining.toLong()), data.deadline)) return true
        data.attempts++
        data.quotedPrice = buyer.second.price
        bot.scriptStack.pushHead(TradeBotScript(bot, BotTradePurpose.SELL, buyer.first.profitMarketplace(),
            time.milliseconds, purchaseDeadline = data.deadline, saleTarget = guard.itemId, fundedSale = data))
        return false
    }

    override suspend fun finish() {
        if (!isPaused()) NativeOutputDemand.release(data.guard.identity)
    }

    override fun snapshot(): SaleData = data
}
