package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Identifies one configured herb type through its normal first inventory option.
 *
 * [InventoryBotScript] supplies zone travel, banking, session expiry, and weak-action gating. After depositing
 * the previous inventory, the script withdraws up to twenty-eight unidentified herbs, allowing a smaller final
 * batch when bank stock runs low. Identification is an individual click rather than a make-item dialogue or
 * repeating action; the existing gameplay handler performs conversion, level validation, and experience awards.
 *
 * Eligibility requires an owned unidentified herb and the permanent Herblore level for its recipe. Missing
 * startup stock is added to wanted items before stopping. Current level is checked again during execution,
 * while combat, locks, and strong actions defer processing. Three consecutive interactions without input
 * consumption end the session; unresolved banking requests are bounded separately. A failed or timed-out
 * withdrawal ends the script through the inherited banking hook.
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
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Saved herb recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class HerbData : ZonedBotScriptData() {
            /** [Herb] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Consecutive interactions that did not consume an unidentified herb. */
            var failures = 0
            /** Banking requests since the last successfully verified withdrawal. */
            var bankFailures = 0

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
            }
        }
    }

    /** Permanent Herblore level required for selection and startup validation. */
    val requiredLevel = herb.level
    /** One unidentified herb, the minimum supply for a single conversion. */
    private val materials = listOf(herb.idItem)
    /** Consecutive failed identification attempts, reset after an input is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the herb, session configuration, and retry budgets from a snapshot.
     * Initialization still checks current owned supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized herb-identification state.
     */
    constructor(bot: Bot, data: HerbData) : this(bot, Herb.valueOf(data.recipe), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Checks permanent level eligibility and at least one unidentified herb across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked unidentified herbs, or an empty batch when stock is exhausted. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_HERBLORE).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials)) {
            val missing = materials.filter {
                bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id) < it.amount
            }.map { it.id }
            missing.forEach { bot.preferences.addWantedItem(it, 28) }
            stop()
            return emptyList()
        }
        // InventoryBotScript validates these minimum supplies; the bank hook calculates the actual batch.
        forceBanking = true
        return materials
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun withdrawBankItems(items: List<Item>): Boolean {
        val success = withTimeoutOrNull(15_000) {
            handler.banking.clickBankingMode(false)
            handler.banking.withdrawAll(items) && bot.inventory.containsAll(items)
        } == true
        if (success) {
            bankFailures = 0
            forceBanking = false
        }
        return success
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return false
        if (!forceBanking && hasMaterials()) return false
        if (bankFailures >= MAX_FAILURES) {
            stop()
            return false
        }
        bankFailures++
        return true
    }

    /** Whether inventory contains at least one unidentified herb of the configured type. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
        if (bot.skill(SKILL_HERBLORE).level < requiredLevel) {
            stop()
            return true
        }
        if (!hasMaterials()) {
            forceBanking = true
            return true
        }
        val amount = bot.inventory.computeAmountForId(materials.first().id)
        val started = withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && startProduction()
        } == true
        val progressed = started && waitFor(10.seconds) {
            bot.inventory.computeAmountForId(materials.first().id) < amount
        }
        if (progressed) failures = 0
        else if (++failures >= MAX_FAILURES) stop()
        bot.naturalDexterityDelay()
        return true
    }

    /** Clicks the herb's first inventory option to invoke the existing identification handler once. */
    private suspend fun startProduction(): Boolean = handler.inventory.clickItem(1, herb.id)

    override fun snapshot(): HerbData = HerbData().also {
        it.recipe = herb.name
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
