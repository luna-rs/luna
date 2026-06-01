package api.bot.action

import api.bot.SuspendableCondition
import api.predef.*
import api.predef.ext.*
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.overlay.NumberInput

/**
 * Handles bot actions related to player-to-player trading.
 *
 * This handler covers sending trade requests, accepting or declining trade screens, and offering inventory items into
 * the active trade container. It intentionally performs actions through normal client outputs so bot trades behave like
 * regular player trades.
 *
 * @param bot The bot performing trade actions.
 * @param handler The parent action handler.
 * @author lare96
 */
class BotTradingActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Sends a trade request to another player.
     *
     * The current implementation only attempts the interaction when the target player has the expected context menu
     * entry available.
     *
     * @param plr The player to trade with.
     * @return `true` if the trade interaction was submitted.
     */
    suspend fun sendTradeRequest(plr: Player): Boolean {
        if (PlayerContextMenuOption.FOLLOW in plr.contextMenu) {
            return handler.interactions.interact(4, plr)
        }
        return false
    }

    /**
     * Clicks the accept button on the first trade screen.
     *
     * @return `true` if the offer trade interface was open and the click was sent.
     */
    fun clickInitialAccept(): Boolean {
        if (OfferTradeInterface::class in bot.overlays) {
            bot.output.clickButton(3420)
            return true
        }
        return false
    }

    /**
     * Clicks the accept button on the final trade confirmation screen.
     *
     * @return `true` if the confirmation trade interface was open and the click was sent.
     */
    fun clickFinalAccept(): Boolean {
        if (ConfirmTradeInterface::class in bot.overlays) {
            bot.output.clickButton(3546)
            return true
        }
        return false
    }

    /**
     * Declines the current trade by closing open trade windows.
     */
    fun clickDecline() {
        bot.overlays.closeWindows()
    }

    /**
     * Offers an item into the current trade.
     *
     * The amount is capped to the amount the bot actually has in its inventory. Small exact amounts use the direct
     * offer options, over-sized requests use "Offer-All", and all other amounts use "Offer-X".
     *
     * @param item The item and amount to offer.
     * @return `true` if the inventory amount changed after the offer action.
     */
    suspend fun offer(item: Item): Boolean {
        bot.log("Offering ${name(item)}.")

        val offer = bot.overlays[OfferTradeInterface::class]
        if (offer == null) {
            // Trade offer screen is not open.
            bot.log("Trade offer screen isn't open.")
            return false
        }

        val inventoryIndex = bot.inventory.computeIndexForId(item.id)
        if (inventoryIndex == -1) {
            // We don't have the item.
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

        // Unsuspend when the inventory amount changes.
        val depositCond = SuspendableCondition {
            bot.inventory.computeAmountForId(item.id) < existingAmount
        }

        if (clickWidget != 5) {
            // Click offer 1, 5, 10, or all.
            bot.output.sendItemWidgetClick(clickWidget, inventoryIndex, 3322, depositItem.id)
            bot.log("Clicking offer option $clickWidget.")
            return depositCond.submit().await()
        }

        // Click offer X.
        val amountCond = SuspendableCondition {
            NumberInput::class in bot.overlays
        }

        bot.output.sendItemWidgetClick(5, inventoryIndex, 3322, depositItem.id)

        // Wait until amount input interface is open.
        if (amountCond.submit().await()) {
            bot.log("Entering amount (${depositItem.amount}).")
            bot.output.enterAmount(depositItem.amount)
            return depositCond.submit().await()
        }

        bot.log("Could not open enter amount interface.")
        return false
    }

    /**
     * Offers a collection of items into the current trade.
     *
     * Each item is attempted even if an earlier offer fails.
     *
     * @param items The items to offer.
     * @return `true` if at least one offer failed.
     */
    suspend fun offerAll(items: Collection<Item>): Boolean {
        var failed = false
        for (it in items) {
            if (!offer(it)) {
                failed = true
            }
        }
        return failed
    }
}