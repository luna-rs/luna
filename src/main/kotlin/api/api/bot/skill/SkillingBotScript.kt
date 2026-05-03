package api.bot.skill

import api.bot.BotScript
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.base.Stopwatch
import engine.bank.Banking
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * A base [BotScript] for bot-controlled skilling activities.
 *
 * This script handles the shared skilling flow used by gathering and production-style bot scripts:
 * checking requirements, preparing at a bank, resolving usable tools, travelling to a valid zone, executing the
 * skill-specific loop, and banking when the inventory is full.
 *
 * Subclasses provide the skill-specific behaviour by defining available tools, level requirements, emergency tools,
 * and the main execution step.
 *
 * @param T The concrete [SkillingGoal] type used by this script.
 * @property goal The active skilling goal that controls training zones and remaining duration.
 * @property skill The skill being trained, or `null` if the script does not depend on a normal skill level.
 * @param bot The bot running this skilling script.
 * @author lare96
 */
abstract class SkillingBotScript<T : SkillingGoal>(bot: Bot, val goal: T, val skill: Skill?) : BotScript<T>(bot) {

    /**
     * Tracks elapsed time between duration checks.
     */
    private var timer = Stopwatch.createUnstarted()

    /**
     * The cached bank object selected for the current primary zone.
     */
    private var bank: GameObject? = null

    /**
     * The tool selected for this skilling session.
     */
    private var tool: SkillingTool? = null

    /**
     * If the bot should attempt to bank all items.
     */
    protected var bankRequested = false

    final override suspend fun init(resumed: Boolean): Boolean {
        // Start tracking elapsed time for this script session.
        timer.start()
        goal.zones.shuffle()

        // Check any level or subclass-defined requirements before doing setup work.
        if (skill != null && skill.staticLevel < levelRequired()) {
            bot.log("I don't have the required level to do this.")
            return true
        } else if (!requirements()) {
            return true
        }

        // Start from a clean inventory by travelling to a bank and depositing everything.
        if (!handler.banking.travelToBankDepositAll()) {
            bot.log("I wasn't able to travel to the bank and deposit all my items.")
            return true
        }

        // Resolve the best available tool, withdraw it, and equip it when possible.
        val tool = resolveTool()
        if (tool != null) {
            val toolItem = Item(tool.id)
            if (!handler.banking.withdraw(toolItem)) {
                bot.log("I wasn't able to withdraw the ${toolItem.name}.")
                return true
            }
            if (toolItem.equipDef != null && !handler.equipment.equip(toolItem.id)) {
                // It is fine if the tool cannot be equipped. Skilling tools can still be used from the inventory.
                bot.log("I wasn't able to equip the ${toolItem.name}.")
            }
        }

        // Add a human-like pause after setup before the main loop starts.
        bot.naturalDecisionDelay()
        return false
    }

    final override suspend fun run(): Boolean {
        // Enable running once the bot has enough energy.
        if (bot.runEnergy >= 50) {
            bot.walking.isRunning = true
        }

        // Stop the script when duration expires or when no usable skilling zone can be reached.
        if (!checkDuration() || !checkZone()) {
            return true
        }

        // Run subclass-specific skilling logic, then bank if the inventory filled up.
        execute()
        checkInventoryFull()

        // Add a human-like pause at the end of each skilling loop.
        bot.naturalDecisionDelay()
        return false
    }

    final override fun paused() {
        // Stop timing while the script is paused and clear cached route-specific state.
        timer.stop()
        bank = null

        // Allow subclasses to clean up their own state.
        onPaused()
    }

    final override fun snapshot(): T {
        return goal
    }

    /**
     * Gets every tool this script can use for the current skilling activity.
     *
     * The returned set should be sorted in order of most to the least effective so that tool selection behaves
     * predictably.
     *
     * @return A sorted set of possible tools for this skilling script.
     */
    abstract fun tools(): SortedSet<SkillingTool>

    /**
     * @return The required level for the configured skilling goal.
     */
    abstract fun levelRequired(): Int

    /**
     * Gets the fallback tool that may be supplied if the bot owns no usable tool.
     *
     * This should usually be the weakest valid base tool for the skill, such as a bronze axe or bronze pickaxe.
     * Returning `null` disables emergency tool spawning for this script.
     *
     * @return The emergency skilling tool, or `null` if no fallback tool should be supplied.
     */
    abstract fun emergencyTool(): SkillingTool?

    /**
     * Runs one cycle of skill-specific behaviour.
     *
     * Implementations should perform the actual skilling action, such as cutting a tree, mining a rock, fishing a spot,
     * or processing items.
     */
    abstract suspend fun execute()

    /**
     * Checks subclass-specific requirements before the script begins.
     *
     * This hook can be used for requirements that are not covered by the normal skill level check, such as quest state,
     * unlocked areas, special items, spellbooks, or account flags.
     *
     * @return `true` if the bot may continue setup, otherwise `false`.
     */
    open fun requirements(): Boolean {
        return true
    }

    /**
     * Runs after the bot has deposited its inventory and withdrawn its selected tool during a banking cycle.
     *
     * Subclasses can override this to withdraw extra supplies required by the activity, such as bait, feathers, runes,
     * food, or secondary ingredients.
     */
    open suspend fun onBankItems() {
        // Bank is open, items were deposited, and the tool was withdrawn. Withdraw any extra supplies here.
    }

