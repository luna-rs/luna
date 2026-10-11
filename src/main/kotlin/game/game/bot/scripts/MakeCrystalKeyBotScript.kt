package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import game.content.crystalChest.MakeCrystalKeyActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Assembles crystal keys from owned tooth and loop halves using the normal item-on-item interaction.
 *
 * [InventoryBotScript] manages banking, travel, session expiry, and weak-action gating. Each bank visit
 * withdraws up to fourteen balanced pairs, limited by the smaller available stock. The make dialogue starts
 * [MakeCrystalKeyActionItem], which consumes both halves and creates a key without experience or a skill
 * requirement. The general-activities coordinator selects this task when both halves are owned.
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
    override val verifiedProductionWithdrawals = true

    companion object {
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
        class CrystalKeyData : InventoryScriptData()
    }

    /** One of each half, the minimum inputs for a single crystal key. */
    private val materials = listOf(Item(TOOTH_HALF), Item(LOOP_HALF))

    /**
     * Restores the session configuration and retry budgets from saved state.
     * Normal lifecycle hooks recheck current supplies and bot safety before assembly resumes.
     *
     * @param bot The bot that owns the saved session.
     * @param data Previously serialized crystal-key assembly state.
     */
    constructor(bot: Bot, data: CrystalKeyData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Whether the bot owns at least one of each half across inventory and bank. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced banked half pairs, or an empty batch when either half is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete pair of key halves. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials())) return true
        attemptProduction(materials.first().id) { startProduction() }
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
        saveInventoryState(it)
    }
}
