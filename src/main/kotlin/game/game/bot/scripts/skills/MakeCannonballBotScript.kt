package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.bot.zone.ProductionZones
import api.predef.*
import api.predef.ext.*
import game.skill.smithing.Smithing
import game.skill.smithing.makeCannonball.MakeCannonballAction
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMMO_MOULD
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.STEEL_BAR
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.CANNONBALL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMOUNT
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.LEVEL
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Converts owned steel bars into cannonballs through the normal steel-bar-on-furnace interaction.
 *
 * A bank batch contains one ammo mould and up to twenty-seven bars. Each player action consumes one bar,
 * creates four stackable cannonballs, and awards the existing Smithing experience. The first bar frees
 * space for a new cannonball stack, so a full inventory can begin production without banking again.
 *
 * [InventoryBotScript] supplies verified unnoted withdrawals, travel, safety checks, and independent
 * bounded banking and production retries. Missing startup supplies request total targets of three moulds
 * or 1,000 bars. Furnace objects are resolved inside the active zone for every interaction, using the
 * same supported object IDs as the player handler. Snapshots retain duration, zones, and retry budgets.
 *
 * @param bot The bot producing cannonballs.
 * @param duration Session duration managed by the inherited zone lifecycle.
 * @param zones Candidate banked furnace zones.
 * @author lare96
 */
class MakeCannonballBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        internal const val RECIPE_KEY = "cannonballs"

        /** Existing banked furnace routes shared with ore-smelting bots. */
        val DEFAULT_ZONES = ProductionZones.FURNACES

        private val materials = listOf(Item(STEEL_BAR))
        private val tools = setOf(AMMO_MOULD)

        internal fun hasSupplies(bot: Bot): Boolean =
            bot.smithing.staticLevel >= LEVEL && bot.ownsProductionSupplies(materials, tools)

        /**
         * Saved session configuration and persistent production and banking retry budgets.
         *
         * @author lare96
         */
        class CannonballData : InventoryScriptData()
    }

    /** Restores configuration and retry budgets; live supplies and safety are validated again on startup. */
    constructor(bot: Bot, data: CannonballData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent Smithing level and owned bars and mould across inventory and bank. */
    fun isEligible(): Boolean = hasSupplies(bot)

    /** Withdraws one mould and up to twenty-seven bars, limited by actual bank stock. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> = productionWithdraw(materials, tools, bot.smithing.staticLevel >= LEVEL)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(canContinue())

    /** Checks carried prerequisites and output capacity, including the slot freed by consuming one bar. */
    private fun canContinue(): Boolean = bot.inventory.containsAll(materials) &&
            bot.inventory.contains(AMMO_MOULD) &&
            bot.inventory.computeSpaceFor(Item(CANNONBALL, AMOUNT)) <= bot.inventory.computeRemainingSize() + 1

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(canContinue(), bot.smithing.level >= LEVEL)) return true
        attemptProduction(STEEL_BAR, outputId = CANNONBALL) { startProduction() }
        return true
    }

    /**
     * Uses the native furnace menu. Each Make-1 keeps the planner's per-bar progress and cancellation checks.
     */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val furnace = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.position in zone.area && it.id in Smithing.FURNACE_OBJECTS
        }.firstOrNull() ?: return false
        if (!handler.inventory.useItem(STEEL_BAR).onObject(furnace)) return false
        if (!waitFor(3.seconds) { bot.overlays[MakeItemDialogue::class]?.length == 1 }) return false
        if (!isActionSafe() || !canContinue()) return false
        handler.widgets.clickMakeItem(0, 1)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeCannonballAction::class.java)?.interrupt()
    }

    override fun snapshot(): CannonballData = CannonballData().also { saveInventoryState(it) }
}
