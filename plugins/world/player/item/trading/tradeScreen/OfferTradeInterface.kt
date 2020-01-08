package world.player.item.trading.tradeScreen

import api.predef.*
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.RefreshListener
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AbstractInterface
import io.luna.game.model.mob.inter.InventoryOverlayInterface
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * An [InventoryOverlayInterface] implementation representing the offer trading screen.
 */
class OfferTradeInterface(val other: Player) : InventoryOverlayInterface(3323, 3321) {

    /**
     * A [RefreshListener] that listens for items on the offer screen.
     */
    inner class OfferListener(private val plr: Player) : RefreshListener() {

        override fun displayUpdate(items: ItemContainer, updateItems: List<IndexedItem>,
                                   msg: WidgetIndexedItemsMessageWriter) {

            plr.queue(msg) // Send to left panel.
            other.queue(WidgetIndexedItemsMessageWriter(3416, updateItems)) // Send to right panel.
        }
    }

    /**
     * The items being offered.
     */
    val items = ItemContainer(28, ItemContainer.StackPolicy.STANDARD, 3415)

    /**
     * The trading player's offer instance (load lazily so its not initialized in the constructor).
     */
    val otherOffer by lazyVal {
        other.interfaces.get(OfferTradeInterface::class)!!
    }

    /**
     * If the "Accept" button has been clicked.
     */
    var accepted = false

    override fun onOpen(plr: Player) {
        sendTradingWith(plr)
        plr.sendText("", 3431)
        plr.sendText("Are you sure you want to make this trade?", 3535)

        // Refresh inventory to trade inventory.
        plr.inventory.setSecondaryRefresh(3322)
        plr.inventory.refreshSecondary(plr)

        // Clear left and right trade panels.
        val clearLeftMsg = WidgetItemsMessageWriter(3415, listOf())
        val clearRightMsg = WidgetItemsMessageWriter(3416, listOf())
        plr.queue(clearLeftMsg)
        plr.queue(clearRightMsg)

        items.setListeners(OfferListener(plr))
    }

    override fun onClose(plr: Player) {
        // Trade was declined.
        plr.tradingWith = -1

        plr.inventory.resetSecondaryRefresh()
        plr.inventory.addAll(items)

        other.interfaces.close()
    }

    override fun onReplace(plr: Player, replace: AbstractInterface) {
        // If replacing interface isn't the confirm screen, decline.
        if (replace::class != ConfirmTradeInterface::class) {
            onClose(plr)
        }
    }

    /**
     * Adds [amount] item from inventory slot [index] to the offer screen.
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
        }
    }

    /**
     * Removes [amount] item from trade screen slot [index] to the inventory.
     */
    fun remove(plr: Player, index: Int, amount: Int) {
        val item = items.get(index) ?: return

        val removeItem = reduceAmount(item, amount, items)
        if (items.remove(index, removeItem)) {
            plr.inventory.add(removeItem)
            otherOffer.sendTradingWith(other)
            resetAccept(plr)
        }
    }

    /**
     * Invoked when [plr] clicks the "Accept" button on the offer screen.
     */
    fun accept(plr: Player) {
        if (accepted) {
            return
        }

        if (other.inventory.computeRemainingSize() < items.size()) {
            plr.sendMessage("${other.username} does not have enough inventory space for your items.")
            return
        }

        if (otherOffer.accepted) {
            val confirm = ConfirmTradeInterface(this)
            val otherConfirm = ConfirmTradeInterface(otherOffer)
            plr.interfaces.open(confirm)
            other.interfaces.open(otherConfirm)
        } else {
            accepted = true
            plr.sendText("Waiting for other player...", 3431)
            other.sendText("Other player has accepted", 3431)
        }
    }

    /**
     * Returns an new amount instance of [item] with [reqAmt] reduced to the amount currently contained
     * within [container].
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
     * Displays the trading Player's name and remaining free space for [plr].
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
     * Resets the accept button click for [plr] and the trading Player.
     */
    private fun resetAccept(plr: Player) {
        accepted = false
        otherOffer.accepted = false
        plr.sendText("", 3431)
        other.sendText("", 3431)
    }
}