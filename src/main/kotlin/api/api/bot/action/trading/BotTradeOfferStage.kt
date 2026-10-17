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
 * Manages item additions, removals, and acceptance on the first trade screen. The bot can inspect either participant's
 * current offer and wait for the trade to advance to [BotTradeConfirmStage].
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
     * Whether the bot has clicked the accept button during this stage.
     *
     * This flag is set after sending the click and does not confirm that both participants accepted.
     * It is not reset if the trade subsequently times out or is declined.
     */
    var offered = false
        private set

    /**
     * Clicks accept on the current offer and waits for the confirmation screen.
     *
     * Performs one of three natural delays before checking whether [OfferTradeInterface] is open.
     * When the interface exists, sends the accept click and sets [offered] to `true`, then waits up
     * to [OFFER_WAIT_SECONDS] seconds for [ConfirmTradeInterface] to appear in the bot's overlays.
     *
     * Calls [decline] if the offer interface is missing or the confirmation screen does not appear
     * before the timeout. Reaching the confirmation screen does not complete the trade itself.
     *
     * @return The confirmation stage if its interface opens in time, or `null` if the trade is declined.
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
     * Attempts to move an item from the bot's inventory into its trade offer.
     *
     * Caps the requested quantity to the currently owned inventory quantity. Uses the built-in
     * offer options for amounts of `1`, `5`, and `10`, or Offer-All when the original request exceeds
     * available stock. Other quantities use Offer-X and the amount input overlay.
     *
     * Waits for the inventory quantity of the item's id to decrease. This confirms that some stock
     * moved out of the inventory, not necessarily that the entire requested quantity was offered.
     *
     * @param item The item id and quantity to offer.
     * @return `true` if the inventory quantity decreases, otherwise `false`.
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
     * Attempts to offer every non-null item in [items].
     *
     * Processes all entries even when an earlier offer fails. A successful offer contributes one
     * logged slot for a stackable item or the requested quantity for a non-stackable item.
     * This logged count is based on the input items and can differ from actual occupied slots if
     * requests were capped or multiple entries occupy a shared stack.
     *
     * @param items The items to offer; `null` entries are ignored.
     * @return `true` if every non-null item was offered successfully, otherwise `false`.
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
     * Attempts to return an item from the bot's trade offer to its inventory.
     *
     * Requires an open [OfferTradeInterface] and an existing offer entry for the specified id.
     * Caps the requested quantity to what is currently offered. Uses the built-in remove options
     * for amounts of `1`, `5`, and `10`, or Remove-All when the original request exceeds available
     * offer stock. Other quantities use Remove-X and the amount input overlay.
     *
     * Waits for the offered quantity to decrease. This confirms that some stock was removed from
     * the offer, not necessarily that the entire requested quantity was removed.
     *
     * @param item The item id and quantity to remove from the offer.
     * @return `true` if the offered quantity decreases, otherwise `false`.
     */
    suspend fun remove(item: Item): Boolean {
        bot.log("Removing ${name(item)}.")

        val offer = bot.overlays[OfferTradeInterface::class]
        if (offer == null) {
            bot.log("Trade offer screen isn't open.")
            return false
        }

        val tradeIndex = offer.items.computeIndexForId(item.id)
        if (tradeIndex == -1) {
            bot.log("I don't have ${name(item)} on the trade screen.")
            return false
        }

        val existingAmount = offer.items.computeAmountForId(item.id)
        var removeItem = item
        if (removeItem.amount > existingAmount) {
            removeItem = removeItem.withAmount(existingAmount)
        }

        val amount = removeItem.amount
        val clickWidget = when {
            amount == 1 -> 1
            amount == 5 -> 2
            amount == 10 -> 3
            item.amount > existingAmount -> 4
            else -> 5
        }

        val removeCond = SuspendableCondition {
            offer.items.computeAmountForId(item.id) < existingAmount
        }

        if (clickWidget != 5) {
            bot.output.sendItemWidgetClick(clickWidget, tradeIndex, 3415, removeItem.id)
            bot.log("Clicking offer option $clickWidget.")
            return removeCond.submit().await()
        }

        val amountCond = SuspendableCondition {
            NumberInput::class in bot.overlays
        }

        bot.output.sendItemWidgetClick(5, tradeIndex, 3415, removeItem.id)

        if (amountCond.submit().await()) {
            bot.log("Entering amount (${removeItem.amount}).")
            bot.output.enterAmount(removeItem.amount)
            return removeCond.submit().await()
        }

        bot.log("Could not open enter amount interface.")
        return false
    }


    /**
     * Returns the items currently offered by the other participant.
     *
     * Takes a snapshot of non-null items from the other player's offer container. Returns an empty
     * list if the other player has no open [OfferTradeInterface].
     *
     * @return The other participant's current offered items, excluding empty slots.
     */
    fun getOtherTradeItems(): List<Item> {
        val overlay = other.overlays[OfferTradeInterface::class] ?: return emptyList()
        return overlay.items.stream().filter { it != null }.toList()
    }

    /**
     * Returns the items currently offered by this bot.
     *
     * Takes a snapshot of non-null items from the bot's offer container. Returns an empty list
     * if the bot has no open [OfferTradeInterface].
     *
     * @return The bot's current offered items, excluding empty slots.
     */
    fun getTradeItems(): List<Item> {
        val overlay = bot.overlays[OfferTradeInterface::class] ?: return emptyList()
        return overlay.items.stream().filter { it != null }.toList()
    }

}
