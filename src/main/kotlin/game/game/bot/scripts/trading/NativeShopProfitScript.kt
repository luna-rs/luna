package game.bot.scripts.trading

import api.bot.action.trading.BotShopResale
import api.bot.action.trading.NativeOutputDemand
import api.bot.action.trading.NativeShopOpportunities
import api.bot.script.DynamicBotScript
import api.bot.script.ProductionSaleGuard
import api.predef.ext.get
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Visits an NPC shop, then banks the stock or hands it to a funded native sale.
 *
 * @author lare96
 */
internal class NativeShopProfitScript(bot: Bot, private val deal: NativeShopOpportunities.Deal) : DynamicBotScript(bot) {
    private var opened: ShopInterface? = null
    private var bankMode: Int? = null
    private var transferred = false

    override suspend fun run(): Boolean {
        val startedAt = System.currentTimeMillis()
        val time = deal.deadline - startedAt
        if (time <= 0L || !current()) return true
        val initial = NativeShopOpportunities.revalidate(bot, deal) ?: return true
        if (!NativeShopOpportunities.reserve(bot, initial)) return true
        withTimeoutOrNull(time) {
            if (!prepare(initial)) return@withTimeoutOrNull
            opened = handler.shop.travelToShop(deal.shop, ::current) ?: return@withTimeoutOrNull
            if (!current()) return@withTimeoutOrNull
            val quote = NativeShopOpportunities.revalidate(bot, initial, wallet()) ?: return@withTimeoutOrNull
            val item = Item(quote.stockId, quote.quantity)
            if (!bot.inventory.hasSpaceFor(item) || !NativeShopOpportunities.reserve(bot, quote)) return@withTimeoutOrNull
            val before = bot.inventory.computeNonDynamicAmountForId(quote.stockId)
            val cashBefore = wallet()
            handler.shop.buyQuoted(quote.shop, quote.stockId, quote.quantity, quote.cost)
            val acquired = bot.inventory.computeNonDynamicAmountForId(quote.stockId) - before
            val spent = cashBefore - wallet()
            if (acquired < 1 || spent !in 1..quote.cost) return@withTimeoutOrNull
            if (bot.preferences.isMerchant) {
                NativeOutputDemand.release(quote.identity)
                if (!ShopPurchaseOffers.queue(bot, listOf(BotShopResale.Batch(quote.id, acquired, spent))))
                    handler.banking.travelToBankDepositAll()
                return@withTimeoutOrNull
            }
            if (!quote.funded) {
                handler.banking.travelToBankDepositAll()
                return@withTimeoutOrNull
            }
            val floor = maxOf(quote.floor, (spent.toLong() + acquired - 1L) / acquired)
            val saleUntil = System.currentTimeMillis() + 180_000L
            val sale = NativeProductionSaleScript.create(bot,
                ProductionSaleGuard(quote.id, floor, spent.toLong(), startedAt, quote.identity),
                acquired, saleUntil) ?: return@withTimeoutOrNull
            bot.scriptStack.softPushHead(sale)
            transferred = true
        }
        return true
    }

    private fun wallet(): Int = bot.inventory.computeNonDynamicAmountForId(Currency.COINS.id)

    private suspend fun prepare(quote: NativeShopOpportunities.Deal): Boolean {
        val coins = Currency.COINS.id
        val item = Item(quote.stockId, quote.quantity)
        if (wallet() >= quote.cost && bot.inventory.hasSpaceFor(item)) return true
        if (!handler.banking.travelToBankOpen() || !current()) return false
        bankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        if (!handler.banking.depositInventory(setOf(coins)) || !current()) return false
        handler.banking.clickBankingMode(false)
        val missing = quote.cost - wallet()
        if (missing > 0 && !handler.banking.withdraw(Item(coins, missing))) return false
        return current() && wallet() >= quote.cost && bot.inventory.hasSpaceFor(item)
    }

    private fun current(): Boolean = bot.state == EntityState.ACTIVE && !bot.client.isPendingLogout &&
        !bot.inWilderness() && isActionSafe() && bot.scriptStack.current() === this

    override suspend fun finish() {
        if (!transferred) NativeOutputDemand.release(deal.identity)
        if (opened != null && bot.overlays[ShopInterface::class] === opened) bot.overlays.closeWindows()
        bankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        opened = null
        bankMode = null
    }
}
