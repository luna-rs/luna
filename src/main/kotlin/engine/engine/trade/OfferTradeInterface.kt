package engine.trade

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.RefreshListener
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.InventoryOverlayInterface
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter
import io.luna.net.msg.out.WidgetItemsMessageWriter
import io.luna.util.NumberUtils

/**
 * An [InventoryOverlayInterface] representing the initial trading screen.
 *
 * Manages a player's offered items, synchronizes both trading interfaces,
 * and handles acceptance before advancing to the confirmation screen.
 *
 * @property other The player on the opposite side of the trade.
 * @author lare96
 */
class OfferTradeInterface(val other: Player) : InventoryOverlayInterface(3323, 3321) {

    /**
     * A [RefreshListener] that synchronizes offer updates between both players.
     *
     * Updates the owning player's offer panel and the corresponding panel
     * displayed to the other player.
     *
     * @property plr The player whose offer container is being observed.
     */
    inner class OfferListener(private val plr: Player) : RefreshListener() {

        override fun displayUpdate(items: ItemContainer, updateItems: List<IndexedItem>,
                                   msg: WidgetIndexedItemsMessageWriter) {

            plr.queue(msg) // Send to left panel.
            other.queue(WidgetIndexedItemsMessageWriter(3416, updateItems)) // Send to right panel.
        }
    }

    /**
     * The container holding items offered by this player.
     */
    val items = ItemContainer(28, ItemContainer.StackPolicy.STANDARD, 3415)

    /**
     * The opposing player's [OfferTradeInterface], resolved lazily.
     */
    val otherOffer by lazyVal {
        other.overlays[OfferTradeInterface::class]!!
    }

    /**
     * Whether this player has accepted the current offer.
     */
    var accepted = false

    /**
     * Whether the offer stage has successfully advanced to confirmation.
     */
    var completed = false

    /**
     * Initializes the trading interface, inventory overlay, and offer panels.
     */
    override fun onOpen(plr: Player) {
        sendTradingWith(plr)
        updateTotalValue(plr)
        plr.sendText("", 3431)
        plr.sendText("Are you sure you want to make this trade?", 3535)

        // Refresh inventory to trade inventory.
        plr.inventory.setSecondaryWidget(3322)
        plr.inventory.updateSecondaryWidget(plr)

        // Clear left and right trade panels.
        val clearLeftMsg = WidgetItemsMessageWriter(3415, listOf())
        val clearRightMsg = WidgetItemsMessageWriter(3416, listOf())
        plr.queue(clearLeftMsg)
        plr.queue(clearRightMsg)

        items.setListeners(OfferListener(plr))
    }

    /**
     * Cancels an incomplete trade, returning offered items to the player
     * and closing the opposing player's trading interface.
     */
    override fun onClose(plr: Player) {
        if (!completed) {
            plr.tradingWith = -1

            plr.inventory.clearSecondaryWidget()
            plr.inventory.addAll(items)
            plr.sendMessage("The trade has been declined.")

            other.overlays.closeWindows()
        }
    }

    /**
     * Marks the trade as incomplete if this interface is replaced by
     * anything other than a [ConfirmTradeInterface].
     */
    override fun onReplace(plr: Player, replace: AbstractOverlay) {
        if (replace::class != ConfirmTradeInterface::class) {
            completed = false
        }
    }

    /**
     * Transfers items from the player's inventory into their trade offer.
     *
     * Rejects untradeable items and limits the transfer to the available
     * quantity. A successful transfer resets both players' acceptance states.
     *
     * @param plr The player offering the items.
     * @param index The inventory slot containing the item.
     * @param amount The quantity to offer, or `-1` for all available.
     */
    fun add(plr: Player, index: Int, amount: Int) {
        val inv = plr.inventory
        val item = inv.get(index) ?: return

        if (!itemDef(item.id).isTradeable) {
            plr.sendMessage("This item cannot be traded.")
            return
        }

        val addItem = reduceAmount(item, amount, inv)
        if (inv.remove(index, addItem)) {
            items.add(addItem)
            otherOffer.sendTradingWith(other)
            resetAccept(plr)
            updateTotalValue(plr)
        }
    }

