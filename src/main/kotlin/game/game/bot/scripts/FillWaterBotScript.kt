package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.obj.resource.fillable.FillActionItem
import game.obj.resource.fillable.WaterResource
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fills owned empty water containers to a total filled-stock target for any activity that needs water.
 *
 * Banking and travel use [InventoryBotScript]. Water sources are discovered through [WaterResource] in the
 * Draynor processing zone. The normal item-on-object interaction and make dialogue start [FillActionItem];
 * the bot does not convert items directly. Each withdrawal is limited by remaining target stock and capacity.
 * Three failed interactions or unresolved banking requests stop the task without queuing more work.
 *
 * Reaching the target ends this task without selecting a consumer. Cooking, Herblore, Crafting, and general
 * activities can independently use the filled stock. Saved state retains the target and retry budgets.
 *
 * @param bot The bot running this prerequisite.
 * @property empty The supported empty container item id.
 * @property target Desired total filled-container ownership; each bank batch is capped by inventory capacity.
 * @param duration Maximum time allowed for this filling session.
 * @param zones Zones in which to search for recognised water sources.
 * @author lare96
 */
class FillWaterBotScript(
    bot: Bot,
    val empty: Int,
    val target: Int,
    duration: Duration,
    zones: MutableList<SubZone> = mutableListOf(SubZone.DRAYNOR_MAIN)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true
    companion object {
        /**
         * Persistent container, target, and retry state alongside inherited duration and zones.
         *
         * @author lare96
         */
        class FillWaterData : InventoryScriptData() {
            /** Empty water-container item id. */
            var empty = 0
            /** Desired total filled-container ownership. */
            var target = 0
            override fun load(data: JsonObject) {
                super.load(data)
                empty = data.get("empty").asInt
                target = data.get("target").asInt
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("empty", empty)
                data.addProperty("target", target)
            }
        }
    }

    init { require(empty in WaterResource.EMPTY_IDS && target > 0) }
    /** Filled item corresponding to the configured empty container. */
    val filled = WaterResource.FILLABLES.getValue(empty)

    /** Restores a prerequisite's container, stock target, session configuration, and retry budgets. */
    constructor(bot: Bot, data: FillWaterData) : this(bot, data.empty, data.target, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Remaining filled stock needed across inventory and bank. */
    private fun remaining() = (target.toLong() - bot.bank.computeAmountForId(filled) -
        bot.inventory.computeAmountForId(filled)).coerceAtLeast(0).toInt()

    /** Banked empties limited by capacity, stock, and the remaining filled-stock target. */
    fun bankBatch(): List<Item> = bot.productionBatch(listOf(Item(empty))).map {
        Item(it.id, minOf(it.amount, remaining()))
    }.filter { it.amount > 0 }

    override fun withdraw(): List<Item> {
        if (remaining() == 0) { stop(); return emptyList() }
        return productionWithdraw(listOf(Item(empty)))
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
        if (!isInventoryActionSafe()) return true
        if (remaining() == 0) { stop(); return true }
        if (empty !in bot.inventory) { forceBanking = true; return true }
        val zone = activeZone
        val source = zone?.let {
            world.locator.findObjects(it.area.centerPosition, it.area.tileRadius) { obj ->
                obj.position.z == bot.position.z && obj.def() != null && WaterResource.isResource(obj.def())
            }.firstOrNull()
        }
        attemptProduction(empty, dexterityDelay = false, available = source != null) { before ->
            if (source == null || !handler.inventory.useItem(empty).onObject(source) ||
                !waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return@attemptProduction false
            handler.widgets.clickMakeItem(0, minOf(before, remaining()))
            true
        }
        return true
    }

    override suspend fun finish() {
        bot.actions.first(FillActionItem::class.java)?.takeIf { it.resource === WaterResource }?.interrupt()
    }

    override fun snapshot(): FillWaterData = FillWaterData().also {
        it.empty = empty
        it.target = target
        saveInventoryState(it)
    }
}
