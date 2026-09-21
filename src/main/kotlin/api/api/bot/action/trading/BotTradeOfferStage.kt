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
 * Handles item offering on the first trade screen for a bot.
 *
 * This stage is responsible for moving items from the bot's inventory into the active trade offer.
 * It expects the first trade interface, [OfferTradeInterface], to already be open before offer actions
 * are attempted.
 *
 * Offer amounts are resolved against the bot's current inventory before clicking. If the requested
 * amount is larger than the amount available, the offer is capped to the amount  he bot actually has.
 *
 * @param bot The bot performing the trade actions.
 * @param other The player being traded with.
 * @author lare96
 */
class BotTradeOfferStage(bot: Bot,
                         other: Player,
                         purpose: TradePurpose,
                         lastStage: BotTradeStage?) : BotTradeStage(bot, other, purpose, lastStage) {

    /**
     * If the accept button was pressed.
     */
    var offered = false
        private set

    /**
     * Clicks the accept button on the first trade screen.
     *
     * This only sends the accept click when [OfferTradeInterface] is currently open. It does not wait
     * for the trade to advance to the next confirmation screen.
     *
     * @return `true` if the offer interface was open and the accept click was sent, otherwise `false`.
     */
    suspend fun offerAndAwait(): BotTradeConfirmStage? {
        bot.log("Added items to offer screen.")
        if(randBoolean()) {
            bot.naturalDecisionDelay()
        } else if(randBoolean()) {
            bot.naturalDelay()
        } else {
            bot.naturalMicroDelay()
        }
        if (OfferTradeInterface::class in bot.overlays) {
            bot.output.clickButton(3420)
            bot.log("Clicked accept button on offer interface, waiting for ${other.username}...")
            offered = true
            return if (waitFor(OFFER_WAIT_SECONDS.seconds) { ConfirmTradeInterface::class in bot.overlays }) {
                bot.log("${other.username} accepted, now on confirm screen.")
                BotTradeConfirmStage(bot, other, purpose,  this)
            } else {
                bot.log("${other.username} took too long to accept.")
                decline()
                return null
            }
        } else {
            bot.log("Offer trade interface is not open, declining trade.")
            decline()
            return null
        }
    }

    /**
     * Offers an item into the current trade.
     *
     * The requested amount is capped to the amount available in the bot's inventory. Small exact
     * amounts use the built-in offer options, over-sized requests use `Offer-All`, and all other
     * amounts use `Offer-X`.
     *
     * This method waits for the bot's inventory amount to decrease after the offer action. A decreased
     * inventory amount is treated as confirmation that the item was successfully moved into the trade
     * offer.
     *
     * @param item The item id and requested amount to offer.
     * @return `true` if the item was offered and the bot's inventory amount decreased, otherwise `false`.
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
     * Offers each item in a collection into the current trade.
     *
     * Every item is attempted even if an earlier offer fails. This allows callers to make a best-effort
     * trade offer while still detecting whether the full requested offer could not be completed.
     *
     * @param items The items to offer.
     * @return `true` if one or more item offers failed, otherwise `false`.
     */
      suspend fun offerAll(items: Collection<Item>): Boolean {
        var failed = false
        for (it in items) {
            if (!offer(it)) {
                failed = true
            }
        }
        return !failed
    }

}