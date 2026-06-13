package api.bot.script

import api.bot.zone.SubZone
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * A base script for inventory-driven activities that operate inside one or more zones.
 *
 * This script handles the common loop for activities that need to withdraw a fixed set of items from the bank, perform
 * an action until banking is needed again, and avoid re-executing while the bot is already busy with a weak action.
 *
 * Subclasses provide the required withdrawal items, the activity-specific execution step, and the condition that decides
 * when another banking trip is needed.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may operate in.
 * @author lare96
 */
abstract class InventoryBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>
) : ZonedBotScript(bot, duration, zones) {

    /**
     * The items this script needs to withdraw from the bank.
     *
     * This is cached during initialization from [withdraw] so the requirement list remains stable for the current script
     * run.
     */
    protected var withdraw: List<Item> = emptyList()
        private set

    /**
     * Initializes this script and verifies that the bot owns the required withdrawal items.
     *
     * Missing items are added to the bot's wanted-item list so another system can try to obtain them later.
     *
     * @param resumed `true` if this script is being restored from a saved snapshot.
     * @return `true` if the bot has the required items, or `false` if the script cannot start yet.
     */
    final override fun onInit(resumed: Boolean): Boolean {
        withdraw = withdraw()
        if (isTerminated()) {
            return false
        }
        if (!bot.bank.containsAll(withdraw)) {
            bot.log("Bot does not have required withdraw items. Adding to wanted list.")
            withdraw.forEach { bot.preferences.wantedItems.add(it.id) }
            return false
        }
        return true
    }

    /**
     * Decides whether this script should bank.
     *
     * The first banking check is always accepted so the script can prepare its starting inventory. Later checks are
     * delegated to [onInventoryBankRequested].
     *
     * @param initial `true` if this is the first banking check for the current request.
     * @return `true` if banking should continue.
     */
    final override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (initial) {
            return true
        }
        return onInventoryBankRequested()
    }

    /**
     * Executes this script inside the selected zone when the bot is idle.
     *
     * If the bot already has a weak action running, this method does nothing and keeps the current zone active.
     *
     * @return `true` if this zone should remain active, or `false` if the script should abandon it.
     */
    final override suspend fun executeInZone(): Boolean {
        if (bot.actions.size(ActionType.WEAK) > 0) {
            // Bot is busy, no need to re-execute.
            return true
        }
        return onExecuteInZone()
    }

    /**
     * Withdraws this script's required items while the bank is open.
     *
     * @param initial `true` if this is the first bank-open call for the current banking request.
     */
    final override suspend fun onBankOpen(initial: Boolean) {
        if (!bot.bank.containsAll(withdraw)) {
            bot.log("We no longer have the required withdraw items. Stopping script.")
            stop()
            return
        }
        handler.banking.withdrawAll(withdraw)
    }

    /**
     * Returns the items this script should withdraw from the bank.
     *
     * @return The items needed for one inventory-processing cycle.
     */
    abstract fun withdraw(): List<Item>

    /**
     * Executes one activity-specific cycle inside the active zone.
     *
     * Subclasses should perform their main interaction here, such as using an item, interacting with an object, or
     * clicking a make-item dialogue.
     *
     * @return `true` if this zone should remain active, or `false` if the script should abandon it.
     */
    open suspend fun onExecuteInZone(): Boolean {
        return true
    }

    /**
     * Returns whether this script needs another banking trip after the initial setup.
     *
     * Subclasses usually return `true` when the required material is missing, the inventory is full, or the current
     * production cycle has finished.
     *
     * @return `true` if the script should bank.
     */
    open suspend fun onInventoryBankRequested(): Boolean {
        return true
    }
}