package api.bot.skill

import api.bot.script.BotScript
import api.bot.script.TargetingZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration

/**
 * A base [BotScript] for bot-controlled skilling activities.
 *
 * This script handles the shared skilling flow used by gathering and production-style bot scripts: checking level and
 * script-specific requirements, resolving a usable tool, banking for missing tools, travelling to a valid zone,
 * executing the skill-specific loop, and banking after skilling trips.
 *
 * Subclasses provide the skill-specific behaviour by defining the usable tools, level requirement, optional emergency
 * tool, extra requirements, banking hook, and execution hook.
 *
 * @param E The type of [Entity] this script targets while skilling.
 * @param bot The bot running this skilling script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may operate in.
 * @property skill The skill being trained by this script.
 * @author lare96
 */
abstract class SkillingBotScript<E : Entity>(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>,
    val skill: Skill
) : TargetingZonedBotScript<E>(bot, duration, zones) {

    // TODO More testing needs to be done with bot death while skilling. Can they retrieve their items and go back
    //  to skilling correctly?

    // TODO Some sort of abstract function for equipment selection once the equipment selector is complete.

    companion object {

        /**
         * Shared empty tool set used by scripts that do not require a skilling tool.
         */
        val EMPTY_TOOL_SET = TreeSet<SkillingTool>()
    }

    /**
     * The tool selected for the current skilling session.
     *
     * This is resolved during initialization and may be replaced later if the bot loses the selected tool while the
     * script is running.
     */
    private var tool: SkillingTool? = null

    /**
     * Initializes this skilling script.
     *
     * This verifies the required skill level, checks subclass-specific requirements, resolves the best usable tool, and
     * logs the selected setup.
     *
     * @param resumed `true` if this script is being restored from a saved snapshot.
     * @return `true` if the script can start, or `false` if the bot does not meet the requirements.
     */
    final override fun onInit(resumed: Boolean): Boolean {
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
     * Decides whether this script needs to bank.
     *
     * On the initial check, the bot banks if its inventory is full or if the selected tool is missing from both
     * inventory and equipment. After the initial check, banking is requested after each skilling trip.
     *
     * @param initial `true` if this is the first banking check for the current request.
     * @return `true` if banking should continue.
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
                // Don't bank, we have no tool to withdraw.
                bot.log("Skipping initial bank because no skilling tool needs to be withdrawn.")
                return false
            }

            // Otherwise bank if the resolved tool isn't in the equipment or inventory.
            val shouldBank = !hasCarriedItem(toolId)
            if (shouldBank) {
                bot.log("Requesting initial bank because I need to collect ${itemName(toolId)}.")
            } else {
                bot.log("Skipping initial bank because I am already carrying ${itemName(toolId)}.")
            }
            return shouldBank
        }
        bot.log("Requesting bank after a skilling trip.")
        return true
    }

    /**
     * Performs skilling-related banking while the bank is open.
     *
     * If the selected tool is missing from both inventory and equipment, this withdraws it from the bank. After tool
     * handling is complete, [onBankOpenSkilling] is called so subclasses can perform activity-specific banking.
     *
     * @param initial `true` if this is the first bank-open call for the current banking request.
     */
    final override suspend fun onBankOpen(initial: Boolean) {
        // Resolve the best available tool we have, withdraw it, and equip it when possible.
        val toolId = tool?.id
        if (toolId != null && !hasCarriedItem(toolId)) {
            val toolItem = Item(toolId)
            bot.log("Withdrawing ${toolItem.name} for this skilling script.")
            if (!handler.banking.withdraw(toolItem)) {
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
     * Performs one targeting execution cycle for this skilling script.
     *
     * Before delegating to [onExecuteSkilling], this verifies that the selected tool is still available. If the bot lost
     * its tool, the script tries to resolve a replacement and may force banking if the replacement is only in the bank.
     * Equippable tools are equipped automatically when carried in the inventory.
     *
     * @param searching `true` when the targeting layer is searching for a new focus.
     */
    final override suspend fun onExecuteInZone(searching: Boolean) {
        val toolId = tool?.id
        if (toolId != null) {
            if (!hasCarriedItem(toolId)) {
                // We don't have the same tool we did before.
                bot.log("I lost or no longer have ${itemName(toolId)}, resolving another skilling tool.")

                val newTool = resolveTool()
                if (newTool != null) {
                    // Resolved a new tool, grab it from the bank if needed.
                    tool = newTool
                    if (!hasCarriedItem(newTool.id)) {
                        bot.log("Resolved ${itemName(newTool.id)}, but I need to bank before using it.")
                        forceBanking = true
                    } else {
                        bot.log("Resolved replacement skilling tool: ${itemName(newTool.id)}.")
                    }
                } else {
                    // This script doesn't actually require a tool, or no fallback could be resolved.
                    bot.log("I couldn't resolve another skilling tool, so I'm continuing without one.")
                    tool = null
                }
                return
            } else if (EquipmentDefinition.ALL[toolId].isPresent && toolId !in bot.equipment) {
                bot.log("Equipping ${itemName(toolId)} for this skilling script.")
                handler.equipment.equip(toolId)
            }
        }

        // Run normal execution hook for subclasses.
        onExecuteSkilling(searching)
    }

    /**
     * Returns the minimum skill level required for the configured skilling goal.
     *
     * @return The required level in [skill].
     */
    abstract fun levelRequired(): Int

    /**
     * Executes the subclass-specific skilling behaviour.
     *
     * This is called after the base script has verified tool availability and equipped the selected tool when possible.
     *
     * @param searching `true` when the targeting layer is searching for a new focus.
     */
    open suspend fun onExecuteSkilling(searching: Boolean) {

    }

    /**
     * Performs subclass-specific banking while the bank is open.
     *
     * Subclasses should use this to deposit gathered resources, withdraw supplies, or prepare inventory state for the
     * next skilling trip.
     *
     * @param initial `true` if this is the first bank-open call for the current banking request.
     */
    open suspend fun onBankOpenSkilling(initial: Boolean) {

    }

    /**
     * Returns every tool this script can use for the current skilling activity.
     *
     * The returned set should be sorted from most effective to least effective so tool selection is predictable.
     *
     * @return A sorted set of possible tools for this skilling script.
     */
    open fun tools(): SortedSet<SkillingTool> {
        return EMPTY_TOOL_SET
    }

    /**
     * Returns the fallback tool that may be supplied if the bot owns no usable tool.
     *
     * This should usually be the weakest valid base tool for the skill, such as a bronze axe or bronze pickaxe.
     * Returning `null` disables emergency tool spawning for this script.
     *
     * @return The emergency skilling tool, or `null` if no fallback tool should be supplied.
     */
    open fun emergencyTool(): SkillingTool? {
        return null
    }

    /**
     * Checks subclass-specific requirements before this script begins.
     *
     * This hook can be used for requirements that are not covered by the normal skill level check, such as quest state,
     * unlocked areas, special items, spellbooks, or account flags.
     *
     * @return `true` if the bot may continue setup, or `false` if the script should not start.
     */
    open fun requirements(): Boolean {
        return true
    }

    /**
     * Selects the tool this bot should use for the current skilling session.
     *
     * The configured tools are copied, filtered by skill level and ownership when the bot has multiple choices, and then
     * selected according to bot intelligence. Average or smarter bots use the best available tool, while less intelligent
     * bots may choose a random candidate. If no candidate is usable, [emergencyTool] may be added to the bot's bank and
     * selected as a fallback.
     *
     * @return The selected skilling tool, or `null` if no tool is available.
     */
    private fun resolveTool(): SkillingTool? {
        // Select possible tools, then remove unavailable tools if the bot has multiple choices.
        val configuredTools = tools()
        if (configuredTools.isEmpty()) {
            bot.log("No skilling tools are configured for this script.")
            return null
        }

        val candidates = TreeSet(configuredTools.comparator())
        candidates.addAll(configuredTools)
        
        candidates.removeIf {
            skill.staticLevel < it.skillLevel || (it.id !in bot.equipment &&
                    it.id !in bot.inventory &&
                    it.id !in bot.bank)
        }

        if (candidates.isEmpty()) {
            // No usable owned tools are available, so try to provide an emergency base tool.
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

        val selectedTool = if (candidates.size == 1 || bot.personality.intelligence >= 0.5) {
            // Average/high intelligence bots, or bots with only one option, use the best available tool.
            candidates.first()
        } else {
            // Lower intelligence bots may make a less optimal tool choice.
            candidates.random()
        }

        bot.log("Resolved skilling tool: ${itemName(selectedTool.id)}.")
        return selectedTool
    }

    /**
     * Returns whether the bot currently has an item equipped or in its inventory.
     *
     * @param id The item id to check.
     *
     * @return `true` if the item is equipped or carried in the inventory.
     */
    private fun hasCarriedItem(id: Int): Boolean {
        return id in bot.equipment || id in bot.inventory
    }
}