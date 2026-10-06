package api.bot.action.trading

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.SuspendableCondition
import api.bot.action.trading.BotTradeManager.OFFER_WAIT_SECONDS
import api.predef.*
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.overlay.NumberInput
import kotlin.time.Duration.Companion.seconds

/**
 * Handles the offer stage of a bot trade.
 *
 * This stage manages the first trade screen, where the bot can add items to its offer and accept the trade. Once both
 * participants accept, the trade advances to a [BotTradeConfirmStage].
 *
 * @param bot The bot performing the trade.
 * @param other The player trading with the bot.
 * @param lastStage The stage that preceded this one.
 * @author lare96
 */
class BotTradeOfferStage(
    bot: Bot,
    other: Player,
    val lastStage: BotTradeStage
) : BotTradeStage(bot, other) {

    /**
     * Whether this bot has accepted the current offer.
     */
    var offered = false
        private set

    /**
     * Accepts the current offer and waits for the trade to advance to the confirmation screen.
     *
     * A short natural delay is performed before accepting. After the accept button is clicked, this method waits up to
     * [OFFER_WAIT_SECONDS] for the other participant to accept and the [ConfirmTradeInterface] to open.
     *
     * The trade is declined if the offer interface is no longer open or the other participant does not accept before
     * the timeout expires.
     *
     * @return The resulting confirmation stage if both participants accept, or `null` if the trade is declined.
     */
    suspend fun offerAndAwait(): BotTradeConfirmStage? {
        bot.log("Added items to offer screen.")
        if (randBoolean()) {
            bot.naturalDecisionDelay()
        } else if (randBoolean()) {
            bot.naturalDelay()
        } else {
            bot.naturalMicroDelay()
        }

        val offerInterface = bot.overlays[OfferTradeInterface::class]
        if (offerInterface != null) {
            bot.output.clickButton(3420)
            bot.log("Clicked accept button on offer interface, waiting for ${other.username}...")
            offered = true

            return if (waitFor(OFFER_WAIT_SECONDS.seconds) { ConfirmTradeInterface::class in bot.overlays }) {
                bot.log("${other.username} accepted, now on confirm screen.")
                BotTradeConfirmStage(bot, other, this)
            } else {
                bot.log("${other.username} took too long to accept.")
                decline()
                null
            }
        } else {
            bot.log("Offer trade interface is not open, declining trade.")
            decline()
            return null
        }
    }

    /**
     * Adds an item from the bot's inventory to the current trade offer.
     *
     * The requested amount is capped to the amount currently held in the inventory. Amounts of `1`, `5`, and `10` use
     * their respective built-in offer options. Requests exceeding the available amount use `Offer-All`, while other
     * amounts use `Offer-X`.
     *
     * The operation is considered successful once the amount of the item in the bot's inventory decreases.
     *
     * @param item The item and amount to offer.
     * @return `true` if at least some of the item was successfully moved into the trade offer.
     */
    suspend fun offer(item: Item): Boolean {
        bot.log("Offering ${name(item)}.")

        val offer = bot.overlays[OfferTradeInterface::class]
        if (offer == null) {
            bot.log("Trade offer screen isn't open.")
            return false
        }

        val inventoryIndex = bot.inventory.computeIndexForId(item.id)
        if (inventoryIndex == -1) {
            bot.log("I don't have ${name(item)}.")
            return false
        }

        val existingAmount = bot.inventory.computeAmountForId(item.id)
        var depositItem = item
        if (depositItem.amount > existingAmount) {
            depositItem = depositItem.withAmount(existingAmount)
        }

        val amount = depositItem.amount
        val clickWidget = when {
            amount == 1 -> 1
            amount == 5 -> 2
            amount == 10 -> 3
            item.amount > existingAmount -> 4
            else -> 5
        }

        val depositCond = SuspendableCondition {
            bot.inventory.computeAmountForId(item.id) < existingAmount
        }

        if (clickWidget != 5) {
            bot.output.sendItemWidgetClick(clickWidget, inventoryIndex, 3322, depositItem.id)
            bot.log("Clicking offer option $clickWidget.")
            return depositCond.submit().await()
        }

        val amountCond = SuspendableCondition {
            NumberInput::class in bot.overlays
        }

        bot.output.sendItemWidgetClick(5, inventoryIndex, 3322, depositItem.id)

        if (amountCond.submit().await()) {
            bot.log("Entering amount (${depositItem.amount}).")
            bot.output.enterAmount(depositItem.amount)
            return depositCond.submit().await()
        }

        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * Adds each item in [items] to the current trade offer.
     *
     * Null entries are ignored and every non-null item is attempted even if an earlier offer fails.
     *
     * The number of trade slots occupied by successfully offered items is logged after all items have been processed.
     * Stackable items occupy one slot, while non-stackable items occupy one slot per item.
     *
     * @param items The items to offer.
     * @return `true` if every non-null item was successfully offered, otherwise `false`.
     */
    suspend fun offerAll(items: Collection<Item?>): Boolean {
        var failed = false
        var count = 0

        for (it in items) {
            if (it == null) {
                continue
            } else if (!offer(it)) {
                failed = true
            } else if (it.itemDef.isStackable) {
                count++
            } else {
                count += it.amount
            }
        }

        bot.log("Offered items taking up $count slots on the trade screen.")
        return !failed
    }

    /**
     * Returns the items currently being offered by the other participant.
     *
     * @return A snapshot of the other participant's offer, or an empty list if their offer interface is not open.
     */
    fun getOtherTradeItems(): List<Item> {
        val overlay = other.overlays[OfferTradeInterface::class] ?: return emptyList()
        return overlay.items.toList()
    }
}