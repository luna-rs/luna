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
import game.skill.herblore.identifyHerb.Herb
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Identifies one configured herb type through its normal first inventory option.
 *
 * [InventoryBotScript] supplies zone travel, banking, session expiry, and weak-action gating. After depositing
 * the previous inventory, the script withdraws up to twenty-eight unidentified herbs, allowing a smaller final
 * batch when bank stock runs low. Identification is an individual click rather than a make-item dialogue or
 * repeating action; the existing gameplay handler performs conversion, level validation, and experience awards.
 *
 * Eligibility requires one owned herb and the permanent Herblore level for its recipe. The stable 100-200-herb
 * gathering goal does not prevent processing a smaller haul. Empty startup stock tries a prepared item-market seller
 * before herb gathering,
 * which hands usable banked stock to identification without requesting another gathering trip. Current level
 * is checked again during execution, while combat, locks, and strong actions defer processing. Three consecutive interactions without input
 * consumption end the session; unresolved banking requests are bounded separately. A failed or timed-out
 * withdrawal ends the script through the inherited banking hook.
 * Successfully exhausting owned herbs queues matching unfinished-potion preparation once, using only the
 * remaining session budget. Failed or paused work never starts a continuation.
 *
 * Snapshots retain the herb recipe, duration, candidate zones, and retry counters. Inventory slots and other
 * live state are rebuilt on resumption. No tool, quest, or orb/potion preparation is required by this activity.
 *
 * @param bot The bot running this script.
 * @property herb The unidentified herb recipe to process throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class IdentifyHerbBotScript(
    bot: Bot,
    val herb: Herb,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_HERBLORE),
    private var allowGathering: Boolean = true
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * A stable restocking goal for gathering sessions, independent of the minimum identification supplies.
         */
        fun herbTarget(bot: Bot): Int = 100 + Math.floorMod(bot.username.hashCode(), 101)

        /**
         * Saved herb recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class HerbData : InventoryScriptData() {
            /** [Herb] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            var allowGathering = true
            var started = false
            var produced = false
            var continued = false
            var marketAttempted = false

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                allowGathering = data.get("allow_gathering")?.asBoolean ?: true
                started = data.get("started")?.asBoolean ?: false
                produced = data.get("produced")?.asBoolean ?: false
                continued = data.get("continued")?.asBoolean ?: false
                marketAttempted = data.get("market_attempted")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("allow_gathering", allowGathering)
                data.addProperty("started", started)
                data.addProperty("produced", produced)
                data.addProperty("continued", continued)
                data.addProperty("market_attempted", marketAttempted)
            }
        }
    }

    /** Permanent Herblore level required for selection and startup validation. */
    val requiredLevel = herb.level
    private var started = false
    private var produced = false
    private var continued = false
    private var marketAttempted = false
    /** One unidentified herb, the minimum supply for a single conversion. */
    private val materials = listOf(herb.idItem)

    /**
     * Restores the herb, session configuration, and retry budgets from a snapshot.
     * Initialization still checks current owned supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized herb-identification state.
     */
    constructor(bot: Bot, data: HerbData) : this(bot, Herb.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
        allowGathering = data.allowGathering
        started = data.started
        produced = data.produced
        continued = data.continued
        marketAttempted = data.marketAttempted
    }

    /**
     * Checks permanent level and at least one unidentified herb across inventory and bank.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked unidentified herbs, or an empty batch when stock is exhausted. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (bot.skill(SKILL_HERBLORE).staticLevel < requiredLevel) { stop(); return emptyList() }
        if (!started && !bot.ownsProductionSupplies(materials)) {
            if (allowGathering) {
                allowGathering = false
                bot.scriptStack.softPushHead(HerbGatheringScript(bot, herb, duration))
            }
            stop()
            return emptyList()
        }
        started = true
        return productionWithdraw(materials, levelEligible = true, requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) {
            queueContinuation()
            stop()
        }
    }

    /**
     * Continues verified production once, retaining the remaining budget and rejecting interrupted work.
     */
    private fun queueContinuation() {
        if (!produced || continued || isPaused() || isTerminated() || productionRetriesExhausted() || !isActionSafe()) return
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return
        continued = true
        HerbloreProduction.afterIdentify(bot, herb, remaining)?.let { bot.scriptStack.softPushHead(it) }
    }

    override suspend fun beforeInventoryInit(): Boolean {
        if (produced && HerbloreProduction.owned(bot, herb.id) < 1) {
            queueContinuation()
            stop()
            return false
        }
        if (allowGathering && productionPlan == null && !marketAttempted &&
            !bot.ownsProductionSupplies(materials) && isActionSafe() && remainingDuration() > Duration.ZERO) {
            marketAttempted = true
            val deadline = System.currentTimeMillis() + minOf(45_000L, remainingDuration().inWholeMilliseconds)
            val trade = TradeBotScript.buySpecific(bot, herb.id, herbTarget(bot), deadline)
            if (trade != null) {
                trade.duration = minOf(remainingDuration(), 45.seconds)
                bot.scriptStack.pushHead(trade)
                return false
            }
        }
        return true
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one unidentified herb of the configured type. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_HERBLORE).level >= requiredLevel)) return true
        val inputBefore = bot.inventory.computeAmountForId(herb.id)
        val outputBefore = bot.inventory.computeAmountForId(herb.identified)
        try {
            attemptProduction(materials.first().id) { startProduction() }
        } finally {
            if (bot.inventory.computeAmountForId(herb.id) < inputBefore ||
                bot.inventory.computeAmountForId(herb.identified) > outputBefore) produced = true
        }
        return true
    }

    /** Clicks the herb's first inventory option to invoke the existing identification handler once. */
    private suspend fun startProduction(): Boolean = handler.inventory.clickItem(1, herb.id)

    override fun snapshot(): HerbData = HerbData().also {
        it.recipe = herb.name
        it.allowGathering = allowGathering
        it.started = started
        it.produced = produced
        it.continued = continued
        it.marketAttempted = marketAttempted
        saveInventoryState(it)
    }
}
