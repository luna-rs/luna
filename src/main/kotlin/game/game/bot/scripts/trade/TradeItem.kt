package game.bot.scripts.trade

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item

/**
 * Represents an item being considered or offered during a bot trade.
 *
 * A trade item stores the item id, amount, and expected unit or total price used by the trade script. Equality is based
 * only on [id], so two [TradeItem] instances with the same item id are treated as the same trade entry even if their
 * amount or price differs.
 *
 * @property id The item id.
 * @property amount The amount of this item.
 * @property price The expected price for this trade item.
 * @author lare96
 */
data class TradeItem(val id: Int, val amount: Int, val price: Int) {

    /**
     * Returns whether another object represents the same trade item id.
     *
     * Amount and price are intentionally ignored so trade items can be grouped or looked up by item id.
     *
     * @param other The object to compare against.
     *
     * @return `true` if [other] is a [TradeItem] with the same [id].
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TradeItem) return false
        if (id != other.id) return false
        return true
    }

    /**
     * Returns a hash code based only on [id].
     *
     * This matches [equals], allowing [TradeItem] instances to behave correctly in hash-based collections.
     *
     * @return The item id.
     */
    override fun hashCode(): Int {
        return id
    }

    /**
     * Converts this trade item into a normal game [Item].
     *
     * @return An [Item] with this trade item's [id] and [amount].
     */
    fun toItem() = Item(id, amount)

    /**
     * Resolves the display name for this trade item's item id.
     *
     * @return The item definition name.
     */
    fun getName(): String = ItemDefinition.ALL.retrieve(id).name
}