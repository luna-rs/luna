package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.skill.MiningScriptFactory
import engine.bot.coordinator.skill.SmithingScriptFactory
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.action.ActionType
import io.luna.game.model.item.Equipment.HANDS
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Smelts available ore combinations into bars at a furnace.
 *
 * If [selectedBar] is supplied, this script only attempts to smelt that bar type. If [selectedBar] is `null`, the
 * script scans the bot's bank from the highest bar tier to the lowest and selects the first [BarType] for which the bot
 * has both the required Smithing level and ore combination.
 *
 * Once a bar type is selected, the script withdraws a balanced inventory of its required ores, travels to a furnace,
 * uses the appropriate ore on the furnace, and repeats until the required ore combination is no longer available.
 *
 * When no smeltable ore combination can be found, the script queues a mining script so the bot can gather additional
 * resources. After this script finishes, capable bots may queue a smithing script to process the bars they produced.
 *
 * @param bot The bot running this script.
 * @param selectedBar The specific bar type to smelt, or `null` to choose from available banked ores.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones containing usable furnaces.
 * @author lare96
 */
class SmeltOreBotScript(
    bot: Bot,
    val selectedBar: BarType? = null,
    duration: Duration,
    zones: MutableList<SubZone>
) : InventoryBotScript(bot, duration, zones) {

    companion object {

        /**
         * All available bar types ordered from the highest tier to the lowest.
         *
         * This ordering ensures automatic selection prioritizes the highest-level bar the bot can currently smelt.
         */
        val BAR_TYPES_DESCENDING: List<BarType> = BarType.entries.reversed()
    }

    /**
     * Recreates a smelting script from saved zone and duration data.
     *
     * This constructor does not restore a fixed [selectedBar], so the script will choose a smeltable bar from the bot's
     * bank when it next withdraws supplies.
     *
     * @param bot The bot running this script.
     * @param data The saved zone and duration data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, null, data.duration, data.zones)

    /**
     * The furnace object currently used by this script.
     *
     * The object is cached after its first successful lookup to avoid repeatedly scanning the active zone.
     */
    private var furnaceObject: GameObject? = null

    /**
     * The bar type selected for the current smelting session.
     *
     * This is resolved during [withdraw] from either [selectedBar] or the available ore combinations in the bot's bank.
     */
    private var smelting: BarType? = null

    override suspend fun equipment(): BotGearLocator? {
        val purpose = if (randBoolean() || bot.personality.isSocial) {
            setOf(BotGearPurpose.SHOW_OFF)
        } else {
            setOf(BotGearPurpose.SKILLING)
        }

        // Goldsmith gauntlets.
        return BotGearSelector.find(bot, purpose)
            .replace(HANDS, 776)
            .buildLocator()
    }

    override fun withdraw(): List<Item> {
        smelting = selectedBar ?: BAR_TYPES_DESCENDING.firstOrNull { bar ->
            bot.smithing.staticLevel >= bar.level && bot.bank.containsAll(bar.oreList)
        }

        val bar = smelting
        if (bar != null) {
            bot.log("Smelting bar: $bar")

            val totalRequired = bar.oreList.sumOf { it.amount }
            val combinations = bot.inventory.capacity() / totalRequired

            return bar.oreList.map { ore ->
                Item(ore.id, combinations * ore.amount)
            }
        }

        bot.log("No smeltable ore combinations could be found in the bank.")
        bot.scriptStack.pushTail(
            MiningScriptFactory.getScript(
                bot,
                bot.mining.staticLevel,
                mutableListOf(),
                randBoolean()
            ),
            2
        )
        stop()
        return emptyList()
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        val bar = smelting

        if (bar == null) {
            bot.log("No smeltable bar configured.")
            stop()
            return true
        }

        if (!bot.inventory.containsAll(bar.oreList) && bot.actions.size(ActionType.WEAK) == 0) {
            forceBanking = true
            return true
        }

        if (furnaceObject == null) {
            furnaceObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) {
                    it.id in Smithing.FURNACE_OBJECTS
                }
                .firstOrNull()
        }

        bot.log("Attempting to interact with furnace.")

        val furnace = furnaceObject
        if (furnace == null) {
            bot.log("No furnace object found in zone area.")
            stop()
            return true
        }

        val useId = if (bar == BarType.STEEL) {
            bar.oreRequired.second!!.id
        } else {
            bar.oreRequired.first.id
        }

        if (!handler.inventory.useItem(useId).onObject(furnace)) {
            bot.log("Could not interact with furnace. Trying again next cycle.")
            return true
        }

        bot.naturalDecisionDelay()
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    override suspend fun finish() {
        // Chance to queue a smithing script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script = SmithingScriptFactory.getSmithingScript(bot, bot.smithing.staticLevel)
            if (script != null) {
                bot.scriptStack.pushTail(script, 2)
            }
        }
    }
}