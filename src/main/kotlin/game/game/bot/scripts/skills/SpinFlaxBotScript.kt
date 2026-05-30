package game.bot.scripts.skills

import api.bot.Suspendable
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Spins flax into bow strings at a spinning wheel.
 *
 * This script keeps the bot supplied with flax, travels to a flax-spinning zone, uses flax on a nearby spinning wheel,
 * and selects the first make-item dialogue option to process a full inventory.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones containing usable spinning wheels.
 */
class SpinFlaxBotScript(bot: Bot, duration: Duration) :
    InventoryBotScript(bot, duration, mutableListOf(SubZone.FLAX_SPINNING_MAIN)) {

    companion object {

        /**
         * The item id for flax.
         */
        const val FLAX = 1779

        /**
         * The object ids for every loaded object definition named "Spinning wheel".
         */
        val SPINNING_WHEELS = GameObjectDefinition.ALL
            .filter { it.name == "Spinning wheel" }
            .map { it.id }
            .toSet()
    }

    /**
     * Recreates a flax spinning bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved script data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration)

    /**
     * The cached spinning wheel object used by this script.
     *
     * This avoids scanning the zone every cycle once a usable spinning wheel has been found.
     */
    private var spinningWheelObj: GameObject? = null

    override suspend fun onInventoryBankRequested(): Boolean {
        return FLAX !in bot.inventory
    }

    override suspend fun onExecuteInZone(zone: SubZone): Boolean {
        if (spinningWheelObj == null) {
            spinningWheelObj = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id in SPINNING_WHEELS }
                .firstOrNull()
        }
        bot.log("Attempting to interact with spinning wheel.")
        val spinningWheel = spinningWheelObj
        if (spinningWheel == null) {
            bot.log("No spinning wheel object found in zone area.")
            spinningWheelObj = null
            return false
        }
        if (!handler.inventory.useItem(FLAX).onObject(spinningWheel)) {
            bot.log("Could not interact with spinning wheel. Trying again next cycle.")
            return true
        }
        bot.log("Waiting for make item interface to open.")
        if (!Suspendable.waitFor { MakeItemDialogue::class in bot.overlays }) {
            bot.log("Make item interface was not opened. Trying again next cycle.")
            return true
        }
        bot.log("Clicking flax make item option. make_item_open?=${MakeItemDialogue::class in bot.overlays}.")
        handler.widgets.clickMakeItem(0, Int.MAX_VALUE)
        bot.naturalDecisionDelay()
        return true
    }

    override fun withdraw(): List<Item> = listOf(Item(FLAX, 28))

    override fun snapshot(): BotScriptData? {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = zones
        return data
    }
}