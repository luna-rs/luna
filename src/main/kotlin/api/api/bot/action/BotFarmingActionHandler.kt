package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.farming.Farming
import game.skill.farming.Farming.storedTools
import game.skill.farming.toolLeprechaun.StorableTool
import game.skill.farming.toolLeprechaun.ToolLeprechaunInterface
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Exchanges genuinely owned farming supplies through the native tool-leprechaun NPC and storage interface.
 * Storage stays in Farming's persisted varbits; this helper never changes quantities or watering-can doses itself.
 * Scripts supply their normal action-safety predicate, which is checked after travel and before every exchange.
 *
 * Retrieval precedes ordinary inventory-script initialization, bringing only missing visit supplies into tracked
 * ownership. Existing banking then handles the full visit. Optional storage stays near the completed plot and
 * respects native single-tool/bulk limits. It never makes a special trip just to put tools away.
 *
 * @param bot The gardener.
 * @param handler The shared navigation, banking and widget actions.
 * @author lare96
 */
class BotFarmingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {
    companion object {
        /**
         * Existing farm travel zones with live native leprechaun spawns; no separate route or NPC is created.
         */
        private val SERVICES = listOf(SubZone.FALADOR_FARM, SubZone.CATHERBY_FARM, SubZone.ARDOUGNE_FARM)

        /**
         * Portable tracked stock, excluding worn gear because native farming handlers need inventory tools.
         */
        fun portableStock(bot: Bot, id: Int): Long =
            (bot.itemTracker.count(id).toLong() - bot.equipment.computeAmountForId(id)).coerceAtLeast(0)

        /**
         * Native storage for this exact item identity. Watering cans retain their stored dose rather than being
         * treated as interchangeable or refilled. Empty storage avoids reading unrelated varbit definitions.
         */
        fun storedStock(bot: Bot, id: Int): Int {
            if (bot.storedTools == 0) return 0
            val tool = StorableTool.entries.firstOrNull { id in it.items } ?: return 0
            return tool.getAmount(bot).takeIf { tool.getDisplayItem(bot) == id } ?: 0
        }

        /**
         * Supplies available to farming selection, including native storage but excluding worn equipment.
         * Ordinary banking/production ownership is deliberately unchanged until a native retrieval succeeds.
         */
        fun stock(bot: Bot, id: Int): Long = portableStock(bot, id) + storedStock(bot, id)

        /**
         * Whether a supplied visit must retrieve something before the normal bank/inventory lifecycle can start.
         */
        fun needsStorage(bot: Bot, items: List<Item>): Boolean = items.any { portableStock(bot, it.id) < it.amount }
    }

    /**
     * Finds a live leprechaun near a mapped plot, including its ordinary wandering range.
     */
    private fun leprechaun(zone: SubZone) = world.locator.findNpcs(zone.inside, 32) {
        it.id == 3021 && it.state == EntityState.ACTIVE && it.position.z == zone.inside.z
    }.minByOrNull { bot.position.computeLongestDistance(it.position) }

    /**
     * Opens Exchange through the usual web-walker NPC interaction. Retrieval may use the shared zone travel
     * fallback first; optional end-of-visit storage only approaches a leprechaun near the current plot.
     */
    private suspend fun open(zone: SubZone, travel: Boolean, allowed: () -> Boolean): Boolean {
        if (!allowed() || travel && !handler.travelTo(zone) || !allowed()) return false
        val npc = leprechaun(zone) ?: return false
        return handler.interactions.interact(2, npc, allowed) && waitFor(3.seconds) {
            allowed() && ToolLeprechaunInterface::class in bot.overlays
        }
    }

    /**
     * Sends one widget-defined Store/Remove request. Bulk quantities use native 1, 5 or X options; the native
     * handler validates capacity and storage. The caller verifies both sides of the resulting exchange.
     */
    private suspend fun exchange(tool: StorableTool, storing: Boolean, amount: Int, id: Int,
                                 allowed: () -> Boolean): Boolean {
        if (!allowed()) return false
        val inter = bot.overlays[ToolLeprechaunInterface::class] ?: return false
        val single = tool in StorableTool.SINGLE
        val widget = when {
            storing && single -> ToolLeprechaunInterface.STORE_SINGLE_WIDGET
            storing -> ToolLeprechaunInterface.STORE_BULK_WIDGET
            single -> ToolLeprechaunInterface.REMOVE_SINGLE_WIDGET
            else -> ToolLeprechaunInterface.REMOVE_BULK_WIDGET
        }
        val option = if (single || amount == 1) 1 else if (amount == 5) 2 else 4
        if (!handler.widgets.clickContainerAction(inter.id, widget, option, tool.slot, id)) return false
        if (option != 4) return true
        return waitFor(3.seconds) { allowed() && io.luna.game.model.mob.overlay.NumberInput::class in bot.overlays } &&
            allowed() && handler.widgets.enterAmount(amount)
    }