    /**
     * Withdraws items from the player's trade offer back into their inventory.
     *
     * Limits the withdrawal to the available quantity and resets both
     * players' acceptance states after a successful removal.
     *
     * @param plr The player withdrawing the items.
     * @param index The offer slot containing the item.
     * @param amount The quantity to withdraw, or `-1` for all available.
     */
    fun remove(plr: Player, index: Int, amount: Int) {
        val item = items.get(index) ?: return

        val removeItem = reduceAmount(item, amount, items)
        if (items.remove(index, removeItem)) {
            plr.inventory.add(removeItem)
            otherOffer.sendTradingWith(other)
            resetAccept(plr)
            updateTotalValue(plr)
        }
    }

    /**
     * Accepts the current trade offer if the opposing player has sufficient
     * inventory space to receive the offered items.
     *
     * If the opposing player has already accepted, both players advance
     * to the [ConfirmTradeInterface]. Otherwise, this player is marked
     * as accepted and waits for the opposing player's acceptance.
     *
     * @param plr The player accepting the offer.
     */
    fun accept(plr: Player) {
        if (accepted) {
            return
        }

        if (!other.inventory.hasSpaceForAll(items)) {
            plr.sendMessage("${other.username} does not have enough inventory space for your items.")
            return
        }

        if (otherOffer.accepted) {
            completed = true
            otherOffer.completed = true
            val confirm = ConfirmTradeInterface(this)
            val otherConfirm = ConfirmTradeInterface(otherOffer)
            plr.overlays.open(confirm)
            other.overlays.open(otherConfirm)
        } else {
            accepted = true
            plr.sendText("Waiting for other player...", 3431)
            other.sendText("Other player has accepted", 3431)
        }
    }

    /**
     * Returns a copy of [item] with its quantity limited to the amount
     * available in [container].
     *
     * For non-stackable items, the available quantity is calculated across
     * the container. A requested amount of `-1` selects all available items.
     *
     * @param item The item whose quantity is being adjusted.
     * @param reqAmt The requested quantity, or `-1` for all available.
     * @param container The container used to determine availability.
     * @return An item with the adjusted quantity.
     */
    private fun reduceAmount(item: Item, reqAmt: Int, container: ItemContainer): Item {
        val id = item.id
        val amount = item.amount
        val hasAmount = if (itemDef(id).isStackable) amount else container.computeAmountForId(id)
        return when {
            // Modify amount if there's less in than requested.
            reqAmt == -1 || reqAmt > hasAmount -> item.withAmount(hasAmount)
            else -> item.withAmount(reqAmt)
        }
    }

    /**
     * Updates the trading partner's name, rank icon, and remaining inventory
     * space displayed on the player's trading interface.
     *
     * @param plr The player receiving the interface update.
     */
    private fun sendTradingWith(plr: Player) {
        val username = other.username
        val sb = StringBuilder(username.length + 5)
        sb.append(username)
        when (other.rights) {
            RIGHTS_MOD -> sb.append("@cr1@")
            RIGHTS_ADMIN, RIGHTS_DEV -> sb.append("@cr2@")
            else -> Unit
        }

        val remaining = other.inventory.computeRemainingSize()
        plr.sendText("Trading with: $sb who has @gre@$remaining free slots", 3417)
    }

    /**
     * Calculates the total value of this player's offered items and updates
     * the corresponding value labels on both trading interfaces.
     *
     * @param plr The player whose offer value is being calculated.
     */
    private fun updateTotalValue(plr: Player) {
        val text = if (items.size() == 0) "Current value ~ 0" else {
            val value = items.filterNotNull().sumOf { world.economy.getTotalPrice(it) }
            "Current value ~ ${NumberUtils.formatPrice(value)}"
        }
        plr.sendText(text, 3419)
        other.sendText(text, 3418)
    }

    /**
     * Resets both players' acceptance states and clears their acceptance
     * messages after an offer modification.
     *
     * @param plr The player whose offer was modified.
     */
    private fun resetAccept(plr: Player) {
        accepted = false
        otherOffer.accepted = false
        plr.sendText("", 3431)
        other.sendText("", 3431)
    }
}
