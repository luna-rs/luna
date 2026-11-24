package game.bot.scripts.trading

import api.bot.action.BotSupplySources
import api.bot.action.stockIdFor
import api.bot.action.trading.ProfitGatheringDemand
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProfitGatheringGoal
import api.predef.ext.get
import com.google.gson.JsonObject
import engine.bot.coordinator.HerbloreSecondaryActivities
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Buys and banks one wanted-stock restock using the shop's actual stocked item form.
 * The native purchase rechecks the quote and uses only the trip's reserved coin allowance.
 *
 * @author lare96
 */
class BuyHerbloreSecondaryBotScript(bot: Bot, private val data: SecondaryPurchaseData) : BotScript(bot) {
    override val allowsPaidTravel = false
    private var previousBankMode: Int? = null
    private var shopWindow: ShopInterface? = null

    /**
     * Retains the original target, price ceiling, and unspent budget across interruptions.
     *
     * @author lare96
     */
    class SecondaryPurchaseData : BotScriptData() {
        var itemId = -1
        var shopName = ""
        var target = 0
        var remainingCoins = 0
        var unitPrice = 0L
        var deadline = 0L
        var needsBanking = false

        override fun load(data: JsonObject) {
            itemId = data.get("itemId")?.asInt ?: -1
            shopName = data.get("shopName")?.asString ?: ""
            target = data.get("target")?.asInt ?: 0
            remainingCoins = data.get("remainingCoins")?.asInt ?: 0
            unitPrice = data.get("unitPrice")?.asLong ?: 0L
            deadline = data.get("deadline")?.asLong ?: 0L
            needsBanking = data.get("needsBanking")?.asBoolean ?: false
        }

        override fun save(data: JsonObject) {
            data.addProperty("itemId", itemId)
            data.addProperty("shopName", shopName)
            data.addProperty("target", target)
            data.addProperty("remainingCoins", remainingCoins)
            data.addProperty("unitPrice", unitPrice)
            data.addProperty("deadline", deadline)
            data.addProperty("needsBanking", needsBanking)
        }
    }

    companion object {
        private const val BATCH = 12
        private const val MAX_SHOP_DISTANCE = 280L

        internal fun candidate(bot: Bot, id: Int, target: Int): BuyHerbloreSecondaryBotScript? {
            val owned = ProfitGatheringGoal.physical(bot, id)
            val missing = (target - ProfitGatheringDemand.owned(bot, id)).coerceIn(0L, BATCH.toLong()).toInt()
            val coins = ProfitGatheringGoal.physical(bot, Currency.COINS.id)
            if (missing < 1 || coins < 1) return null
            val quote = BotSupplySources.visits(bot, id, missing).asSequence()
                .filter { it.unitPrice in 1..coins && it.distance <= MAX_SHOP_DISTANCE }
                .minWithOrNull(compareBy({ it.unitPrice }, { it.distance })) ?: return null
            val shopName = quote.shop?.name ?: return null
            val amount = minOf(quote.quantity.toLong(), coins / quote.unitPrice)
            return BuyHerbloreSecondaryBotScript(bot, SecondaryPurchaseData().also {
                it.itemId = id
                it.shopName = shopName
                it.target = (owned + amount).toInt()
                it.remainingCoins = (amount * quote.unitPrice).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                it.unitPrice = quote.unitPrice
                it.deadline = System.currentTimeMillis() + 4.minutes.inWholeMilliseconds
            })
        }
    }

    override suspend fun run(): Boolean {
        val id = data.itemId
        val remaining = data.deadline - System.currentTimeMillis()
        if (id !in HerbloreSecondaryActivities.shopSecondaries || remaining <= 0 || data.unitPrice < 1 ||
            !isActionSafe() || bot.inWilderness()) return true
        withTimeoutOrNull(remaining) {
            if (!data.needsBanking && data.remainingCoins > 0 && shortfall() > 0) {
                val shop = bot.world.shops.lookup(data.shopName).orElse(null)
                if (shop != null && shop.currency == Currency.COINS) purchase(shop)
            }
            if (data.needsBanking && handler.banking.travelToBankDepositAll()) data.needsBanking = false
        }
        return true
    }

    private fun shortfall(): Int = minOf(
        data.target - ProfitGatheringGoal.physical(bot, data.itemId),
        HerbloreSecondaryActivities.personalTarget(bot, data.itemId) - ProfitGatheringDemand.owned(bot, data.itemId)
    ).coerceIn(0L, BATCH.toLong()).toInt()

    private suspend fun purchase(shop: Shop) {
        val coins = Currency.COINS.id
        val stockId = shop.stockIdFor(data.itemId)
        if (bot.inventory.computeAmountForId(coins) < data.remainingCoins ||
            !bot.inventory.hasSpaceFor(Item(stockId))) {
            if (!handler.banking.travelToBankOpen() || !isActionSafe()) return
            previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
            handler.banking.clickBankingMode(false)
            if (!bot.inventory.hasSpaceForAll(listOf(Item(coins), Item(stockId))) &&
                !handler.banking.depositInventory(setOf(coins))) return
            val withdraw = minOf(data.remainingCoins - bot.inventory.computeAmountForId(coins),
                bot.bank.computeAmountForId(coins))
            if (withdraw > 0 && !handler.banking.withdraw(Item(coins, withdraw))) return
        }
        shopWindow = handler.shop.travelToShop(shop) { isActionSafe() && !bot.inWilderness() && shortfall() > 0 }
            ?: return
        val budget = minOf(data.remainingCoins, bot.inventory.computeAmountForId(coins))
        val quantity = minOf(shortfall(), shop.items.computeAmountForId(stockId))
        for (amount in quantity downTo 1) {
            val cost = shop.computeBuyValue(stockId, amount).orElse(-1)
            if (cost !in 1..budget || cost.toLong() > data.unitPrice * amount ||
                !bot.inventory.hasSpaceFor(Item(stockId, amount))) continue
            if (handler.shop.buyQuoted(shop, stockId, amount, cost)) {
                data.remainingCoins = 0
                data.needsBanking = true
            }
            return
        }
    }

    override suspend fun finish() {
        if (shopWindow != null && bot.overlays[ShopInterface::class] === shopWindow) bot.overlays.closeWindows()
        shopWindow = null
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
    }

    override fun snapshot() = data
}
