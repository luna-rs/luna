package game.bot.scripts.trading

import api.bot.action.trading.MerchantStoreBargains
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import api.predef.ext.get
import api.predef.world
import com.google.gson.JsonObject
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Visits one previously selected general-store bargain with finite quantity, money and action budgets.
 * The native shop action rechecks and executes each quoted purchase without a suspension between price and payment.
 * A paused market session resumes afterwards and rebuilds its offers from the genuinely acquired resale stock.
 *
 * @author lare96
 */
class BuyStoreBargainBotScript private constructor(
    bot: Bot,
    private val shopName: String,
    private val itemId: Int,
    private var remainingQuantity: Int,
    private var remainingBudget: Int,
    private val resaleUnit: Long,
    private val expiresAt: Long,
    private var attempts: Int,
    private var purchased: Boolean
) : BotScript(bot) {
    private var ownOverlay: ShopInterface? = null
    private var prepared = false
    private var previousBankMode: Int? = null

    companion object {
        private const val MAX_ACTIONS = 1

        /**
         * Retains the selected item and the unspent budgets rather than reserving mutable shop stock.
         * An expired or unavailable opportunity completes on restoration without another scan or a replacement trip.
         *
         * @author lare96
         */
        class BargainData : BotScriptData() {
            var shopName = ""
            var itemId = -1
            var remainingQuantity = 0
            var remainingBudget = 0
            var resaleUnit = 0L
            var expiresAt = 0L
            var attempts = 0
            var purchased = false

            override fun load(data: JsonObject) {
                shopName = data.get("shopName")?.asString ?: ""
                itemId = data.get("itemId")?.asInt ?: -1
                remainingQuantity = data.get("remainingQuantity")?.asInt ?: 0
                remainingBudget = data.get("remainingBudget")?.asInt ?: 0
                resaleUnit = data.get("resaleUnit")?.asLong ?: 0L
                expiresAt = data.get("expiresAt")?.asLong ?: 0L
                attempts = data.get("attempts")?.asInt ?: 0
                purchased = data.get("purchased")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                data.addProperty("shopName", shopName)
                data.addProperty("itemId", itemId)
                data.addProperty("remainingQuantity", remainingQuantity)
                data.addProperty("remainingBudget", remainingBudget)
                data.addProperty("resaleUnit", resaleUnit)
                data.addProperty("expiresAt", expiresAt)
                data.addProperty("attempts", attempts)
                data.addProperty("purchased", purchased)
            }
        }
    }

    internal constructor(bot: Bot, deal: MerchantStoreBargains.Deal) : this(bot, deal.shop.name, deal.itemId,
        deal.quantity, deal.budget, deal.resaleUnit, deal.expiresAt, 0, false)

    constructor(bot: Bot, data: BargainData) : this(bot, data.shopName, data.itemId,
        data.remainingQuantity, data.remainingBudget, data.resaleUnit, data.expiresAt, data.attempts, data.purchased)

    private fun usable(): Boolean = remainingQuantity in 1..100 && remainingBudget > 0 && attempts in 0 until MAX_ACTIONS &&
        resaleUnit > 0 && System.currentTimeMillis() < expiresAt && bot.preferences.isMerchant && isActionSafe()

    private fun opportunity(shop: Shop): MerchantStoreBargains.Deal = MerchantStoreBargains.Deal(
        shop, itemId, remainingQuantity, remainingBudget, resaleUnit, expiresAt)

    private fun currentDeal(shop: Shop, budget: Int = remainingBudget): MerchantStoreBargains.Deal? =
        if (!usable()) null else MerchantStoreBargains.revalidate(bot, opportunity(shop), remainingQuantity, budget)

    private suspend fun prepareInventory(deal: MerchantStoreBargains.Deal): Boolean {
        val carried = bot.inventory.computeAmountForId(Currency.COINS.id)
        if (carried > 0 && bot.inventory.hasSpaceFor(Item(itemId, deal.quantity)) &&
            currentDeal(deal.shop, minOf(carried, remainingBudget)) != null) return true
        if (!handler.banking.travelToBankOpen() || !usable()) return false
        if (previousBankMode == null) previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        handler.banking.clickBankingMode(false)
        if (!handler.banking.depositInventory(setOf(Currency.COINS.id)) || !usable()) return false
        val missing = (remainingBudget - bot.inventory.computeAmountForId(Currency.COINS.id)).coerceAtLeast(0)
        if (missing > 0 && bot.bank.contains(Currency.COINS.id)) {
            handler.banking.withdraw(Item(Currency.COINS.id, missing))
        }
        return usable() && bot.inventory.computeAmountForId(Currency.COINS.id) > 0 &&
            bot.inventory.hasSpaceFor(Item(itemId))
    }

    override suspend fun run(): Boolean {
        if (!usable()) return true
        val shop = world.shops.lookup(shopName).orElse(null) ?: return true
        val initial = currentDeal(shop) ?: return true
        withTimeoutOrNull((expiresAt - System.currentTimeMillis()).coerceAtMost(120_000L)) {
            if (!prepared) {
                if (!prepareInventory(initial) || !usable()) return@withTimeoutOrNull
                prepared = true
            }
            if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) return@withTimeoutOrNull
            if (!usable()) return@withTimeoutOrNull
            val wallet = minOf(remainingBudget, bot.inventory.computeAmountForId(Currency.COINS.id))
            val deal = currentDeal(shop, wallet) ?: return@withTimeoutOrNull
            val amount = deal.quantity
            if (!bot.inventory.hasSpaceFor(Item(itemId, amount))) return@withTimeoutOrNull
            val overlay = ShopInterface(shop)
            bot.overlays.open(overlay)
            ownOverlay = overlay
            if (bot.overlays[ShopInterface::class] !== overlay) return@withTimeoutOrNull

            val cost = shop.computeBuyValue(itemId, amount).orElse(-1)
            if (cost !in 1..wallet) return@withTimeoutOrNull
            val beforeItems = bot.inventory.computeAmountForId(itemId)
            val beforeCoins = bot.inventory.computeAmountForId(Currency.COINS.id)
            attempts++
            val bought = handler.shop.buyQuoted(shop, itemId, amount, cost)
            val acquired = bot.inventory.computeAmountForId(itemId) - beforeItems
            val spent = beforeCoins - bot.inventory.computeAmountForId(Currency.COINS.id)
            remainingQuantity = (remainingQuantity - acquired.coerceAtLeast(0)).coerceAtLeast(0)
            remainingBudget = (remainingBudget - spent.coerceAtLeast(0)).coerceAtLeast(0)
            purchased = bought && acquired == amount && spent == cost
        }
        return true
    }

    override suspend fun finish() {
        if (!purchased && !isPaused()) MerchantStoreBargains.failed(bot)
        if (ownOverlay != null && bot.overlays[ShopInterface::class] === ownOverlay) bot.overlays.closeWindows()
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
        ownOverlay = null
        prepared = false
    }

    override fun snapshot() = BargainData().also {
        it.shopName = shopName
        it.itemId = itemId
        it.remainingQuantity = remainingQuantity
        it.remainingBudget = remainingBudget
        it.resaleUnit = resaleUnit
        it.expiresAt = expiresAt
        it.attempts = attempts
        it.purchased = purchased
    }
}
