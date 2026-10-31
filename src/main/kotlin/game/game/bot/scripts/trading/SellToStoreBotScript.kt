
package game.bot.scripts.trading

import api.bot.script.DynamicBotScript
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import api.predef.ext.get
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot

/**
 * Sells an ordinary bot's actual surplus to registered GP shops while the bot is at home.
 *
 * Active supplies, personal cosmetic copies and useful equipment share marketplace retention rules. Broad default
 * wanted targets do not prevent gathered loot from being sold. Merchants never use this script. A trip starts only
 * when actual transferable surplus has an accepting GP shop, and its original quantities cap the entire visit.
 *
 * Items are banked and withdrawn in batches, leaving room for proceeds. Each batch refreshes equipment retention;
 * each sale checks current ownership and newly recorded supply requirements. Equipped and dynamic items are protected.
 *
 * Shop currency, bot-access restrictions, and selling policies remain authoritative. Shops that
 * cannot be opened or stop accepting an item are skipped for the remainder of that item's sale.
 *
 * This is a dynamic script because the shopping trip has no state that must survive logout or restart.
 *
 * @param bot The bot performing the selling activity.
 * @author lare96
 */
class SellToStoreBotScript(bot: Bot) : DynamicBotScript(bot) {

    private var retainedStock = emptyMap<Int, Int>()
    private var startedTrip = false

    override suspend fun run(): Boolean {
        if (bot.preferences.isMerchant) return true
        val surplus = saleCandidates()
        if (surplus.isEmpty()) return true
        startedTrip = true
        if (!travelHome()) {
            bot.log("Could not travel home to sell items.")
            return true
        }

        retainedStock = BotTradeIntentions.retainedStock(bot)
        for (item in surplus) {
            val id = item.id
            var remaining = item.amount
            val failedShops = HashSet<Shop>()

            while (remaining > 0) {
                if (shopsFor(id).none { it !in failedShops }) {
                    break
                }
                val available = minOf(remaining, sellableAmount(id))
                if (available < 1) {
                    break
                }

                // Banking normalizes noted inventory items and leaves a free slot for sale proceeds.
                if (!handler.banking.travelToBankDepositAll()) {
                    bot.log("Could not prepare inventory for selling.")
                    return true
                }
                retainedStock = BotTradeIntentions.retainedStock(bot)
                handler.banking.clickBankingMode(false)
                // Withdrawal selects by id and cannot safely choose a normal item from mixed dynamic stock.
                if (bot.bank.any { it != null && it.id == id && it.isDynamic }) break
                val capacity = if (itemDef(id).isStackable) Int.MAX_VALUE else
                    (bot.inventory.computeRemainingSize() - 1).coerceAtLeast(0)
                val amount = minOf(available, sellableAmount(id), bot.bank.computeAmountForId(id), capacity)
                if (amount < 1 || !handler.banking.withdraw(Item(id, amount))) {
                    break
                }
                if (!travelHome()) {
                    return true
                }

                val before = bot.inventory.computeAmountForId(id)
                for (shop in shopsFor(id).filterNot { it in failedShops }.shuffled()) {
                    if (!openShop(shop)) {
                        failedShops += shop
                        continue
                    }
                    remaining -= sellItem(shop, id, remaining)
                    if (bot.inventory.computeAmountForId(id) < 1 || remaining < 1 || sellableAmount(id) < 1) {
                        break
                    }
                    // This shop stopped accepting the item; try another instead of repeating failed clicks.
                    failedShops += shop
                }
                if (bot.inventory.computeAmountForId(id) >= before) {
                    break
                }
            }
        }
        return true
    }

    /**
     * Shares actual marketplace surplus and prices while excluding stock without an accessible shop buyer.
     */
    internal fun saleCandidates(): List<Item> {
        if (bot.preferences.isMerchant) return emptyList()
        return BotTradeManager.computeOfferItems(bot, BotTradePrice.EXACT, BotTradePurpose.SELL)
            .filter { shopsFor(it.item.id).isNotEmpty() }
            .sortedByDescending { it.totalPrice() }.map { it.item }
    }

