
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
 * an optional emergency tool may be added to the bot's bank. Required tools must actually be owned
 * and usable at the current level; failed provisioning stops the session. Selected tools are replaced
 * after loss or a level drain, and banked replacements are collected before native interactions.
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

    final override suspend fun onInit(resumed: Boolean): Boolean {
        val requiredLevel = levelRequired()

        // Check any level or subclass-defined requirements before doing setup work.
        if (skill.level < requiredLevel) {
            bot.log("I need $skill level $requiredLevel, but I only have ${skill.level}.")
            return false
        } else if (!requirements()) {
            bot.log("I don't meet the requirements for this skilling script.")
            return false
        }

        tool = resolveTool()
        if (skill.level < requiredLevel) return false
        val toolId = tool?.id
        if (toolId != null) {
            bot.log("Skilling setup complete. Using ${itemName(toolId)}.")
        } else if (tools().isNotEmpty()) {
            bot.log("Skilling setup failed. No usable required tool could be obtained.")
            return false
        }
        return true
    }

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

    /**
     * A skill may handle a full inventory locally. True defers banking; other skills bank as before.
     */
    protected open suspend fun handleFullInventory(): Boolean = false

    final override suspend fun onBankOpen(initial: Boolean) {
        if (skill.level < levelRequired() || tools().isNotEmpty() && tool == null ||
            tool?.let { skill.level < it.skillLevel } == true) {
            stop()
            return
        }
        val selected = tool
        val toolId = selected?.id
        if (toolId != null && !hasCarriedItem(toolId)) {
            val withdrawalId = toolIds(toolId).firstOrNull { it in bot.bank } ?: toolId
            if (!handler.banking.withdraw(Item(withdrawalId)) || !hasCarriedItem(toolId) ||
                skill.level < levelRequired() || skill.level < selected.skillLevel) {
                bot.log("I couldn't collect a usable ${itemName(toolId)}.")
                stop()
                return
            }
        }
        if (!isActionSafe() || isTerminated() || isPaused()) return
        onBankOpenSkilling(initial)
    }

    final override suspend fun onExecuteInZone(searching: Boolean) {
        if (!prepareCarriedTool()) return
        val selected = tool
        if (selected != null && selected.id !in bot.equipment &&
            EquipmentDefinition.ALL[selected.id].map { it.meetsAllRequirements(bot) }.orElse(false)) {
            // A tool still works from inventory when its wielding requirements are unmet.
            handler.equipment.equip(selected.id)
        }
        if (!isActionSafe() || isTerminated() || isPaused()) return
        if (skill.level < levelRequired() || selected?.let {
                skill.level < it.skillLevel || !hasCarriedItem(it.id)
            } == true) {
            stop()
            return
        }
        onExecuteSkilling(searching)
    }

    final override suspend fun onPrepareTargeting(): Boolean = prepareCarriedTool()

    /**
     * Resolves lost or unusable tools before the targeting layer can retry an existing focus.
     * A banked replacement requests ordinary banking; no viable replacement ends this session.
     * Tool-free activities retain their ordinary targeting flow.
     */
    private suspend fun prepareCarriedTool(): Boolean {
        if (skill.level < levelRequired()) {
            stop()
            return false
        }
        if (tools().isEmpty()) return true
        val selected = tool
        if (selected == null || skill.level < selected.skillLevel || !hasCarriedItem(selected.id)) {
            tool = resolveTool()
        }
        val usable = tool
        if (usable == null || skill.level < levelRequired()) {
            stop()
            return false
        }
        if (!hasCarriedItem(usable.id)) {
            forceBanking = true
            return false
        }
        return true
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
     * The set's ordering determines preference for bots that do not use personality-based shuffling.
     * Subclasses order tools from most preferred to least preferred. Current skill levels, ownership
     * and successful acquisition determine which candidates can actually be selected.
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
     * The fallback must meet the current skill level and be owned or successfully added to the bank.
     * Subclasses should return a tool usable at this script's minimum level.
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
     * Preserves configured preference and personality shuffling, filtering by current skill level.
     * Actual ownership is verified after acquisition, including after its suspensions; a successful
     * shop return alone cannot invent a tool. Existing emergency provisioning remains a fallback,
     * but an unusable fallback or a failed bank insertion cannot satisfy readiness.
     */
    private suspend fun resolveTool(): SkillingTool? {
        val configured = tools()
        if (configured.isEmpty()) return null
        val candidates = TreeSet(configured.comparator()).apply {
            addAll(configured)
            removeIf { skill.level < it.skillLevel }
        }
        for (candidate in if (bot.personality.isDumb) candidates.shuffled() else candidates) {
            if (skill.level < candidate.skillLevel) continue
            if (!ownsTool(candidate.id)) handler.shop.obtainWantedItem(candidate.id)
            if (skill.level >= candidate.skillLevel && ownsTool(candidate.id)) {
                bot.log("Resolved skilling tool: ${itemName(candidate.id)}.")
                return candidate
            }
        }
        val emergency = emergencyTool() ?: return null
        if (skill.level < emergency.skillLevel) return null
        if (!ownsTool(emergency.id) && !bot.bank.add(Item(emergency.id))) {
            bot.log("I couldn't add the emergency ${itemName(emergency.id)} to my bank.")
            return null
        }
        return emergency
    }

    /**
     * Checks live carried and banked forms, including a fishing vessel temporarily loaded with bait.
     */
    private fun ownsTool(id: Int): Boolean = hasCarriedItem(id) || toolIds(id).any { it in bot.bank }

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
