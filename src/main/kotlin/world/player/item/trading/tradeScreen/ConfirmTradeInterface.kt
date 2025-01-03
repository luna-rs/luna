package world.player.item.trading.tradeScreen

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.InventoryOverlayInterface
import world.player.item.trading.tradingWith
import java.text.NumberFormat
import java.util.*

/**
 * An [InventoryOverlayInterface] implementation representing the confirmation trading screen.
 *
 * @author lare96 
 */
class ConfirmTradeInterface(val offer: OfferTradeInterface) : InventoryOverlayInterface(3443, 3213) {
 // TODO declining here dupes items???
    companion object {

        /**
         * The number formatter.
         */
        val FORMATTER = NumberFormat.getInstance(Locale.UK)
    }

    /**
     * The player being traded with.
     */
    private val other = offer.other

    /**
     * The items being traded.
     */
    private val tradingItems = offer.items

    /**
     * The trading player's confirm instance (load lazily so its not initialized in
     * the constructor).
     */
    private val confirm by lazyVal {
        other.interfaces.get(ConfirmTradeInterface::class)!!
    }

    /**
     * If the "Accept" button has been clicked.
     */
    private var accepted = false

    /**
     * If the trade has been completed.
     */
    private var completed = false


    override fun onOpen(plr: Player) {

        // Display item names on confirmation screens.
        val itemText = computeItemsText()
        plr.sendText(itemText, 3557)
        other.sendText(itemText, 3558)
    }

    override fun onClose(plr: Player) {
        if (!completed) {
            // Interface closed when trade wasn't finished yet, decline.
            offer.completed = false
            offer.otherOffer.completed = false
            offer.onClose(plr)
        } else {
            // Trade completed successfully!
            plr.tradingWith = -1
        }
    }

    /**
     * Invoked when [plr] clicks the "Accept" button on the confirmation screen.
     */
    fun accept(plr: Player) {
        if (accepted) {
            return
        }

        if (confirm.accepted) {
            completed = true
            plr.inventory.updateAll(confirm.tradingItems, tradingItems)
            plr.interfaces.close()

            confirm.completed = true
            other.inventory.updateAll(tradingItems, confirm.tradingItems)
            other.interfaces.close()
        } else {
            accepted = true
            plr.sendText("Waiting for other player...", 3535)
            other.sendText("Other player has accepted.", 3535)
        }
    }

    /**
     * Returns the text of the items that will be displayed on confirmation screens.
     */
    private fun computeItemsText(): String {
        val size = tradingItems.size()
        if (size == 0) {
            // Size is 0, lookup not needed.
            return "Absolutely nothing!"
        }

        // Otherwise, iterate through items.
        val text = StringBuilder(size * 16 + 3)
        for (item in tradingItems) {
            item ?: continue

            if (text.isNotEmpty()) {
                // Newline if we're not on the first item.
                text.append("\\n")
            }

            // Append name and amount to the builder.
            text.append(item.itemDef.name)
            if (item.itemDef.isStackable) {
                val amountText = computeAmountText(item.amount)
                text.append(' ')
                    .append('x')
                    .append(' ')
                    .append(amountText)
            }
        }
        return text.toString()
    }

    /**
     * Returns [amount] as a String that will be displayed alongside item names.
     */
    private fun computeAmountText(amount: Int): String {
        val sb = StringBuilder(if (amount < 1_000) 6 else 36)
        return when {
            amount in 1_000 until 1_000_000 -> {
                sb.append("@cya@")
                    .append(amount / 1_000)
                    .append("K @whi@")
                    .append('(')
                    .append(FORMATTER.format(amount))
                    .append(')')
                    .toString()
            }
            amount >= 1_000_000 -> {
                sb.append("@gre@")
                    .append(amount / 1_000_000)
                    .append(' ')
                    .append("million @whi@")
                    .append('(')
                    .append(FORMATTER.format(amount))
                    .append(')').toString()
            }
            else -> sb.append(amount).toString()
        }
    }
}
