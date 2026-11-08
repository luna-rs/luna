package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.controllers.Controllers.inWilderness
import engine.spawn.PersistentGroundItem
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.LEVEL
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.REQUIREMENTS
import game.skill.magic.telekineticGrab.TelekineticGrabAction.Companion.SPELL_ID
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collects a selected world-spawned item through native Telekinetic Grab packets.
 * Wine sessions are selected only for Herblore ingredient gathering or profit, never ordinary Magic training.
 * Rune plans respect staffs and combination runes, reserve output space and carry one inventory's casts.
 * Native spell actions own costs, XP and collection; only a received item counts as progress.
 *
 * The shared inventory lifecycle owns banking, failure budgets and persistence. Empty or contested sources have
 * bounded waits. Occupied sources and two consecutive attempts without receiving an item reject the source
 * so another candidate can be tried. Wilderness entry and ongoing trips respect the existing death-loss budget and escape API.
 * Player drops are excluded. Other item types can reuse this script with their own source zones.
 * Herblore-owned wine trips collect only enough for owned ranging-potion inputs, then queue finished mixing
 * once with the remaining budget. Partial wine hauls can be mixed when rune stock is exhausted.
 *
 * @property itemId The unnoted world-spawned item to collect.
 * @property profit Whether guide value must exceed the actual planned rune costs.
 * @author lare96
 */
class TelegrabBotScript(bot: Bot, val itemId: Int, val profit: Boolean, duration: Duration, zones: MutableList<SubZone>,
                       private val herbloreContinuation: Boolean = false) :
    InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_MAGIC
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Total owned collection goal; rune shortages use the shared wanted-item targets.
         */
        const val STOCK_TARGET = 500
        const val WINE = 245

        /**
         * Existing Wilderness sources, subject to confidence and death-loss checks.
         */
        private val wildernessZones = setOf(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE,
            SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE)

        /**
         * Profit trips consider all three sources. Ingredient gathering retains the confidence preference for Wilderness.
         * Existing travel and death-loss checks still govern whether a Wilderness trip can proceed.
         */
        fun wineZones(bot: Bot, profit: Boolean = false): MutableList<SubZone> = mutableListOf(SubZone.NORTH_FALADOR_CHAOS_TEMPLE).also {
            if (profit || bot.personality.confidence >= 0.5) it.addAll(wildernessZones)
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
            var continued = false

            override fun load(data: JsonObject) {
                super.load(data)
                itemId = data.get("itemId")?.asInt ?: WINE
                profit = data.get("profit")?.asBoolean ?: false
                herbloreContinuation = data.get("herbloreContinuation")?.asBoolean ?: false
                continued = data.get("continued")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("itemId", itemId)
                data.addProperty("profit", profit)
                data.addProperty("herbloreContinuation", herbloreContinuation)
                data.addProperty("continued", continued)
            }
        }
    }

    /**
     * Restores the resource and purpose without retaining live ground-item references.
     */
    constructor(bot: Bot, data: TelegrabData) :
        this(bot, data.itemId, data.profit, data.duration, data.zones, data.herbloreContinuation) {
        restoreInventoryState(data)
        continued = data.continued
    }

    private var continued = false

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
     * Checks spellbook, permanent Magic and build caps, minimum stock, and the total owned output target.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= LEVEL &&
        BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) && purposeAllowed() && outputStock() < collectionGoal() &&
        bot.ownsProductionSupplies(bot.productionRuneCosts(REQUIREMENTS)) && (!profit || margin() > 0)

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
        if (bot.inWilderness() && !handler.combat.fleeWilderness()) return false
        if (!herbloreContinuation) return true
        if (!purposeAllowed()) { stop(); return false }
        if (outputStock() >= collectionGoal() || outputStock() > 0 &&
            !bot.ownsProductionSupplies(bot.productionRuneCosts(REQUIREMENTS))) {
            queueHerbloreContinuation()
            stop()
            return false
        }
        return true
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
        bot.scriptStack.softPushHead(MakePotionBotScript(bot, FinishedPotion.RANGING_POTION, remaining))
    }

    /**
     * Rechecks carried spell costs and output space without sending repeated missing-supply messages.
     */
    private fun canContinue(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.inventory.hasSpaceFor(Item(itemId)) &&
        Magic.spellCost(bot, LEVEL, REQUIREMENTS) is SpellCost.Castable && (!profit || margin() > 0)

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        if (zone in wildernessZones && !handler.combat.pkRiskBudget().withinBudget) return false
        return super.travelToWorkZone(zone, bank)
    }

    /**
     * Finds only active visible public spawns in this work zone, excluding player loot.
     */
    private fun findTarget(): GroundItem? {
        val zone = activeZone ?: return null
        return world.locator.findItems(zone.area.centerPosition, zone.area.tileRadius, true) {
            it is PersistentGroundItem && it.id == itemId && it.state == EntityState.ACTIVE &&
                it.position in zone.area && it.isVisibleTo(bot)
        }.firstOrNull()
    }

    /**
     * Yields this source to another living player or bot in view inside its work area. This also detects
     * someone waiting for a respawn, without requiring an active wine or guessing their current activity.
     * Returning false from the work cycle lets the shared zone lifecycle try another source; if every
     * source is occupied or unavailable, the session ends rather than bouncing between occupied locations.
     */
    private fun sourceOccupied(): Boolean {
        val zone = activeZone ?: return false
        return world.locator.findPlayers(bot.position, Position.VIEWING_DISTANCE) {
            it !== bot && it.state == EntityState.ACTIVE && it.isAlive && it.position in zone.area
        }.isNotEmpty()
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!purposeAllowed()) { stop(); return true }
        if (outputStock() >= collectionGoal()) {
            if (herbloreContinuation) forceBanking = true else stop()
            return true
        }
        if (bot.inWilderness() && !handler.combat.pkRiskBudget().withinBudget) {
            handler.combat.fleeWilderness()
            return false
        }
        if (!productionReady(canContinue(), levelEligible = bot.magic.level >= LEVEL &&
                bot.spellbook == Spellbook.REGULAR)) return true
        if (sourceOccupied()) return false
        var target = findTarget()
        if (target == null) waitFor(45.seconds) {
            target = findTarget()
            target != null || sourceOccupied() || !isActionSafe()
        }
        if (sourceOccupied()) return false
        val selected = target
        attemptProduction(itemId, available = selected != null, outputId = itemId) {
            val item = selected ?: return@attemptProduction false
            if (!bot.position.isViewable(item.position) || !world.collisionManager.raycast(bot.position, item.position)) {
                if (!handler.webWalkTo(item.position, radius = 1, bank = BankAccess.NONE)) return@attemptProduction false
            }
            if (sourceOccupied()) return@attemptProduction false
            output.useSpellOnGroundItem(SPELL_ID, item)
        }
        return failures < 2 && !sourceOccupied()
    }

    override suspend fun finish() { bot.actions.first(TelekineticGrabAction::class.java)?.interrupt() }

    override fun snapshot(): TelegrabData = TelegrabData().also {
        it.itemId = itemId
        it.profit = profit
        it.herbloreContinuation = herbloreContinuation
        it.continued = continued
        saveInventoryState(it)
    }
}
