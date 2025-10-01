package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject

/**
 * A [BotActionHandler] implementation for banking related actions.
 */
class BotBankingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {

        /**
         * A list of positions containing bank booths at the home area.
         */
        val HOME_BANK_POSITIONS = listOf(Position(3091, 3245), Position(3091, 3242), Position(3091, 3243))

        /**
         * The home banks.
         */
        private val homeBanks = HashSet<GameObject>()
    }

    /**
     * Returns a random bank at the home area.
     */
    fun homeBank(): GameObject {
        if (homeBanks.isEmpty()) {
            world.objects.stream().filter { HOME_BANK_POSITIONS.contains(it.position) }
                .forEach { homeBanks.add(it) }
        }
        return homeBanks.random()
    }

    /**
     * An action that forces the [Bot] to deposit an item into their bank. The returned future will unsuspend once the
     * bank changes.
     *
     * @param item The item to deposit.
     */
    suspend fun deposit(item: Item): Boolean {
        bot.log("Depositing ${name(item)}.")
        if (!bot.bank.isOpen) {
            // Bank is not open.
            bot.log("Bank isn't open.")
            return false
        }
        val inventoryIndex = bot.inventory.computeIndexForId(item.id)
        if (inventoryIndex.isEmpty) {
            // We don't have the item.
            bot.log("I don't have ${name(item)}.")
            return false
        }

        val existingAmount = bot.inventory.computeAmountForId(item.id)
        var depositItem = item
        if (depositItem.amount > existingAmount) {
            depositItem = depositItem.withAmount(existingAmount)
        }
        val amountCond =
            SuspendableCondition({ bot.interfaces.currentInput.filter { it is AmountInputInterface }.isPresent })
        bot.output.sendItemWidgetClick(5, inventoryIndex.asInt, 5064, depositItem.id) // Click "Deposit X" on item.
        // Wait until amount input interface is open.
        if (amountCond.submit().await()) {
            bot.log("Entering amount (${depositItem.amount}).")
            bot.output.enterAmount(depositItem.amount) // Enter amount.
            val depositCond =
                SuspendableCondition({ bot.inventory.computeAmountForId(item.id) < existingAmount })
            // Unsuspend when the inventory amount changes.
            return depositCond.submit().await()
        }
        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * An action that forces the [Bot] to deposit all of an item into their bank. The returned future will unsuspend
     * once the items have been deposited.
     *
     * @param id The id of the item to deposit.
     */
    suspend fun depositAll(id: Int): Boolean {
        return deposit(Item(id, Int.MAX_VALUE))
    }

    /**
     * Calls [depositAll] on every item in the inventory of the [Bot]. Returns `true` if all items were deposited.
     */
    suspend fun depositAll(): Boolean {
        if (!bot.bank.isOpen) {
            return false
        }
        bot.log("Trying to deposit all items.")
        for (item in bot.inventory) {
            if (item != null) {
                depositAll(item.id)
            }
        }
        return bot.inventory.size() == 0
    }

    /**
     * An action that forces the [Bot] to withdraw an item from their bank. The returned future will unsuspend once the
     * bank changes.
     *
     * @param item The item to withdraw.
     */
    suspend fun withdraw(item: Item): Boolean {
        bot.log("Withdrawing ${name(item)}.")
        if (!bot.bank.isOpen) {
            // Bank is not open.
            bot.log("Bank isn't open.")
            return false
        }
        val bankIndex = bot.bank.computeIndexForId(item.id)
        if (bankIndex.isEmpty) {
            // We don't have the item.
            bot.log("I don't have ${name(item)}.")
            return false
        }
        val existingAmount = bot.bank.computeAmountForId(item.id)
        var withdrawItem = item
        if (withdrawItem.amount > existingAmount) {
            withdrawItem = withdrawItem.withAmount(existingAmount)
        }
        val amountCond =
            SuspendableCondition({ bot.interfaces.currentInput.filter { it is AmountInputInterface }.isPresent })
        bot.output.sendItemWidgetClick(5, bankIndex.asInt, 5382, withdrawItem.id) // Click "Withdraw X" on item.
        // Wait until amount input interface is open.
        if (amountCond.submit().await()) {
            bot.log("Entering amount (${withdrawItem.amount}).")
            bot.output.enterAmount(withdrawItem.amount) // Enter amount.
            val withdrawCond = SuspendableCondition({ bot.bank.computeAmountForId(item.id) < existingAmount })
            return withdrawCond.submit().await()
        }
        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * An action that forces the [Bot] to withdraw all of `id` from their bank. The returned future will unsuspend once the
     * bank changes.
     *
     * @param id The id of the item to withdraw.
     */
    suspend fun withdrawAll(id: Int): Boolean {
        return withdraw(Item(id, Int.MAX_VALUE))
    }

    /**
     * Clicks the widget to set the banking mode to either noted or unnoted. Will unsuspend when the banking mode
     * matches `noted`.
     */
    fun clickBankingMode(noted: Boolean): SuspendableFuture {
        val currentNoted = { bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 1 }
        if (noted == currentNoted()) {
            return SuspendableFuture().signal(true)
        }
        val suspendCond = SuspendableCondition({ currentNoted() == noted }, 5)
        if (!noted) {
            bot.output.clickButton(5387)
        } else {
            bot.output.clickButton(5386)
        }
        return suspendCond.submit()
    }
}