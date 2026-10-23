package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.content.partyRoom.PartyRoom
import game.content.partyRoom.dropParty.DropPartyInterface
import game.content.partyRoom.dropParty.DropPartyOption
import game.content.partyRoom.dropParty.DropPartyOption.depositItems
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Considers occasional donations shared by social and minigame dispatch. Both personality paths donate only surplus
 * above existing retention goals. A thirty-minute consideration cooldown and a one-in-four roll avoid repeated trips.
 * Donations are capped at 100,000 economy value and eight non-stackable items per visit.
 *
 * @author lare96
 */
object PartyRoomDonations {
    /**
     * The persisted epoch-millisecond time when this bot may next consider a donation.
     */
    private var Bot.nextPartyDonation by Attr.long().persist("next_party_donation")

    /**
     * Selects a bounded donation without purchasing supplies or reducing wanted-item reserves.
     */
    fun select(bot: Bot): BotScript? {
        val now = System.currentTimeMillis()
        if (now < bot.nextPartyDonation || !eligible(bot) || bot.depositItems.size() != 0 || DropPartyOption.chest.locked) return null
        bot.nextPartyDonation = now + 30 * 60_000L
        if (!rand(0.25)) return null
        val item = candidates(bot).randomOrNull() ?: return null
        return DepositPartyRoomItemBotScript(bot, item.id, item.amount, now + 180_000L)
    }

    /**
     * Kind minigame enthusiasts and unintelligent bots with significant trim candidates can volunteer.
     * The surplus requirement for the latter is enforced by candidates rather than treating intelligence as stock.
     */
    internal fun eligible(bot: Bot): Boolean =
        bot.personality.isKind && bot.preferences.likesActivity(BotActivity.MINIGAMES) || bot.personality.isDumb

    /**
     * Reuses merchanting's surplus thresholds and protects the same persistent minimum and target quantities.
     */
    internal fun candidates(bot: Bot): List<Item> = bot.preferences.wantedItemsToTrim().mapNotNull { (wanted, surplus) ->
        val definition = itemDef(wanted.id)
        val price = world.economy.getRealPrice(wanted.id)
        if (!definition.isTradeable || definition.isNoted || price <= 0.0 || !price.isFinite()) return@mapNotNull null
        val affordable = (100_000.0 / price).toInt()
        val count = minOf(surplus, affordable, if (definition.isStackable) Int.MAX_VALUE else 8)
        if (count > 0) Item(wanted.id, count) else null
    }
}

/**
 * Withdraws a single planned surplus donation and uses the ordinary chest interface to stage and confirm it.
 * A saved confirmation attempt is never repeated, including when interrupted before the result can be observed.
 * Bounded failures leave retained supplies intact and allow the progressive coordinator to continue.
 *
 * @param bot The bot donating its existing surplus.
 * @property itemId The unnoted item identifier authorized for donation.
 * @property amount The maximum authorized quantity, checked against live surplus before staging.
 * @property deadline The epoch-millisecond deadline for the entire donation attempt.
 * @property attempted Whether confirmation was already sent and must not be repeated.
 *
 * @author lare96
 */
class DepositPartyRoomItemBotScript(
    bot: Bot,
    private val itemId: Int,
    private val amount: Int,
    private val deadline: Long,
    private var attempted: Boolean = false
) : BotScript(bot) {
    /**
     * Persists the maximum authorized quantity and whether confirmation was already attempted.
     *
     * @author lare96
     */
    class DonationData : BotScriptData() {
        /**
         * The unnoted item identifier authorized for this donation.
         */
        var itemId = -1
        /**
         * The maximum authorized donation quantity before live surplus is checked again.
         */
        var amount = 0
        /**
         * The epoch-millisecond deadline shared by banking, travel and chest interactions.
         */
        var deadline = 0L
        /**
         * Whether confirmation was sent, preventing another attempt after restoration.
         */
        var attempted = false
        override fun load(data: JsonObject) {
            itemId = data.get("item_id").asInt
            amount = data.get("amount").asInt
            deadline = data.get("deadline").asLong
            attempted = data.get("attempted").asBoolean
        }
        override fun save(data: JsonObject) {
            data.addProperty("item_id", itemId)
            data.addProperty("amount", amount)
            data.addProperty("deadline", deadline)
            data.addProperty("attempted", attempted)
        }
    }

    /**
     * Restores the authorized donation and confirmation state for [bot] from [data].
     */
    constructor(bot: Bot, data: DonationData) : this(bot, data.itemId, data.amount, data.deadline, data.attempted)

    override suspend fun run(): Boolean {
        if (attempted || System.currentTimeMillis() >= deadline || amount <= 0 || bot.depositItems.size() != 0) return true
        withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1)) {
            if (!handler.banking.travelToBankDepositAll()) return@withTimeoutOrNull
            val surplus = PartyRoomDonations.candidates(bot).firstOrNull { it.id == itemId }?.amount ?: return@withTimeoutOrNull
            val quantity = minOf(amount, surplus)
            handler.banking.clickBankingMode(false)
            if (!handler.banking.travelToBankWithdraw(listOf(Item(itemId, quantity)))) return@withTimeoutOrNull
            if (!handler.widgets.clickCloseInterface() || !handler.travelTo(SubZone.PARTY_ROOM)) return@withTimeoutOrNull
            val chest = world.locator.findObjects(PartyRoom.TELEPORT_POSITIONS.first(), 20) { it.id == 2417 }
                .firstOrNull() ?: return@withTimeoutOrNull
            if (!handler.interactions.interact(1, chest) ||
                !waitFor(5.seconds) { bot.overlays.has(DropPartyInterface::class.java) }) return@withTimeoutOrNull
            // Travel can consume or acquire items. Never stage more than the original quantity or live surplus.
            val allowed = PartyRoomDonations.candidates(bot).firstOrNull { it.id == itemId }?.amount ?: return@withTimeoutOrNull
            val held = bot.inventory.computeAmountForId(itemId)
            if (held <= 0 || held > quantity || held > allowed || bot.depositItems.size() != 0) return@withTimeoutOrNull
            val index = bot.inventory.computeIndexForId(itemId)
            if (!output.sendItemWidgetClick(4, index, 5064, itemId) ||
                !waitFor(5.seconds) { bot.depositItems.computeAmountForId(itemId) == held }) return@withTimeoutOrNull
            attempted = true
            output.clickButton(2246)
            waitFor(5.seconds) { bot.depositItems.size() == 0 }
        }
        return true
    }

    override suspend fun finish() {
        // Native close returns any unconfirmed items; cancellation must not clear staged ownership.
        if (bot.overlays.has(DropPartyInterface::class.java)) bot.overlays.closeWindows()
    }

    override fun snapshot() = DonationData().also {
        it.itemId = itemId
        it.amount = amount
        it.deadline = deadline
        it.attempted = attempted
    }
}
