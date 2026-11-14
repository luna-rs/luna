package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import game.bot.scripts.trading.TradeBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.bot.zone.ProductionZones
import api.predef.*
import api.predef.ext.*
import engine.bot.coordinator.skill.CraftingScriptFactory
import engine.bot.coordinator.skill.FletchingScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spins wool, flax, or magic roots using the existing player textile recipes and spinning-wheel dialogue.
 *
 * Owned inputs are withdrawn in partial or full batches and converted by [MakeTextileActionItem]. Current
 * Crafting level is checked before each interaction. [InventoryBotScript] supplies verified unnoted banking,
 * safety checks, progress detection, and persistent retry budgets. Missing wool or roots request total stock
 * targets of 1,000; missing flax first uses a prepared item-market seller, then shops and harvesting.
 * Bowstring sessions below level ten pause for normal glove training and resume the same session.
 * Three prerequisite attempts without earned Crafting XP end the session; this budget survives saved state.
 *
 * Native town and Gnome Stronghold wheels share the ordinary zone lifecycle. Seers sessions climb before interacting and
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
    zones: MutableList<SubZone> = ProductionZones.SPINNING.toMutableList(),
    val textile: Textile = Textile.BOWSTRING,
    val supplyTarget: Int = 0,
    private var supplyDeadline: Long = 0L
) : InventoryBotScript(bot, duration, zones) {
    init {
        require(textile.type == TextileType.SPINNING_WHEEL) { "A spinning-wheel recipe is required." }
        require(supplyTarget >= 0 && (supplyTarget == 0 || textile == Textile.BOWSTRING))
    }

    override val verifiedProductionWithdrawals = true
    override val trainingSkill = SKILL_CRAFTING

    companion object {

        /**
         * Recipe and common inventory lifecycle state for a saved spinning session.
         *
         * @author lare96
         */
        class TextileData : InventoryScriptData() {
            /** The source recipe; snapshots without this field retain legacy bowstring behavior. */
            var textile = Textile.BOWSTRING
            var levelPreparationAttempts = 0
            var levelPreparationExperience = -1.0
            var supplyTarget = 0
            var supplyDeadline = 0L
            var flaxSupplyPhase = 0

            override fun load(data: JsonObject) {
                super.load(data)
                textile = data.get("textile")?.asString?.let(Textile::valueOf) ?: Textile.BOWSTRING
                levelPreparationAttempts = data.get("level_preparation_attempts")?.asInt ?: 0
                levelPreparationExperience = data.get("level_preparation_experience")?.asDouble ?: -1.0
                supplyTarget = data.get("supplyTarget")?.asInt ?: 0
                supplyDeadline = data.get("supplyDeadline")?.asLong ?: 0L
                flaxSupplyPhase = data.get("flaxSupplyPhase")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("textile", textile.name)
                data.addProperty("level_preparation_attempts", levelPreparationAttempts)
                data.addProperty("level_preparation_experience", levelPreparationExperience)
                data.addProperty("supplyTarget", supplyTarget)
                data.addProperty("supplyDeadline", supplyDeadline)
                data.addProperty("flaxSupplyPhase", flaxSupplyPhase)
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
         * Uses the recipe input on [wheel] and selects its single make-item option for the carried batch.
         *
         * @return `true` if the make-item interface opened and an amount was entered.
         */
        private suspend fun spin(bot: Bot, wheel: GameObject, textile: Textile = Textile.BOWSTRING,
                                 limit: Int = Int.MAX_VALUE): Boolean {
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
            val amount = minOf(limit,
                bot.inventory.computeAmountForId(textile.rawItem.id) / textile.rawItem.amount)
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
        (data as? TextileData)?.textile ?: Textile.BOWSTRING,
        (data as? TextileData)?.supplyTarget ?: 0, (data as? TextileData)?.supplyDeadline ?: 0L) {
        if (data is InventoryScriptData) restoreInventoryState(data)
        if (data is TextileData) {
            levelPreparationAttempts = data.levelPreparationAttempts
            levelPreparationExperience = data.levelPreparationExperience
            flaxSupplyPhase = data.flaxSupplyPhase
        }
    }

    /** Permanent Crafting level required for selection and initialization. */
    val requiredLevel = textile.level
    private var levelPreparationAttempts = 0
    private var levelPreparationExperience = -1.0
    private var flaxSupplyPhase = 0
    /** Non-stackable input consumed by one player action. */
    private val materials = listOf(textile.rawItem)

    /** Checks permanent level and one complete owned recipe across inventory and bank. */
    fun isEligible(): Boolean = bot.crafting.staticLevel >= requiredLevel && bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight inputs from bank stock, including a partially stocked bank. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials).mapNotNull { item ->
        if (supplyTarget == 0) item else {
            val amount = minOf(item.amount, (supplyTarget - ownedStrings()).coerceAtLeast(0))
            if (amount == 0) null else item.withAmount(amount)
        }
    }

    private fun ownedRaw(): Long = bot.inventory.computeAmountForId(FLAX).toLong() + bot.bank.computeAmountForId(FLAX)
    private fun ownedStrings(): Int = (bot.inventory.computeAmountForId(Textile.BOWSTRING.processedItem.id).toLong() +
        bot.bank.computeAmountForId(Textile.BOWSTRING.processedItem.id)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun supplyTime(): Duration {
        val remaining = remainingDuration()
        if (supplyDeadline > 0) duration = minOf(remaining,
            (supplyDeadline - System.currentTimeMillis()).milliseconds)
        return duration
    }

    private suspend fun normalizeTradedFlax(): Boolean {
        if (bot.inventory.contains(FLAX + 1) && isActionSafe()) {
            return withTimeoutOrNull(minOf(120.seconds, supplyTime())) {
                handler.banking.travelToBankDepositAll()
            } == true
        }
        return !bot.inventory.contains(FLAX + 1)
    }

    private suspend fun prepareFlax(): Boolean {
        if (supplyTime() <= Duration.ZERO || !isActionSafe() ||
            !BotCombatActionHandler.mayGainExperience(bot, SKILL_CRAFTING) ||
            supplyTarget > 0 && !bot.profitMode &&
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_FLETCHING)) return false
        if (!normalizeTradedFlax()) return false
        if (ownedRaw() > 0) return true
        if (supplyTarget > 0 && ownedStrings() > 0) return false
        if (supplyDeadline == 0L) supplyDeadline = System.currentTimeMillis() + duration.inWholeMilliseconds
        val target = if (supplyTarget == 0) 28 else (supplyTarget - ownedStrings()).coerceAtLeast(1)
        if (flaxSupplyPhase == 0) {
            flaxSupplyPhase = 1
            val deadline = minOf(supplyDeadline, System.currentTimeMillis() + 120_000L)
            val trade = TradeBotScript.buySpecific(bot, FLAX, target, deadline)
            if (trade != null) {
                trade.duration = minOf(45.seconds, supplyTime())
                bot.scriptStack.pushHead(trade)
                return false
            }
        }
        if (supplyTime() <= Duration.ZERO || !isActionSafe()) return false
        if (ownedRaw() > 0) return true
        if (flaxSupplyPhase == 1) {
            flaxSupplyPhase = 2
            withTimeoutOrNull(minOf(120.seconds, supplyTime())) { handler.shop.obtainWantedItem(FLAX, target) }
        }
        if (supplyTime() <= Duration.ZERO || !isActionSafe()) return false
        if (ownedRaw() > 0) return true
        if (flaxSupplyPhase == 2 && supplyTime() > Duration.ZERO) {
            flaxSupplyPhase = 3
            bot.scriptStack.pushHead(HarvestBotScript(bot, Harvestable.FLAX, duration,
                ProductionZones.FLAX.toMutableList(), targetAmount = target, supplyDeadline = supplyDeadline))
        }
        return false
    }

    override suspend fun beforeInventoryInit(): Boolean {
        if (supplyTime() <= Duration.ZERO) return false
        if (textile != Textile.BOWSTRING) return true
        if (supplyTarget > 0 && ownedStrings() >= supplyTarget) return false
        if (bot.crafting.staticLevel < requiredLevel) {
            if (supplyDeadline == 0L) supplyDeadline = System.currentTimeMillis() + duration.inWholeMilliseconds
            return BotCombatActionHandler.wantsToTrain(bot, SKILL_CRAFTING)
        }
        return prepareFlax()
    }

    override suspend fun equipment(): BotGearLocator? {
        withTimeoutOrNull(15_000) { leaveSeersWheel(bot) }
        return super.equipment()
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (supplyTarget > 0 && ownedStrings() >= supplyTarget) {
            stop()
            return false
        }
        if (textile == Textile.BOWSTRING && ownedRaw() == 0L && !prepareFlax()) {
            if (!isPaused()) stop()
            return false
        }
        if (!requestProductionBank(bot.inventory.containsAll(materials))) return false
        if (bot.z == 1 && bot in SEERS_HOUSE) {
            return withTimeoutOrNull(15_000) { climb(bot, 0) } == true
        }
        return true
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (supplyTarget > 0 && ownedStrings() >= supplyTarget) {
            stop()
            return true
        }
        if (!productionReady(bot.inventory.containsAll(materials), bot.crafting.level >= requiredLevel)) return true
        val before = bot.inventory.computeAmountForId(textile.rawItem.id)
        attemptProduction(textile.rawItem.id, outputId = textile.processedItem.id) { startProduction() }
        if (supplyTarget == 0 && bot.inventory.computeAmountForId(textile.rawItem.id) < before) flaxSupplyPhase = 0
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
        return spin(bot, wheel, textile,
            if (supplyTarget == 0) Int.MAX_VALUE else (supplyTarget - ownedStrings()).coerceAtLeast(0))
    }

    override fun withdraw(): List<Item> {
        if (textile == Textile.BOWSTRING && bot.crafting.staticLevel < requiredLevel) {
            val experience = bot.crafting.experience
            if (experience > levelPreparationExperience) levelPreparationAttempts = 0
            levelPreparationExperience = experience
            if (levelPreparationAttempts >= 3) {
                bot.log("Crafting prerequisite made no XP progress after three attempts.")
                stop()
            } else {
                levelPreparationAttempts++
                // Pause this exact session. The stack resumes it after the prerequisite leaves.
                val remaining = supplyTime()
                if (remaining <= Duration.ZERO || !BotCombatActionHandler.mayGainExperience(bot, SKILL_CRAFTING)) {
                    stop()
                } else {
                    val prerequisite = CraftingScriptFactory.getBasicScript(bot, requiredLevel)
                    prerequisite.duration = minOf(prerequisite.duration, remaining)
                    bot.scriptStack.pushHead(prerequisite)
                }
            }
            return emptyList()
        }
        return productionWithdraw(materials, levelEligible = bot.crafting.staticLevel >= requiredLevel,
            requestMissing = textile != Textile.BOWSTRING)
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override fun snapshot(): TextileData = TextileData().also {
        it.textile = textile
        it.levelPreparationAttempts = levelPreparationAttempts
        it.levelPreparationExperience = levelPreparationExperience
        it.supplyTarget = supplyTarget
        it.supplyDeadline = supplyDeadline
        it.flaxSupplyPhase = flaxSupplyPhase
        saveInventoryState(it)
    }

    override suspend fun finish() {
        bot.actions.first(MakeTextileActionItem::class.java)?.interrupt()
        withTimeoutOrNull(15_000) { leaveSeersWheel(bot) }
    }

    override suspend fun completed() {
        // Chance to queue a fletching script.
        if (supplyTarget == 0 && textile == Textile.BOWSTRING && (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            val script =
                FletchingScriptFactory.getScript(bot, bot.fletching.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}
