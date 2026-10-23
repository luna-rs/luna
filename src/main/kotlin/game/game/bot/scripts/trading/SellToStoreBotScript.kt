
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
 *
 * Surplus quantities are determined from the bot's wanted-item preferences and retained ownership.
 * Items are withdrawn from the bank in batches, then sold through accessible shops using normal
 * shop interactions. Equipped items are never withdrawn for selling.
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

    /**
     * Attempts to sell surplus wanted items through registered shops.
     *
     * Travels home before selecting a snapshot of surplus items in descending total surplus economy value order.
     * Each item is processed independently, with failed shops excluded from subsequent attempts.
     *
     * Before each withdrawal, the bot deposits its inventory, switches to unnoted withdrawal mode,
     * and reserves an inventory slot for sale proceeds when withdrawing non-stackable items.
     * The bot then returns home and attempts to sell through eligible shops.
     *
     * Sale quantities are limited by the original surplus, current retention requirements, bank
     * stock, and inventory capacity. Items that cannot be withdrawn or sold are skipped, while
     * failures to travel or prepare the inventory end the trip.
     *
     * @return `true` when the trip finishes or terminates early, regardless of how much was sold.
     */
    override suspend fun run(): Boolean {
        if (!travelHome()) {
            bot.log("Could not travel home to sell items.")
            return true
        }

        // Snapshot the trip's quantities so changing ownership cannot expand an ongoing sale.
        val surplus = bot.preferences.wantedItemsToTrim()
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
     * Computes the quantity of an item that can be sold without violating its retention target.
     *
     * Uses the greater of zero, the wanted target, and the wanted minimum as the retained quantity.
     * Total ownership includes items tracked across the bot's containers, including equipped items,
     * but does not require those equipped items to be withdrawn.
     *
     * The result is recalculated during selling because ownership and wanted-item requirements may
     * change while the script is suspended.
     *
     * @param id The item id to check.
     * @return The surplus quantity available for sale, clamped between zero and [Int.MAX_VALUE].
     */
    private fun sellableAmount(id: Int): Int {
        val wanted = bot.preferences.getWantedItem(id) ?: return 0
        val retained = maxOf(0, wanted.target, wanted.min)
        return (bot.totalItemAmount(id).toLong() - retained).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
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
        while (bot.overlays[ShopInterface::class]?.shop === shop &&
            SubZone.HOME in bot.subZones && shop.botAccess.apply(bot) && shop.computeCanSell(id)) {
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
        return SubZone.HOME in bot.subZones || handler.travelTo(SubZone.HOME)
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

    /**
     * Performs cleanup when this selling script finishes.
     *
     * Closes the bot's currently open interfaces, including any shop or banking interface
     * remaining from the selling trip.
     */
    override suspend fun finish() {
        bot.overlays.closeWindows()
    }
}
