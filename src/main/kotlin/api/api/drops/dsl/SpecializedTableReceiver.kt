package api.drops.dsl

import api.drops.DropTable
import api.drops.dsl.DropTableItemReceiver.ImmutableDropTableItemReceiver

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