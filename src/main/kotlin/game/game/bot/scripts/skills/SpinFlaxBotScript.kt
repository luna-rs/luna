package game.bot.scripts.skills

import api.bot.Suspendable
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.coordinator.skill.CraftingScriptFactory
import engine.bot.coordinator.skill.FletchingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.skill.crafting.textileCrafting.Textile
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
    // TODO not working or not used?
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
            .map { it.id() }
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

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
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

    override fun withdraw(): List<Item> {
        if (bot.itemTracker.count(FLAX) < 28) {
            bot.log("Don't have flax, queuing flax picking script.")
            stop()
            val flaxScript =
                HarvestBotScript(bot, Harvestable.FLAX, duration, mutableListOf(SubZone.SOUTH_SEERS_VILLAGE_FLAX))
            bot.scriptStack.push(flaxScript)
            return listOf()
        }
        if (bot.crafting.staticLevel < Textile.BOWSTRING.level) {
            bot.log("Not high enough level, queuing basic crafting script.")
            bot.scriptStack.push(CraftingScriptFactory.getBasicScript(bot))
            stop()
            return listOf()
        }
        return listOf(Item(FLAX, 28))
    }

    override fun snapshot(): BotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = zones
        return data
    }

    override suspend fun finish() {
        // Chance to queue a fletching script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script =
                FletchingScriptFactory.getScript(bot, bot.fletching.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}