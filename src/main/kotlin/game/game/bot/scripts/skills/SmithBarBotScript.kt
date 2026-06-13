package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import game.skill.smithing.Smithing.HAMMER
import game.skill.smithing.smithBar.SmithingInterface
import game.skill.smithing.smithBar.SmithingItem
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Smiths bars into finished smithing items at an anvil.
 *
 * If [selectedItem] is supplied, this script attempts to smith that specific item when the bot has the required level
 * and enough bars. Otherwise, it chooses a smithable item from the bot's available bars and smithing level.
 *
 * The script withdraws a hammer and a full inventory of the required bar type, travels to an anvil, opens the smithing
 * interface, and clicks the matching smithing-table slot.
 *
 * @param bot The bot running this script.
 * @param selectedItem The specific item to smith, or `null` to choose from available bars.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
class SmithBarBotScript(
    bot: Bot,
    val selectedItem: SmithingItem? = null,
    duration: Duration
) : InventoryBotScript(bot, duration, mutableListOf(SubZone.HOME)) {

    /**
     * Recreates a smithing script from saved zone and duration data.
     *
     * This constructor does not restore a fixed [selectedItem], so the script will choose a smithable item from the
     * bot's available bars when it initializes.
     *
     * @param bot The bot running this script.
     * @param data The saved zone and duration data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, null, data.duration)

    /**
     * The cached anvil object used by this script.
     *
     * This avoids scanning the active zone every cycle once a usable anvil has been found.
     */
    private var anvilObject: GameObject? = null

    /**
     * The item selected for the current smithing session.
     *
     * This is resolved by [withdraw] from either [selectedItem] or the bot's available bars.
     */
    private var smithingItem: SmithingItem? = null

    override fun withdraw(): List<Item> {
        // Resolve smithing bar type and item type.
        if (selectedItem == null || bot.smithing.staticLevel < selectedItem.level || bot.itemTracker.count(selectedItem.barType.id) < 27) {
            // Important: Always attempt bars from the lowest -> the highest level.
            var smithingBar: BarType? = null
            for (bar in BarType.VALUES) {
                if (bot.itemTracker.count(bar.id) >= 27) {
                    smithingBar = bar
                    break
                }
            }
            if (smithingBar == null) {
                bot.log("No smithing bar types could be found in the bank.")
                stop()
                return listOf()
            }

            val possible = ArrayList<SmithingItem>()
            for (item in SmithingTable.BAR_TO_ITEM[smithingBar]) {
                if (bot.smithing.staticLevel >= item.level) {
                    possible += item
                }
            }
            if (possible.isEmpty()) {
                bot.log("No smithing item types can be created with $smithingBar.")
                stop()
                return listOf()
            }
            smithingItem = possible.random()
        } else {
            smithingItem = selectedItem
        }

        val item = smithingItem
        if (item == null) {
            bot.log("Could not resolve smithing item.")
            return listOf()
        }
        return listOf(Item(HAMMER), Item(item.barType.id, 27))
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        val item = smithingItem
        if (item == null) {
            bot.log("No smithing item configured.")
            stop()
            return true
        }

        val id = item.item.id
        val table = SmithingTable.ID_TO_TABLE[id]!!
        val bar = item.barType
        if (!bot.inventory.contains(Item(bar.id, table.bars)) && bot.actions.size(ActionType.WEAK) == 0) {
            bot.log("Not enough bars to continue smithing, forcing a bank run.")
            forceBanking = true
            return true
        }

        if (anvilObject == null) {
            anvilObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id in Smithing.ANVIL_OBJECTS }
                .firstOrNull()
        }

        bot.log("Attempting to interact with anvil.")
        val anvil = anvilObject
        if (anvil == null) {
            bot.log("No anvil object found in zone area.")
            stop()
            return true
        }

        if (!handler.inventory.useItem(bar.id).onObject(anvil)) {
            bot.log("Could not interact with anvil. Trying again next cycle.")
            return true
        }

        bot.naturalDelay()
        if (SmithingInterface::class !in bot.overlays) {
            bot.log("Smithing interface isn't open. Trying again next cycle.")
            return true
        }

        output.sendItemWidgetClick(3, table.slotId, table.widgetId, id)
        bot.naturalDecisionDelay()
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    override fun onPaused() {
        anvilObject = null
    }
}