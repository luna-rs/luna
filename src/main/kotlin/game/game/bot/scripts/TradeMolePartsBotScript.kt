package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trades owned, unnoted mole claws and skins with Wyson through native Talk-to and the explicit exchange option.
 * The native dialogue replaces each part with its nest reward; this script never creates rewards or removes inputs.
 * Falador east bank is the staging area, with normal webwalking to Wyson before each bounded dialogue attempt.
 * Banking, verified unnoted withdrawals, progress checks and saved retry budgets use [InventoryBotScript].
 *
 * @param bot The bot exchanging its owned mole parts.
 * @param duration The inherited activity budget.
 * @param zones Existing Falador bank staging areas, retained by saved sessions.
 * @author lare96
 */
class TradeMolePartsBotScript(bot: Bot, duration: Duration,
                            zones: MutableList<SubZone> = mutableListOf(SubZone.FALADOR_EAST_BANK)) :
    InventoryBotScript(bot, duration, zones) {

    companion object {
        /**
         * Native Wyson inputs and the only dialogue response this activity may select.
         */
        const val WYSON = 36
        val PARTS = setOf(7416, 7418)
        const val RESPONSE = "Okay, I will trade the mole parts."

        /**
         * Persists the inherited duration, staging zone and independent banking/interaction retry budgets.
         *
         * @author lare96
         */
        class MolePartsData : InventoryScriptData()
    }

    override val verifiedProductionWithdrawals = true

    /**
     * Queue opened by the native Talk-to interaction. Single-entry confirmations reuse this queue,
     * so cleanup also checks the actual displayed page before touching its window.
     */
    private var ownedDialogue: DialogueQueue? = null

    /**
     * Last recognized page from this exchange; foreign single-entry overlays may reuse the same queue.
     */
    private var ownedPage: DialogueInterface? = null

    /**
     * The exact option page whose exchange response was queued, permitting only its matching confirmation next.
     */
    private var selectedOption: OptionDialogue? = null

    /**
     * Restores a session without retaining a stale NPC, dialogue queue or trusted withdrawal result.
     */
    constructor(bot: Bot, data: MolePartsData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Enables this general activity only for parts already present in inventory or bank.
     */
    fun isEligible(): Boolean = PARTS.any { bot.bank.computeAmountForId(it).toLong() +
        bot.inventory.computeAmountForId(it) > 0 }

    /**
     * Fills one inventory with available unnoted parts. Native rewards replace the same slots one for one.
     */
    fun bankBatch(): List<Item> {
        var remaining = bot.inventory.capacity()
        return PARTS.mapNotNull { id ->
            val amount = minOf(remaining, bot.bank.computeAmountForId(id))
            if (amount <= 0) null else Item(id, amount).also { remaining -= amount }
        }
    }

    override fun withdraw(): List<Item> {
        val id = PARTS.firstOrNull { bot.bank.computeAmountForId(it).toLong() +
            bot.inventory.computeAmountForId(it) > 0 } ?: run { stop(); return emptyList() }
        return productionWithdraw(listOf(Item(id)), requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasParts())

    override suspend fun onExecuteInZone(): Boolean {
        val input = PARTS.firstOrNull { bot.inventory.contains(it) }
        if (!productionReady(input != null)) return true
        val npc = world.locator.findNearestNpc(SubZone.FALADOR_EAST_BANK.inside, 80) {
            it.id == WYSON && it.state == EntityState.ACTIVE
        }
        val approached = npc != null && handler.interactions.approach(npc, timeout = 60.seconds,
            canContinue = { isActionSafe() && hasParts() })
        if (!isActionSafe()) return true
        attemptProduction(input!!, available = approached) {
            npc != null && exchange(npc, input)
        }
        return true
    }

    /**
     * Keeps dialogue advancement with the existing reflex, selecting only the native mole-parts option.
     * Speaker or unique option text establishes ownership. A foreign queue or an unrecognized replacement page
     * aborts without touching its UI, including single-entry dialogues that reuse the original queue.
     * The inherited production deadline bounds this whole conversation, including the native confirmation.
     */
    private suspend fun exchange(npc: Npc, input: Int): Boolean {
        val before = bot.inventory.computeAmountForId(input)
        val previous = bot.dialogues
        try {
            if (!handler.interactions.interact(1, npc) {
                    isActionSafe() && npc.state == EntityState.ACTIVE && hasParts()
                }) return false
            while (isActionSafe()) {
                if (bot.inventory.computeAmountForId(input) < before) return true
                val page = bot.overlays.getOverlay(DialogueInterface::class.java)
                if (ownedDialogue == null) {
                    val expected = page is NpcDialogue && page.npcId == WYSON ||
                        page is OptionDialogue && RESPONSE in page.options
                    if (expected && bot.dialogues != null && bot.dialogues !== previous) {
                        ownedDialogue = bot.dialogues
                        ownedPage = page
                    }
                }
                if (ownedDialogue != null && !refreshOwnedPage()) return false
                if (page is OptionDialogue && selectedOption == null && ownedDialogue != null) {
                    if (RESPONSE !in page.options || !handler.widgets.clickDialogueOption(RESPONSE)) return false
                    selectedOption = page
                }
                delay(1.seconds)
            }
            return false
        } finally {
            closeOwnedDialogue()
        }
    }

    /**
     * Counts carried parts without requiring spare output slots for the native in-place exchange.
     */
    private fun hasParts(): Boolean = PARTS.any { bot.inventory.contains(it) }

    /**
     * Recognizes only the native next page in this exchange. Queue identity alone cannot authorize cleanup:
     * the native builder can replace an overlay without replacing its queue. The confirmation is accepted only
     * after this script selected the exchange option and its displayed lines match the player's exact response.
     */
    private fun refreshOwnedPage(): Boolean {
        val queue = ownedDialogue ?: return false
        if (bot.dialogues !== queue) return false
        val page = bot.overlays.getOverlay(DialogueInterface::class.java) ?: return false
        if (page === ownedPage) return true
        val expected = if (selectedOption == null) page is OptionDialogue && RESPONSE in page.options
            else page is PlayerDialogue && page.lines == listOf(RESPONSE)
        if (expected) ownedPage = page
        return expected
    }

    /**
     * Cancels only a recognized page of this exchange without Continue, preventing a pending reward callback.
     * Rechecking the page also covers confirmation arriving after the last poll but before cancellation.
     */
    private fun closeOwnedDialogue() {
        if (refreshOwnedPage()) {
            bot.overlays.closeWindows()
            bot.resetDialogues()
        }
        ownedDialogue = null
        ownedPage = null
        selectedOption = null
    }

    override fun onPaused() { closeOwnedDialogue() }
    override suspend fun finish() { closeOwnedDialogue() }
    override fun snapshot() = MolePartsData().also { saveInventoryState(it) }
}
