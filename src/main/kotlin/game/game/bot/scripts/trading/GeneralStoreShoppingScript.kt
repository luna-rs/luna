package game.bot.scripts.trading

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.stockIdFor
import api.bot.action.trading.BotFoodDemand
import api.bot.action.trading.BotMerchantCapital
import api.bot.action.trading.BotMerchantProceeds
import api.bot.action.trading.BotShopResale
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.NativeOutputDemand
import api.bot.script.DynamicBotScript
import api.predef.ext.rebalanceWantedItems
import api.predef.ext.totalItemAmount
import api.predef.rand
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Uses a small independent purse to fill wants at a general store and buy surplus at a profit.
 * The follow-up runs after the purchasing activity so a recipe or filling script keeps its own budget and timing.
 *
 * @author lare96
 */
class GeneralStoreShoppingScript private constructor(
    bot: Bot,
    private val shop: Shop,
    private val interest: Int
) : DynamicBotScript(bot) {
    companion object {
        private var Bot.nextGeneralStoreCheck by Attr.long()
        private val coins = Currency.COINS.id

        /**
         * Schedules one follow-up after a real purchase, including purchases made by dedicated supply scripts.
         */
        @JvmStatic
        fun queue(bot: Bot, shop: Shop) {
            val now = System.currentTimeMillis()
            if (now < bot.nextGeneralStoreCheck || bot.scriptStack.scripts.any { it is GeneralStoreShoppingScript }) return
            bot.nextGeneralStoreCheck = now + 120_000L
            val interest = when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> 2
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> 1
                else -> 0
            }
            if (rand(0.60 + 0.20 * interest)) bot.scriptStack.push(GeneralStoreShoppingScript(bot, shop, interest))
        }
    }

    private var overlay: ShopInterface? = null

    override suspend fun run(): Boolean {
        withTimeoutOrNull(45_000L + 30_000L * interest) { shopForSupplies() }
        return true
    }

    private suspend fun shopForSupplies() {
        if (bot.world.shops.lookup(shop.name).orElse(null) !== shop) return
        val budget = (bot.totalItemAmount(coins) * rand(0.01 + 0.02 * interest, 0.04 + 0.03 * interest))
            .toInt().coerceAtMost(25_000)
        if (budget < 1 || handler.shop.shopDistance(shop) == null) return
        bot.preferences.rebalanceWantedItems()
        if (wantedHere().none() && surplusQuote(budget) == null) return
        if (!handler.banking.travelToBankOpen() || !handler.banking.depositInventory(setOf(coins))) return
        val needed = budget - bot.inventory.computeAmountForId(coins)
        if (needed > 0 && !handler.banking.withdraw(Item(coins, needed))) return
        overlay = handler.shop.travelToShop(shop) { isActionSafe() } ?: return
        val startingCoins = bot.inventory.computeAmountForId(coins)
        fun remainingBudget() = budget - (startingCoins - bot.inventory.computeAmountForId(coins))
        for (want in wantedHere().sortedByDescending { it.priority.level }) {
            val food = BotFoodDemand.identity(bot)?.takeIf { BotFoodDemand.handles(bot, want.id) }
            while (remainingBudget() > 0 && isActionSafe()) {
                val missing = want.target - bot.totalItemAmount(want.id)
                if (missing <= 0 || !handler.shop.buyUpTo(shop, want.id, missing, remainingBudget(),
                        expectedFoodIdentity = food)) break
            }
        }
        bot.preferences.rebalanceWantedItems()
        if (wantedHere().none()) buySurplus(remainingBudget())
    }

    private fun wantedHere() = BotTradeIntentions.purchases(bot).asSequence().filter {
        it.id != coins && bot.totalItemAmount(it.id) < it.target &&
            shop.items.computeAmountForId(shop.stockIdFor(it.id)) > 0
    }

    /**
     * A single batch priced from the current shop quote, guide margin and buyer depth.
     *
     * @author lare96
     */
    private data class Surplus(val id: Int, val stockId: Int, val amount: Int, val cost: Int,
                               val floor: Long, val margin: Long, val funded: Boolean)

    private fun surplusQuote(budget: Int): Surplus? {
        if (budget < 1) return null
        val reserves = BotTradeIntentions.retainedStock(bot)
        val capital = if (BotMerchantCapital.active(bot)) BotMerchantCapital.snapshot(bot) else null
        val margin = BotMerchantProceeds.margin(bot)
        val cap = when (interest) {
            2 -> 60
            1 -> 30
            else -> 10
        }
        return shop.items.asSequence().filterNotNull()
            .filter { it.amount > 0 && !it.isDynamic && it.id != coins && it.itemDef.isTradeable }
            .mapNotNull { stock ->
                val id = stock.itemDef.unnotedId.orElse(stock.id)
                if (!BotShopResale.canResell(id) || BotTradeIntentions.productionLimit(bot, id) != null) return@mapNotNull null
                val stocking = bot.preferences.isMerchant && BotShopResale.target(id) > 0
                if (!stocking && (bot.totalItemAmount(id) < (reserves[id] ?: 0) ||
                    BotTradeManager.saleableAmount(bot, id) > 0)) return@mapNotNull null
                val slots = shop.items.computeIndexForId(stock.id)
                if (slots < 0) return@mapNotNull null
                val available = BotShopResale.available(shop.originalStock(slots), stock.amount)
                val demand = BotShopResale.demand(bot, id)
                val guide = BotShopResale.guide(bot.world, id).toLong()
                val quiet = BotShopResale.quietBatch(id, bot.preferences.isMerchant)
                val maximum = minOf(available, cap, BotShopResale.remainingStock(bot, id),
                    (demand(1) + quiet.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                (maximum downTo 1).firstNotNullOfOrNull { amount ->
                    if (!bot.inventory.hasSpaceFor(Item(stock.id, amount))) return@firstNotNullOfOrNull null
                    val cost = shop.computeBuyValue(stock.id, amount).orElse(-1)
                    if (cost !in 1..budget || capital?.permitsShopPurchase(id, amount, cost.toLong()) == false)
                        return@firstNotNullOfOrNull null
                    val floor = BotShopResale.floor(cost, amount, taxed = false, margin)
                    val funded = demand(floor)
                    if (!BotShopResale.permitsQuantity(amount, funded, quiet, guide, floor))
                        return@firstNotNullOfOrNull null
                    Surplus(id, stock.id, amount, cost, floor, (guide - floor).coerceAtLeast(0L) * amount +
                        minOf(amount, funded), funded >= amount)
                }
            }.maxByOrNull { it.margin }
    }

    private suspend fun buySurplus(budget: Int) {
        if (!isActionSafe()) return
        val quote = surplusQuote(minOf(budget, bot.inventory.computeAmountForId(coins))) ?: return
        val identity = UUID.randomUUID().toString()
        val deadline = System.currentTimeMillis() + 120_000L
        val quantity = mapOf(quote.id to quote.amount.toLong())
        val reserved = if (quote.funded) NativeOutputDemand.reserve(bot, identity, quantity,
            deadline, minimumPrices = mapOf(quote.id to quote.floor))
        else NativeOutputDemand.track(bot, identity, quantity, deadline)
        if (!reserved) return
        val before = bot.inventory.computeNonDynamicAmountForId(quote.stockId)
        val coinsBefore = bot.inventory.computeNonDynamicAmountForId(coins)
        handler.shop.buyQuoted(shop, quote.stockId, quote.amount, quote.cost)
        val acquired = bot.inventory.computeNonDynamicAmountForId(quote.stockId) - before
        if (acquired > 0 && bot.preferences.isMerchant) {
            NativeOutputDemand.release(identity)
            val spent = coinsBefore - bot.inventory.computeNonDynamicAmountForId(coins)
            if (spent <= 0 || !ShopPurchaseOffers.queue(bot, listOf(BotShopResale.Batch(quote.id, acquired, spent))))
                handler.banking.travelToBankDepositAll()
            return
        }
        if (acquired > 0) {
            val sale = if (quote.funded) NativeProductionSaleScript.createForOutput(bot, identity, quote.id,
                acquired, quote.floor, deadline) else null
            if (sale != null) {
                bot.scriptStack.softPushHead(sale)
                return
            }
        }
        NativeOutputDemand.release(identity)
        if (acquired > 0) handler.banking.travelToBankDepositAll()
    }

    override suspend fun finish() {
        if (overlay != null && bot.overlays.getOverlay(ShopInterface::class.java) === overlay) bot.overlays.closeWindows()
        overlay = null
    }
}
