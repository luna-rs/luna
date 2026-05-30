package api.bot.action

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.SuspendableCondition
import api.bot.SuspendableFuture.SuspendableFutureSuccess
import api.bot.zone.SubZone
import api.bot.zone.Zone
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
 * Handles bank-related actions for a single [Bot].
 *
 * This action handler exposes higher-level suspendable banking operations such as depositing items,
 * withdrawing items, depositing the inventory, locating the home bank, and toggling noted withdrawal mode.
 *
 * Most actions simulate normal client interaction by clicking the appropriate bank widget option and waiting until
 * the expected bank or inventory state changes.
 *
 * @param bot The bot that will perform the banking actions.
 * @param handler The parent bot action handler that owns this banking handler.
 * @author lare96
 */
class BotBankingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    // todo clean up redo docs
    companion object {

        /**
         * The known home-area bank booth positions.
         *
         * These positions are lazily resolved into loaded [GameObject] instances the first time [homeBank] is called.
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
         * The cached home bank booth objects.
         *
         * This cache is shared across all banking handlers because the home bank booths are static world objects.
         * It is populated lazily from its getter.
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
     * Returns a random loaded bank object from the home area.
     *
     * The home bank objects are looked up lazily and cached after the first successful lookup.
     * Only objects whose ids are registered in [Banking.bankingObjects] are accepted.
     *
     * @return A random home-area bank object.
     * @throws IllegalStateException If none of the configured home bank positions contain a loaded
     * banking object.
     */
    fun homeBank(): GameObject {
        return homeBanks.random()
    }

    /**
     * Deposits the requested [item] from the bot's inventory into the open bank.
     *
     * If the requested amount is greater than the amount currently held, the action deposits the
     * amount the bot actually has. The method chooses the matching bank menu option for 1, 5, 10,
     * all, or X, then waits until the inventory amount decreases.
     *
     * @param item The item id and amount to deposit.
     * @return `true` if the inventory amount changed after the deposit attempt, otherwise `false`.
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
     * Deposits every inventory item matching [id].
     *
     * This is a convenience wrapper around [deposit] that requests [Int.MAX_VALUE] as the amount,
     * causing the handler to use the bank's deposit-all option when possible.
     *
     * @param id The id of the item to deposit.
     * @return `true` if the item amount changed after the deposit attempt, otherwise `false`.
     */
    suspend fun depositAll(id: Int): Boolean {
        return deposit(Item(id, Int.MAX_VALUE))
    }

    /**
     * Deposits every inventory item except the ids listed in [except].
     *
     * The bank must already be open before this method is called. Each item is deposited one at a time
     * through [depositAll]. If any deposit fails, the method stops immediately and returns `false`.
     *
     * @param except Item ids that should remain in the bot's inventory.
     * @return `true` if at least one item was deposited, otherwise `false`.
     */
    suspend fun depositInventory(except: Set<Int> = emptySet()): Boolean {
        if (!bot.bank.isOpen) {
            return false
        }

        bot.log("Trying to deposit all items.")

        var deposited = false
        for (item in bot.inventory) {
            if (item != null) {
                if (item.id !in except) {
                    if (!depositAll(item.id)) {
                        bot.log("Could not deposit $item.")
                    } else {
                        deposited = true
                    }
                }
            }
        }
        return deposited
    }

    /**
     * Withdraws the requested [item] from the open bank into the bot's inventory.
     *
     * If the requested amount is greater than the amount currently stored in the bank, the action
     * withdraws the amount that is actually available. The method chooses the matching bank menu
     * option for 1, 5, 10, all, or X, then waits until the bank amount decreases.
     *
     * @param item The item id and amount to withdraw.
     * @return `true` if the bank amount changed after the withdrawal attempt, otherwise `false`.
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
     * Withdraws every banked item matching [id].
     *
     * This is a convenience wrapper around [withdraw] that requests [Int.MAX_VALUE] as the amount,
     * causing the handler to use the bank's withdraw-all option when possible.
     *
     * @param id The id of the item to withdraw.
     * @return `true` if the bank amount changed after the withdraw attempt, otherwise `false`.
     */
    suspend fun withdrawAll(id: Int): Boolean {
        return withdraw(Item(id, Int.MAX_VALUE))
    }

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
     * Attempts to withdraw food from the bot's bank.
     *
     * The bank is scanned from start to finish, selecting food whose heal amount falls between [minimumHeal] and
     * [maximumHeal]. If a single stack cannot satisfy the requested [amount], additional matching food stacks are selected
     * until the requested amount is reached or no more valid food is available.
     *
     * If [retry] is enabled and the first pass finds no matching food, the method may retry with no heal restrictions. This
     * lets callers prefer a specific food range while still falling back to any available food instead of failing outright.
     *
     * @param amount The total number of food items to withdraw.
     * @param minimumHeal The minimum heal amount each selected food item must provide.
     * @param maximumHeal The maximum heal amount each selected food item may provide.
     * @param retry If `true`, retry with no heal restrictions when the restricted search finds no food.
     * @return `true` if at least one selected food item was successfully withdrawn.
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
        // TODO add wanted food
        handler.supplies.getWantedFood().forEach { bot.preferences.wantedItems += it.id }
        }
        return success
    }

    /**
     * Toggles the bank withdrawal mode between noted and unnoted.
     *
     * The bank must already be open. If the requested mode is already active, this method returns
     * [SuspendableFutureSuccess] immediately. Otherwise, it clicks the matching bank button and waits
     * until [PersistentVarp.WITHDRAW_AS_NOTE] reflects the requested mode.
     *
     * @param noted `true` to withdraw items as notes, or `false` to withdraw items normally.
     * @return `true` if the change succeeded, `false` otherwise.
     */
    suspend fun clickBankingMode(noted: Boolean): Boolean {
        if (!bot.bank.isOpen) {
            return false
        }

        val currentNoted = {
            bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 1
        }

        if (noted == currentNoted()) {
            return true
        }

        val suspendCond = SuspendableCondition {
            currentNoted() == noted
        }

        if (!noted) {
            bot.output.clickButton(5387)
        } else {
            bot.output.clickButton(5386)
        }

        return suspendCond.submit(5).await()
    }

    /**
     * Travels to the nearest usable bank object.
     *
     * If the bot is already inside [Zone.HOME], this method uses the configured home bank. Otherwise, it first searches
     * for visible banking objects and tries to navigate to them. If no nearby bank can be reached, the bot attempts to
     * travel home and use the home bank as a fallback.
     *
     * @return The reachable bank object, or `null` if no bank could be found or reached.
     */
    suspend fun travelToNearestBank(): GameObject? {
        bot.log("Travelling to nearest bank.")
        if (bot.subZone == SubZone.HOME) {
            // We're home, use closest home bank.
            return homeBanks.minByOrNull { it.position.computeLongestDistance(bot.position) }
        }
        bot.log("Looking in current zone.")
        val localBanks = bot.zone?.bankAnchors
        if (!localBanks.isNullOrEmpty()) {
            for (bank in localBanks) {
                val bankObj = world.locator.findObjectsOnTile(bank) { it.id in Banking.bankingObjects }.firstOrNull()
                if (bankObj != null && (bot.navigator.navigate(bankObj, true).await() == NavigationResult.REACHED ||
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
                if (bot.navigator.navigate(it, true).await() == NavigationResult.REACHED) {
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
     * Travels to the nearest bank and opens it.
     *
     * If the bank is already open, this method succeeds immediately. Otherwise, it finds a reachable bank through
     * [travelToNearestBank], applies a natural delay, interacts with the bank object, and waits until the bank interface opens.
     *
     * @return `true` if the bank was already open or opened successfully.
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
     * Travels to the nearest bank, opens it, and withdraws the requested items.
     *
     * If the inventory does not have enough space for [items], the bot first deposits all carried items. Each requested
     * item is then withdrawn individually. The method reports partial failure if any single withdraw action fails.
     *
     * @param items The items to withdraw.
     * @return `true` if the bank was opened and every requested item was withdrawn.
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
     * Travels to the nearest bank, opens it, and deposits the requested items.
     *
     * Each requested item is deposited individually after the bank is opened. The method reports partial failure if any
     * single deposit action fails.
     *
     * @param items The items to deposit.
     * @return `true` if the bank was opened and every requested item was deposited.
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
     * Travels to the nearest bank, opens it, and deposits all carried inventory items.
     *
     * @return `true` if the bank was opened and the deposit-all action succeeded.
     */
    suspend fun travelToBankDepositAll(): Boolean {
        if (travelToBankOpen()) {
            return depositInventory()
        }
        return false
    }

}