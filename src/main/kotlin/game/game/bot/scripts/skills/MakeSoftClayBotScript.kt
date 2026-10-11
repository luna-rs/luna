package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.obj.resource.fillable.WaterResource
import game.bot.scripts.FillWaterBotScript
import game.skill.crafting.potteryCrafting.MakeSoftClayActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Prepares soft clay from owned clay and one configured kind of filled water container.
 *
 * [InventoryBotScript] manages banking, travel, session expiry, and weak-action gating. Bank visits withdraw
 * up to fourteen balanced input pairs, limited by the smaller available stock. The ordinary clay-on-water
 * interaction starts [MakeSoftClayActionItem], which converts the inputs and returns the corresponding empty
 * containers. It awards no experience and has no skill requirement. Selection is limited to the Crafting
 * factory's non-training mode as pottery preparation; no sale price or market margin is assumed.
 *
 * If water runs out while clay and matching empty containers remain, a bounded [FillWaterBotScript] prerequisite
 * refills enough for the next clay batch. Activity selection can then choose preparation using the new stock.
 * This script does not mine clay or make pottery.
 * Missing startup inputs raise total wanted-stock targets to at least 1,000 before stopping. Three consecutive
 * interactions without clay consumption or unresolved bank requests end the session. Snapshots retain the
 * water-container id, duration, zones, and retry budgets, while live inventory and safety are checked again.
 *
 * @param bot The bot running this script.
 * @property water The filled water-container item id supported by [WaterResource].
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class MakeSoftClayBotScript(
    bot: Bot,
    val water: Int,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Clay input consumed by the existing soft-clay handler. */
        const val CLAY = 434

        /**
         * Saved water-container id and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains exhausted budgets instead of granting new attempts.
         *
         * @author lare96
         */
        class SoftClayData : InventoryScriptData() {
            /** Decimal filled-container item id used to reconstruct this session. */
            var recipe = ""

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    init { require(water in WaterResource.FILLED_IDS) }

    /** Level-one selector bucket; preparation itself has no skill requirement and grants no experience. */
    val requiredLevel = 1
    /** One clay and one filled water container, the minimum inputs for a conversion. */
    private val materials = listOf(Item(CLAY), Item(water))

    /**
     * Restores the water-container id, session configuration, and retry budgets from saved state.
     * Normal lifecycle hooks recheck current supplies and bot safety before preparation resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized soft-clay preparation state.
     */
    constructor(bot: Bot, data: SoftClayData) : this(bot, data.recipe.toInt(), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Whether at least one clay and configured filled water container are owned across inventory and bank. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Whether owned clay and matching empties can satisfy missing water through the refill prerequisite. */
    fun canPrepareWater(): Boolean = !bot.ownsProductionSupplies(listOf(Item(water))) &&
        bot.ownsProductionSupplies(listOf(Item(CLAY), Item(WaterResource.FILLABLES.inverse().getValue(water))))

    /** Queues one bounded refill only when both clay and matching empties remain; no acquisition loop is created. */
    private fun queueWaterPreparation(): Boolean {
        if (!isInventoryActionSafe()) return false
        if (!canPrepareWater()) return false
        val empty = WaterResource.FILLABLES.inverse().getValue(water)
        val clayStock = bot.bank.computeAmountForId(CLAY).toLong() + bot.inventory.computeAmountForId(CLAY)
        val emptyStock = bot.bank.computeAmountForId(empty).toLong() + bot.inventory.computeAmountForId(empty)
        bot.scriptStack.softPushHead(FillWaterBotScript(bot, empty, minOf(14L, clayStock, emptyStock).toInt(), duration))
        stop()
        return true
    }

    /** Returns up to fourteen balanced banked input pairs, or an empty batch when either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, prepareMissing = { queueWaterPreparation() })

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty() && !queueWaterPreparation()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete clay/water input pair. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials())) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses clay on the configured filled container; the gameplay handler starts the repeating action directly. */
    private suspend fun startProduction(): Boolean = handler.inventory.useItem(CLAY).onItem(water)

    override suspend fun finish() {
        bot.actions.first(MakeSoftClayActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): SoftClayData = SoftClayData().also {
        it.recipe = water.toString()
        saveInventoryState(it)
    }
}