    /**
     * Computes the quantity of an item that can be sold without violating its retention target.
     *
     * Uses this banking batch's equipment retention and any newly recorded supply target. Ownership and
     * non-dynamic transferable quantities are checked again after every suspension without rescanning all gear.
     *
     * @param id The item id to check.
     * @return The surplus quantity available for sale, clamped between zero and [Int.MAX_VALUE].
     */
    internal fun sellableAmount(id: Int): Int {
        if (bot.preferences.isMerchant) return 0
        val retained = maxOf(retainedStock[id] ?: 0, BotTradeIntentions.supplyTarget(bot, id))
        val transferable = (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).asSequence()
            .filter { !it.isDynamic && it.itemDef.unnotedId.orElse(it.id) == id }
            .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return minOf(transferable,
            (bot.totalItemAmount(id).toLong() - retained).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * Sells an item to the specified shop without exceeding the permitted sale quantity.
     *
     * Continues while the correct shop is open, the bot remains at home, and the shop still
     * authorizes the bot and accepts the item. Each transaction rechecks the current inventory
     * quantity and retained stock before choosing a Sell-1, Sell-5, or Sell-10 action.
     *
     * Successful sales are counted using the actual inventory decrease. Processing stops when
     * an action fails, makes no progress, or the shop is no longer eligible.
     *
     * @param shop The shop receiving the items.
     * @param id The item id being sold.
     * @param maxAmount The maximum quantity this invocation may sell.
     * @return The total quantity successfully sold during this invocation.
     */
    private suspend fun sellItem(shop: Shop, id: Int, maxAmount: Int): Int {
        var sold = 0
        while (!bot.preferences.isMerchant && bot.overlays[ShopInterface::class]?.shop === shop &&
            SubZone.HOME in bot.subZones && shop.botAccess.apply(bot) && shop.computeCanSell(id)) {
            if (bot.inventory.any { it != null && it.id == id && it.isDynamic }) break
            val available = minOf(maxAmount - sold, sellableAmount(id), bot.inventory.computeAmountForId(id))
            if (available < 1) {
                break
            }
            val before = bot.inventory.computeAmountForId(id)
            val success = when {
                available >= 10 -> handler.shop.sell10(id)
                available >= 5 -> handler.shop.sell5(id)
                else -> handler.shop.sell1(id)
            }.await()
            val decrease = before - bot.inventory.computeAmountForId(id)
            if (!success || decrease < 1) {
                break
            }
            sold += decrease
        }
        return sold
    }

    /**
     * Finds registered shops that currently accept an item from this bot.
     *
     * Only GP shops are considered. Each candidate must authorize the bot through its access
     * predicate and permit the item through its selling policy.
     *
     * @param id The item id the bot wants to sell.
     * @return The currently eligible shops, or an empty list if none are available.
     */
    private fun shopsFor(id: Int): List<Shop> {
        return world.shops.values()
            .filter { it.currency == Currency.COINS && it.botAccess.apply(bot) && it.computeCanSell(id) }
    }

    /**
     * Ensures the bot is within the home subzone before accessing shops remotely.
     *
     * Does not initiate travel when the bot is already at home.
     *
     * @return `true` if the bot is already at home or the travel action succeeds.
     */
    private suspend fun travelHome(): Boolean {
        return SubZone.HOME in bot.subZones || handler.webWalkTo(SubZone.HOME)
    }

    /**
     * Opens a shop interface when the bot is at home and authorized to access the shop.
     *
     * Closes existing overlays before opening the requested shop. Successful opening is verified
     * by checking that the active shop interface references the requested shop instance.
     *
     * @param shop The shop to open.
     * @return `true` if the requested shop interface becomes active.
     */
    private fun openShop(shop: Shop): Boolean {
        if (SubZone.HOME !in bot.subZones || !shop.botAccess.apply(bot)) {
            return false
        }
        bot.overlays.closeWindows()
        bot.overlays.open(ShopInterface(shop))
        return bot.overlays[ShopInterface::class]?.shop === shop
    }

    override suspend fun finish() {
        if (startedTrip) bot.overlays.closeWindows()
    }
}
