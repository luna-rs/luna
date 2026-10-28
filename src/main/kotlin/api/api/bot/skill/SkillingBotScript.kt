
package api.bot.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.script.TargetingZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import java.util.*
import kotlin.time.Duration

/**
 * A base [BotScript] for bot-controlled skilling activities.
 *
 * This class integrates the shared skilling behaviour with the targeting, travelling, and banking lifecycle provided
 * by [TargetingZonedBotScript]. It validates skill and script requirements, selects a usable tool, requests banking when
 * necessary, checks tool availability during execution, and delegates skill-specific behaviour to subclasses.
 *
 * Tool selection considers the bot's skill level, available items, and personality. If no suitable tool can be resolved,
 * an optional emergency tool may be added to the bot's bank. A selected tool can also be replaced during execution if
 * the bot no longer carries it.
 *
 * Subclasses define the required level, available tools, optional emergency tool, additional requirements, and
 * skill-specific execution and banking behaviour.
 *
 * @param E The type of [Entity] targeted by this skilling script.
 * @param bot The bot running this skilling script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones in which this script may operate.
 * @property skill The skill being trained by this script.
 * @author lare96
 */
abstract class SkillingBotScript<E : Entity>(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>,
    val skill: Skill
) : TargetingZonedBotScript<E>(bot, duration, zones) {

    companion object {

        /**
         * A shared empty tool set used by scripts that do not require a skilling tool.
         *
         * This set is mutable but should be treated as read-only by callers.
         */
        val EMPTY_TOOL_SET = TreeSet<SkillingTool>()
    }

    /**
     * The tool selected for the current skilling session.
     *
     * This is initially resolved by [onInit] and may be replaced if the bot loses access to the selected tool.
     * A `null` value indicates that no tool is currently selected.
     */
    private var tool: SkillingTool? = null

    /**
     * Initializes this skilling script.
     *
     * This checks the required skill level and subclass-specific requirements before resolving a usable tool.
     * Initialization succeeds even when no tool is selected, provided the requirements are satisfied.
     *
     * @param resumed `true` if this script is being restored from a saved snapshot. This implementation does not
     * inspect the parameter.
     * @return `true` if the bot satisfies the requirements, or `false` otherwise.
     */
    final override suspend fun onInit(resumed: Boolean): Boolean {
        val requiredLevel = levelRequired()

        // Check any level or subclass-defined requirements before doing setup work.
        if (skill.staticLevel < requiredLevel) {
            bot.log("I need $skill level $requiredLevel, but I only have ${skill.staticLevel}.")
            return false
        } else if (!requirements()) {
            bot.log("I don't meet the requirements for this skilling script.")
            return false
        }

        tool = resolveTool()
        val toolId = tool?.id
        if (toolId != null) {
            bot.log("Skilling setup complete. Using ${itemName(toolId)}.")
        } else {
            bot.log("Skilling setup complete. No tool was selected.")
        }
        return true
    }

    /**
     * Determines whether banking should be requested for this skilling script.
     *
     * During the initial check, banking is requested if the inventory is full or the selected tool is missing from
     * both inventory and equipment. If no tool is selected and the inventory is not full, banking is not requested.
     *
     * Subsequent checks request banking unless the subclass handles its full inventory locally. Forced banking
     * still collects missing tools or supplies through the normal banking flow.
     *
     * @param initial `true` when performing the initial banking check.
     * @return `true` if banking should be requested, or `false` if it can be skipped.
     */
    final override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        if (initial) {
            val toolId = tool?.id
            if (bot.inventory.isFull) {
                // Always bank if inventory is full.
                bot.log("Requesting initial bank because my inventory is full.")
                return true
            }
            if (toolId == null) {
                // No selected tool needs to be withdrawn.
                bot.log("Skipping initial bank because no skilling tool needs to be withdrawn.")
                return false
            }

            // Bank if the selected tool is missing from both inventory and equipment.
            val shouldBank = !hasCarriedItem(toolId)
            if (shouldBank) {
                bot.log("Requesting initial bank because I need to collect ${itemName(toolId)}.")
            } else {
                bot.log("Skipping initial bank because I am already carrying ${itemName(toolId)}.")
            }
            return shouldBank
        }
        if (!forceBanking && bot.inventory.isFull && handleFullInventory()) {
            return false
        }
        bot.log("Requesting bank after a skilling trip.")
        return true
    }

    /** A skill may handle a full inventory locally. True defers banking; ordinary skill scripts bank as before. */
    protected open suspend fun handleFullInventory(): Boolean = false

    /**
     * Performs skilling-related banking while the bank is open.
     *
     * If a tool is selected but missing from inventory and equipment, this attempts to withdraw it from the bank.
     * The tool is not equipped here; equipping is handled during [onExecuteInZone].
     *
     * After tool handling, [onBankOpenSkilling] is invoked for subclass-specific banking. If the required withdrawal
     * fails, this method returns without invoking the subclass hook.
     *
     * @param initial `true` when this is considered the initial bank-open call.
     */
    final override suspend fun onBankOpen(initial: Boolean) {
        // Withdraw the selected tool if the bot is not already carrying it.
        val toolId = tool?.id
        if (toolId != null && !hasCarriedItem(toolId)) {
            val toolItem = Item(toolId)
            bot.log("Withdrawing ${toolItem.name} for this skilling script.")
            val withdrawalId = toolIds(toolId).firstOrNull { it in bot.bank } ?: toolId
            if (!handler.banking.withdraw(Item(withdrawalId))) {
                bot.log("I wasn't able to withdraw the ${toolItem.name}.")
                return
            }

            bot.log("Withdrew ${toolItem.name}.")
        } else if (toolId != null) {
            bot.log("No tool withdrawal needed; I am already carrying ${itemName(toolId)}.")
        }

        onBankOpenSkilling(initial)
    }

    /**
     * Performs one execution cycle while operating inside a skilling zone.
     *
     * If the selected tool is no longer carried, this attempts to resolve a replacement. A replacement that is not
     * currently carried triggers forced banking. If no replacement is resolved, the selected tool is cleared.
     * In either case, execution returns without invoking [onExecuteSkilling] during that cycle.
     *
     * When a selected tool is in the inventory and the bot meets its equipment requirements, this attempts to equip
     * it; otherwise the tool stays in the inventory. Execution then delegates to [onExecuteSkilling] without
     * verifying whether the equipment attempt succeeded.
     *
     * If no tool is selected at the beginning of the cycle, execution delegates directly to the subclass hook.
     *
     * @param searching `true` when the targeting layer is searching for a new focus.
     */
    final override suspend fun onExecuteInZone(searching: Boolean) {
        val toolId = tool?.id
        if (toolId != null) {
            if (!hasCarriedItem(toolId)) {
                // The previously selected tool is no longer carried.
                bot.log("I lost or no longer have ${itemName(toolId)}, resolving another skilling tool.")

                val newTool = resolveTool()
                if (newTool != null) {
                    // Select the replacement and request banking if it is not carried.
                    tool = newTool
                    if (!hasCarriedItem(newTool.id)) {
                        bot.log("Resolved ${itemName(newTool.id)}, but I need to bank before using it.")
                        forceBanking = true
                    } else {
                        bot.log("Resolved replacement skilling tool: ${itemName(newTool.id)}.")
                    }
                } else {
                    // No replacement was resolved; continue without a selected tool on a later cycle.
                    bot.log("I couldn't resolve another skilling tool, so I'm continuing without one.")
                    tool = null
                }
                return
            } else if (toolId !in bot.equipment &&
                EquipmentDefinition.ALL[toolId].map { it.meetsAllRequirements(bot) }.orElse(false)) {
                // Without the Attack level the tool still works from the inventory; trying to wield it every
                // cycle only spams the failure message.
                bot.log("Equipping ${itemName(toolId)} for this skilling script.")
                handler.equipment.equip(toolId)
            }
        }

        // Run normal execution hook for subclasses.
        onExecuteSkilling(searching)
    }

    /**
     * Refocuses the targeting script, requesting banking if the selected tool is no longer carried.
     *
     * If a selected tool is missing from both inventory and equipment, this sets [forceBanking] and returns `true`
     * without delegating to the targeting layer. Otherwise, normal refocusing is performed by the superclass.
     *
     * @return `true` when banking is forced because the selected tool is missing, or the superclass result otherwise.
     */
    override suspend fun refocus(): Boolean {
        val toolId = tool?.id
        if (toolId != null) {
            if (!hasCarriedItem(toolId)) {
                forceBanking = true
                return true
            }
        }
        return super.refocus()
    }

    /**
     * Returns the minimum skill level required to run this skilling script.
     *
     * The returned level is checked against [skill] during initialization.
     *
     * @return The required level in [skill].
     */
    abstract fun levelRequired(): Int

    /**
     * Executes the subclass-specific skilling behaviour.
     *
     * This is invoked when the base execution cycle does not need to recover a missing selected tool.
     * The base class may attempt to equip the selected tool before invoking this hook, but does not verify
     * that the equipment attempt succeeded.
     *
     * @param searching `true` when the targeting layer is searching for a new focus.
     */
    open suspend fun onExecuteSkilling(searching: Boolean) {

    }

    /**
     * Performs subclass-specific banking while the bank is open.
     *
     * This hook runs after the base class handles withdrawal of the selected tool, provided any required withdrawal
     * succeeds. Subclasses may use it to deposit gathered resources, withdraw supplies, or prepare inventory state
     * for the next skilling trip.
     *
     * @param initial `true` when this is considered the initial bank-open call.
     */
    open suspend fun onBankOpenSkilling(initial: Boolean) {

    }

    /**
     * Returns the tools available for selection by this skilling script.
     *
     * The set's ordering determines tool preference for bots that do not use randomized selection. Subclasses should
     * therefore order tools from most preferred to least preferred. Tools exceeding the bot's skill level are filtered
     * out during selection.
     *
     * Scripts that require no tool may return [EMPTY_TOOL_SET].
     *
     * @return A sorted set of candidate tools for this skilling script.
     */
    open fun tools(): SortedSet<SkillingTool> {
        return EMPTY_TOOL_SET
    }

    /**
     * Returns an optional emergency tool to use when normal tool selection fails.
     *
     * If no eligible configured tool can be found or obtained, [resolveTool] may add this tool to the bot's bank and
     * select it for the current skilling session.
     *
     * The fallback is not separately checked against the bot's skill level, so subclasses should return a tool that
     * the bot can use at the minimum level required by this script.
     *
     * Returning `null` disables emergency tool provisioning.
     *
     * @return The emergency skilling tool, or `null` if no fallback should be provided.
     */
    open fun emergencyTool(): SkillingTool? {
        return null
    }

    /**
     * Checks additional requirements before this skilling script starts.
     *
     * Subclasses may use this hook to validate requirements not covered by the normal skill level check, such as
     * quest progress, area access, required items, spellbooks, or account flags.
     *
     * @return `true` if the additional requirements are satisfied, or `false` otherwise.
     */
    open fun requirements(): Boolean {
        return true
    }

    /**
     * Resolves a tool for the current skilling session.
     *
     * This copies the configured tools and removes those exceeding the bot's current skill level. The remaining
     * candidates are checked in sorted order, unless the bot's personality is considered dumb, in which case the
     * candidates are shuffled.
     *
     * A candidate is selected if the bot's item handler reports it as available or the shop handler successfully
     * handles its acquisition through [api.bot.script.BotScriptHandler.Shop.obtainWantedItem].
     *
     * If no candidate qualifies, an optional [emergencyTool] is added to the bot's bank and selected. The emergency
     * tool is not checked against the skill level filter applied to normal candidates.
     *
     * @return The selected skilling tool, or `null` if no tool can be resolved.
     */
    private suspend fun resolveTool(): SkillingTool? {
        // Copy configured tools and remove candidates above the bot's current skill level.
        val configuredTools = tools()
        if (configuredTools.isEmpty()) {
            bot.log("No skilling tools are configured for this script.")
            return null
        }

        val candidates = TreeSet(configuredTools.comparator())

        candidates.addAll(configuredTools)
        candidates.removeIf { skill.staticLevel < it.skillLevel }

        for (candidate in (if (bot.personality.isDumb) candidates.shuffled() else candidates)) {
            if (hasCarriedItem(candidate.id) || toolIds(candidate.id).any { handler.has(it) } ||
                handler.shop.obtainWantedItem(candidate.id)) {
                bot.log("Resolved skilling tool: ${itemName(candidate.id)}.")
                return candidate
            }
        }

        // No eligible candidate was found or obtained, so try the emergency fallback.
        val emergencyTool = emergencyTool()
        if (emergencyTool == null) {
            bot.log("I don't own any usable tools for $skill, and no emergency tool is configured.")
            return null
        }

        val emergencyItem = Item(emergencyTool.id)
        bot.log("I don't own any usable tools for $skill, so I'm adding ${emergencyItem.name} to my bank.")
        bot.bank.add(emergencyItem)
        return emergencyTool
    }

    /**
     * Checks whether the bot currently carries the specified item.
     *
     * Both inventory and equipment are considered. Items stored exclusively in the bank do not satisfy this check.
     * Subclasses may also recognize alternate forms of the same carried tool.
     *
     * @param id The item ID to check.
     * @return `true` if the item is equipped or present in inventory, or `false` otherwise.
     */
    private fun hasCarriedItem(id: Int): Boolean {
        return toolIds(id).any { it in bot.equipment || it in bot.inventory }
    }

    /**
     * Returns usable forms of a tool in withdrawal preference order. Used consistently for ownership, banking,
     * and carried-tool checks when an activity transforms its tool.
     */
    protected open fun toolIds(id: Int): List<Int> = listOf(id)
}
