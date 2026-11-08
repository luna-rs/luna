package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import engine.bot.speech.BotConversationPool.ConversationSpeech
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.cutLog.CutLogActionItem
import game.skill.fletching.cutLog.Log
import game.skill.fletching.cutLog.Log.Companion.KNIFE
import game.skill.fletching.stringBow.Bow
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cuts a configured log into its existing arrow-shaft or unstrung-bow recipe through the player dialogue.
 * One log and a knife qualify. Banking withdraws up to twenty-seven logs, including the final partial stock.
 * If arrow shafts are defined as non-stackable, the batch is reduced to fit their fifteen-per-log output.
 *
 * The shared inventory lifecycle handles verified banking, travel, safety, expiry, and bounded retries.
 * Snapshots retain the log, valid recipe index, zones, duration, retry budgets and verified production state.
 * Exhausted log stock queues arrow preparation or personality-selected stringing before the script stops.
 * Follow-up work keeps the remaining session budget and obtains missing secondary supplies during startup.
 *
 * @param bot The bot cutting logs.
 * @property log The log type consumed throughout the session.
 * @property index The existing player dialogue index within this log's recipes.
 * @param duration The production session duration.
 * @param zones Existing processing zones with banking and travel support.
 * @author lare96
 */
class CutLogBotScript(
    bot: Bot,
    val log: Log,
    val index: Int,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val ambientProductionTopic = ConversationSpeech.FLETCHING

    override val verifiedProductionWithdrawals = true

    init {
        require(index in log.bows.indices) { "Make-item index must identify a recipe for $log." }
    }

    companion object {
        /**
         * Saved log recipe, continuation guard and inherited session/retry state.
         *
         * @author lare96
         */
        class CutLogData : InventoryScriptData() {
            /** Log enum retained under the existing snapshot field name. */
            var log: Log = Log.NORMAL
            /** Player recipe index retained under the existing snapshot field name. */
            var index = 0
            var produced = false
            var continued = false

            override fun load(data: JsonObject) {
                super.load(data)
                log = Log.valueOf(data.get("log").asString)
                index = data.get("index").asInt
                produced = data.get("produced")?.asBoolean ?: false
                continued = data.get("continued")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("log", log.name)
                data.addProperty("index", index)
                data.addProperty("produced", produced)
                data.addProperty("continued", continued)
            }
        }
    }

    /** Actual product selected by this log's player dialogue. */
    val bow = log.bows[index]
    /** Permanent Fletching level required to select and initialize the configured recipe. */
    val requiredLevel = bow.level
    /** One input log; the knife is a reusable tool rather than a consumable. */
    private val materials = listOf(Item(log.id))
    /** Tool reserved in bank batches and requested at the shared tool-stock target when missing. */
    private val requiredTools = setOf(KNIFE)
    private var produced = false
    private var continued = false

    /** Restores the existing log/index fields, saved zones, duration, and shared retry budgets. */
    constructor(bot: Bot, data: CutLogData) : this(bot, data.log, data.index, data.duration, data.zones) {
        restoreInventoryState(data)
        produced = data.produced
        continued = data.continued
    }

    /** Checks permanent level, one owned log, and a knife across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials, requiredTools)

    /** Returns a partial log batch with one tool slot and sufficient room for the configured output. */
    fun bankBatch(): List<Item> {
        val batch = bot.productionBatch(materials, requiredTools)
        if (batch.isEmpty() || bow != Bow.ARROW_SHAFT || bot.inventory.stackable(bow.unstrung)) return batch
        val amount = minOf(batch.last().amount, (bot.inventory.capacity() - requiredTools.size) / Arrow.SET_AMOUNT)
        return if (amount > 0) listOf(Item(KNIFE), Item(log.id, amount)) else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials, requiredTools,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) {
            queueContinuation()
            stop()
        }
    }

    override suspend fun beforeInventoryInit(): Boolean {
        if (produced && FletchingProduction.owned(bot, log.id) < 1) {
            queueContinuation()
            stop()
            return false
        }
        return true
    }

    /**
     * Enqueues a single forward stage before stock exhaustion cancels this coroutine.
     */
    private fun queueContinuation() {
        if (!produced || continued || isPaused() || isTerminated() || productionRetriesExhausted() || !isActionSafe()) return
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return
        continued = true
        if (bow != Bow.ARROW_SHAFT && !(rand(bot.personality.intelligence) || bot.personality.isDextrous)) return
        FletchingProduction.afterCutting(bot, bow, remaining)?.let { bot.scriptStack.softPushHead(it) }
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Checks the minimum supplies and space released by consuming the next log. */
    private fun hasMaterials(): Boolean {
        if (!bot.inventory.containsAll(materials) || !bot.inventory.contains(KNIFE)) return false
        val freed = if (!bot.inventory.stackable(log.id) || bot.inventory.computeAmountForId(log.id) == 1) 1 else 0
        val output = if (!bot.inventory.stackable(bow.unstrung)) {
            if (bow == Bow.ARROW_SHAFT) Arrow.SET_AMOUNT else 1
        } else if (bot.inventory.contains(bow.unstrung)) 0 else 1
        return bot.inventory.computeRemainingSize() + freed >= output
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        val inputBefore = bot.inventory.computeAmountForId(log.id)
        val outputBefore = bot.inventory.computeAmountForId(bow.unstrung)
        try {
            attemptProduction(log.id) { startProduction() }
        } finally {
            if (bot.inventory.computeAmountForId(log.id) < inputBefore ||
                bot.inventory.computeAmountForId(bow.unstrung) > outputBefore) produced = true
        }
        return true
    }

    /** Uses a knife on the configured log and requests exactly the carried log count for its recipe index. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(KNIFE).onItem(log.id)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        handler.widgets.clickMakeItem(index, bot.inventory.computeAmountForId(log.id))
        return true
    }

    override suspend fun finish() {
        bot.actions.first(CutLogActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): CutLogData = CutLogData().also {
        it.log = log
        it.index = index
        it.produced = produced
        it.continued = continued
        saveInventoryState(it)
    }
}
