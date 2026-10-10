package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.obj.resource.fillable.FillActionItem
import game.obj.resource.fillable.WaterResource
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlinx.coroutines.withTimeoutOrNull
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
    companion object {
        /** Retry budget shared by source/interaction failures and independently by banking failures. */
        private const val MAX_FAILURES = 3

        /**
         * Persistent container, target, and retry state alongside inherited duration and zones.
         *
         * @author lare96
         */
        class FillWaterData : ZonedBotScriptData() {
            /** Empty water-container item id. */
            var empty = 0
            /** Desired total filled-container ownership. */
            var target = 0
            /** Consecutive interactions without empty-container consumption. */
            var failures = 0
            /** Banking requests since the last verified withdrawal. */
            var bankFailures = 0
            override fun load(data: JsonObject) {
                super.load(data)
                empty = data.get("empty").asInt
                target = data.get("target").asInt
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("empty", empty)
                data.addProperty("target", target)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
            }
        }
    }

    init { require(empty in WaterResource.EMPTY_IDS && target > 0) }
    /** Filled item corresponding to the configured empty container. */
    val filled = WaterResource.FILLABLES.getValue(empty)
    /** Consecutive failed source or production interactions. */
    private var failures = 0
    /** Unresolved banking requests, reset after verified withdrawal. */
    private var bankFailures = 0

    /** Restores a prerequisite's container, stock target, session configuration, and retry budgets. */
    constructor(bot: Bot, data: FillWaterData) : this(bot, data.empty, data.target, data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Remaining filled stock needed across inventory and bank. */
    private fun remaining() = (target.toLong() - bot.bank.computeAmountForId(filled) -
        bot.inventory.computeAmountForId(filled)).coerceAtLeast(0).toInt()

    /** Current safety state required before banking, production, or follow-up selection. */
    private fun safe() = bot.health > 0 && !bot.isLocked && !bot.combat.inCombat() &&
        bot.actions.size(ActionType.STRONG) == 0

    /** Banked empties limited by capacity, stock, and the remaining filled-stock target. */
    fun bankBatch(): List<Item> = bot.productionBatch(listOf(Item(empty))).map {
        Item(it.id, minOf(it.amount, remaining()))
    }.filter { it.amount > 0 }

    override fun withdraw(): List<Item> {
        if (!safe() || failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES) {
            stop()
            return emptyList()
        }
        if (remaining() == 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(listOf(Item(empty)))) {
            bot.preferences.raiseWantedItemTarget(empty, 1_000)
            stop()
            return emptyList()
        }
        forceBanking = true
        return listOf(Item(empty))
    }

    override fun bankWithdraw(): List<Item> {
        if (remaining() == 0) {
            stop()
            return emptyList()
        }
        return bankBatch().also { if (it.isEmpty()) stop() }
    }

    override suspend fun withdrawBankItems(items: List<Item>): Boolean {
        val success = withTimeoutOrNull(15_000) {
            handler.banking.clickBankingMode(false)
            handler.banking.withdrawAll(items) && bot.inventory.containsAll(items)
        } == true
        if (success) { bankFailures = 0; forceBanking = false }
        return success
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (!safe()) return false
        if (!forceBanking && empty in bot.inventory) return false
        if (bankFailures >= MAX_FAILURES) { stop(); return false }
        bankFailures++
        return true
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!safe()) return true
        if (remaining() == 0) { stop(); return true }
        if (empty !in bot.inventory) { forceBanking = true; return true }
        val zone = activeZone
        val source = zone?.let {
            world.locator.findObjects(it.area.centerPosition, it.area.tileRadius) { obj ->
                obj.position.z == bot.position.z && obj.def() != null && WaterResource.isResource(obj.def())
            }.firstOrNull()
        }
        val before = bot.inventory.computeAmountForId(empty)
        val started = source != null && withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && handler.inventory.useItem(empty).onObject(source) &&
                waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }
        } == true
        if (started) handler.widgets.clickMakeItem(0, minOf(before, remaining()))
        val progressed = started && waitFor(10.seconds) { bot.inventory.computeAmountForId(empty) < before }
        if (progressed) failures = 0 else if (++failures >= MAX_FAILURES) stop()
        return true
    }

    override suspend fun finish() {
        bot.actions.first(FillActionItem::class.java)?.takeIf { it.resource === WaterResource }?.interrupt()
    }

    override fun snapshot(): FillWaterData = FillWaterData().also {
        it.empty = empty
        it.target = target
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
