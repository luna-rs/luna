package api.item.dropTable.dsl

import api.item.dropTable.DropTable

/**
 * Enables the transfer of items to a specialized [DropTable] type in our DSL.
 *
 * @author lare96
 */
open class SpecializedTableReceiver(val receiver: DropTableItemReceiver) {

    /**
     * Transfers the items in [receiver] to the returned table.
     */
    fun <E : DropTable> table(function: DropTableItemReceiver.() -> E): E {
        return function(receiver)
    }
}