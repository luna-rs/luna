package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Smelts available ore combinations into bars at a furnace.
 *
 * If [selectedBar] is supplied, this script only attempts to smelt that bar type. If [selectedBar] is `null`, the script
 * scans the bot's bank for the first [BarType] whose ore requirements are available and uses that as the active
 * smelting target.
 *
 * Once a bar type is selected, the script withdraws a balanced inventory of the required ores, travels to a furnace,
 * uses the appropriate ore on the furnace, and repeats until the required ore combination is no longer available in the
 * inventory.
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

    /**
     * Recreates a smelting script from saved zone and duration data.
     *
     * This constructor does not restore a fixed [selectedBar], so the script will choose a smeltable bar from the bot's
     * bank when it initializes.
     *
     * @param bot The bot running this script.
     * @param data The saved zone and duration data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, null, data.duration, data.zones)

    /**
     * The cached furnace object used by this script.
     *
     * This avoids scanning the active zone every cycle once a usable furnace has been found.
     */
    private var furnaceObject: GameObject? = null

    /**
     * The bar type selected for the current smelting session.
     *
     * This is resolved by [withdraw] either from [selectedBar] or by scanning the bot's bank for the first available
     * ore combination.
     */
    private var smelting: BarType? = null

    override fun withdraw(): List<Item> {
        if (selectedBar == null) {
            for (bar in BarType.VALUES) {
                if (bot.bank.containsAll(bar.oreList)) {
                    smelting = bar
                    break
                }
            }
        } else {
            smelting = selectedBar
        }

        val bar = smelting
        if (bar != null) {
            return bar.oreList.map { Item(it.id, (bot.inventory.capacity() / bar.oreList.size) * it.amount) }
        }

        bot.log("No smeltable ore combinations could be found in the bank.")
        stop()
        return listOf()
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        if (smelting == null) {
            bot.log("No smeltable ore configured.")
            stop()
            return true
        }
        if(!bot.inventory.containsAll(withdraw) && bot.actions.size(ActionType.WEAK) == 0) {
            forceBanking = true
            return true
        }
        if (furnaceObject == null) {
            furnaceObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id in Smithing.FURNACE_OBJECTS }
                .firstOrNull()
        }

        bot.log("Attempting to interact with furnace.")
        val furnace = furnaceObject
        if (furnace == null) {
            bot.log("No furnace object found in zone area.")
            stop()
            return true
        }

        val useId =
            if (smelting == BarType.STEEL) smelting!!.oreRequired.second!!.id else smelting!!.oreRequired.first.id
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
}