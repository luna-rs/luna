package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import api.predef.ext.*
import engine.bot.coordinator.skill.CraftingScriptFactory
import engine.bot.coordinator.skill.FletchingScriptFactory
import engine.bot.gear.BotGearLocator
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationRequest
import io.luna.game.model.mob.movement.PathfinderType
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Spins flax into bow strings at a spinning wheel.
 *
 * This script keeps the bot supplied with flax, travels to a flax-spinning zone, uses flax on a nearby spinning wheel,
 * and selects the first make-item dialogue option to process a full inventory. Clever bots in Seers' Village spin at the
 * wheel upstairs in the house south of the bank, and every other bot spins in Varrock.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones containing usable spinning wheels.
 * @author lare96
 * @author TheLining
 */
class SpinFlaxBotScript(bot: Bot, duration: Duration, zones: MutableList<SubZone> = mutableListOf(spinningZone(bot))) :
    InventoryBotScript(bot, duration, zones) {
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

        /**
         * The spinning wheel upstairs in the house south of the Seers' Village bank.
         */
        private val SEERS_WHEEL = Position(2710, 3471, 1)

        /**
         * The ladder up to [SEERS_WHEEL], just inside the house's east door.
         */
        private val SEERS_LADDER = Position(2715, 3470)

        /**
         * The house around [SEERS_LADDER], on either floor.
         */
        private val SEERS_HOUSE = SimpleBoxArea.of(2710, 3470, 2715, 3473)

        /**
         * A tile inside [SEERS_HOUSE] whose neighbours are all inside it too, since a bot's walk can stop one tile short.
         */
        private val SEERS_HOUSE_INSIDE = Position(2714, 3472)

        /**
         * Returns where [bot] spins. Clever bots in Seers' Village stay there, and every other bot goes to Thessalia's
         * shop in Varrock.
         */
        private fun spinningZone(bot: Bot) =
            if (bot in Zone.SEERS_VILLAGE && rand(bot.personality.intelligence)) SubZone.SEERS_VILLAGE_MAIN
            else SubZone.FLAX_SPINNING_MAIN

        /**
         * Uses flax on [wheel] and selects the first make-item dialogue option to spin the whole inventory.
         *
         * @return `true` if the make-item interface opened and an amount was entered.
         */
        private suspend fun spin(bot: Bot, wheel: GameObject): Boolean {
            if (!bot.actionHandler.inventory.useItem(FLAX).onObject(wheel)) {
                bot.log("Could not interact with spinning wheel. Trying again next cycle.")
                return false
            }
            bot.log("Waiting for make item interface to open.")
            if (!waitFor { MakeItemDialogue::class in bot.overlays }) {
                bot.log("Make item interface was not opened. Trying again next cycle.")
                return false
            }
            bot.log("Clicking flax make item option. make_item_open?=${MakeItemDialogue::class in bot.overlays}.")
            bot.actionHandler.widgets.clickMakeItem(0, Int.MAX_VALUE)
            bot.naturalDecisionDelay()
            return true
        }

        /**
         * Climbs up to the Seers' Village spinning wheel, spins all the flax in the inventory, and climbs back down.
         *
         * Zones don't track floors, so the whole trip happens here instead of through a zone.
         *
         * @return `true` if all the flax was spun and the bot is back downstairs.
         */
        suspend fun spinAtSeers(bot: Bot): Boolean {
            val wheel = world.locator.findObjectsOnTile(SEERS_WHEEL) { it.id in SPINNING_WHEELS }.firstOrNull()
            if (wheel == null || !climb(bot, 1)) {
                bot.log("Could not get to the Seers' Village spinning wheel.")
                return false
            }
            var failures = 0
            while (FLAX in bot.inventory && failures < 3) {
                val flax = bot.inventory.computeAmountForId(FLAX)
                if (spin(bot, wheel)) {
                    waitFor(1.minutes) { bot.actions.size(ActionType.WEAK) == 0 }
                    bot.naturalMicroDelay()
                }
                failures = if (bot.inventory.computeAmountForId(FLAX) < flax) 0 else failures + 1
            }
            if (!climb(bot, 0)) {
                bot.log("Could not climb down from the Seers' Village spinning wheel.")
                return false
            }
            return FLAX !in bot.inventory
        }

        /**
         * Climbs back down if [bot] is still upstairs at the Seers' Village wheel, where no bank can be reached. Bots
         * that log out there start upstairs.
         */
        suspend fun leaveSeersWheel(bot: Bot) {
            if (bot.z == 1 && bot in SEERS_HOUSE) {
                climb(bot, 0)
            }
        }

        /**
         * Climbs the Seers' Village ladder to [plane]. Going up, the bot walks in through the house's door first.
         *
         * @return `true` once the bot is on [plane].
         */
        private suspend fun climb(bot: Bot, plane: Int): Boolean {
            if (bot.z == plane) {
                return true
            }
            if (plane == 1) {
                // Any action still running would cancel the walk.
                waitFor { bot.actions.size(ActionType.WEAK) == 0 && bot.actions.size(ActionType.STRONG) == 0 }
                // The bot pathfinder can miss the one-tile doorway. The player one can't.
                val walk = NavigationRequest.builder(bot)
                    .async(true)
                    .continuous(false)
                    .policy(InteractionPolicy.EQUAL_POSITION)
                    .target(SEERS_HOUSE_INSIDE)
                    .pathfinder(PathfinderType.BOT)
                bot.navigator.submit(walk.build()).await()
                if (bot !in SEERS_HOUSE) {
                    bot.log("Could not walk into the Seers' Village house. position=${bot.position}")
                    return false
                }
            }
            val ladder = world.locator.findObjectsOnTile(SEERS_LADDER.setZ(bot.z)) { it.id == 1746 || it.id == 1747 }
            bot.actionHandler.interactions.interact(1, ladder.firstOrNull())
            // The climb locks the bot for a tick after landing, which would cancel its next action.
            return waitFor { bot.z == plane && !bot.isLocked }
        }
    }

    /**
     * Recreates a flax spinning bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved script data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones)

    /**
     * The cached spinning wheel object used by this script.
     *
     * This avoids scanning the zone every cycle once a usable spinning wheel has been found.
     */
    private var spinningWheelObj: GameObject? = null

    override suspend fun equipment(): BotGearLocator? {
        // onInit is final, and this is the only hook before the first bank trip.
        leaveSeersWheel(bot)
        return super.equipment()
    }

    override fun onPaused() {
        forceBanking = false
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        return FLAX !in bot.inventory
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (FLAX !in bot.inventory) {
            bot.log("No flax left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        val zone = activeZone!!
        if (zone == SubZone.SEERS_VILLAGE_MAIN) {
            return spinAtSeers(bot)
        }
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
        spin(bot, spinningWheel)
        return true
    }

    override fun withdraw(): List<Item> {
        if (!handler.has(Item(FLAX, 28))) {
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

    override suspend fun completed() {
        // Chance to queue a fletching script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script =
                FletchingScriptFactory.getScript(bot, bot.fletching.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}
