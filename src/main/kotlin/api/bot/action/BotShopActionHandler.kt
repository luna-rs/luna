package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.ext.get
import api.predef.ext.isOpen
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot

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
     * Attempts to purcase either 1, 5, or 10 of an item.
     */
    private fun buy(id: Int, amount: Int): SuspendableFuture {
        // Check if a shop is open.
        val shopInterface = bot.interfaces.get(ShopInterface::class) ?: return SuspendableFuture().signal(false)
        val shopIndex = shopInterface.shop.container.computeIndexForId(id)
        if (shopIndex.isEmpty) {
            // Shop doesn't have the item.
            return SuspendableFuture().signal(false)
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, shopIndex.asInt, 3900, id)
            5 -> bot.output.sendItemWidgetClick(3, shopIndex.asInt, 3900, id)
            10 -> bot.output.sendItemWidgetClick(4, shopIndex.asInt, 3900, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        val boughtItemCondition = SuspendableCondition({ bot.inventory.computeAmountForId(id) > amountBefore }, 5)
        return boughtItemCondition.submit() // Unsuspend when the inventory amount increases.
    }

    /**
     * Attempts to sell either 1, 5, or 10 of an item.
     */
    private fun sell(id: Int, amount: Int): SuspendableFuture {
        if (!bot.interfaces.isOpen(ShopInterface::class)) {
            // No shop is currently open.
            return SuspendableFuture().signal(false)
        }
        val inventoryIndex = bot.inventory.computeIndexForId(id)
        if (inventoryIndex.isEmpty) {
            // Bot doesn't have the item.
            return SuspendableFuture().signal(false)
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, inventoryIndex.asInt, 3823, id)
            5 -> bot.output.sendItemWidgetClick(3, inventoryIndex.asInt, 3823, id)
            10 -> bot.output.sendItemWidgetClick(4, inventoryIndex.asInt, 3823, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        val boughtItemCondition = SuspendableCondition({ bot.inventory.computeAmountForId(id) < amountBefore }, 5)
        return boughtItemCondition.submit() // Unsuspend when the inventory amount decreases.
    }
}
