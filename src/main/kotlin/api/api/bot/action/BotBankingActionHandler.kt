package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.SuspendableFuture.SuspendableFutureSuccess
import api.predef.*
import api.predef.ext.*
import engine.bank.Banking
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.overlay.NumberInput
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
            for (position in HOME_BANK_POSITIONS) {
                val gameObject = world.chunks.findOnPosition(position, GameObject::class.java)
                { Banking.bankingObjects.contains(it.id) }
                if (gameObject != null) {
                    homeBanks += gameObject
                }
            }
            if (homeBanks.isEmpty()) {
                // Should never happen unless no objects are loaded.
                throw IllegalStateException("Could not generate home banks!")
            }
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
        if (inventoryIndex == -1) {
            // We don't have the item.
            bot.log("I don't have ${name(item)}.")
            return false
        }

        val existingAmount = bot.inventory.computeAmountForId(item.id)
        var depositItem = item
        if (depositItem.amount > existingAmount) {
            depositItem = depositItem.withAmount(existingAmount)
        }
        val amount = depositItem.amount
        val clickWidget = when {
            amount == 1 -> 1
            amount == 5 -> 2
            amount == 10 -> 3
            item.amount > existingAmount -> 4
            else -> 5
        }
        // Unsuspend when the inventory amount changes.
        val depositCond =
            SuspendableCondition { bot.inventory.computeAmountForId(item.id) < existingAmount }
        if (clickWidget != 5) {
            // Click deposit 1, 5, 10, or all.
            bot.output.sendItemWidgetClick(clickWidget, inventoryIndex, 5064, depositItem.id)
            bot.log("Clicking deposit option $clickWidget.")
            return depositCond.submit().await()
        } else {
            // Click deposit x.
            val amountCond =
                SuspendableCondition { NumberInput::class in bot.overlays }
            bot.output.sendItemWidgetClick(5, inventoryIndex, 5064, depositItem.id) // Click "Deposit X" on item.
            // Wait until amount input interface is open.
            if (amountCond.submit().await()) {
                bot.log("Entering amount (${depositItem.amount}).")
                bot.output.enterAmount(depositItem.amount) // Enter amount.
                return depositCond.submit().await()
            }
            bot.log("Could not open enter amount interface.")
            return false
        }
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
                if (!depositAll(item.id)) {
                    bot.log("Could not deposit $item.")
                    return false
                }
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
        if (bankIndex == -1) {
            // We don't have the item.
            bot.log("I don't have ${name(item)}.")
            return false
        }
        val existingAmount = bot.bank.computeAmountForId(item.id)
        var withdrawItem = item
        if (withdrawItem.amount > existingAmount) {
            withdrawItem = withdrawItem.withAmount(existingAmount)
        }
        val amount = withdrawItem.amount
        val clickWidget = when {
            amount == 1 -> 1
            amount == 5 -> 2
            amount == 10 -> 3
            item.amount > existingAmount -> 4
            else -> 5
        }
        val withdrawCond = SuspendableCondition { bot.bank.computeAmountForId(item.id) < existingAmount }
        if (clickWidget != 5) {
            // Withdraw 1, 5, 10, or all.
            bot.log("Clicking withdraw option $clickWidget.")
            bot.output.sendItemWidgetClick(clickWidget, bankIndex, 5382, withdrawItem.id)
            return withdrawCond.submit().await()
        } else {
            val amountCond = SuspendableCondition { NumberInput::class in bot.overlays }
            bot.output.sendItemWidgetClick(5, bankIndex, 5382, withdrawItem.id) // Click "Withdraw X" on item.
            // Wait until amount input interface is open.
            if (amountCond.submit().await()) {
                bot.log("Entering amount (${withdrawItem.amount}).")
                bot.output.enterAmount(withdrawItem.amount) // Enter amount.
                return withdrawCond.submit().await()
            }
            bot.log("Could not open enter amount interface.")
            return false
        }
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
        if (!bot.bank.isOpen) {
            return SuspendableFutureFailed
        }
        val currentNoted = { bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 1 }
        if (noted == currentNoted()) {
            return SuspendableFutureSuccess
        }
        val suspendCond = SuspendableCondition { currentNoted() == noted }
        if (!noted) {
            bot.output.clickButton(5387)
        } else {
            bot.output.clickButton(5386)
        }
        return suspendCond.submit(5)
    }
}