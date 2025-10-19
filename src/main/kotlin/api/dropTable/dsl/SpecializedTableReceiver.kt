package api.dropTable.dsl

import api.dropTable.DropTable
import api.dropTable.dsl.DropTableItemReceiver.ImmutableDropTableItemReceiver

/**
 * Enables the transfer of items to a specialized [DropTable] type in our DSL.
 *
 * @author lare96
 */
open class SpecializedTableReceiver(val receiver: DropTableItemReceiver) {

    /**
     * Transfers the items in [receiver] to the returned table.
     */
    fun <E : DropTable> table(function: ImmutableDropTableItemReceiver.() -> E): E {
        return function(ImmutableDropTableItemReceiver(receiver.items))
    }
}