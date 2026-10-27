package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import game.bot.scripts.FillWaterBotScript
import game.skill.farming.Farming
import game.skill.farming.hops.HopsPatchLocation
import game.skill.farming.patch.PatchLocation
import game.skill.farming.seedling.TreeSeedling
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Prepares owned tree or fruit-tree seeds through native pot-filling, planting and watering interactions.
 * Existing seedlings are watered first. Empty pots use a vacant mapped patch, raking only when necessary;
 * occupied plots are never cleared for soil. Filled pots and existing seedlings can be processed in normal hubs.
 *
 * The target counts ready saplings and watered seedlings across inventory and bank. Batches fit one owned can's
 * remaining doses and the useful stock target. Empty cans use the shared water-filling script, then normal activity
 * selection can resume preparation. Finished batches are banked; native growth runs while the bot does other work.
 * No items, experience or growth timestamps are assigned here. Banking, travel, retries and safety are inherited.
 *
 * @param bot The gardener.
 * @property seedling Native recipe, including its level and intermediate item identities.
 * @property target Desired total saplings and watered seedlings of this recipe.
 * @param duration Maximum preparation session duration.
 * @property soilLocation Optional vacant patch used only when empty pots need soil.
 * @author lare96
 */
class PrepareSaplingBotScript(
    bot: Bot,
    val seedling: TreeSeedling,
    val target: Int,
    duration: Duration,
    val soilLocation: PatchLocation? = findSoilSite(bot, seedling)
) : InventoryBotScript(bot, duration, soilLocation?.let { mutableListOf(FarmingBotScript.SITES.getValue(it)) }
    ?: StationaryInventoryBotScript.zonesForSkill(SKILL_FARMING)) {
    override val trainingSkill = SKILL_FARMING
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Selects an unused mapped patch only when no filled pot or existing seedling can be processed first.
         * Entrana is excluded so preparation never needs to alter equipment to obtain soil.
         */
        fun findSoilSite(bot: Bot, seedling: TreeSeedling): PatchLocation? {
            fun owned(id: Int) = bot.bank.computeAmountForId(id).toLong() + bot.inventory.computeAmountForId(id)
            if (owned(seedling.seed) == 0L || owned(Farming.PLANT_POT) == 0L ||
                owned(seedling.seedling) > 0 || Farming.FILLED_PLANT_POTS.any { owned(it) > 0 }) return null
            return FarmingBotScript.SITES.keys.firstOrNull {
                val patch = Farming.patches(bot).getValue(it)
                it != HopsPatchLocation.ENTRANA && !patch.isOccupied() &&
                    (!patch.needsRaking() || owned(Farming.RAKE) > 0)
            }
        }

        /**
         * Saves recipe, finished-stock target and optional soil site with the shared session and retry state.
         * Native item ownership and seedling growth timestamps are never duplicated in script data.
         *
         * @author lare96
         */
        class SaplingData : InventoryScriptData() {
            /**
             * Native recipe identity.
             */
            var seedling = TreeSeedling.OAK
            /**
             * Total ready or watered saplings to own before ending preparation.
             */
            var target = 1
            /**
             * Native patch key for obtaining soil, or null for portable preparation.
             */
            var soilKey: String? = null
            override fun load(data: JsonObject) {
                super.load(data)
                seedling = TreeSeedling.valueOf(data.get("seedling").asString)
                target = data.get("target").asInt
                soilKey = data.get("soilKey")?.takeUnless { it.isJsonNull }?.asString
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("seedling", seedling.name)
                data.addProperty("target", target)
                data.addProperty("soilKey", soilKey)
            }
        }
    }

    init {
        require(target > 0 && (soilLocation == null || soilLocation in FarmingBotScript.SITES))
    }

    /**
     * Restores the exact recipe, optional soil site, target and remaining retry/session budgets.
     */
    constructor(bot: Bot, data: SaplingData) : this(bot, data.seedling, data.target, data.duration,
        data.soilKey?.let { key -> FarmingBotScript.SITES.keys.first { it.key == key } }) {
        restoreInventoryState(data)
    }

    /**
     * Counts unnoted stock only; noted inputs are not accepted by the native handlers.
     */
    private fun owned(id: Int): Long = bot.bank.computeAmountForId(id).toLong() + bot.inventory.computeAmountForId(id)

    /**
     * Watered seedlings already satisfy demand, so the script never waits for the next growth boundary.
     */
    private fun remaining(): Int = (target.toLong() - owned(seedling.sapling) - owned(seedling.wateredSeedling))
        .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Soil is available only from an unoccupied patch; weeds additionally require an owned rake.
     */
    private fun canFillPots(): Boolean = soilLocation?.let {
        val patch = Farming.patches(bot).getValue(it)
        !patch.isOccupied() && (!patch.needsRaking() || owned(Farming.RAKE) > 0)
    } == true

    /**
     * Requires a native intermediate or a complete seed/pot/tool recipe, independent of water availability.
     */
    private fun hasWork(): Boolean = remaining() > 0 && bot.farming.level >= seedling.level &&
        (owned(seedling.seedling) > 0 || owned(seedling.seed) > 0 && owned(Farming.GARDENING_TROWEL) > 0 &&
            (Farming.FILLED_PLANT_POTS.any { owned(it) > 0 } || owned(Farming.PLANT_POT) > 0 && canFillPots()))

    /**
     * Accepts only owned usable supplies; an owned empty can can be refilled through normal gameplay.
     */
    fun isEligible(): Boolean = hasWork() &&
        (Farming.WATERING_CANS.any { owned(it) > 0 } || owned(Farming.EMPTY_WATERING_CAN) > 0)

    /**
     * Queues a single generic refill when all owned cans are empty. This session then ends rather than spinning
     * through a prerequisite loop; a later Farming selection rechecks actual stock and native growth.
     */
    private fun prepareWater(): Boolean {
        if (!isActionSafe() || !hasWork() || Farming.WATERING_CANS.any { owned(it) > 0 } ||
            owned(Farming.EMPTY_WATERING_CAN) == 0L) return false
        bot.scriptStack.softPushHead(FillWaterBotScript(bot, Farming.EMPTY_WATERING_CAN, 1, duration))
        stop()
        return true
    }

    /**
     * Packs a small batch limited by the chosen can's doses, useful demand and owned inputs. Existing seedlings
     * take priority over new potting. Even the largest batch needs at most twelve inventory slots.
     */
    private fun supplies(stock: (Int) -> Long): List<Item> {
        if (!hasWork()) return emptyList()
        val can = Farming.WATERING_CANS.asReversed().firstOrNull { stock(it) > 0 } ?: return emptyList()
        val limit = minOf(remaining(), Farming.WATERING_CANS.indexOf(can) + 1)
        val seedlings = minOf(limit.toLong(), stock(seedling.seedling)).toInt()
        if (seedlings > 0) return listOf(Item(can), Item(seedling.seedling, seedlings))
        if (stock(Farming.GARDENING_TROWEL) == 0L) return emptyList()
        val pot = Farming.FILLED_PLANT_POTS.firstOrNull { stock(it) > 0 }
            ?: Farming.PLANT_POT.takeIf { canFillPots() && stock(it) > 0 } ?: return emptyList()
        val amount = minOf(limit.toLong(), stock(seedling.seed), stock(pot)).toInt()
        if (amount == 0) return emptyList()
        return buildList {
            add(Item(can))
            add(Item(Farming.GARDENING_TROWEL))
            add(Item(seedling.seed, amount))
            add(Item(pot, amount))
            if (pot == Farming.PLANT_POT && soilLocation?.let { Farming.patches(bot).getValue(it).needsRaking() } == true) {
                if (stock(Farming.RAKE) == 0L) return emptyList()
                add(Item(Farming.RAKE))
            }
        }
    }

    override suspend fun equipment(): BotGearLocator? = null
    override suspend fun beforeInventoryInit(): Boolean {
        Farming.update(bot)
        return isEligible() && !prepareWater()
    }
    override fun withdraw(): List<Item> = productionWithdraw(supplies(::owned), requestMissing = false)
    override fun bankWithdraw(): List<Item> {
        Farming.update(bot)
        if (prepareWater()) return emptyList()
        return supplies { bot.bank.computeAmountForId(it).toLong() }.also { if (it.isEmpty()) stop() }
    }

    /**
     * Verifies carried prerequisites before choosing a native packet; finished or stranded batches return to bank.
     */
    private fun canContinue(): Boolean {
        val inventory = bot.inventory
        if (remaining() == 0 || bot.farming.level < seedling.level || Farming.WATERING_CANS.none { it in inventory }) return false
        if (seedling.seedling in inventory) return true
        if (seedling.seed !in inventory || Farming.GARDENING_TROWEL !in inventory) return false
        if (Farming.FILLED_PLANT_POTS.any { it in inventory }) return true
        return Farming.PLANT_POT in inventory && canFillPots() && soilLocation?.let {
            !Farming.patches(bot).getValue(it).needsRaking() || Farming.RAKE in inventory
        } == true
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun onExecuteInZone(): Boolean {
        Farming.update(bot)
        if (!productionReady(canContinue())) return true
        val inventory = bot.inventory
        when {
            seedling.seedling in inventory -> {
                val can = Farming.WATERING_CANS.asReversed().first { it in inventory }
                attemptProduction(seedling.seedling, outputId = seedling.wateredSeedling) {
                    handler.inventory.useItem(can).onItem(seedling.seedling)
                }
            }
            Farming.FILLED_PLANT_POTS.any { it in inventory } -> {
                val pot = Farming.FILLED_PLANT_POTS.first { it in inventory }
                attemptProduction(seedling.seed, outputId = seedling.seedling) {
                    handler.inventory.useItem(seedling.seed).onItem(pot)
                }
            }
            else -> {
                val location = soilLocation ?: return false
                val patch = Farming.patches(bot).getValue(location)
                val zone = FarmingBotScript.SITES.getValue(location)
                val obj = world.locator.findObjects(zone.inside, zone.area.tileRadius) {
                    it.id == location.objectId && it.state == EntityState.ACTIVE && it.position.z == 0 && it in zone.area
                }.firstOrNull()
                val weeding = patch.needsRaking()
                val input = if (weeding) Farming.RAKE else Farming.PLANT_POT
                attemptProduction(input, available = obj != null, outputId = if (weeding) Farming.WEEDS else Farming.FILLED_PLANT_POT,
                    progressCondition = { weeding && !patch.needsRaking() }) {
                    obj != null && handler.inventory.useItem(input).onObject(obj)
                }
            }
        }
        return true
    }

    override fun snapshot(): SaplingData = SaplingData().also {
        saveInventoryState(it)
        it.seedling = seedling
        it.target = target
        it.soilKey = soilLocation?.key
    }
}
