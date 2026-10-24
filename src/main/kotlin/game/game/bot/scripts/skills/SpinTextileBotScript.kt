package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
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
import game.skill.crafting.textileCrafting.TextileType
import game.skill.crafting.textileCrafting.MakeTextileActionItem
import com.google.gson.JsonObject
import kotlinx.coroutines.withTimeoutOrNull
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Spins wool, flax, or magic roots using the existing player textile recipes and spinning-wheel dialogue.
 *
 * Owned inputs are withdrawn in partial or full batches and converted by [MakeTextileActionItem]. Current
 * Crafting level is checked before each interaction. [InventoryBotScript] supplies verified unnoted banking,
 * safety checks, progress detection, and persistent retry budgets. Missing wool or roots request total stock
 * targets of 1,000; missing flax retains the existing flax-harvesting prerequisite.
 *
 * Existing Varrock and Seers' Village wheels remain available. Seers sessions climb before interacting and
 * descend before banking or finishing. Harvesting can still use [spinAtSeers] to process flax between harvests.
 * Saved sessions retain their recipe, duration, zones, and budgets; legacy flax snapshots restore as bowstrings.
 *
 * @param bot The bot spinning textiles.
 * @param duration Session duration managed by the inherited zone lifecycle.
 * @param zones Candidate spinning-wheel zones.
 * @param textile An existing spinning-wheel recipe; loom recipes are not supported by this script.
 * @author lare96
 * @author TheLining
 */
class SpinTextileBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = mutableListOf(spinningZone(bot)),
    val textile: Textile = Textile.BOWSTRING
) : InventoryBotScript(bot, duration, zones) {
    init {
        require(textile.type == TextileType.SPINNING_WHEEL) { "A spinning-wheel recipe is required." }
    }

    override val verifiedProductionWithdrawals = true

    companion object {

        /**
         * Recipe and common inventory lifecycle state for a saved spinning session.
         *
         * @author lare96
         */
        class TextileData : InventoryScriptData() {
            /** The source recipe; snapshots without this field retain legacy bowstring behavior. */
            var textile = Textile.BOWSTRING

            override fun load(data: JsonObject) {
                super.load(data)
                textile = data.get("textile")?.asString?.let(Textile::valueOf) ?: Textile.BOWSTRING
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("textile", textile.name)
            }
        }

        /**
         * The item id for flax.
         */
        const val FLAX = 1779

        /**
         * The spinning-wheel object IDs supported by the player textile handler.
         */
        val SPINNING_WHEELS = TextileType.SPINNING_WHEEL.objectIds

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
         * Uses the recipe input on [wheel] and selects its single make-item option for the carried batch.
         *
         * @return `true` if the make-item interface opened and an amount was entered.
         */
        private suspend fun spin(bot: Bot, wheel: GameObject, textile: Textile = Textile.BOWSTRING): Boolean {
            if (!bot.actionHandler.inventory.useItem(textile.rawItem.id).onObject(wheel)) {
                bot.log("Could not interact with spinning wheel. Trying again next cycle.")
                return false
            }
            bot.log("Waiting for make item interface to open.")
            if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) {
                bot.log("Make item interface was not opened. Trying again next cycle.")
                return false
            }
            bot.log("Clicking textile make item option. make_item_open?=${MakeItemDialogue::class in bot.overlays}.")
            val amount = bot.inventory.computeAmountForId(textile.rawItem.id) / textile.rawItem.amount
            if (amount < 1) return false
            bot.actionHandler.widgets.clickMakeItem(0, amount)
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
                    bot.naturalDecisionDelay()
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
                if (!bot.actionHandler.webWalkToAny(listOf(SEERS_HOUSE_INSIDE))) {
                    return false
                }
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

    /** Restores recipe-aware data or an older flax snapshot, retaining any saved retry counters. */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones,
        (data as? TextileData)?.textile ?: Textile.BOWSTRING) {
        if (data is InventoryScriptData) restoreInventoryState(data)
    }

    /** Permanent Crafting level required for selection and initialization. */
    val requiredLevel = textile.level
    /** Non-stackable input consumed by one player action. */
    private val materials = listOf(textile.rawItem)

    /** Checks permanent level and one complete owned recipe across inventory and bank. */
    fun isEligible(): Boolean = bot.crafting.staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight inputs from bank stock, including a partially stocked bank. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override suspend fun equipment(): BotGearLocator? {
        withTimeoutOrNull(15_000) { leaveSeersWheel(bot) }
        return super.equipment()
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (!requestProductionBank(bot.inventory.containsAll(materials))) return false
        if (bot.z == 1 && bot in SEERS_HOUSE) {
            return withTimeoutOrNull(15_000) { climb(bot, 0) } == true
        }
        return true
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials), bot.crafting.level >= requiredLevel)) return true
        attemptProduction(textile.rawItem.id, outputId = textile.processedItem.id) { startProduction() }
        return true
    }

    /** Resolves a live wheel and opens the recipe's single-option player dialogue. */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val wheel = if (zone == SubZone.SEERS_VILLAGE_MAIN) {
            world.locator.findObjectsOnTile(SEERS_WHEEL) { it.id in SPINNING_WHEELS }.firstOrNull()
        } else {
            world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
                it.position in zone.area && it.id in SPINNING_WHEELS
            }.firstOrNull()
        } ?: return false
        if (zone == SubZone.SEERS_VILLAGE_MAIN && !climb(bot, 1)) return false
        return spin(bot, wheel, textile)
    }

    override fun withdraw(): List<Item> {
        if (textile == Textile.BOWSTRING && bot.crafting.staticLevel < requiredLevel) {
            bot.scriptStack.push(CraftingScriptFactory.getBasicScript(bot))
        }
        return productionWithdraw(materials, levelEligible = bot.crafting.staticLevel >= requiredLevel,
            prepareMissing = {
                if (textile != Textile.BOWSTRING) false else {
                    stop()
                    bot.scriptStack.push(HarvestBotScript(bot, Harvestable.FLAX, duration,
                        mutableListOf(SubZone.SOUTH_SEERS_VILLAGE_FLAX)))
                    true
                }
            })
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override fun snapshot(): TextileData = TextileData().also {
        it.textile = textile
        saveInventoryState(it)
    }

    override suspend fun finish() {
        bot.actions.first(MakeTextileActionItem::class.java)?.interrupt()
        withTimeoutOrNull(15_000) { leaveSeersWheel(bot) }
    }

    override suspend fun completed() {
        // Chance to queue a fletching script.
        if (textile == Textile.BOWSTRING && (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            val script =
                FletchingScriptFactory.getScript(bot, bot.fletching.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}