    /**
     * Retrieves missing portions of one visit's requirements, preserving quantities already in bank or inventory.
     * Space is obtained through existing banking only when necessary. Failure ends the preparation attempt; already
     * retrieved items remain owned. All travel, clicks and progress waits share one bounded deadline.
     */
    suspend fun retrieve(items: List<Item>, preferred: SubZone?, allowed: () -> Boolean): Boolean =
        withTimeoutOrNull(120.seconds) {
            val requirements = items.groupBy { it.id }.map { (id, entries) -> Item(id, entries.maxOf { it.amount }) }
            val missing = requirements.mapNotNull {
                val amount = (it.amount - portableStock(bot, it.id)).coerceAtLeast(0).toInt()
                if (amount == 0) null else Item(it.id, amount)
            }
            if (missing.isEmpty()) return@withTimeoutOrNull allowed()
            if (!allowed() || missing.any { storedStock(bot, it.id) < it.amount } ||
                missing.sumOf { it.amount.toLong() } > bot.inventory.capacity()) return@withTimeoutOrNull false
            if (bot.inventory.computeRemainingSize() < missing.sumOf { it.amount } &&
                (!handler.banking.travelToBankDepositAll() || !allowed())) return@withTimeoutOrNull false
            val zone = preferred?.takeIf { leprechaun(it) != null } ?: SERVICES.filter { leprechaun(it) != null }
                .minByOrNull { bot.position.computeLongestDistance(it.inside) } ?: return@withTimeoutOrNull false
            if (!open(zone, true, allowed)) return@withTimeoutOrNull false
            try {
                for (item in missing) {
                    if (!allowed() || storedStock(bot, item.id) < item.amount) return@withTimeoutOrNull false
                    val tool = StorableTool.entries.first { item.id in it.items }
                    val before = bot.inventory.computeAmountForId(item.id)
                    val stored = tool.getAmount(bot)
                    if (!exchange(tool, false, item.amount, item.id, allowed) || !waitFor(3.seconds) {
                        allowed() && bot.inventory.computeAmountForId(item.id) == before + item.amount &&
                            tool.getAmount(bot) == stored - item.amount
                    }) return@withTimeoutOrNull false
                }
                requirements.all { portableStock(bot, it.id) >= it.amount }
            } finally {
                if (ToolLeprechaunInterface::class in bot.overlays) handler.widgets.clickCloseInterface()
            }
        } == true

    /**
     * Puts carried farming tools, empty buckets and unused fertilizer away at a nearby leprechaun after a plot
     * visit. Native caps and can identity are respected; seeds, produce and worn gear are never selected.
     * A rejected or interrupted exchange stops this optional cleanup without blocking the next activity.
     */
    suspend fun store(zone: SubZone, allowed: () -> Boolean): Boolean = withTimeoutOrNull(30.seconds) {
        if (!allowed()) return@withTimeoutOrNull false
        val tools = StorableTool.entries.filter { tool ->
            tool.getAmount(bot) < tool.max && tool.items.any { it in bot.inventory }
        }
        if (tools.isEmpty()) return@withTimeoutOrNull true
        if (leprechaun(zone) == null || !open(zone, false, allowed)) return@withTimeoutOrNull false
        try {
            for (tool in tools) {
                if (!allowed()) return@withTimeoutOrNull false
                val id = tool.items.lastOrNull { it in bot.inventory } ?: continue
                val before = bot.inventory.computeAmountForId(id)
                val stored = tool.getAmount(bot)
                val amount = minOf(before, tool.max - stored)
                if (amount <= 0) continue
                if (!exchange(tool, true, amount, id, allowed) || !waitFor(3.seconds) {
                    allowed() && bot.inventory.computeAmountForId(id) == before - amount &&
                        tool.getAmount(bot) == stored + amount && tool.getDisplayItem(bot) == id
                }) return@withTimeoutOrNull false
            }
            true
        } finally {
            if (ToolLeprechaunInterface::class in bot.overlays) handler.widgets.clickCloseInterface()
        }
    } == true
}
