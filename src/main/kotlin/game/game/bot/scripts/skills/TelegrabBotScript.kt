package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.PkRiskBudget
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.combat.death.PlayerDeathItems
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.coordinator.skill.BotProductionPlanner
import engine.controllers.Controllers.inWilderness
import engine.spawn.PersistentGroundItem
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.LEVEL
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.REQUIREMENTS
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.SPELL_ID
import game.obj.resource.ZamorakWineSources
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collects a selected world-spawned item through native Telekinetic Grab packets.
 * Wine sessions support ordinary Magic training, Herblore ingredient gathering and profitable collection.
 * Rune plans respect staffs and combination runes, reserve output space and carry one inventory's casts.
 * Native spell actions own costs, XP and collection; only a received item counts as progress.
 *
 * The shared inventory lifecycle owns banking, failure budgets and persistence. Empty or contested sources have
 * bounded waits. Confirmed consecutive races have a separate personality-based budget; bystanders never reject a source.
 * Two other consecutive failures reject a source. Wilderness trips reassess native death loss and flee on danger.
 * Player drops are excluded. Other item types can reuse this script with their own source zones.
 * Herblore-owned wine trips collect only enough for owned ranging-potion inputs, then queue finished mixing
 * once with the remaining budget. Partial wine hauls can be mixed when rune stock is exhausted.
 *
 * @property itemId The unnoted world-spawned item to collect.
 * @property profit Whether guide value must exceed the actual planned rune costs.
 * @author lare96
 */
