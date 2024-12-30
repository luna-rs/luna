package api.item.dropTable.dsl;

import api.item.dropTable.DropTableItem
import io.luna.util.Rational

/**
 * Represents the receiver for building drop table item chances in our DSL.
 *
 * @author lare96
 */
class DropTableItemChanceReceiver(val id: Int, val amount: IntRange, val items: ArrayList<DropTableItem>) {

    /**
     * A constructor for single item amounts.
     */
    constructor(id: Int, amount: Int, items: ArrayList<DropTableItem>) :
            this(id, amount..amount, items)

    /**
     * Builds the chance value for this item.
     */
    infix fun chance(rational: Rational) {
        items += DropTableItem(id, amount, rational)
    }
}
