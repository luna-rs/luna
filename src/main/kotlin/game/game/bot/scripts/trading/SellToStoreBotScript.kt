package game.bot.scripts.trading

import api.bot.script.DynamicBotScript
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
 * Sells surplus wanted items to registered GP shops while the bot is at home.
 * Non-merchant bots sell exclusively to the General Store.
 *
 * Surplus selection uses the bot's existing retention and merchanting preferences. Equipped items are never
 * withdrawn, and every shop's bot-access predicate and selling policy remain authoritative.
 * Like [BuyFromStoreBotScript], this shopping trip has no state that needs to survive logout or server restart.
 *
 * @author lare96
 */
class SellToStoreBotScript(bot: Bot) : DynamicBotScript(bot) {

    override suspend fun run(): Boolean {
        if (!travelHome()) {
            bot.log("Could not travel home to sell items.")
            return true
        }

        // Snapshot the trip's quantities so changing ownership cannot expand an ongoing sale.
        val surplus = bot.preferences.wantedItemsToTrim().shuffled()
        for ((wanted, quantity) in surplus) {
            val id = wanted.id
            var remaining = quantity
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
                handler.banking.clickBankingMode(false)
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
     * Returns ownership above the current retention target, including any equipped items in retained ownership.
     * Rechecked before each click because the script suspends between transactions.
     */
    private fun sellableAmount(id: Int): Int {
        val wanted = bot.preferences.getWantedItem(id) ?: return 0
        val retained = maxOf(0, wanted.target, wanted.min)
        return (bot.totalItemAmount(id).toLong() - retained).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Sells at most [maxAmount], stopping immediately when a click makes no progress. */
    private suspend fun sellItem(shop: Shop, id: Int, maxAmount: Int): Int {
        var sold = 0
        while (bot.overlays[ShopInterface::class]?.shop === shop &&
            SubZone.HOME in bot.subZones && canUseShop(shop) && shop.computeCanSell(id)) {
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

    /** Returns registered GP shops that currently accept [id] and authorize this bot. */
    private fun shopsFor(id: Int): List<Shop> {
        return world.shops.values()
            .filter { canUseShop(it) && it.computeCanSell(id) }
    }

    /** Restricts non-merchants to the registered General Store, retaining normal access checks. */
    private fun canUseShop(shop: Shop): Boolean {
        return shop.currency == Currency.COINS && shop.botAccess.apply(bot) &&
                (bot.preferences.isMerchant || shop === world.shops.get("General Store"))
    }

    /** Re-establishes home-only remote access after banking travel. */
    private suspend fun travelHome(): Boolean {
        return SubZone.HOME in bot.subZones || handler.travelTo(SubZone.HOME)
    }

    /** Opens [shop] only while the bot is at home and still authorized to use it. */
    private fun openShop(shop: Shop): Boolean {
        if (SubZone.HOME !in bot.subZones || !canUseShop(shop)) {
            return false
        }
        bot.overlays.closeWindows()
        bot.overlays.open(ShopInterface(shop))
        return bot.overlays[ShopInterface::class]?.shop === shop
    }

    override suspend fun finish() {
        bot.overlays.closeWindows()
    }
}
