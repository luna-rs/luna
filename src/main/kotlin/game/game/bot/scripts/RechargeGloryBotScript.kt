package game.bot.scripts

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotGearLocator
import game.item.degradable.jewellery.TeleportJewellery
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import kotlin.time.Duration

/**
 * Recharges owned empty and partially charged glories at the Fountain of Heroes through item-on-object packets.
 * The native fountain handler replaces each carried amulet with a four-charge glory. No jewellery is purchased,
 * spawned or removed from worn equipment, and no skill experience is awarded.
 *
 * [InventoryBotScript] supplies banking, travel, session expiry, progress detection and persistent failure budgets.
 * Each bank trip packs at most one inventory of mixed glory variants. Fully charged, noted and worn amulets are
 * excluded; completed batches are banked normally. Saved visits recheck live stock and resolve the fountain afresh.
 *
 * @param bot The bot restoring its travel supplies.
 * @param duration Maximum session duration.
 * @param zones Fountain work areas reached through the shared travel layer and its existing fallback.
 * @author lare96
 */
class RechargeGloryBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = mutableListOf(SubZone.HEROES_GUILD_FOUNTAIN)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Native fountain object and its revision 377 placement, also present in Lost City's m45_154 map.
         */
        const val FOUNTAIN = 2638
        val FOUNTAIN_POSITION = Position(2917, 9893)

        /**
         * Native charge variants with empty amulets first; four-charge and noted glories never need processing.
         */
        val RECHARGEABLE = listOf(requireNotNull(TeleportJewellery.AMULET_OF_GLORY.emptyId)) +
            TeleportJewellery.AMULET_OF_GLORY.items.drop(1)

        /**
         * Retains the shared duration, zones and exhausted retry budgets across pauses and saved script stacks.
         *
         * @author lare96
         */
        class GloryData : InventoryScriptData()
    }

    /**
     * Only the native confirmation opened by this activity belongs to its cleanup.
     */
    private var confirmation: DialogueInterface? = null

    /**
     * Restores the shared lifecycle state without replenishing interaction or banking attempts.
     */
    constructor(bot: Bot, data: GloryData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * A carried or banked unnoted glory with fewer than four charges makes this preparation useful.
     */
    fun isEligible(): Boolean = RECHARGEABLE.any {
        bot.bank.computeAmountForId(it) > 0 || bot.inventory.computeAmountForId(it) > 0
    }

    /**
     * Packs banked variants into one inventory, including smaller final batches and without treating them as pairs.
     */
    fun bankBatch(): List<Item> {
        var slots = bot.inventory.capacity()
        return buildList {
            for (id in RECHARGEABLE) {
                val amount = minOf(slots, bot.bank.computeAmountForId(id))
                if (amount > 0) { add(Item(id, amount)); slots -= amount }
            }
        }
    }

    override suspend fun equipment(): BotGearLocator? = null

    override fun withdraw(): List<Item> {
        val id = RECHARGEABLE.firstOrNull {
            bot.bank.computeAmountForId(it) > 0 || bot.inventory.computeAmountForId(it) > 0
        }
        if (id == null) { stop(); return emptyList() }
        return productionWithdraw(listOf(Item(id)), requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean =
        requestProductionBank(RECHARGEABLE.any { bot.inventory.contains(it) })

    override suspend fun onExecuteInZone(): Boolean {
        val id = RECHARGEABLE.firstOrNull { bot.inventory.contains(it) }
        if (!productionReady(id != null)) return true
        val fountain = world.locator.findObjectsOnTile(FOUNTAIN_POSITION) {
            it.id == FOUNTAIN && it.state == EntityState.ACTIVE
        }.firstOrNull()
        attemptProduction(id!!, available = fountain != null) {
            fountain != null && handler.inventory.useItem(id).onObject(fountain)
        }
        confirmation = bot.overlays.getOverlay(DialogueInterface::class.java)?.takeIf { it.id == 306 }
        if (confirmation != null) handler.widgets.clickCloseInterface()
        return true
    }

    /**
     * Closes only the captured fountain confirmation; native inventory conversions need no rollback or cleanup.
     */
    private fun closeConfirmation() {
        if (confirmation != null && bot.overlays.getOverlay(DialogueInterface::class.java) === confirmation)
            bot.overlays.closeWindows()
        confirmation = null
    }

    override fun onPaused() { closeConfirmation() }
    override suspend fun finish() { closeConfirmation() }
    override fun snapshot() = GloryData().also { saveInventoryState(it) }
}
