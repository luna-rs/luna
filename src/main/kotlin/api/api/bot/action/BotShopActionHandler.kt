package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.zone.SubZone
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

    /**
     * Ensures one [id] is carried or equipped, withdrawing owned stock before purchasing it at home.
     * Returns false when travel, funds, access, stock, or inventory space prevent acquisition.
     * The caller resumes directly; no shopping script is queued. Attempts are bounded to two minutes.
     */
    suspend fun obtainWantedItem(id: Int): Boolean {
        return id in bot.inventory || id in bot.equipment || withTimeoutOrNull(120.seconds) {
            try {
                if (id in bot.bank) {
                    return@withTimeoutOrNull handler.banking.travelToBankWithdraw(listOf(Item(id))) &&
                            id in bot.inventory
                }
                val shops = world.shops.values().filter { id in it.itemIds && it.botAccess.apply(bot) }
                if (shops.isEmpty()) return@withTimeoutOrNull false
                bot.actions.interruptWeak()
                bot.overlays.closeWindows()
                if (SubZone.HOME !in bot.subZones && !handler.travelTo(SubZone.HOME)) {
                    return@withTimeoutOrNull false
                }
                for (shop in shops) {
                    if (!shop.botAccess.apply(bot) || shop.items.computeAmountForId(id) < 1) continue
                    val price = shop.computeBuyValue(id, 1).orElse(-1)
                    if (price < 0) continue
                    val currency = shop.currency.id
                    val carried = bot.inventory.computeAmountForId(currency)
                    if (carried.toLong() + bot.bank.computeAmountForId(currency) < price) continue
                    if (carried < price || !bot.inventory.hasSpaceFor(Item(id))) {
                        if (!handler.banking.travelToBankOpen()) continue
                        if (!bot.inventory.hasSpaceForAll(listOf(Item(id), Item(currency, maxOf(1, price))))) {
                            if (!handler.banking.depositInventory(setOf(currency))) continue
                        }
                        val missing = price - bot.inventory.computeAmountForId(currency)
                        if (missing > 0 && !handler.banking.withdraw(Item(currency, missing))) continue
                    }
                    bot.overlays.closeWindows()
                    if (SubZone.HOME !in bot.subZones && !handler.travelTo(SubZone.HOME)) continue
                    if (!shop.botAccess.apply(bot)) continue
                    bot.overlays.open(ShopInterface(shop))
                    if (bot.overlays[ShopInterface::class]?.shop === shop && buy1(id).await()) {
                        return@withTimeoutOrNull id in bot.inventory
                    }
                }
                false
            } finally {
                bot.overlays.closeWindows()
            }
        } == true
    }
}
