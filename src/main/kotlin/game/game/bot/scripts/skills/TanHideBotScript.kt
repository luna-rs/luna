package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.hideTanning.TanInterface
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils.roll
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tans hides by interacting with a tanner NPC.
 *
 * This script searches the bot's bank for an untanned hide, withdraws enough coins and hides for one inventory cycle,
 * counts startup supplies across inventory and bank, then recalculates each withdrawal from bank stock after deposit.
 * It requests only missing hides or an unaffordable tanning fee, preserving existing wanted targets and metadata.
 * The script travels to a tanner, opens the tanning interface, and clicks the matching "tan all" button for the selected hide.
 * Carried coins must cover every carried hide before tan-all is attempted; otherwise another bank visit balances
 * the batch. Bank withdrawals use the inherited bounded, unnoted verification and must deliver both coins
 * and hides. Shared production and banking budgets bound repeated failures and survive saves; only input
 * consumption or a verified withdrawal resets the corresponding counter.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
class TanHideBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = mutableListOf(SubZone.AL_KHARID_BANK)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true
    companion object {
        /** Saved tanning session configuration and inherited retry counters. @author lare96 */
        class TanningData : InventoryScriptData()

        /** Shared by all tanning scripts, with at most one entry per configured zone. */
        private val tanners = mutableMapOf<SubZone, Npc>()

        private fun findTanner(zone: SubZone): Npc? {
            tanners[zone]?.let {
                if (it.state == EntityState.ACTIVE && it.id in TanInterface.TANNER_IDS &&
                    it.position.z == zone.inside.z) return it
            }
            return world.locator.findNearestNpc(zone.inside) {
                it.state == EntityState.ACTIVE && it.id in TanInterface.TANNER_IDS
            }.also { if (it == null) tanners.remove(zone) else tanners[zone] = it }
        }
    }

    // todo canifis tannery, crafting guild
    /**
     * Recreates a hide-tanning script from saved zone and duration data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved script data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration, data.zones) {
        if (data is InventoryScriptData) restoreInventoryState(data)
    }

    /**
     * The hide type selected for the current tanning cycle.
     *
     * This is resolved from owned startup supplies or deposited bank stock in [withdrawBatch] and reused by the execution loop to choose the correct tanning
     * interface button.
     */
    private var tanning: Hide? = null

    override suspend fun onInventoryBankRequested(): Boolean {
        if (tanning == null) return false
        return requestProductionBank(hasTanningSupplies())
    }

    /** The native tan-all handler requires coins for all carried hides, rather than just one conversion. */
    private fun hasTanningSupplies(): Boolean {
        val hide = tanning ?: return false
        val amount = bot.inventory.computeAmountForId(hide.hide)
        return amount > 0 && bot.inventory.computeAmountForId(995).toLong() >= hide.cost.toLong() * amount
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (tanning == null) { stop(); return true }
        if (!productionReady(hasTanningSupplies())) return true
        val tanner = findTanner(activeZone!!)
        bot.log("Attempting to interact with tanner NPC.")
        if (tanner == null) {
            bot.log("No active tanner NPC found.")
            stop()
            return false
        }
        // Travel has its own deadline; the production-start budget is only fifteen seconds.
        val approached = bot.position.isViewable(tanner.position) || withTimeoutOrNull(60.seconds) {
            handler.widgets.clickCloseInterface() && isActionSafe() &&
                handler.webWalkTo(tanner.position, radius = 1, bank = BankAccess.NONE)
        } == true
        attemptProduction(tanning!!.hide,
            available = approached && bot.position.isViewable(tanner.position)) {
            startTanning(tanning!!, tanner)
        }
        return true
    }

    /** Opens the normal tanning interface and selects its existing tan-all button; shared hooks verify progress. */
    private suspend fun startTanning(hide: Hide, tanner: Npc): Boolean {
        if (!handler.interactions.interact(2, tanner)) {
            return false
        }
        if (!waitFor(3.seconds) { TanInterface::class in bot.overlays }) return false
        val button = TanInterface.hideToMakeAll[hide] ?: return false
        output.clickButton(button)
        return true
    }

    override fun bankWithdraw(): List<Item> = withdrawBatch(bankOnly = true)

    override fun withdraw(): List<Item> = withdrawBatch(bankOnly = false)

    /**
     * Counts owned startup supplies, or bank-only supplies after deposit, and chooses an affordable partial batch.
     * Cowhide defaults to soft leather; hard leather is considered only when its level and fee are available.
     */
    private fun withdrawBatch(bankOnly: Boolean): List<Item> {
        if (!bankOnly && productionRetriesExhausted()) { stop(); return emptyList() }
        fun amount(id: Int) = if (bankOnly) bot.bank.computeAmountForId(id) else bot.itemTracker.count(id)
        val coins = amount(995)
        for ((id, configured) in Hide.HIDE_TO_HIDE) {
            val stock = amount(id)
            if (stock < 1) continue
            val hide = if (configured == Hide.HARD_LEATHER) {
                val canCraftHard = bot.crafting.staticLevel >= HideArmor.HARD_LEATHER_BODY.level
                if (canCraftHard && coins >= Hide.HARD_LEATHER.cost && roll(1 of 4)) Hide.HARD_LEATHER else Hide.SOFT_LEATHER
            } else configured
            val batch = minOf(stock, 27, coins / hide.cost)
            if (batch > 0) {
                tanning = hide
                return listOf(Item(995, hide.cost * batch), Item(hide.hide, batch))
            }
        }
        stop()
        requestMissingSupplies()
        bot.log("No affordable untanned hide batch, ending script.")
        return emptyList()
    }

    /**
     * Requests untanned hides only when none are owned, and coins only when even the cheapest owned recipe
     * is unaffordable. Cowhide's soft-leather fee is included when computing that minimum. Targets represent
     * total stock and never lower existing demand or replace its skill, level, priority, and purpose metadata.
     */
    private fun requestMissingSupplies() {
        val ownedHides = Hide.entries.filter { bot.itemTracker.count(it.hide) > 0 }
        if (ownedHides.isEmpty()) Hide.HIDE_TO_HIDE.keys.forEach { bot.preferences.raiseWantedItemTarget(it, 500) }
        val minimumFee = ownedHides.minOfOrNull { it.cost } ?: Hide.SOFT_LEATHER.cost
        if (bot.itemTracker.count(995) < minimumFee) bot.preferences.raiseWantedItemTarget(995, 100_000)
    }

    override fun snapshot(): TanningData = TanningData().also { saveInventoryState(it) }

    override suspend fun equipment(): BotGearLocator? {
        if (bot.personality.isIntelligent) {
            return BotGearSelector.find(bot, setOf(BotGearPurpose.SKILLING)).buildLocator()
        }
        return null
    }
}
