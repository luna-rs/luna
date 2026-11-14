package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.InventoryBotScript
import engine.bot.speech.BotConversationPool.ConversationSpeech
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.stringBow.Bow
import game.skill.fletching.stringBow.StringBowAction
import game.skill.crafting.textileCrafting.Textile
import game.bot.scripts.trading.TradeBotScript
import kotlinx.coroutines.withTimeoutOrNull
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Strings one configured bow type using owned unstrung bows and bow strings obtained during startup if missing.
 * A single unstrung bow qualifies; bank withdrawals balance both inputs up to fourteen pairs, including
 * the last partial batch. Production uses the ordinary item interaction and make dialogue, with conversion
 * and experience supplied by [StringBowAction]. Arrow shafts are excluded because they cannot be strung.
 *
 * [InventoryBotScript] supplies travel, verified banking, safety, session expiry, and bounded retries.
 * Saved scripts retain their bow, zones, duration, and retry counters. Missing strings first use a prepared item-market
 * seller, then shops, then a bounded flax-and-spinning prerequisite. Native production progress permits
 * another refill; a failed prerequisite cannot repeatedly rebuild the same child chain.
 *
 * @param bot The bot stringing bows.
 * @property bow The bow recipe used throughout the session.
 * @param duration The session duration.
 * @param zones Existing processing zones with bank and travel support.
 * @author lare96
 */
class StringBowBotScript(
    bot: Bot,
    val bow: Bow,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val ambientProductionTopic = ConversationSpeech.FLETCHING
    override val trainingSkill = SKILL_FLETCHING

    override val verifiedProductionWithdrawals = true

    init {
        require(bow != Bow.ARROW_SHAFT) { "ARROW_SHAFT bow type cannot be used with this script." }
    }

    companion object {
        /**
         * Saved bow recipe and inherited session/retry state.
         *
         * @author lare96
         */
        class StringBowData : InventoryScriptData() {
            /** Bow enum retained under its existing snapshot field name. */
            var bow: Bow = Bow.SHORTBOW
            var supplyAttempts = 0
            var marketAttempted = false
            var supplyDeadline = 0L

            override fun load(data: JsonObject) {
                super.load(data)
                bow = Bow.valueOf(data.get("bow").asString)
                supplyAttempts = data.get("supplyAttempts")?.asInt ?: 0
                marketAttempted = data.get("marketAttempted")?.asBoolean ?: false
                supplyDeadline = data.get("supplyDeadline")?.asLong ?: 0L
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("bow", bow.name)
                data.addProperty("supplyAttempts", supplyAttempts)
                data.addProperty("marketAttempted", marketAttempted)
                data.addProperty("supplyDeadline", supplyDeadline)
            }
        }
    }

    /** Permanent Fletching level required for selection and startup. */
    val requiredLevel = bow.level
    /** One matching unstrung bow and string, the minimum complete recipe. */
    private val materials = listOf(bow.unstrungItem, Item(Bow.BOW_STRING))
    private var supplyAttempts = 0
    private var marketAttempted = false
    private var supplyDeadline = 0L

    /** Restores the legacy bow field together with saved zones and shared retry counters. */
    constructor(bot: Bot, data: StringBowData) : this(bot, data.bow, data.duration, data.zones) {
        restoreInventoryState(data)
        supplyAttempts = data.supplyAttempts
        marketAttempted = data.marketAttempted
        supplyDeadline = data.supplyDeadline
    }

    /**
     * Checks permanent level and an owned unstrung bow, leaving missing strings to startup acquisition.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
            FletchingProduction.owned(bot, bow.unstrung) > 0

    /** Returns up to fourteen balanced pairs, capped by the smaller bank stock. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    private fun supplyTime(): Duration {
        val remaining = remainingDuration()
        if (supplyDeadline > 0) duration = minOf(remaining,
            (supplyDeadline - System.currentTimeMillis()).milliseconds)
        return duration
    }

    private suspend fun prepareStrings(): Boolean {
        if (supplyTime() <= Duration.ZERO || !bot.profitMode &&
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_FLETCHING)) return false
        if (FletchingProduction.owned(bot, Bow.BOW_STRING) > 0) return true
        val bows = FletchingProduction.owned(bot, bow.unstrung)
        if (bows == 0L || supplyAttempts >= 1 || !isActionSafe() ||
            bot.fletching.staticLevel < requiredLevel) return false
        if (supplyDeadline == 0L) supplyDeadline = System.currentTimeMillis() + duration.inWholeMilliseconds
        supplyAttempts++
        val target = minOf(28L, bows).toInt()
        withTimeoutOrNull(minOf(120.seconds, supplyTime())) {
            handler.shop.obtainWantedItem(Bow.BOW_STRING, target)
        }
        if (supplyTime() <= Duration.ZERO || !isActionSafe()) return false
        if (FletchingProduction.owned(bot, Bow.BOW_STRING) > 0) return true
        if (!BotCombatActionHandler.mayGainExperience(bot, SKILL_CRAFTING) ||
            bot.crafting.staticLevel < Textile.BOWSTRING.level &&
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_CRAFTING)) return false
        bot.scriptStack.pushHead(SpinTextileBotScript(bot, duration,
            supplyTarget = target, supplyDeadline = supplyDeadline))
        return false
    }

    override suspend fun beforeInventoryInit(): Boolean {
        if (!marketAttempted && supplyTime() > Duration.ZERO && isActionSafe() &&
            FletchingProduction.owned(bot, Bow.BOW_STRING) == 0L &&
            FletchingProduction.owned(bot, bow.unstrung) > 0L && bot.fletching.staticLevel >= requiredLevel) {
            if (supplyDeadline == 0L) supplyDeadline = System.currentTimeMillis() + duration.inWholeMilliseconds
            marketAttempted = true
            val target = minOf(28L, FletchingProduction.owned(bot, bow.unstrung)).toInt()
            val trade = TradeBotScript.buySpecific(bot, Bow.BOW_STRING, target,
                minOf(supplyDeadline, System.currentTimeMillis() + 45_000L))
            if (trade != null) {
                trade.duration = minOf(45.seconds, supplyTime())
                bot.scriptStack.pushHead(trade)
                return false
            }
        }
        return prepareStrings()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (FletchingProduction.owned(bot, Bow.BOW_STRING) == 0L &&
            FletchingProduction.owned(bot, bow.unstrung) > 0 && !prepareStrings()) {
            if (!isPaused()) stop()
            return false
        }
        return requestProductionBank(hasMaterials())
    }

    /** Whether the carried inputs can produce at least one configured bow. */
    private fun hasMaterials(): Boolean = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        val before = bot.inventory.computeAmountForId(bow.unstrung)
        attemptProduction(bow.unstrung) { startProduction() }
        if (bot.inventory.computeAmountForId(bow.unstrung) < before) {
            supplyAttempts = 0
            marketAttempted = false
        }
        return true
    }

    /** Opens the native single-recipe dialogue and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(Bow.BOW_STRING).onItem(bow.unstrung)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(StringBowAction::class.java)?.interrupt()
    }

    override fun snapshot(): StringBowData = StringBowData().also {
        it.bow = bow
        saveInventoryState(it)
        it.supplyAttempts = supplyAttempts
        it.marketAttempted = marketAttempted
        it.supplyDeadline = supplyDeadline
    }
}
