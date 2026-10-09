package game.bot.scripts

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
import game.content.crystalChest.MakeCrystalKeyActionItem
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Assembles crystal keys from owned tooth and loop halves using the normal item-on-item interaction.
 *
 * [InventoryBotScript] manages banking, travel, session expiry, and weak-action gating. Each bank visit
 * withdraws up to fourteen balanced pairs, limited by the smaller available stock. The make dialogue starts
 * [MakeCrystalKeyActionItem], which consumes both halves and creates a key without experience or a skill
 * requirement. The combat coordinator selects this preparation activity only in profit-combat mode.
 * This script does not open the crystal chest or claim a market profit.
 *
 * Missing startup halves raise their total wanted-stock targets to at least 1,000 before stopping. Three
 * consecutive interactions without input consumption or unresolved banking requests end the session.
 * Withdrawals are unnoted and verified. Snapshots preserve duration, zones, and retry budgets; inventory
 * and bot safety are checked again when a saved session resumes.
 *
 * @param bot The bot running this script.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class MakeCrystalKeyBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /** Tooth half consumed by the existing crystal-key assembly handler. */
        const val TOOTH_HALF = 985
        /** Loop half consumed by the existing crystal-key assembly handler. */
        const val LOOP_HALF = 987

        /**
         * Saved retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains exhausted budgets rather than granting new attempts.
         *
         * @author lare96
         */
        class CrystalKeyData : ZonedBotScriptData() {
            /** Consecutive interactions that did not consume a tooth half. */
            var failures = 0
            /** Banking requests since the last successfully verified withdrawal. */
            var bankFailures = 0

            override fun load(data: JsonObject) {
                super.load(data)
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
            }
        }
    }

    /** One of each half, the minimum inputs for a single crystal key. */
    private val materials = listOf(Item(TOOTH_HALF), Item(LOOP_HALF))
    /** Consecutive failed production interactions, reset after an input is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset only after the entire batch is withdrawn. */
    private var bankFailures = 0

    /**
     * Restores the session configuration and retry budgets from saved state.
     * Normal lifecycle hooks recheck current supplies and bot safety before assembly resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized crystal-key assembly state.
     */
    constructor(bot: Bot, data: CrystalKeyData) : this(bot, data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /** Whether the bot owns at least one of each half across inventory and bank. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced banked half pairs, or an empty batch when either half is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials)) {
            val missing = materials.filter {
                bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id) < it.amount
            }.map { it.id }
            missing.forEach { bot.preferences.raiseWantedItemTarget(it, 1_000) }
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

    /** Whether inventory contains at least one complete pair of key halves. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
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

    /** Uses the tooth half on the loop half, waits for its dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(TOOTH_HALF).onItem(LOOP_HALF)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeCrystalKeyActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): CrystalKeyData = CrystalKeyData().also {
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
