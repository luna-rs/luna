package api.bot.script

import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.action.ActionType
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * A base script for stationary inventory-processing activities.
 *
 * This is meant for scripts that repeatedly use inventory items while standing near a bank or in a safe hub area, such
 * as stringing bows, cutting gems, or other bank-supported production loops.
 *
 * Unlike normal inventory scripts, this script does not deposit the full inventory automatically when banking. Required
 * tools are kept, missing tools/materials are pulled from the bank, and execution only continues while the bot is not
 * already performing a weak action.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
abstract class StationaryInventoryBotScript(
    bot: Bot,
    duration: Duration
) : ZonedBotScript(bot, duration, DEFAULT_ZONES.toMutableList()) {

    companion object {

        /**
         * The default safe zones used by stationary inventory scripts.
         *
         * Subclasses can rely on these general-purpose banking/skilling hubs unless they need a more specific location.
         */
        val DEFAULT_ZONES = setOf(SubZone.HOME, SubZone.DRAYNOR_MAIN, SubZone.SEERS_VILLAGE_MAIN)
    }

    /**
     * The required tool item ids for this script.
     *
     * These are loaded once during initialization from [tools]. Tools are preserved when depositing inventory and are
     * withdrawn again if missing.
     */
    private var tools: Set<Int> = emptySet()

    /**
     * The material or consumable items this script withdraws from the bank.
     *
     * These are loaded once during initialization from [withdraw].
     */
    private var withdraw: List<Item> = emptyList()

    /**
     * Initializes this script and verifies that the bot owns its required tools and withdrawal items.
     *
     * This disables full-inventory depositing during banking, caches the subclass-provided tool and withdrawal
     * requirements, and adds missing requirements to the bot's wanted-item list.
     *
     * @param resumed `true` if this script is being resumed from a saved snapshot.
     * @return `true` if all required items are available, or `false` if the script cannot start yet.
     */
    final override fun onInit(resumed: Boolean): Boolean {
        depositInventoryWhenBanking = false
        withdraw = withdraw()
        tools = tools()
        if (!bot.bank.containsAll(withdraw)) {
            bot.log("Bot does not have required withdraw items. Adding to wanted list.")
            withdraw.forEach { bot.preferences.wantedItems.add(it.id) }
            return false
        } else if (!bot.bank.containsAllIds(tools)) {
            bot.log("Bot does not have required tools. Adding to wanted list.")
            tools.forEach { bot.preferences.wantedItems.add(it) }
            return false
        }
        return true
    }

    /**
     * Requests banking when this script is idle.
     *
     * The first banking check is always accepted. Later checks only continue while the bot has no weak action running,
     * preventing the script from interrupting an active production action.
     *
     * @param initial `true` if this is the first check for the current banking request.
     * @return `true` if banking should continue.
     */
    final override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (initial) {
            return true
        }
        return bot.actions.size(ActionType.WEAK) == 0
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
     * Restocks tools and withdrawal items while the bank is open.
     *
     * Existing inventory items are deposited except for required tools. Missing tools are withdrawn first, with stackable
     * tools withdrawn as full stacks and non-stackable tools withdrawn as a single item. If a required tool cannot be
     * withdrawn, the script stops. Finally, this withdraws the script's normal material list.
     *
     * @param initial `true` if this is the first bank-open call for the current banking request.
     */
    final override suspend fun onBankOpen(initial: Boolean) {
        suspend fun cannotWithdrawTool(stackable: Boolean, id: Int): Boolean =
            if (stackable) !handler.banking.withdrawAll(id) else !handler.banking.withdraw(Item(id))

        handler.banking.depositInventory(tools)
        for (id in tools) {
            if (id !in bot.inventory) {
                val stackable = ItemDefinition.ALL[id].map { it.isStackable }.orElse(false)
                if (cannotWithdrawTool(stackable, id)) {
                    bot.log("No more of the required tool item [${itemName(id)}].")
                    stop()
                    return
                }
            }
        }
        handler.banking.withdrawAll(withdraw)
    }

    /**
     * Returns the material or consumable items this script should withdraw from the bank.
     *
     * @return The items needed for one restock cycle.
     */
    abstract fun withdraw(): List<Item>

    /**
     * Returns the tool item ids this script requires.
     *
     * Tools are preserved during inventory deposits and re-withdrawn if missing. Stackable tools are withdrawn as full
     * stacks, while non-stackable tools are withdrawn one at a time.
     *
     * @return The required tool item ids.
     */
    abstract fun tools(): Set<Int>

    /**
     * Executes one stationary production cycle inside the active zone.
     *
     * Subclasses should perform their activity-specific interaction here, such as using one inventory item on another,
     * interacting with a nearby object, or clicking a make-item dialogue.
     *
     * @return `true` if this zone should remain active, or `false` if the script should abandon it.
     */
    open suspend fun onExecuteInZone(): Boolean {
        return true
    }
}