    /**
     * Runs when the script is paused.
     *
     * Subclasses can override this to clear temporary state, reset cached targets, or stop any skill-specific timers.
     */
    open fun onPaused() {

    }

    /**
     * Updates the goal duration and checks whether the script should continue running.
     *
     * @return `true` if the script still has time remaining, otherwise `false`.
     */
    private suspend fun checkDuration(): Boolean {
        // Decrease duration based on elapsed time.
        goal.duration = goal.duration.minus(timer.elapsed().toMillis().milliseconds)
        timer.reset()

        // End the script when the configured duration has expired.
        if (goal.duration.isNegative()) {
            bot.log("Completing script ${this::class.simpleName} due to normal duration timeout.")
            bot.naturalDecisionDelay()
            return false
        }
        return true
    }

    /**
     * Resolves and validates the goal's active skilling zone.
     *
     * If no primary zone has been selected yet, this attempts to travel through the goal's zone list until a reachable
     * zone is found.
     *
     * @return `true` if the bot has a usable primary zone, otherwise `false`.
     */
    private suspend fun checkZone(): Boolean {
        // Resolve our primary skilling zone if needed.
        while (goal.primaryZone == null) {
            // If dexterous go to closest zone first, otherwise choose a random one.
            val newZone =
                if (bot.personality.isDextrous)
                    goal.zones.minByOrNull { bot.position.computeLongestDistance(it.inside) } ?: return false
                else
                    goal.zones.removeFirstOrNull() ?: return false
            if (bot.subZone == newZone || handler.travelTo(newZone)) {
                // Successfully moved into the new primary zone.
                goal.primaryZone = newZone
                bank = null
                return true
            }
            bot.naturalDelay()
        }

        // A primary zone is already selected, so continue the skilling loop.
        bot.naturalDecisionDelay()
        return true
    }

    /**
     * Selects the tool this bot should use for the current skilling session.
     *
     * The bot first filters out tools it does not own when more than one option exists. If no owned tools remain, an
     * emergency tool may be spawned into the bot's bank. Smarter bots choose the best available tool, while less
     * intelligent bots may choose randomly.
     *
     * @return The selected skilling tool, or `null` if no tool is available.
     */
    private fun resolveTool(): SkillingTool? {
        // Select possible tools, then remove unavailable tools if the bot has multiple choices.
        val tools = tools()
        if (tools.isEmpty()) {
            return null
        }
        if (tools.size > 1) {
            tools.removeIf { it.id !in bot.equipment && it.id !in bot.bank }
        }

        tool = if (tools.isEmpty()) {
            // No usable owned tools are available, so try to provide an emergency base tool.
            val emergencyTool = emergencyTool() ?: return null

            // TODO@0.5.0 Track how many emergency tools were given out, what ids were supplied, etc.
            bot.bank.add(Item(emergencyTool.id))
            emergencyTool
        } else if (tools.size == 1 || bot.personality.intelligence >= 0.5) {
            // Average/high intelligence bots, or bots with only one option, use the best available tool.
            tools.first()
        } else {
            // Lower intelligence bots may make a less optimal tool choice.
            tools.random()
        }
        return tool!!
    }

    /**
     * Banks the bot's inventory when it becomes full.
     *
     * If the inventory is not full, this keeps the bot inside the active skilling zone. When the inventory is full, this
     * resolves a nearby bank, deposits gathered items, withdraws the selected tool again, lets subclasses withdraw extra
     * supplies, and then travels back to the primary zone.
     */
    private suspend fun checkInventoryFull() {
        val primaryZone = goal.primaryZone!!
        if (!bot.inventory.isFull && !bankRequested) {
            // If the bot wandered outside the primary zone, return before continuing skilling.
            if (!primaryZone.isWithinDistance(bot, 64) && bot.walking.isEmpty) {
                handler.travelTo(primaryZone)
            }
            return
        }

        // Resolve and cache a bank instance for this primary zone.
        val parent = primaryZone.parent(bot)
        if (bank == null) {
            val bankAnchors = parent.bankAnchors
            val bankPosition =
                when (bankAnchors.size) {
                    0 -> null
                    1 -> bankAnchors.first()
                    else ->
                        // Low dexterity bots may choose a random bank. Most bots choose the closest one.
                        if (bot.personality.dexterity > 0.25)
                            bankAnchors.sortedWith(LocatableDistanceComparator(bot)).first()
                        else
                            bankAnchors.random()
                }

            // Use the selected bank anchor, or fall back to the home bank if no anchor exists.
            bank =
                if (bankPosition == null) {
                    handler.banking.homeBank()
                } else {
                    world.locator.findObjectsOnTile(bankPosition) { it.id in Banking.bankingObjects }.first()
                }
        }

        // We're not in the parent zone (for banking). Travel there, if we can't, fall back to home bank.
        if (bot.zone != parent && !handler.travelTo(parent)) {
            bank = handler.banking.homeBank()
            output.sendCommand("home")
            bot.naturalDelay()
        }

        // Deposit gathered items, restore the selected tool, let subclasses handle supplies, then return to the zone.
        if (handler.interactions.interact(2, bank)) {
            bankRequested = false
            handler.banking.depositInventory()
            if (tool != null) {
                handler.banking.withdraw(Item(tool!!.id))
            }
            onBankItems()
            handler.travelTo(primaryZone)
        } else {
            bank = null
        }
    }
}