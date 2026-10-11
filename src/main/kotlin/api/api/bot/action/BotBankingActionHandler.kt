package api.bot.action

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.SuspendableCondition
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bank.Banking
import game.player.item.consume.food.Food
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.future.await

/**
 * Handles banking actions performed by [bot].
 *
 * Provides higher-level operations for depositing and withdrawing items, locating banks, opening the bank interface, and
 * configuring the bot's withdrawal mode. Banking interactions that use the client interface suspend until the expected
 * inventory, bank, or overlay state changes.
 *
 * @param bot The bot performing the banking actions.
 * @param handler The parent action handler used for travel, interactions, and supply management.
 * @author lare96
 */
class BotBankingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {

        /**
         * Positions of the bank booths used by bots in the home area.
         */
        val HOME_BANK_POSITIONS = listOf(
            Position(3186, 3446),
            Position(3186, 3444),
            Position(3186, 3442),
            Position(3186, 3440),
            Position(3186, 3438),
            Position(3186, 3436),
        )

        /**
         * Lazily resolved bank objects at [HOME_BANK_POSITIONS].
         *
         * The cache is populated the first time it is accessed and shared by all banking handlers. Only objects registered
         * as banking objects by [Banking] are included.
         *
         * @throws IllegalStateException If no banking objects can be resolved from [HOME_BANK_POSITIONS].
         */
        private val homeBanks = HashSet<GameObject>()
            get() {
                if (field.isEmpty()) {
                    for (position in HOME_BANK_POSITIONS) {
                        val gameObject = world.locator
                            .findObjectsOnTile(position) { Banking.bankingObjects.contains(it.id) }
                            .firstOrNull()

                        if (gameObject != null) {
                            field += gameObject
                        }
                    }

                    if (field.isEmpty()) {
                        // Should never happen unless no objects are loaded.
                        throw IllegalStateException("Could not generate home banks!")
                    }
                }
                return field
            }
    }

    /**
     * Returns a random bank object from the configured home bank booths.
     *
     * @return A loaded home bank object.
     * @throws IllegalStateException If the home bank objects cannot be resolved.
     */
    fun homeBank(): GameObject {
        return homeBanks.random()
    }

    /**
     * Deposits [item] from the bot's inventory into the currently open bank.
     *
     * The requested amount is capped to the amount currently held. The corresponding deposit option is used for amounts of
     * 1, 5, 10, or all; other amounts use the deposit-X interface. The action succeeds once the held amount decreases.
     *
     * If the inventory slot exists but reports an amount of zero, the stale slot is cleared and the action succeeds.
     *
     * @param item The item and amount to deposit.
     * @return `true` if the deposit completed or a stale zero-amount slot was cleared.
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
        if (existingAmount == 0) {
            bot.inventory[inventoryIndex] = null
            return true
        }

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
        val depositCond = SuspendableCondition {
            bot.inventory.computeAmountForId(item.id) < existingAmount
        }

        if (clickWidget != 5) {
            // Click deposit 1, 5, 10, or all.
            bot.output.sendItemWidgetClick(clickWidget, inventoryIndex, 5064, depositItem.id)
            bot.log("Clicking deposit option $clickWidget.")
            return depositCond.submit().await()
        }

        // Click deposit X.
        val amountCond = SuspendableCondition {
            NumberInput::class in bot.overlays
        }

        bot.output.sendItemWidgetClick(5, inventoryIndex, 5064, depositItem.id)

        // Wait until amount input interface is open.
        if (amountCond.submit().await()) {
            bot.log("Entering amount (${depositItem.amount}).")
            bot.output.enterAmount(depositItem.amount)
            return depositCond.submit().await()
        }

        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * Deposits the bot's entire inventory stack of [id].
     *
     * @param id The item id to deposit.
     * @return `true` if the deposit succeeds.
     */
    suspend fun depositAll(id: Int): Boolean {
        return deposit(Item(id, Int.MAX_VALUE))
    }

    /**
     * Deposits all carried items except those whose ids are contained in [except].
     *
     * The bank must already be open. Every eligible inventory item is attempted even if an earlier deposit fails.
     *
     * @param except Item ids that should remain in the inventory.
     * @return `true` if no items other than those in [except] remain.
     */
    suspend fun depositInventory(except: Set<Int> = emptySet()): Boolean {
        if (!bot.bank.isOpen) {
            return false
        }

        bot.log("Trying to deposit all items.")

        for (item in bot.inventory) {
            if (item != null) {
                if (item.id !in except) {
                    if (!depositAll(item.id)) {
                        bot.log("Could not deposit $item.")
                    }
                }
            }
        }

        return bot.inventory.none { it != null && it.id !in except }
    }

    /**
     * Withdraws [item] from the currently open bank.
     *
     * The requested amount is capped to the amount currently banked. The corresponding withdrawal option is used for
     * amounts of 1, 5, 10, or all; other amounts use the withdraw-X interface. The action succeeds once the banked amount
     * decreases.
     *
     * @param item The item and amount to withdraw.
     * @return `true` if the withdrawal succeeds.
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

        val withdrawCond = SuspendableCondition {
            bot.bank.computeAmountForId(item.id) < existingAmount
        }

        if (clickWidget != 5) {
            // Withdraw 1, 5, 10, or all.
            bot.log("Clicking withdraw option $clickWidget.")
            bot.output.sendItemWidgetClick(clickWidget, bankIndex, 5382, withdrawItem.id)
            return withdrawCond.submit().await()
        }

        val amountCond = SuspendableCondition {
            NumberInput::class in bot.overlays
        }

        bot.output.sendItemWidgetClick(5, bankIndex, 5382, withdrawItem.id)

        // Wait until amount input interface is open.
        if (amountCond.submit().await()) {
            bot.log("Entering amount (${withdrawItem.amount}).")
            bot.output.enterAmount(withdrawItem.amount)
            return withdrawCond.submit().await()
        }

        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * Withdraws the entire bank stack of [id].
     *
     * @param id The item id to withdraw.
     * @return `true` if the withdrawal succeeds.
     */
    suspend fun withdrawAll(id: Int): Boolean {
        return withdraw(Item(id, Int.MAX_VALUE))
    }

    /**
     * Withdraws every item in [items].
     *
     * If the inventory cannot currently hold all requested items, it is deposited first. All withdrawals are attempted even
     * if an earlier withdrawal fails.
     *
     * @param items The items and amounts to withdraw.
     * @return `true` if every requested withdrawal succeeds.
     */
    suspend fun withdrawAll(items: List<Item>): Boolean {
        if (!bot.inventory.hasSpaceForAll(items)) {
            depositInventory()
        }

        var success = true
        for (it in items) {
            if (!withdraw(it)) {
                success = false
            }
        }
        return success
    }

    /**
     * Attempts to withdraw each item in [items].
     *
     * If the inventory cannot currently hold all requested items, it is deposited first. Every item is attempted regardless
     * of whether another withdrawal succeeds or fails.
     *
     * @param items The items and amounts to attempt to withdraw.
     * @return `true` if at least one withdrawal succeeds.
     */
    suspend fun withdrawAny(items: List<Item>): Boolean {
        if (!bot.inventory.hasSpaceForAll(items)) {
            depositInventory()
        }

        var success = false
        for (it in items) {
            if (withdraw(it)) {
                success = true
            }
        }
        return success
    }

    /**
     * Attempts to withdraw up to [amount] food items from the bot's bank.
     *
     * Food is selected in bank order when its heal amount is within [minimumHeal] and [maximumHeal]. Multiple food stacks may
     * be selected until the requested amount is satisfied or no more matching food remains.
     *
     * When [retry] is enabled and no food matches the requested heal range, the bank is scanned again without heal
     * restrictions. If no withdrawal succeeds, the bot's preferred food is added to its wanted-item list.
     *
     * @param amount The number of food items to withdraw.
     * @param minimumHeal The minimum permitted heal amount.
     * @param maximumHeal The maximum permitted heal amount.
     * @param retry Whether to retry using any available food if the preferred heal range produces no matches.
     * @return `true` if at least one food withdrawal succeeds.
     */
    suspend fun withdrawAnyFood(
        amount: Int,
        minimumHeal: Int = 0,
        maximumHeal: Int = Int.MAX_VALUE,
        retry: Boolean = true
    ): Boolean {
        var currentAmount = amount.coerceAtLeast(1)

        fun resolveWithdrawList(min: Int, max: Int): List<Item> {
            val withdraw = ArrayList<Item>()

            for (item in bot.bank) {
                if (currentAmount < 1) {
                    break
                }

                if (item == null) {
                    continue
                }

                val food = Food.ID_TO_FOOD[item.id]
                if (food != null && food.heal >= min && food.heal <= max) {
                    // Withdraw as much as possible from this stack, then keep searching if more food is still needed.
                    val withdrawAmount = item.amount.coerceAtMost(currentAmount)
                    currentAmount -= withdrawAmount
                    withdraw += Item(item.id, withdrawAmount)
                }
            }
            return withdraw
        }

        var withdraw = resolveWithdrawList(minimumHeal, maximumHeal)
        if (retry && withdraw.isEmpty() && (minimumHeal != 0 || maximumHeal != Int.MAX_VALUE)) {
            currentAmount = amount
            withdraw = resolveWithdrawList(0, Int.MAX_VALUE)
        }

        var success = false
        for (item in withdraw) {
            if (withdraw(item)) {
                success = true
            }
        }

        if (!success) {
            // We have no food. Ensure the bot starts looking for some.
            handler.supplies.getWantedFood().forEach { bot.preferences.addWantedItem(it.id, 750) }
        }
        return success
    }

    /**
     * Sets whether bank withdrawals should produce noted items.
     *
     * The setting is only changed while the bank is open.
     *
     * @param noted `true` to withdraw items as notes, or `false` to withdraw them normally.
     */
    fun clickBankingMode(noted: Boolean) {
        if (!bot.bank.isOpen) {
            return
        }

        bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, if (noted) 1 else 0)
    }

    /**
     * Finds and travels to a usable bank.
     *
     * Bots already in [SubZone.HOME] use the closest configured home bank. Otherwise, bank anchors for the current zone are
     * attempted first, followed by viewable banking objects. If neither produces a usable bank, the bot attempts to travel
     * home and use a configured home bank as a fallback.
     *
     * @return A usable bank object, or `null` if no bank can be found or reached.
     */
    suspend fun travelToNearestBank(): GameObject? {
        bot.log("Travelling to nearest bank.")

        if (SubZone.HOME in bot.subZones) {
            // We're home, use closest home bank.
            return homeBanks.minByOrNull { it.position.computeLongestDistance(bot.position) }
        }

        bot.log("Looking in current zone.")
        val localBanks = bot.zone?.bankAnchors
        if (!localBanks.isNullOrEmpty()) {
            for (bank in localBanks) {
                val bankObj = world.locator
                    .findObjectsOnTile(bank) { it.id in Banking.bankingObjects }
                    .firstOrNull()

                if (bankObj != null &&
                    (bot.navigator.navigate(bankObj, true).await() == NavigationResult.REACHED ||
                            bankObj.isWithinDistance(bot, 2))
                ) {
                    return bankObj
                }

                bot.log("Bank $bankObj inaccessible.")
                bot.naturalDecisionDelay()
            }
        }

        bot.log("Looking nearby.")
        val banks = world.locator.findViewableObjects(bot) { it.id in Banking.bankingObjects }
        if (banks.isNotEmpty()) {
            // Try to travel to nearby banks.
            bot.log("Found ${banks.size}.")

            for (it in banks) {
                if (bot.navigator.navigate(it, true).await() != NavigationResult.NO_VALID_PATH) {
                    return it
                }

                bot.log("Bank $it inaccessible.")
                bot.naturalDecisionDelay()
            }
        }

        // Travel home, then use home bank.
        bot.log("Trying to travel home for a bank.")
        if (handler.travelTo(SubZone.HOME)) {
            return homeBank()
        }

        bot.log("Cannot travel or find a bank.")
        return null
    }

    /**
     * Travels to a bank and opens it.
     *
     * If the bank is already open, no action is performed. Otherwise, [travelToNearestBank] is used to locate a bank before
     * the bot interacts with it. After the interaction delay, the bot's bank is opened directly if it has not already been
     * marked open.
     *
     * @return `true` if the bank is open after the operation.
     */
    suspend fun travelToBankOpen(): Boolean {
        if (bot.bank.isOpen) {
            return true
        }

        val bank = travelToNearestBank()
        if (bank != null) {
            bot.naturalDelay()
            bot.log("Opening bank.")

            if (handler.interactions.interact(2, bank)) {
                bot.naturalDelay()

                if (!bot.bank.isOpen) {
                    bot.bank.open()
                }
                return true
            } else {
                bot.log("Could not interact with $bank.")
                return false
            }
        }
        return false
    }

    /**
     * Travels to a bank, opens it, and withdraws every item in [items].
     *
     * If the inventory cannot hold all requested items, its current contents are deposited first. All requested withdrawals
     * are attempted even if one fails.
     *
     * @param items The items and amounts to withdraw.
     * @return `true` if the bank opens and every requested withdrawal succeeds.
     */
    suspend fun travelToBankWithdraw(items: List<Item>): Boolean {
        if (travelToBankOpen()) {
            if (!bot.inventory.hasSpaceForAll(items)) {
                depositInventory()
            }

            var success = true
            for (it in items) {
                if (!withdraw(it)) {
                    success = false
                }
            }
            return success
        }
        return false
    }

    /**
     * Travels to a bank, opens it, and deposits every item in [items].
     *
     * All requested deposits are attempted even if one fails.
     *
     * @param items The items and amounts to deposit.
     * @return `true` if the bank opens and every requested deposit succeeds.
     */
    suspend fun travelToBankDeposit(items: List<Item>): Boolean {
        if (travelToBankOpen()) {
            var success = true

            for (it in items) {
                if (!deposit(it)) {
                    success = false
                }
            }
            return success
        }
        return false
    }

    /**
     * Travels to a bank, opens it, and deposits the bot's entire inventory.
     *
     * @return `true` if the bank opens and the inventory is successfully deposited.
     */
    suspend fun travelToBankDepositAll(): Boolean {
        if (travelToBankOpen()) {
            return depositInventory()
        }
        return false
    }
}