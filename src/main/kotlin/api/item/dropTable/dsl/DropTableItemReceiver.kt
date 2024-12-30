package api.item.dropTable.dsl

import api.item.dropTable.DropTableItem
import io.luna.game.model.def.ItemDefinition
import io.luna.util.Rational

/**
 * Represents the receiver for building drop table items in our DSL.
 *
 * @author lare96
 */
class DropTableItemReceiver(val items: ArrayList<DropTableItem>, private val noted: Boolean) {

    /**
     * Returns an instance of this receiver that works with noted items.
     */
    fun noted(action: DropTableItemReceiver.() -> Unit) = action(DropTableItemReceiver(items, true))

    /**
     * Adds an additional chance to receive nothing as a drop.
     */
    infix fun nothing(chance: Rational) {
        items += DropTableItem(-1, 0, chance)
    }

    /**
     * Adds the id value to the item.
     */
    infix fun String.x(amount: IntRange): DropTableItemChanceReceiver {
        return DropTableItem.computeId(this, noted).x(amount)
    }

    /**
     * Adds the id value to the item.
     */
    infix fun String.x(amount: Int): DropTableItemChanceReceiver {
        return DropTableItem.computeId(this, noted).x(amount)
    }

    /**
     * Adds the amount value to the item.
     */
    infix fun Int.x(amount: IntRange) = DropTableItemChanceReceiver(this, amount, items)

    /**
     * Adds the amount value to the item.
     */
    infix fun Int.x(amount: Int) = DropTableItemChanceReceiver(this, amount, items)
}