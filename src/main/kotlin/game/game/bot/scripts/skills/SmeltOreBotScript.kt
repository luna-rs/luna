package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Smelts ores into bars at a furnace.
 *
 * This script keeps the bot supplied with the ores required for [bar], travels to a furnace zone, uses the appropriate
 * ore on the furnace, and repeats until another banking trip is needed.
 *
 * @param bot The bot running this script.
 * @param bar The bar type this script should smelt.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones containing usable furnaces.
 */
class SmeltOreBotScript(
    bot: Bot,
    val bar: BarType,
    duration: Duration,
    zones: MutableList<SubZone>
) : InventoryBotScript(bot, duration, zones) {

    companion object {

        /**
         * Serializable script data for [SmeltOreBotScript].
         *
         * This stores the selected [BarType] along with the inherited duration and zone data needed to recreate the
         * script after persistence.
         */
        class SmeltOreData : ZonedBotScriptData() {

            /**
             * The bar type this script should smelt.
             */
            var bar: BarType = BarType.BRONZE

            override fun load(data: JsonObject) {
                super.load(data)
                bar = BarType.valueOf(data.get("bar").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("bar", bar.name)
            }
        }
    }

    /**
     * The cached furnace object used by this script.
     *
     * This avoids scanning the active zone every cycle once a usable furnace has been found.
     */
    private var furnaceObject: GameObject? = null

    /**
     * Recreates a smelting script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved smelting script data.
     */
    constructor(bot: Bot, data: SmeltOreData) : this(bot, data.bar, data.duration, data.zones)

    override fun withdraw(): List<Item> = bar.oreList

    override suspend fun onInventoryBankRequested(): Boolean {
        return !bot.inventory.containsAll(bar.oreList)
    }

    override suspend fun onExecuteInZone(zone: SubZone): Boolean {
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
            return false
        }

        val useId = if (bar == BarType.STEEL) bar.oreRequired.second!!.id else bar.oreRequired.first.id
        if (!handler.inventory.useItem(useId).onObject(furnace)) {
            bot.log("Could not interact with furnace. Trying again next cycle.")
            return true
        }

        bot.naturalDecisionDelay()
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = SmeltOreData()
        data.bar = bar
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }
}