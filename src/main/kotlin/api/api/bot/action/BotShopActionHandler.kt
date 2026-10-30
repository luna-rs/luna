package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
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
     * Attempts to purchase either 1, 5, or 10 of an item.
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
        // Shop.buy already clamps Buy 5/Buy 10 to the amount that actually fits in the inventory. Checking against the entire
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
            else -> throw IllegalStateException("Invalid amount.")
        }
        return boughtItemCond.submit(5) // Unsuspend when the inventory amount increases.
    }

    /**
     * Attempts to sell either 1, 5, or 10 of an item.
     */
    private fun sell(id: Int, amount: Int): SuspendableFuture {
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

    /** Keeps the one-item acquisition API used by tool selection. */
    suspend fun obtainWantedItem(id: Int): Boolean = obtainWantedItem(id, 1)

    /**
     * Obtains the minimum carried quantity through owned bank stock and normal shop purchases.
     * Each purchase rechecks access, stock, price, funds, capacity and actual inventory progress.
     * Partial purchases remain owned on failure; no items or currency are generated. The whole attempt
     * has one two-minute deadline, including banking, travel and every purchase.
     */
    suspend fun obtainWantedItem(id: Int, amount: Int): Boolean {
        require(amount > 0)
        fun carried() = bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id)
        if (carried() >= amount) return true
        return withTimeoutOrNull(120.seconds) {
            try {
                if (!ActionSafety.NON_COMBAT.await(bot)) return@withTimeoutOrNull false
                if (bot.bank.contains(id)) {
                    if (!handler.banking.travelToBankOpen()) return@withTimeoutOrNull false
                    handler.banking.clickBankingMode(false)
                    val banked = minOf(bot.bank.computeAmountForId(id).toLong(), amount - carried()).toInt()
                    if (banked > 0) {
                        if (!bot.inventory.hasSpaceFor(Item(id, banked)) &&
                            !handler.banking.depositInventory(setOf(id))) return@withTimeoutOrNull false
                        if (!handler.banking.withdraw(Item(id, banked))) return@withTimeoutOrNull false
                    }
                }
                if (carried() >= amount) return@withTimeoutOrNull true
                val shops = world.shops.values().filter { id in it.itemIds && it.botAccess.apply(bot) }
                    .sortedWith(compareBy<io.luna.game.model.item.shop.Shop> { it.currency.id != 995 }
                        .thenBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) })
                if (shops.isEmpty()) return@withTimeoutOrNull false
                bot.actions.interruptWeak()
                bot.overlays.closeWindows()
                for (shop in shops) {
                    while (carried() < amount) {
                        if (!ActionSafety.NON_COMBAT.await(bot)) return@withTimeoutOrNull false
                        if (!shop.botAccess.apply(bot) || shop.items.computeAmountForId(id) < 1) break
                        val price = shop.computeBuyValue(id, 1).orElse(-1)
                        if (price < 0) break
                        val currency = shop.currency.id
                        val heldCoins = bot.inventory.computeAmountForId(currency)
                        if (heldCoins.toLong() + bot.bank.computeAmountForId(currency) < price) break
                        if (heldCoins < price || !bot.inventory.hasSpaceFor(Item(id))) {
                            if (!handler.banking.travelToBankOpen()) break
                            if (!bot.inventory.hasSpaceForAll(listOf(Item(id), Item(currency, maxOf(1, price))))) {
                                if (!handler.banking.depositInventory(setOf(currency, id))) break
                            }
                            // Carry enough for this purchase target instead of returning to the bank for every rune.
                            if (carried() >= amount) return@withTimeoutOrNull true
                            val needed = (amount - carried()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            val stocked = shop.items.computeAmountForId(id)
                            if (stocked < 1) break
                            val quoted = shop.computeBuyValue(id, minOf(needed, stocked)).orElse(price)
                            val wallet = minOf(heldCoins.toLong() + bot.bank.computeAmountForId(currency),
                                maxOf(price.toLong(), quoted.toLong() + needed)).coerceAtMost(Int.MAX_VALUE.toLong())
                            val missing = (wallet - bot.inventory.computeAmountForId(currency)).coerceAtLeast(0).toInt()
                            if (missing > 0 && !handler.banking.withdraw(Item(currency, missing))) break
                        }
                        if (!ActionSafety.NON_COMBAT.await(bot)) return@withTimeoutOrNull false
                        bot.overlays.closeWindows()
                        if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) break
                        if (!ActionSafety.NON_COMBAT.await(bot) || !shop.botAccess.apply(bot)) break
                        bot.overlays.open(ShopInterface(shop))
                        val before = carried()
                        val remaining = amount - before
                        if (remaining <= 0) return@withTimeoutOrNull true
                        val buy = when {
                            remaining >= 10 && shop.items.computeAmountForId(id) >= 10 &&
                                shop.computeBuyValue(id, 10).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy10(id)
                            remaining >= 5 && shop.items.computeAmountForId(id) >= 5 &&
                                shop.computeBuyValue(id, 5).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy5(id)
                            else -> buy1(id)
                        }
                        if (bot.overlays[ShopInterface::class]?.shop !== shop || !buy.await() ||
                            carried() <= before) break
                    }
                    if (carried() >= amount) return@withTimeoutOrNull true
                }
                false
            } finally {
                bot.overlays.closeWindows()
            }
        } == true
    }
}
