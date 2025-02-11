package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.*
import io.luna.game.model.Entity.EntityDistanceComparator
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import world.player.item.banking.regularBank.Banking
import java.util.*
import java.util.stream.Stream

/**
 * A [BotActionHandler] implementation for banking related actions.
 */
class BotBankingActionHandler(private val bot: Bot, private val handler: BotActionHandler)  {

    /**
     * An action that forces the [Bot] to deposit an item into their bank. The returned future will unsuspend once the
     * bank changes.
     *
     * @param item The item to deposit.
     */
    suspend fun deposit(item: Item): SuspendableFuture {
        if (!bot.bank.isOpen) {
            // Bank is not open.
            return SuspendableFuture().signal(false)
        }
        val inventoryIndex = bot.inventory.computeIndexForId(item.id)
        if (inventoryIndex.isEmpty) {
            // We don't have the item.
            return SuspendableFuture().signal(false)
        }

        val existingAmount = bot.inventory.computeAmountForId(item.id)
        var depositItem = item
        if (depositItem.amount > existingAmount) {
            depositItem = depositItem.withAmount(existingAmount)
        }
        val amountSuspendCond =
            SuspendableCondition({ bot.interfaces.currentInput.filter { it is AmountInputInterface }.isPresent })
        bot.output.sendItemWidgetClick(5, inventoryIndex.asInt, 5064, depositItem.id) // Click "Deposit X" on item.
        amountSuspendCond.submit().await() // Wait until amount input interface is open.
        bot.output.enterAmount(depositItem.amount) // Enter amount.
        val depositSuspendCond = SuspendableCondition({ bot.inventory.computeAmountForId(item.id) < existingAmount })
        return depositSuspendCond.submit() // Unsuspend when the inventory amount changes.
    }

    /**
     * An action that forces the [Bot] to deposit all of an item into their bank. The returned future will unsuspend
     * once the items have been deposited.
     *
     * @param id The id of the item to deposit.
     */
    suspend fun depositAll(id: Int): SuspendableFuture {
        return deposit(Item(id, Int.MAX_VALUE))
    }

    /**
     * Calls [depositAll] on every item in the inventory of the [Bot]. Returns `true` if all items were deposited.
     */
    suspend fun depositAll(): Boolean {
        if (!bot.bank.isOpen) {
            return false
        }
        for (item in bot.inventory) {
            if (item != null) {
                depositAll(item.id).await()
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
    suspend fun withdraw(item: Item): SuspendableFuture {
        if (!bot.bank.isOpen) {
            // Bank is not open.
            return SuspendableFuture().signal(false)
        }
        val bankIndex = bot.bank.computeIndexForId(item.id)
        if (bankIndex.isEmpty) {
            // We don't have the item.
            return SuspendableFuture().signal(false)
        }
        val existingAmount = bot.bank.computeAmountForId(item.id)
        var withdrawItem = item
        if (withdrawItem.amount > existingAmount) {
            withdrawItem = withdrawItem.withAmount(existingAmount)
        }
        val amountSuspendCond =
            SuspendableCondition({ bot.interfaces.currentInput.filter { it is AmountInputInterface }.isPresent })
        bot.output.sendItemWidgetClick(5, bankIndex.asInt, 5382, withdrawItem.id) // Click "Withdraw X" on item.
        amountSuspendCond.submit().await() // Wait until amount input interface is open.
        bot.output.enterAmount(withdrawItem.amount) // Enter amount.
        val withdrawSuspendCond = SuspendableCondition({ bot.bank.computeAmountForId(item.id) < existingAmount })
        return withdrawSuspendCond.submit()
    }

    /**
     * An action that forces the [Bot] to withdraw all of `id` from their bank. The returned future will unsuspend once the
     * bank changes.
     *
     * @param id The id of the item to withdraw.
     */
    suspend fun withdrawAll(id: Int): SuspendableFuture {
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

    /**
     * Attempts to find the nearest bank to this [Bot].
     */
    fun findNearestBank(): GameObject? {
        // Filter and sort stream to find nearest bank.
        fun filterNearestBank(stream: Stream<GameObject>): Optional<GameObject> {
            return stream.filter { Banking.bankingObjects.contains(it.id) }
                .sorted(EntityDistanceComparator(bot))
                .findFirst()
        }

        // First check surrounding chunks.
        val result = filterNearestBank(world.chunks.getEntities<GameObject>(bot.position, TYPE_OBJECT).stream())
        if (result.isPresent) {
            return result.get()
        }

        // Then check entire world.
        return filterNearestBank(world.objects.stream()).orElse(null)
    }
}