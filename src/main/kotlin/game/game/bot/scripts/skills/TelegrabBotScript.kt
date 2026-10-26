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
 *
 * @property itemId The unnoted world-spawned item to collect.
 * @property profit Whether guide value must exceed the actual planned rune costs.
 * @author lare96
 */
class TelegrabBotScript(bot: Bot, val itemId: Int, val profit: Boolean, duration: Duration, zones: MutableList<SubZone>) :
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

            override fun load(data: JsonObject) {
                super.load(data)
                itemId = data.get("itemId")?.asInt ?: WINE
                profit = data.get("profit")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("itemId", itemId)
                data.addProperty("profit", profit)
            }
        }
    }

    /**
     * Restores the resource and purpose without retaining live ground-item references.
     */
    constructor(bot: Bot, data: TelegrabData) : this(bot, data.itemId, data.profit, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Current guide margin after staff and combination-rune substitutions in the supplied plan.
     */
    fun margin(costs: List<Item> = bot.productionRuneCosts(REQUIREMENTS)): Long =
        world.economy.getPrice(itemId) - costs.sumOf { world.economy.getTotalPrice(it) }

    /**
     * Checks spellbook, permanent Magic and build caps, minimum stock, and the total owned output target.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= LEVEL &&
        BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) && bot.itemTracker.count(itemId) < STOCK_TARGET &&
        bot.ownsProductionSupplies(bot.productionRuneCosts(REQUIREMENTS)) && (!profit || margin() > 0)

    /**
     * Withdraws a small cast budget, reserving room for every output and limiting it to banked rune stock.
     */
    fun bankBatch(): List<Item> {
        val costs = bot.productionRuneCosts(REQUIREMENTS, bankedOnly = true)
        val casts = minOf(bot.inventory.capacity() - costs.size, STOCK_TARGET - bot.itemTracker.count(itemId),
            costs.minOfOrNull { bot.bank.computeAmountForId(it.id) / it.amount } ?: 0)
        if (casts < 1 || (profit && margin(costs) <= 0)) return emptyList()
        return costs.map { Item(it.id, it.amount * casts) }
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        resetProductionFailures()
    }

    override suspend fun equipment(): BotGearLocator? = null
    override suspend fun beforeInventoryInit(): Boolean = !bot.inWilderness() || handler.combat.fleeWilderness()

    override fun withdraw(): List<Item> = productionWithdraw(bot.productionRuneCosts(REQUIREMENTS), levelEligible =
        bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= LEVEL && bot.itemTracker.count(itemId) < STOCK_TARGET)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

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
        if (bot.itemTracker.count(itemId) >= STOCK_TARGET) { stop(); return true }
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
        saveInventoryState(it)
    }
}