class TelegrabBotScript(bot: Bot, val itemId: Int, val profit: Boolean, duration: Duration, zones: MutableList<SubZone>,
                       private val herbloreContinuation: Boolean = false, private val ownedRunesOnly: Boolean = false) :
    InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_MAGIC
    override val verifiedProductionWithdrawals = true
    override val ownedSuppliesOnly get() = ownedRunesOnly
    override val actionSafety get() = if (itemId == WINE) ActionSafety.COMBAT else ActionSafety.NON_COMBAT
    override val allowsCombatResponse get() = !bot.inWilderness()

    companion object {
        /**
         * Total owned collection goal; rune shortages use the shared wanted-item targets.
         */
        const val STOCK_TARGET = 500
        const val WINE = 245

        /**
         * All configured wine sources share one registry. Confident collectors may accept Wilderness risk;
         * profit-seeking PK profiles retain their existing candidate access. Departure still checks actual death loss.
         */
        fun wineZones(bot: Bot, profit: Boolean = false): MutableList<SubZone> = ZamorakWineSources.zones.filter {
            ZamorakWineSources.canUse(bot, it) && (!ZamorakWineSources.isWilderness(it) || bot.personality.isConfident ||
                profit && !bot.preferences.isSkiller && bot.preferences.pk != null)
        }.toMutableList()

        /**
         * Smarter or kinder collectors yield sooner, with a neutral collector allowing seven confirmed losses.
         */
        internal fun competitionLimit(intelligence: Double, kindness: Double): Int {
            fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
            val patience = 1.0 - (normalized(intelligence) + normalized(kindness)) / 2.0
            return 3 + (7 * patience).roundToInt()
        }

        /**
         * Collection configuration alongside the shared inventory session and retry state.
         *
         * @author lare96
         */
        class TelegrabData : InventoryScriptData() {
            /**
             * Resource selected by the owning gathering or profit factory.
             */
            var itemId = WINE
            /**
             * Retains the profit-margin requirement after login or interruption.
             */
            var profit = false
            var herbloreContinuation = false
            var ownedRunesOnly = false
            var continued = false
            var competitionLimit = 0
            var competitionLosses: Map<SubZone, Int> = emptyMap()

            override fun load(data: JsonObject) {
                super.load(data)
                itemId = data.get("itemId")?.asInt ?: WINE
                profit = data.get("profit")?.asBoolean ?: false
                herbloreContinuation = data.get("herbloreContinuation")?.asBoolean ?: false
                ownedRunesOnly = data.get("owned_runes_only")?.asBoolean ?: false
                continued = data.get("continued")?.asBoolean ?: false
                competitionLimit = data.get("competition_limit")?.asInt ?: 0
                competitionLosses = data.getAsJsonObject("competition_losses")?.entrySet()?.associate {
                    SubZone.valueOf(it.key) to it.value.asInt.coerceAtLeast(0)
                } ?: emptyMap()
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("itemId", itemId)
                data.addProperty("profit", profit)
                data.addProperty("herbloreContinuation", herbloreContinuation)
                data.addProperty("owned_runes_only", ownedRunesOnly)
                data.addProperty("continued", continued)
                data.addProperty("competition_limit", competitionLimit)
                data.add("competition_losses", JsonObject().also { losses ->
                    competitionLosses.forEach { (zone, count) -> losses.addProperty(zone.name, count) }
                })
            }
        }
    }

    /**
     * Restores the resource and purpose without retaining live ground-item references.
     */
    constructor(bot: Bot, data: TelegrabData) :
        this(bot, data.itemId, data.profit, data.duration, data.zones, data.herbloreContinuation, data.ownedRunesOnly) {
        restoreInventoryState(data)
        continued = data.continued
        if (data.competitionLimit in 3..10) competitionLimit = data.competitionLimit
        competitionLosses.putAll(data.competitionLosses)
    }

    private var continued = false
    private var competitionLimit = competitionLimit(bot.personality.intelligence, bot.personality.kindness)
    private val competitionLosses = mutableMapOf<SubZone, Int>()
    private var wildernessGuard = false
    private var previousCombatDisabled = false

    /**
     * Ingredient trips stop at the matching owned unfinished-potion stock; ordinary trips retain their reserve.
     */
    internal fun collectionGoal(): Int = if (herbloreContinuation)
        minOf(STOCK_TARGET.toLong(), HerbloreProduction.owned(bot, FinishedPotion.RANGING_POTION.unf)).toInt()
    else STOCK_TARGET

    private fun outputStock(): Int = if (herbloreContinuation)
        HerbloreProduction.owned(bot, itemId).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    else bot.itemTracker.count(itemId)

    private fun purposeAllowed(): Boolean = !herbloreContinuation || itemId == WINE &&
        bot.herblore.staticLevel >= FinishedPotion.RANGING_POTION.level && collectionGoal() > 0

    /**
     * Current guide margin after staff and combination-rune substitutions in the supplied plan.
     */
    fun margin(costs: List<Item> = bot.productionRuneCosts(REQUIREMENTS)): Long =
        world.economy.getPrice(itemId) - costs.sumOf { world.economy.getTotalPrice(it) }

    /**
     * Checks spellbook, permanent Magic, build caps and the output target. Missing runes remain eligible so
     * the shared inventory lifecycle can acquire them through normal wanted-item shopping.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= LEVEL &&
        BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) && purposeAllowed() && outputStock() < collectionGoal() &&
        (!ownedRunesOnly || bot.ownsProductionSupplies(bot.productionRuneCosts(REQUIREMENTS))) &&
        (!profit || margin() > 0)

    /**
     * Withdraws a small cast budget, reserving room for every output and limiting it to banked rune stock.
     */
    fun bankBatch(): List<Item> {
        if (!purposeAllowed()) return emptyList()
        val costs = bot.productionRuneCosts(REQUIREMENTS, bankedOnly = true)
        val casts = minOf(bot.inventory.capacity() - costs.size, collectionGoal() - outputStock(),
            costs.minOfOrNull { bot.bank.computeAmountForId(it.id) / it.amount } ?: 0)
        if (casts < 1 || (profit && margin(costs) <= 0)) return emptyList()
        return costs.map { Item(it.id, it.amount * casts) }
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        resetProductionFailures()
    }

    override suspend fun equipment(): BotGearLocator? = null
    override suspend fun beforeInventoryInit(): Boolean {
        zones.removeAll { (competitionLosses[it] ?: 0) >= competitionLimit }
        if (zones.isEmpty()) { stop(); return false }
        if (bot.inWilderness() && !handler.combat.fleeWilderness()) return false
        restoreWildernessGuard()
        if (!purposeAllowed()) { stop(); return false }
        if (outputStock() >= collectionGoal() || !obtainCastBatch()) {
            queueHerbloreContinuation()
            stop()
            return false
        }
        return true
    }

    /**
     * Requests one inventory's actual staff-aware rune costs through native shopping before startup or banking.
     * Partial affordable purchases remain usable when they supply at least one cast. Each refill shares a bounded
     * deadline with the remaining work budget; acquisition cannot renew a session or create replacement runes.
     */
    private suspend fun obtainCastBatch(): Boolean {
        if (productionRetriesExhausted() || !ActionSafety.NON_COMBAT.allows(bot) || !purposeAllowed() ||
            bot.spellbook != Spellbook.REGULAR || bot.magic.level < LEVEL) return false
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return false
        val costs = bot.productionRuneCosts(REQUIREMENTS)
        if (profit && margin(costs) <= 0) return false
        val casts = minOf(bot.inventory.capacity() - costs.size, collectionGoal() - outputStock())
        if (casts < 1 || costs.isEmpty()) return false
        withTimeoutOrNull(minOf(120_000, remaining.inWholeMilliseconds).coerceAtLeast(1)) {
            for (cost in costs) {
                if (!ActionSafety.NON_COMBAT.allows(bot) || remainingDuration() <= Duration.ZERO) break
                val target = cost.amount * casts
                val owned = bot.inventory.computeAmountForId(cost.id).toLong() + bot.bank.computeAmountForId(cost.id)
                if (owned < target && !ownedRunesOnly) handler.shop.obtainWantedItem(cost.id, target)
                if (bot.inventory.computeAmountForId(cost.id).toLong() + bot.bank.computeAmountForId(cost.id) < cost.amount) break
            }
        }
        return ActionSafety.NON_COMBAT.allows(bot) && bot.ownsProductionSupplies(bot.productionRuneCosts(REQUIREMENTS))
    }

    override fun withdraw(): List<Item> = productionWithdraw(bot.productionRuneCosts(REQUIREMENTS), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= LEVEL && purposeAllowed() && outputStock() < collectionGoal())

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) {
            queueHerbloreContinuation()
            stop()
        }
    }

    /**
     * Uses a real partial or completed haul once; interruptions and failed collection cannot create ingredients.
     */
    private fun queueHerbloreContinuation() {
        if (!herbloreContinuation || continued || !purposeAllowed() || isPaused() || isTerminated() ||
            productionRetriesExhausted() || !isActionSafe() || outputStock() < 1) return
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return
        continued = true
        BotProductionPlanner.select(bot, SKILL_HERBLORE, bot.herblore.staticLevel,
            BotCombatActionHandler.wantsToTrain(bot, SKILL_HERBLORE), remaining, "potion:RANGING_POTION")
            ?.let { bot.scriptStack.softPushHead(it) }
    }

    /**
     * Rechecks carried spell costs and output space without sending repeated missing-supply messages.
     */
    private fun canContinue(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.inventory.hasSpaceFor(Item(itemId)) &&
        Magic.spellCost(bot, LEVEL, REQUIREMENTS) is SpellCost.Castable && (!profit || margin() > 0)

    override suspend fun onInventoryBankRequested(): Boolean {
        if (!requestProductionBank(canContinue())) return false
        if (bot.inWilderness() && !handler.combat.fleeWilderness()) return false
        restoreWildernessGuard()
        if (obtainCastBatch()) return true
        queueHerbloreContinuation()
        stop()
        return false
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        if (!ActionSafety.NON_COMBAT.allows(bot)) return false
        if (ZamorakWineSources.isWilderness(zone)) {
            if (!withinWineRiskBudget() || bot.healthPercent < 50) return false
            installWildernessGuard()
        }
        val arrived = super.travelToWorkZone(zone, bank)
        if (bot.inWilderness() && !wildernessSafe()) {
            handler.combat.fleeWilderness()
            return false
        }
        if (!bot.inWilderness()) restoreWildernessGuard()
        return arrived
    }

    /**
     * Confident non-PK collectors risk at most five percent of owned wealth. Other activities retain their
     * original eligibility and shares; all loss, protection, skull and price calculations remain native.
     */
    internal fun withinWineRiskBudget(): Boolean {
        if (itemId != WINE) return handler.combat.pkRiskBudget().withinBudget
        val share = if (!bot.preferences.isSkiller && bot.preferences.pk != null) null
            else if (bot.personality.isConfident) 500 else 0
        return PkRiskBudget.assessor(bot, PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()), share)(
            bot.inventory.filterNotNull(), bot.equipment.filterNotNull(), false).withinBudget
    }

    private fun installWildernessGuard() {
        if (wildernessGuard) return
        previousCombatDisabled = bot.combat.isDisabled
        bot.combat.isDisabled = true
        bot.combat.target = null
        wildernessGuard = true
    }

    private fun restoreWildernessGuard() {
        if (!wildernessGuard) return
        bot.combat.isDisabled = previousCombatDisabled
        wildernessGuard = false
    }

    private fun wildernessSafe(): Boolean = !bot.combat.inCombat() && bot.healthPercent >= 50 && withinWineRiskBudget()

    override suspend fun beforeZoneWork(): Boolean {
        if (!bot.inWilderness()) {
            restoreWildernessGuard()
            return false
        }
        installWildernessGuard()
        if (wildernessSafe()) return false
        handler.combat.fleeWilderness()
        stop()
        return true
    }

    override fun selectWorkZone(): SubZone? {
        zones.removeAll { (competitionLosses[it] ?: 0) >= competitionLimit }
        return super.selectWorkZone()
    }

    /**
     * Finds only active visible public spawns in this work zone, excluding player loot.
     */
    private fun findTarget(): PersistentGroundItem? {
        val zone = activeZone ?: return null
        return world.locator.findItems(zone.area.centerPosition, zone.area.tileRadius, true) {
            it is PersistentGroundItem && it.id == itemId && it.state == EntityState.ACTIVE &&
                it.position in zone.area && it.isVisibleTo(bot)
        }.firstOrNull() as? PersistentGroundItem
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!purposeAllowed()) { stop(); return true }
        if (outputStock() >= collectionGoal()) {
            if (herbloreContinuation) forceBanking = true else stop()
            return true
        }
        if (bot.inWilderness() && !wildernessSafe()) {
            handler.combat.fleeWilderness()
            return false
        }
        if (!ActionSafety.NON_COMBAT.allows(bot)) return true
        val zone = activeZone ?: return false
        if ((competitionLosses[zone] ?: 0) >= competitionLimit) return false
        if (!productionReady(canContinue(), levelEligible = bot.magic.level >= LEVEL &&
                bot.spellbook == Spellbook.REGULAR)) return true
        var target = findTarget()
        if (target == null) waitFor(45.seconds) {
            target = findTarget()
            target != null || !ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() && !wildernessSafe()
        }
        if (!ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() && !wildernessSafe()) return true
        val selected = target
        val before = bot.inventory.computeAmountForId(itemId)
        var lostRace = false
        fun confirmRace(): Boolean {
            if (lostRace) return true
            val collector = selected?.collectedBy ?: return false
            if (collector == bot.username) return false
            lostRace = true
            competitionLosses[zone] = (competitionLosses[zone] ?: 0) + 1
            return true
        }
        attemptProduction(itemId, available = selected != null, progressCondition = {
            (bot.inventory.computeAmountForId(itemId) > before).also { gained ->
                if (gained) competitionLosses.remove(zone)
            }
        }, neutralCompletion = ::confirmRace) {
            val item = selected ?: return@attemptProduction false
            if (confirmRace()) return@attemptProduction true
            if (!bot.position.isViewable(item.position) || !world.collisionManager.raycast(bot.position, item.position)) {
                if (!handler.webWalkTo(item.position, radius = 1, bank = BankAccess.NONE)) return@attemptProduction confirmRace()
            }
            if (confirmRace()) return@attemptProduction true
            if (!ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness() && !wildernessSafe()) return@attemptProduction false
            output.useSpellOnGroundItem(SPELL_ID, item) || confirmRace()
        }
        return failures < 2 && (competitionLosses[zone] ?: 0) < competitionLimit
    }

    override suspend fun finish() {
        bot.actions.first(TelekineticGrabAction::class.java)?.interrupt()
        if (isPaused()) {
            restoreWildernessGuard()
            return
        }
        try {
            if (bot.inWilderness() && bot.state == EntityState.ACTIVE && bot.isAlive) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(30.seconds) { handler.combat.fleeWilderness() }
                }
            }
        } finally {
            restoreWildernessGuard()
        }
    }

    override fun snapshot(): TelegrabData = TelegrabData().also {
        it.itemId = itemId
        it.profit = profit
        it.herbloreContinuation = herbloreContinuation
        it.ownedRunesOnly = ownedRunesOnly
        it.continued = continued
        it.competitionLimit = competitionLimit
        it.competitionLosses = competitionLosses.toMap()
        saveInventoryState(it)
    }
}
