package api.bot.script

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotTradeIntentions
import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.bot.social.SocialBotScript
import api.predef.ext.*
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.speech.BotConversationPool.ConversationSpeech
import io.luna.game.action.ActionType
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A base script for inventory-driven activities that operate inside one or more zones.
 *
 * This script handles the common loop for activities that need to withdraw a fixed set of items from the bank, perform
 * an action until banking is needed again, and avoid re-executing while the bot is already busy with a weak action.
 *
 * Production subclasses reuse [productionWithdraw], [requestProductionBank], and [attemptProduction], supplying
 * recipe requirements and the normal player interaction. Failure budgets and their legacy JSON fields live here.
 * [verifiedProductionWithdrawals] opts production scripts into bounded unnoted withdrawal verification; existing
 * inventory scripts retain their default withdrawal behavior. Custom stock targets, output space, prerequisites,
 * and completion remain in the activity script. Missing minimum startup supplies use the shared
 * quantity-aware shop acquisition before an optional activity-specific prerequisite is queued.
 *
 * Subclasses provide the required withdrawal items, the activity-specific execution step, and the condition that decides
 * when another banking trip is needed.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may operate in.
 * @author lare96
 */
abstract class InventoryBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone>
) : ZonedBotScript(bot, duration, zones), SocialBotScript {

    /**
     * Only native batch activities with natural waiting periods opt into ambient conversation.
     */
    protected open val ambientProductionTopic: ConversationSpeech? = null

    override fun ambientConversationTopic(): ConversationSpeech? = ambientProductionTopic

    override fun canSocialize(): Boolean = canTalkWhileWorking()

    override fun canTalkWhileWorking(): Boolean = canContinueConversation() &&
        bot.actions.first(InventoryAction::class.java) != null

    override fun canContinueConversation(): Boolean = ambientProductionTopic != null && !forceBanking &&
        duration > Duration.ZERO && activeZone?.area?.contains(bot.position) == true &&
        bot.walking.isEmpty && !bot.navigator.isActive && !bot.combat.inCombat() && bot.combat.target == null &&
        !bot.overlays.hasWindow()

    /**
     * Optional experience-bearing skill checked before startup and each production cycle.
     */
    protected open val trainingSkill: Int? = null

    /**
     * Stops resumed or active production when its skill no longer fits the account build.
     */
    private fun trainingAllowed(): Boolean {
        if (trainingSkill?.let { BotCombatActionHandler.mayGainExperience(bot, it) } != false) return true
        stop()
        return false
    }

    companion object {
        /**
         * Common production retry state stored with the existing duration and zones.
         * Flat failures/bankFailures fields remain compatible with previously saved recipe data classes.
         * Recipe subclasses add their own identifiers and call super from load/save.
         *
         * @author lare96
         */
        open class InventoryScriptData : ZonedBotScriptData() {
            /** Consecutive production interactions without verified production progress. */
            var failures = 0
            /** Unresolved banking requests since the last verified withdrawal. */
            var bankFailures = 0
            /**
             * The actual tried site whose third failure has not yet completed its zone transition.
             * Absence preserves the legacy meaning of an exhausted failure counter.
             */
            var failedZone: SubZone? = null
            /**
             * Sites already exhausted by this session, retained when its original zones are saved.
             */
            var failedZones: Set<SubZone> = emptySet()
            override fun load(data: JsonObject) {
                super.load(data)
                failures = data.get("failures")?.asInt ?: 0
                bankFailures = data.get("bankFailures")?.asInt ?: 0
                failedZone = data.get("failed_zone")?.takeUnless { it.isJsonNull }?.asString?.let(SubZone::valueOf)
                failedZones = data.getAsJsonArray("failed_zones")?.map { SubZone.valueOf(it.asString) }?.toSet()
                    ?: emptySet()
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("failures", failures)
                data.addProperty("bankFailures", bankFailures)
                if (failedZone == null) data.remove("failed_zone") else data.addProperty("failed_zone", failedZone!!.name)
                if (failedZones.isEmpty()) data.remove("failed_zones") else
                    data.add("failed_zones", JsonArray().also { array -> failedZones.forEach { array.add(it.name) } })
            }
        }
    }

    /** Consecutive failed attempts allowed; banking and interactions have independent budgets. */
    protected open val maxFailures = 3
    /** Enables the common fifteen-second, unnoted, inventory-verified production withdrawal. */
    protected open val verifiedProductionWithdrawals = false
    /**
     * Runs an owned-stock disposal batch without creating replacement demand or purchasing missing inputs.
     */
    protected open val ownedSuppliesOnly = false
    private var productionTools = emptySet<Int>()
    /** Consecutive interactions without verified production progress. */
    protected var failures = 0
        private set
    /** Unresolved production banking requests; reset only after a successful withdrawal. */
    protected var bankFailures = 0
        private set
    /**
     * Exact failed-site identity survives cancellation between the final attempt and zone departure.
     */
    private var failedProductionZone: SubZone? = null
    /**
     * Prevents a later snapshot of original destinations from reintroducing previously exhausted sites.
     */
    private val failedProductionZones = linkedSetOf<SubZone>()

    /**
     * Starts a fresh interaction budget after an activity abandons a work site. Banking failures are retained.
     */
    protected fun resetProductionFailures() {
        failures = 0
        failedProductionZone = null
    }

    /** Restores retry state; duration and zones are supplied to the script constructor. */
    protected fun restoreInventoryState(data: InventoryScriptData) {
        failures = data.failures
        bankFailures = data.bankFailures
        failedProductionZone = data.failedZone
        failedProductionZones.addAll(data.failedZones)
        data.failedZone?.let { failedProductionZones.add(it) }
    }

    /** Writes common configuration and counters into a recipe snapshot using the legacy field names. */
    protected fun saveInventoryState(data: InventoryScriptData) {
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.failures = failures
        data.bankFailures = bankFailures
        data.failedZone = failedProductionZone
        data.failedZones = failedProductionZones.toSet()
    }

    /**
     * Banking and legacy interaction exhaustion always end the session. A recorded newly failed site
     * may instead complete its exact pending zone transition without retrying an unrelated location.
     */
    protected fun productionRetriesExhausted(): Boolean =
        bankFailures >= maxFailures || failures >= maxFailures && failedProductionZone == null

    /** The selected recipe's startup requirements; rebuilt rather than persisted on each resume. */
    private class StartupSupplies(
        val items: List<Item>, val allowAcquisition: Boolean, val prepareMissing: () -> Boolean
    )
    private var startupSupplies: StartupSupplies? = null

    /**
     * Plans minimum inputs and tools without ending a session solely because supplies are missing.
     * Suspending acquisition belongs to onInit. Unbuyable nest/lamp inputs retain requestMissing=false;
     * levels, safety and exhausted retry budgets still reject before any acquisition.
     */
    protected fun productionWithdraw(
        materials: List<Item>, tools: Set<Int> = emptySet(), levelEligible: Boolean = true,
        requestMissing: Boolean = true, prepareMissing: () -> Boolean = { false }
    ): List<Item> {
        if (productionRetriesExhausted() || !isActionSafe() || !levelEligible) {
            stop()
            return emptyList()
        }
        val items = tools.map { Item(it) } + materials
        productionTools = tools
        if (!ownedSuppliesOnly) BotTradeIntentions.recordSupplies(bot, materials, tools, stockTarget = 1_000)
        if (!bot.ownsProductionSupplies(materials, tools)) {
            if (!requestMissing) { stop(); return emptyList() }
            if (!ownedSuppliesOnly) items.filter { bot.bank.computeAmountForId(it.id).toLong() +
                    bot.inventory.computeAmountForId(it.id) < it.amount }.forEach {
                bot.preferences.raiseWantedItemTarget(it.id, if (it.id in tools) 3 else maxOf(1_000, it.amount))
            }
        }
        startupSupplies = StartupSupplies(items, requestMissing && !ownedSuppliesOnly, prepareMissing)
        forceBanking = true
        return items
    }

    /** Acquires only missing minimum quantities, within one shared deadline and normal non-combat safety. */
    private suspend fun obtainStartupSupplies(items: List<Item>): Boolean = withTimeoutOrNull(120.seconds) {
        for (item in items) {
            if (!awaitActionSafety()) return@withTimeoutOrNull false
            if (bot.bank.computeAmountForId(item.id).toLong() + bot.inventory.computeAmountForId(item.id) >= item.amount) continue
            if (!handler.shop.obtainWantedItem(item.id, item.amount)) return@withTimeoutOrNull false
        }
        items.all { bot.bank.computeAmountForId(it.id).toLong() +
                bot.inventory.computeAmountForId(it.id) >= it.amount }
    } == true

    /**
     * Allows banking only when safe and necessary, charging its independent retry budget.
     * canContinue includes supplies and recipe-specific output space. Initial requests retain the
     * existing lifecycle behavior and do not charge this budget.
     */
    protected fun requestProductionBank(canContinue: Boolean): Boolean {
        if (!isActionSafe() || (!forceBanking && canContinue)) return false
        if (bankFailures >= maxFailures) { stop(); return false }
        bankFailures++
        return true
    }

    /**
     * Checks safety, current recipe level, and carried supplies/output space before a production attempt.
     * Temporary unsafe states defer work; level loss ends the session; missing capacity or inputs force banking.
     */
    protected fun productionReady(canContinue: Boolean, levelEligible: Boolean = true): Boolean {
        if (!isActionSafe()) return false
        if (!levelEligible) { stop(); return false }
        if (!canContinue) { forceBanking = true; return false }
        return true
    }

    /**
     * Runs a player interaction with a fifteen-second start bound and ten-second progress wait.
     * Closing interfaces, progress detection, retries, and optional dexterity pacing are shared.
     * Unavailable targets fail without closing an interface or starting an interaction.
     * Optional output tracking also accepts newly produced items, including spells with beta-mode cost bypasses.
     * State-only activities can supply [progressCondition], such as clearing a dead farming patch with a reusable tool.
     * [neutralCompletion] can settle a confirmed contested interaction without changing its production failure budget.
     * The callback starts normal gameplay; it must not directly create products or award experience.
     */
    protected suspend fun attemptProduction(
        inputId: Int, dexterityDelay: Boolean = true, available: Boolean = true, outputId: Int? = null,
        progressCondition: () -> Boolean = { false },
        neutralCompletion: () -> Boolean = { false },
        start: suspend (amount: Int) -> Boolean
    ) {
        if (!isActionSafe()) return
        val attemptedZone = activeZone
        val before = bot.inventory.computeAmountForId(inputId)
        val outputBefore = outputId?.let { bot.inventory.computeAmountForId(it) } ?: 0
        val started = available && withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && isActionSafe() && start(before)
        } == true
        var progressed = false
        var neutral = false
        if (started) waitFor(10.seconds) {
            progressed = bot.inventory.computeAmountForId(inputId) < before ||
                (outputId != null && bot.inventory.computeAmountForId(outputId) > outputBefore) || progressCondition()
            neutral = !progressed && neutralCompletion()
            progressed || neutral
        }
        if (progressed) failures = 0 else if (!neutral && isActionSafe() && ++failures >= maxFailures) {
            val zone = attemptedZone
            if (zones.size <= 1 || zone == null) stop() else {
                failedProductionZone = zone
                failedProductionZones.add(zone)
            }
        }
        if (dexterityDelay) bot.naturalDexterityDelay()
    }

    /**
     * The items this script needs to withdraw from the bank.
     *
     * This is initialized from [withdraw]. Scripts with variable batch sizes can refresh it through [bankWithdraw]
     * after each deposit.
     */
    protected var withdraw: List<Item> = emptyList()
        private set

    /**
     * Optional preparation before supply planning, including returning a resumed hazardous trip to safety.
     * The shared initialization and quantity checks still run after this hook succeeds.
     */
    protected open suspend fun beforeInventoryInit(): Boolean = true

    override fun selectWorkZone(): SubZone? {
        // Zoned travel recovery can rebuild original candidates; exhausted work sites stay retired.
        zones.removeAll(failedProductionZones)
        return super.selectWorkZone()
    }

    final override suspend fun onInit(resumed: Boolean): Boolean {
        // A paused attempt belongs to its recorded site, not whichever candidate is selected on resume.
        zones.removeAll(failedProductionZones)
        if (failedProductionZone != null) resetProductionFailures()
        if (zones.isEmpty()) { stop(); return false }
        if (!trainingAllowed() || productionRetriesExhausted()) { stop(); return false }
        if (!beforeInventoryInit()) return false
        startupSupplies = null
        withdraw = withdraw()
        if (isTerminated() || isPaused()) return false
        val startup = startupSupplies
        val supplied = if (ownedSuppliesOnly || startup?.allowAcquisition == false) {
            (startup?.items ?: withdraw).all { bot.bank.computeAmountForId(it.id).toLong() +
                    bot.inventory.computeAmountForId(it.id) >= it.amount }
        } else obtainStartupSupplies(startup?.items ?: withdraw)
        if (!supplied) {
            bot.log("Could not obtain the minimum production supplies.")
            if (startup != null && isActionSafe() && startup.prepareMissing()) return false
            stop()
            return false
        }
        if (!awaitActionSafety() || !trainingAllowed()) return false
        // Acquisition may suspend through travel/banking. Re-evaluate recipe levels, costs and ownership afterwards.
        if (startup != null) withdraw = withdraw()
        if (isTerminated() || isPaused()) return false
        if (!withdraw.all { bot.itemTracker.count(it.id) >= it.amount }) {
            bot.log("Bot does not have required withdraw items. Ending script.")
            return false
        }
        return true
    }

    final override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (initial) {
            return true
        }
        if (bot.actions.size(ActionType.WEAK) > 0) {
            return false
        }
        return onInventoryBankRequested()
    }

    final override suspend fun executeInZone(): Boolean {
        if (isPaused() || isTerminated()) return true
        if (!trainingAllowed()) return false
        if (!isActionSafe() || bot.actions.size(ActionType.WEAK) > 0) {
            // Bot is busy, no need to re-execute.
            return true
        }
        if (failedProductionZone != null) {
            if (activeZone == failedProductionZone) {
                failures = 0
                return false
            }
            resetProductionFailures()
        }
        if (productionRetriesExhausted()) {
            stop()
            return false
        }
        val accepted = onExecuteInZone()
        if (isActionSafe() && !isPaused() && !isTerminated() &&
            bot.actions.size(ActionType.WEAK) == 0 && failedProductionZone == activeZone && failedProductionZone != null) {
            // Reject only the site that was actually tried; stock and banking budgets remain intact.
            failures = 0
            return false
        }
        return accepted
    }

    final override suspend fun onBankOpen(initial: Boolean) {
        withdraw = bankWithdraw()
        if (isTerminated()) return
        if (withdraw.isEmpty()) {
            // Some harvesting visits need only empty inventory space after depositing their produce.
            bankFailures = 0
            if (verifiedProductionWithdrawals) forceBanking = false
            return
        }
        if (!bot.bank.containsAll(withdraw)) {
            bot.log("We no longer have the required withdraw items. Stopping script.")
            stop()
            return
        }
        if (!awaitActionSafety(ActionSafety.NON_COMBAT)) return
        val withdrawn = withdrawBankItems(withdraw)
        if (!awaitActionSafety(ActionSafety.NON_COMBAT)) return
        if (!withdrawn) {
            bot.log("Could not withdraw required inventory items. Stopping script.")
            stop()
        } else {
            bankFailures = 0
            if (verifiedProductionWithdrawals) forceBanking = false
        }
    }

    /**
     * Returns the items this script should withdraw from the bank.
     *
     * @return The items needed for one inventory-processing cycle.
     */
    abstract fun withdraw(): List<Item>

    /**
     * Protects an active or paused session's planned inputs from emergency bank sales. Owned-stock relief sessions
     * intentionally consume their materials and reserve only their reusable tools.
     */
    internal fun bankSpaceProtectedItems(): Set<Int> =
        if (ownedSuppliesOnly) productionTools else withdraw.mapTo(HashSet()) { it.id }

    /** Supplies for this banking cycle; fixed-recipe scripts retain the initial list by default. */
    // TODO rename to setWithdraw or something else
    protected open fun bankWithdraw(): List<Item> = withdraw

    /**
     * Withdraws the current batch. Subclasses may select a banking mode or bound their own withdrawal attempt.
     *
     * Returning false ends the script instead of continuing with an incomplete inventory.
     */
    protected open suspend fun withdrawBankItems(items: List<Item>): Boolean {
        if (!verifiedProductionWithdrawals) return handler.banking.withdrawAll(items)
        return withTimeoutOrNull(15_000) {
            handler.banking.clickBankingMode(false)
            isActionSafe(ActionSafety.NON_COMBAT) && handler.banking.withdrawAll(items) && bot.inventory.containsAll(items)
        } == true
    }

    /**
     * Executes one activity-specific cycle inside the active zone.
     *
     * Subclasses should perform their main interaction here, such as using an item, interacting with an object, or
     * clicking a make-item dialogue.
     *
     * @return `true` if this zone should remain active, or `false` if the script should abandon it.
     */
    open suspend fun onExecuteInZone(): Boolean {
        return true
    }

    /**
     * Returns whether this script needs another banking trip after the initial setup.
     *
     * Subclasses usually return `true` when the required material is missing, the inventory is full, or the current
     * production cycle has finished.
     *
     * @return `true` if the script should bank.
     */
    open suspend fun onInventoryBankRequested(): Boolean {
        return true
    }
}
