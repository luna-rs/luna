package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.obj.resource.fillable.FillActionItem
import game.obj.resource.fillable.WaterResource
import game.obj.resource.fillable.SandResource
import game.obj.resource.fillable.MilkResource
import game.obj.resource.fillable.FillableResource
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fills owned containers with water, sand or milk through the existing player resource handlers.
 * The original script and data class names are retained so saved water tasks continue to load.
 *
 * [InventoryBotScript] supplies banking, travel and persistent retry budgets. [Source] pairs each native
 * resource with supported containers and existing processing zones. Item-on-object interactions and the
 * make dialogue start [FillActionItem]; only that native action converts items. Each bank batch is capped
 * by owned empties, inventory capacity and the remaining total filled-stock target across bank and inventory.
 * Missing empties, three failed interactions or unresolved banking requests end the task.
 *
 * Water retains its existing source zones. Sand uses Yanille's sandpit, while milk uses the dairy cow at
 * the south Falador farm. Shared travel and object interactions own navigation. Reaching the target ends
 * this task independently of any consumer. No experience is awarded or materials purchased by this script.
 *
 * @param bot The bot preparing containers.
 * @property empty The empty container accepted by the selected source.
 * @property target Desired total ownership of the filled item.
 * @param duration Maximum session duration.
 * @property source Native filling recipe and default source zones.
 * @param zones Candidate zones containing this resource.
 * @author lare96
 */
class FillWaterBotScript(
    bot: Bot,
    val empty: Int,
    val target: Int,
    duration: Duration,
    val source: Source = Source.WATER,
    zones: MutableList<SubZone> = source.zones.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true
    /**
     * Supported native resources and their default processing areas. A bucket can be filled with any of
     * these resources; saved source identity prevents a resumed task from selecting the wrong product.
     *
     * @author lare96
     */
    enum class Source(val resource: FillableResource, val emptyIds: Set<Int>, val zones: List<SubZone>) {
        WATER(WaterResource, WaterResource.EMPTY_IDS.toSet(),
              listOf(SubZone.SOUTH_FALADOR_FARM, SubZone.EDGEVILLE_MAIN, SubZone.VARROCK_SQUARE)),
        SAND(SandResource, setOf(1925), listOf(SubZone.YANILLE_SAND_PIT)),
        MILK(MilkResource, setOf(1925), listOf(SubZone.FALADOR_DAIRY_COW))
    }

    companion object {
        /**
         * Persistent container, target, and retry state alongside inherited duration and zones.
         *
         * @author lare96
         */
        class FillWaterData : InventoryScriptData() {
            /**
             * Empty container item id.
             */
            var empty = 0
            /**
             * Desired total filled-container ownership.
             */
            var target = 0
            /**
             * Native resource identity. Older water snapshots omit this field and retain water behavior.
             */
            var source = Source.WATER
            override fun load(data: JsonObject) {
                super.load(data)
                empty = data.get("empty").asInt
                target = data.get("target").asInt
                source = data.get("source")?.asString?.let(Source::valueOf) ?: Source.WATER
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("empty", empty)
                data.addProperty("target", target)
                data.addProperty("source", source.name)
            }
        }
    }

    init { require(empty in source.emptyIds && target > 0) }
    /**
     * Product of filling this container with the configured native resource.
     */
    val filled = requireNotNull(source.resource.getFilled(empty))

    /**
     * Restores source identity, container, total stock target, zones and retry budgets.
     */
    constructor(bot: Bot, data: FillWaterData) : this(bot, data.empty, data.target, data.duration, data.source, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Remaining filled stock needed across inventory and bank.
     */
    private fun remaining() = (target.toLong() - bot.bank.computeAmountForId(filled) -
        bot.inventory.computeAmountForId(filled)).coerceAtLeast(0).toInt()

    /**
     * Banked empties limited by capacity, stock and the remaining filled-stock target.
     */
    fun bankBatch(): List<Item> = bot.productionBatch(listOf(Item(empty))).map {
        Item(it.id, minOf(it.amount, remaining()))
    }.filter { it.amount > 0 }

    override fun withdraw(): List<Item> {
        if (remaining() == 0) { stop(); return emptyList() }
        return productionWithdraw(listOf(Item(empty)), requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> {
        if (remaining() == 0) {
            stop()
            return emptyList()
        }
        return bankBatch().also { if (it.isEmpty()) stop() }
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(empty in bot.inventory)

    override suspend fun onExecuteInZone(): Boolean {
        if (!isActionSafe()) return true
        if (remaining() == 0) { stop(); return true }
        if (empty !in bot.inventory) { forceBanking = true; return true }
        val zone = activeZone
        val resourceObject = zone?.let {
            world.locator.findObjects(it.area.centerPosition, it.area.tileRadius) { obj ->
                obj.position.z == bot.position.z && obj.position in it.area &&
                    obj.def() != null && source.resource.isResource(obj.def())
            }.firstOrNull()
        }
        attemptProduction(empty, dexterityDelay = false, available = resourceObject != null) { before ->
            if (resourceObject == null || !handler.inventory.useItem(empty).onObject(resourceObject) ||
                !waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return@attemptProduction false
            handler.widgets.clickMakeItem(0, minOf(before, remaining()))
            true
        }
        return true
    }

    override suspend fun finish() {
        bot.actions.first(FillActionItem::class.java)?.takeIf { it.resource === source.resource }?.interrupt()
    }

    override fun snapshot(): FillWaterData = FillWaterData().also {
        it.source = source
        it.empty = empty
        it.target = target
        saveInventoryState(it)
    }
}
