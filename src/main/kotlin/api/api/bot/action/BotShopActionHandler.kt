package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.action.trading.BotTradeIntentions
import api.bot.zone.SubZone
import api.bot.script.BotScript.ActionSafety
import io.luna.game.model.mob.bot.Bot
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.ShopInterface
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * A [BotActionHandler] implementation for shop related actions.
 */
class BotShopActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Attempts to purchase 1 of [id] from the currently open shop.
     */
    fun buy1(id: Int) = buy(id, 1)

    /**
     * Attempts to purchase 5 of [id] from the currently open shop.
     */
    fun buy5(id: Int) = buy(id, 5)

    /**
     * Attempts to purchase 10 of [id] from the currently open shop.
     */
    fun buy10(id: Int) = buy(id, 10)

    /**
     * Attempts to purchase 100 of [id] from the currently open shop.
     */
    fun buy100(id: Int) = buy(id, 100)

    /**
     * Attempts to sell 1 of [id] to the currently open shop.
     */
    fun sell1(id: Int) = sell(id, 1)

    /**
     * Attempts to sell 5 of [id] to the currently open shop.
     */
    fun sell5(id: Int) = sell(id, 5)

    /**
     * Attempts to sell 10 of [id] to the currently open shop.
     */
    fun sell10(id: Int) = sell(id, 10)

    /**
     * Attempts to purchase either 1, 5, 10, or 100 of an item.
     */
    private fun buy(id: Int, amount: Int): SuspendableFuture {
        // Check if a shop is open.
        val shopInterface = bot.overlays[ShopInterface::class] ?: return SuspendableFutureFailed

        bot.log("Buying $amount of ${itemName(id)}.")
        val shopIndex = shopInterface.shop.items.computeIndexForId(id)
        if (shopIndex == -1) {
            // Shop doesn't have the item.
            bot.log("Shop doesn't have that item.")
            return SuspendableFutureFailed
        }
        val shopItem = shopInterface.shop.items[shopIndex]
        if (shopItem == null || shopItem.amount < 1) {
            bot.log("Shop doesn't have that item in stock.")
            return SuspendableFutureFailed
        }

        // Only require enough room for one item here.
        //
        // Shop.buy already clamps bulk purchases to the amount that actually fits in the inventory. Checking against the entire
        // shop stack would incorrectly reject something like Buy 1 on a non-stackable item when the shop currently stocks 50.
        if (!bot.inventory.hasSpaceFor(shopItem.withAmount(1))) {
            bot.log("Not enough inventory space to buy this item.")
            return SuspendableFutureFailed
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        val boughtItemCond = SuspendableCondition { bot.inventory.computeAmountForId(id) > amountBefore }
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, shopIndex, 3900, id)
            5 -> bot.output.sendItemWidgetClick(3, shopIndex, 3900, id)
            10 -> bot.output.sendItemWidgetClick(4, shopIndex, 3900, id)
            100 -> bot.output.sendItemWidgetClick(5, shopIndex, 3900, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        return boughtItemCond.submit(5) // Unsuspend when the inventory amount increases.
    }

    /**
     * Attempts to sell either 1, 5, or 10 of an item.
     */
    private fun sell(id: Int, amount: Int): SuspendableFuture {
        if (bot.preferences.isMerchant) return SuspendableFutureFailed
        // Check if a shop is open.
        val shopInterface = bot.overlays[ShopInterface::class] ?: return SuspendableFutureFailed

        bot.log("Selling $amount of ${itemName(id)}.")
        val inventoryIndex = bot.inventory.computeIndexForId(id)
        if (inventoryIndex == -1) {
            // Bot doesn't have the item.
            bot.log("I don't have that item.")
            return SuspendableFutureFailed
        }
        if (!shopInterface.shop.computeCanSell(id)) {
            bot.log("I cannot sell ${itemName(id)} item here.")
            return SuspendableFutureFailed
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, inventoryIndex, 3823, id)
            5 -> bot.output.sendItemWidgetClick(3, inventoryIndex, 3823, id)
            10 -> bot.output.sendItemWidgetClick(4, inventoryIndex, 3823, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        val soldItemCond = SuspendableCondition { bot.inventory.computeAmountForId(id) < amountBefore }
        return soldItemCond.submit(5) // Unsuspend when the inventory amount decreases.
    }

    /**
     * Keeps the one-item acquisition API used by tool selection.
     */
    suspend fun obtainWantedItem(id: Int): Boolean = obtainWantedItem(id, 1)

    /**
     * Obtains the minimum carried quantity through owned bank stock and normal shop purchases.
     */
    suspend fun obtainWantedItem(id: Int, amount: Int): Boolean {
        require(amount > 0)
        BotTradeIntentions.recordSupplies(bot, listOf(Item(id, amount)), stockTarget = amount)
        return id in obtainWantedItems(mapOf(id to amount))
    }

    /**
     * Attempts to carry or equip one of each requested item, returning the ids available when the attempt ends.
     * Unavailable items do not prevent attempts to obtain the rest of the basket.
     */
    suspend fun obtainWantedItems(ids: Collection<Int>): Set<Int> = obtainWantedItems(ids.associateWith { 1 })

    /**
     * Attempts the requested minimum carried quantities through owned bank stock and normal shop purchases.
     * Each purchase rechecks access, stock, price, funds, capacity and actual inventory progress.
     * Basket items remain carried when depositing other inventory. Partial purchases remain owned on failure;
     * the returned ids meet their targets when the attempt ends. The whole basket shares one two-minute
     * deadline, including banking, travel and every purchase.
     */
    suspend fun obtainWantedItems(amounts: Map<Int, Int>): Set<Int> {
        require(amounts.values.all { it > 0 })
        val targets = amounts.toMap()
        fun acquired() = targets.filter { (id, amount) ->
            bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id) >= amount
        }.keys
        if (acquired().size == targets.size) return targets.keys
        withTimeoutOrNull(120.seconds) {
            try {
                for ((id, amount) in targets) {
                    if (!ActionSafety.NON_COMBAT.await(bot)) break
                    acquireWantedItem(id, amount, targets.keys)
                }
            } finally {
                bot.overlays.closeWindows()
            }
        }
        return acquired()
    }

    /**
     * Attempts one target while preserving the rest of the basket during banking.
     */
    private suspend fun acquireWantedItem(id: Int, amount: Int, basket: Set<Int>): Boolean {
        fun carried() = bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id)
        if (carried() >= amount) return true
        if (!ActionSafety.NON_COMBAT.await(bot)) return false
        if (bot.bank.contains(id)) {
            if (!handler.banking.travelToBankOpen()) return false
            handler.banking.clickBankingMode(false)
            val banked = minOf(bot.bank.computeAmountForId(id).toLong(), amount - carried()).toInt()
            if (banked > 0) {
                if (!bot.inventory.hasSpaceFor(Item(id, banked)) &&
                    !handler.banking.depositInventory(basket)) return false
                if (!handler.banking.withdraw(Item(id, banked))) return false
            }
        }
        if (carried() >= amount) return true
        val shops = world.shops.values().filter { id in it.itemIds && it.botAccess.apply(bot) }
            .sortedWith(compareBy<io.luna.game.model.item.shop.Shop> { it.currency.id != 995 }
                .thenBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) })
        if (shops.isEmpty()) return false
        bot.actions.interruptWeak()
        bot.overlays.closeWindows()
        for (shop in shops) {
            while (carried() < amount) {
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                if (!shop.botAccess.apply(bot) || shop.items.computeAmountForId(id) < 1) break
                val price = shop.computeBuyValue(id, 1).orElse(-1)
                if (price < 0) break
                val currency = shop.currency.id
                val heldCoins = bot.inventory.computeAmountForId(currency)
                if (heldCoins.toLong() + bot.bank.computeAmountForId(currency) < price) break
                if (heldCoins < price || !bot.inventory.hasSpaceFor(Item(id))) {
                    if (!handler.banking.travelToBankOpen()) break
                    handler.banking.clickBankingMode(false)
                    if (!bot.inventory.hasSpaceForAll(listOf(Item(id), Item(currency, maxOf(1, price))))) {
                        if (!handler.banking.depositInventory(basket + currency)) break
                    }
                    // Carry enough for this purchase target instead of returning to the bank for every rune.
                    if (carried() >= amount) return true
                    val needed = (amount - carried()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val stocked = shop.items.computeAmountForId(id)
                    if (stocked < 1) break
                    val quoted = shop.computeBuyValue(id, minOf(needed, stocked)).orElse(price)
                    val wallet = minOf(heldCoins.toLong() + bot.bank.computeAmountForId(currency),
                        maxOf(price.toLong(), quoted.toLong() + needed)).coerceAtMost(Int.MAX_VALUE.toLong())
                    val missing = (wallet - bot.inventory.computeAmountForId(currency)).coerceAtLeast(0).toInt()
                    if (missing > 0 && !handler.banking.withdraw(Item(currency, missing))) break
                }
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                bot.overlays.closeWindows()
                if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) break
                if (!ActionSafety.NON_COMBAT.await(bot) || !shop.botAccess.apply(bot)) break
                bot.overlays.open(ShopInterface(shop))
                val before = carried()
                val remaining = amount - before
                if (remaining <= 0) return true
                val buy = when {
                    remaining >= 100 && shop.items.computeAmountForId(id) >= 100 &&
                        shop.computeBuyValue(id, 100).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy100(id)
                    remaining >= 10 && shop.items.computeAmountForId(id) >= 10 &&
                        shop.computeBuyValue(id, 10).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy10(id)
                    remaining >= 5 && shop.items.computeAmountForId(id) >= 5 &&
                        shop.computeBuyValue(id, 5).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy5(id)
                    else -> buy1(id)
                }
                if (bot.overlays[ShopInterface::class]?.shop !== shop || !buy.await() ||
                    carried() <= before) break
            }
            if (carried() >= amount) return true
        }
        return false
    }
}
