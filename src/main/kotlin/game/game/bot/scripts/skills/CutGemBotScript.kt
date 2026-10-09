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
import game.skill.crafting.gemCutting.CutGemActionItem
import game.skill.crafting.gemCutting.Gem
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cuts one configured precious gem using a chisel and the normal player make-item dialogue.
 *
 * [InventoryBotScript] supplies banking, zone travel, session expiry, and weak-action gating. Each bank visit
 * deposits the previous inventory before withdrawing one chisel and up to twenty-seven uncut gems. Smaller
 * batches are allowed when bank stock is low. Semi-precious gems are rejected because their crushing rules differ.
 *
 * The script requires owned supplies rather than assuming another system can purchase them. Missing startup
 * items are requested through the wanted-item list; depleted stock ends the session. Interactions must consume
 * an input within ten seconds, with three consecutive failures ending the script. Banking requests are also
 * bounded, and an unsuccessful or timed-out withdrawal ends the script through the base class.
 *
 * Saved state contains the recipe, duration, candidate zones, and failure counters. Live actions and inventory
 * positions are rebuilt on resumption. Production uses player interactions so [CutGemActionItem] remains
 * responsible for level checks, item conversion, and experience.
 *
 * @param bot The bot running this script.
 * @property gem The precious gem recipe to process throughout this session.
 * @param duration The session duration managed by the inherited zone lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class CutGemBotScript(
    bot: Bot,
    val gem: Gem,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        /** Maximum consecutive failed interactions or unresolved banking requests before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Serializable recipe and retry state, alongside the inherited duration and candidate zones.
         * Failure counters survive resumption so restarting a saved script cannot reset an exhausted budget.
         *
         * @author lare96
         */
        class CutGemData : ZonedBotScriptData() {
            /** [Gem] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Consecutive interactions that did not consume an uncut gem. */
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

    init { require(!gem.isSemiPrecious()) { "Semi-precious gem success rates need review." } }

    /** Static Crafting level required for factory selection and startup validation. */
    val requiredLevel = gem.level
    /** Minimum input for one conversion; the bank batch expands this to available inventory capacity. */
    private val materials = listOf(Item(gem.uncut))
    /** Reusable tools reserved in every batch and retained by the gem-cutting action. */
    private val tools = setOf(Gem.CHISEL)
    /** Consecutive failed production interactions, reset when an input is consumed. */
    private var failures = 0
    /** Unresolved banking requests, reset only after the complete batch is present in inventory. */
    private var bankFailures = 0

    /**
     * Restores the recipe, session configuration, and failure budgets from a saved snapshot.
     * Startup still validates current supplies and bot state before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized gem-cutting state.
     */
    constructor(bot: Bot, data: CutGemData) : this(bot, Gem.valueOf(data.recipe), data.duration, data.zones) {
        failures = data.failures
        bankFailures = data.bankFailures
    }

    /**
     * Checks permanent level eligibility and minimum supplies across inventory and bank for factory selection.
     * Temporary combat, lock, action, and current-level restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials, tools)

    /** Returns one chisel and as many banked gems as fit, or an empty batch when either supply is unavailable. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> {
        if (failures >= MAX_FAILURES || bankFailures >= MAX_FAILURES ||
            bot.skill(SKILL_CRAFTING).staticLevel < requiredLevel || bot.health < 1 || bot.isLocked ||
            bot.combat.inCombat() || bot.actions.size(ActionType.STRONG) > 0) {
            stop()
            return emptyList()
        }
        if (!bot.ownsProductionSupplies(materials, tools)) {
            val missing = materials.filter {
                bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id) < it.amount
            }.map { it.id } + tools.filter { !bot.ownsProductionSupplies(emptyList(), setOf(it)) }
            missing.forEach { bot.preferences.raiseWantedItemTarget(it, if (it in tools) 3 else 1_000) }
            stop()
            return emptyList()
        }
        // InventoryBotScript validates these minimum supplies; the bank hook calculates the actual batch.
        forceBanking = true
        return tools.map { Item(it) } + materials
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

    /** Whether inventory currently holds a chisel and at least one configured uncut gem. */
    private fun hasMaterials() = bot.inventory.containsAll(materials) && tools.all { it in bot.inventory }

    override suspend fun onExecuteInZone(): Boolean {
        if (bot.health < 1 || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.size(ActionType.STRONG) > 0) return true
        if (bot.skill(SKILL_CRAFTING).level < requiredLevel) {
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

    /** Uses the chisel on an uncut gem, waits for the single-recipe dialogue, and requests the carried batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(Gem.CHISEL).onItem(gem.uncut)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(CutGemActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): CutGemData = CutGemData().also {
        it.recipe = gem.name
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.failures = failures
        it.bankFailures = bankFailures
    }
}
